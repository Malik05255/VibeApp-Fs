import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const OAUTH_TTL_MS = 10 * 60 * 1000;
const TOKEN_SKEW_MS = 60 * 1000;
const MCP_STATELESS = "2026-07-28";
const MCP_LEGACY = "2025-11-25";

Deno.serve(async (req) => {
  const url = new URL(req.url);
  const path = routePath(url.pathname);
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRole) {
    return json({ ok: false, error: "Supabase runtime credentials unavailable" }, 500);
  }

  const db = createClient(supabaseUrl, serviceRole, {
    auth: { persistSession: false },
  });

  try {
    if (req.method === "GET" && ["/", "/health", "/status"].includes(path)) {
      const { data: creds } = await db
        .from("h_runtime_credentials")
        .select("id,expires_at,resource_url,updated_at")
        .eq("id", "peach_default")
        .maybeSingle();
      const { data: tools } = await db
        .from("h_runtime_mcp_tools")
        .select("name,description,discovered_at")
        .order("name");

      return json({
        ok: true,
        service: "h-whatsapp-peach",
        connected: Boolean(creds),
        tokenExpiresAt: creds?.expires_at ?? null,
        resourceUrl: creds?.resource_url ?? null,
        toolCount: tools?.length ?? 0,
        tools: (tools ?? []).map((tool) => tool.name),
        updatedAt: creds?.updated_at ?? null,
      });
    }

    if (req.method === "GET" && path === "/connect") {
      const setup = url.searchParams.get("setup")?.trim();
      if (!setup) return html("رابط إعداد H غير صالح أو ناقص.", 400);

      const { data: setupRow } = await db
        .from("h_runtime_setup_links")
        .select("token,expires_at,used_at")
        .eq("token", setup)
        .maybeSingle();

      if (
        !setupRow ||
        setupRow.used_at ||
        new Date(setupRow.expires_at).getTime() <= Date.now()
      ) {
        return html("انتهت صلاحية رابط إعداد H. اطلب رابطًا جديدًا.", 403);
      }

      const functionBase = `${url.origin}/functions/v1/h-whatsapp-peach`;
      const redirectUri = `${functionBase}/callback`;
      const metadata = await discoverOAuth();
      const clientId = await registerClient(metadata, redirectUri);
      const verifier = randomUrlSafe(64);
      const state = randomUrlSafe(32);
      const challenge = await pkceChallenge(verifier);

      const { error } = await db.from("h_runtime_oauth_pending").insert({
        state,
        verifier,
        redirect_uri: redirectUri,
        client_id: clientId,
        token_endpoint: metadata.tokenEndpoint,
        resource_url: metadata.resourceUrl,
        scope: metadata.scope,
        expires_at: new Date(Date.now() + OAUTH_TTL_MS).toISOString(),
      });
      if (error) throw error;

      const auth = new URL(metadata.authorizationEndpoint);
      auth.searchParams.set("response_type", "code");
      auth.searchParams.set("client_id", clientId);
      auth.searchParams.set("redirect_uri", redirectUri);
      auth.searchParams.set("code_challenge", challenge);
      auth.searchParams.set("code_challenge_method", "S256");
      auth.searchParams.set("state", state);
      auth.searchParams.set("resource", metadata.resourceUrl);
      if (metadata.scope) auth.searchParams.set("scope", metadata.scope);
      return Response.redirect(auth.toString(), 302);
    }

    if (req.method === "GET" && path === "/callback") {
      const state = url.searchParams.get("state")?.trim();
      const code = url.searchParams.get("code")?.trim();
      const oauthError = url.searchParams.get("error")?.trim();
      if (!state) return html("Peach لم يُرجع حالة OAuth المطلوبة.", 400);

      const { data: pending } = await db
        .from("h_runtime_oauth_pending")
        .select("*")
        .eq("state", state)
        .maybeSingle();

      if (!pending || new Date(pending.expires_at).getTime() <= Date.now()) {
        return html("جلسة ربط Peach انتهت صلاحيتها. أعد المحاولة من رابط جديد.", 403);
      }
      if (oauthError) return html(`Peach رفض التفويض: ${escapeHtml(oauthError)}`, 400);
      if (!code) return html("Peach لم يُرجع رمز التفويض.", 400);

      const tokenResponse = await fetch(pending.token_endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "authorization_code",
          code,
          redirect_uri: pending.redirect_uri,
          client_id: pending.client_id,
          code_verifier: pending.verifier,
          resource: pending.resource_url,
        }),
      });
      const tokenText = await tokenResponse.text();
      if (!tokenResponse.ok) {
        throw new Error(
          `Peach token exchange failed (${tokenResponse.status}): ${tokenText.slice(0, 300)}`,
        );
      }
      const token = JSON.parse(tokenText);
      if (!token.access_token) throw new Error("Peach token response did not include access_token");

      const expiresAt = Number.isFinite(Number(token.expires_in))
        ? new Date(Date.now() + Number(token.expires_in) * 1000).toISOString()
        : null;

      const { error: credentialError } = await db
        .from("h_runtime_credentials")
        .upsert(
          {
            id: "peach_default",
            access_token: token.access_token,
            refresh_token: token.refresh_token ?? null,
            token_type: token.token_type ?? "Bearer",
            scope: token.scope ?? pending.scope ?? null,
            expires_at: expiresAt,
            client_id: pending.client_id,
            token_endpoint: pending.token_endpoint,
            resource_url: pending.resource_url,
            oauth_metadata: {},
            updated_at: new Date().toISOString(),
          },
          { onConflict: "id" },
        );
      if (credentialError) throw credentialError;

      await db.from("h_runtime_oauth_pending").delete().eq("state", state);
      await db
        .from("h_runtime_setup_links")
        .update({ used_at: new Date().toISOString() })
        .is("used_at", null);

      let tools: any[] = [];
      let probeError: string | null = null;
      try {
        tools = await listMcpTools(db);
        await db.from("h_runtime_mcp_tools").delete().neq("name", "__never__");
        const rows = tools
          .map((tool) => ({
            name: String(tool.name ?? ""),
            description: String(tool.description ?? ""),
            input_schema: tool.inputSchema ?? tool.input_schema ?? {},
            discovered_at: new Date().toISOString(),
          }))
          .filter((tool) => tool.name);
        if (rows.length) {
          await db.from("h_runtime_mcp_tools").upsert(rows, { onConflict: "name" });
        }
      } catch (error) {
        probeError = errorMessage(error);
      }

      return html(`
        <div dir="rtl" style="font-family:system-ui;max-width:640px;margin:48px auto;padding:24px">
          <h2>تم ربط H السحابي مع Peach ✅</h2>
          <p>تم حفظ الاتصال داخل خادم H.</p>
          <p>أدوات Peach المكتشفة: <strong>${tools.length}</strong></p>
          ${probeError ? `<p style="color:#9a6700">تم الربط، لكن فحص الأدوات يحتاج إعادة محاولة: ${escapeHtml(probeError)}</p>` : ""}
          <p>ارجع إلى ChatGPT واكتب: <strong>تم</strong>.</p>
        </div>
      `);
    }

    return json({ ok: false, error: "Not found" }, 404);
  } catch (error) {
    console.error(error);
    return json({ ok: false, error: errorMessage(error) }, 500);
  }
});

function routePath(pathname: string): string {
  const marker = "/functions/v1/h-whatsapp-peach";
  const index = pathname.indexOf(marker);
  if (index < 0) return pathname || "/";
  return pathname.slice(index + marker.length) || "/";
}

async function discoverOAuth() {
  const resourceCandidates = [
    "https://app.trypeach.ai/.well-known/oauth-protected-resource/api/mcp",
    "https://app.trypeach.ai/.well-known/oauth-protected-resource",
    "https://app.trypeach.io/.well-known/oauth-protected-resource/api/mcp",
    "https://app.trypeach.io/.well-known/oauth-protected-resource",
  ];

  let resourceMeta: any = null;
  let resourceUrl = "";
  for (const candidate of resourceCandidates) {
    try {
      const response = await fetch(candidate, { headers: { Accept: "application/json" } });
      if (!response.ok) continue;
      resourceMeta = await response.json();
      resourceUrl =
        String(resourceMeta.resource ?? "").trim() ||
        (candidate.includes(".ai")
          ? "https://app.trypeach.ai/api/mcp"
          : "https://app.trypeach.io/api/mcp");
      break;
    } catch (_) {
      // Try next documented Peach origin.
    }
  }
  if (!resourceMeta) {
    throw new Error("Peach OAuth protected-resource metadata could not be discovered");
  }

  const authorizationServer = Array.isArray(resourceMeta.authorization_servers)
    ? resourceMeta.authorization_servers[0]
    : resourceMeta.authorization_server;
  if (!authorizationServer) throw new Error("Peach did not advertise an OAuth authorization server");

  const normalized = String(authorizationServer).replace(/\/$/, "");
  const issuer = new URL(normalized);
  const origin = issuer.origin;
  const issuerPath = issuer.pathname.replace(/\/$/, "");
  const candidates = [
    `${normalized}/.well-known/oauth-authorization-server`,
    `${origin}/.well-known/oauth-authorization-server${issuerPath}`,
    `${normalized}/.well-known/openid-configuration`,
    `${origin}/.well-known/openid-configuration${issuerPath}`,
  ];

  let authMeta: any = null;
  for (const candidate of [...new Set(candidates)]) {
    try {
      const response = await fetch(candidate, { headers: { Accept: "application/json" } });
      if (!response.ok) continue;
      authMeta = await response.json();
      break;
    } catch (_) {
      // Try next standards-compliant metadata URL.
    }
  }
  if (!authMeta) throw new Error("Peach OAuth authorization metadata could not be loaded");
  if (!authMeta.authorization_endpoint || !authMeta.token_endpoint || !authMeta.registration_endpoint) {
    throw new Error("Peach OAuth metadata is missing required endpoints");
  }

  const scopes = Array.isArray(resourceMeta.scopes_supported) && resourceMeta.scopes_supported.length
    ? resourceMeta.scopes_supported
    : Array.isArray(authMeta.scopes_supported)
      ? authMeta.scopes_supported
      : [];

  return {
    resourceUrl,
    authorizationEndpoint: String(authMeta.authorization_endpoint),
    tokenEndpoint: String(authMeta.token_endpoint),
    registrationEndpoint: String(authMeta.registration_endpoint),
    scope: scopes
      .filter((scope: string) => String(scope).toLowerCase() !== "offline_access")
      .join(" "),
  };
}

async function registerClient(metadata: any, redirectUri: string): Promise<string> {
  const response = await fetch(metadata.registrationEndpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({
      client_name: "H Cloud Runtime",
      application_type: "web",
      token_endpoint_auth_method: "none",
      redirect_uris: [redirectUri],
      grant_types: ["authorization_code", "refresh_token"],
      response_types: ["code"],
    }),
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`Peach MCP client registration failed (${response.status}): ${text.slice(0, 300)}`);
  }
  const body = JSON.parse(text);
  if (!body.client_id) throw new Error("Peach dynamic registration did not return client_id");
  return String(body.client_id);
}

async function listMcpTools(db: any): Promise<any[]> {
  const credentials = await loadValidCredentials(db);
  let result = await mcpRequest(
    credentials.resource_url,
    credentials.access_token,
    "tools/list",
    {},
    MCP_STATELESS,
    null,
  );
  if (result.ok && result.body?.result?.tools) return result.body.result.tools;

  const init = await mcpRequest(
    credentials.resource_url,
    credentials.access_token,
    "initialize",
    {
      protocolVersion: MCP_LEGACY,
      capabilities: {},
      clientInfo: { name: "H Cloud Runtime", version: "1.0.0" },
    },
    MCP_LEGACY,
    null,
  );
  if (!init.ok) throw new Error(`Peach MCP initialize failed: ${init.error}`);

  await mcpNotify(
    credentials.resource_url,
    credentials.access_token,
    "notifications/initialized",
    MCP_LEGACY,
    init.sessionId,
  );
  result = await mcpRequest(
    credentials.resource_url,
    credentials.access_token,
    "tools/list",
    {},
    MCP_LEGACY,
    init.sessionId,
  );
  if (!result.ok) throw new Error(`Peach MCP tools/list failed: ${result.error}`);
  return result.body?.result?.tools ?? [];
}

async function loadValidCredentials(db: any): Promise<any> {
  const { data: credentials, error } = await db
    .from("h_runtime_credentials")
    .select("*")
    .eq("id", "peach_default")
    .single();
  if (error || !credentials) throw new Error("H cloud is not connected to Peach");

  const expiry = credentials.expires_at
    ? new Date(credentials.expires_at).getTime()
    : Number.MAX_SAFE_INTEGER;
  if (expiry - Date.now() > TOKEN_SKEW_MS || !credentials.refresh_token) {
    return credentials;
  }

  const response = await fetch(credentials.token_endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "refresh_token",
      refresh_token: credentials.refresh_token,
      client_id: credentials.client_id,
      resource: credentials.resource_url,
    }),
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`Peach token refresh failed (${response.status}): ${text.slice(0, 300)}`);
  }
  const token = JSON.parse(text);
  const next = {
    ...credentials,
    access_token: token.access_token ?? credentials.access_token,
    refresh_token: token.refresh_token ?? credentials.refresh_token,
    token_type: token.token_type ?? credentials.token_type,
    scope: token.scope ?? credentials.scope,
    expires_at: Number.isFinite(Number(token.expires_in))
      ? new Date(Date.now() + Number(token.expires_in) * 1000).toISOString()
      : credentials.expires_at,
    updated_at: new Date().toISOString(),
  };
  await db.from("h_runtime_credentials").upsert(next, { onConflict: "id" });
  return next;
}

async function mcpRequest(
  resourceUrl: string,
  accessToken: string,
  method: string,
  params: any,
  version: string,
  sessionId: string | null,
) {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json",
    Accept: "application/json, text/event-stream",
    "MCP-Protocol-Version": version,
    "Mcp-Method": method,
  };
  if (sessionId) headers["Mcp-Session-Id"] = sessionId;

  const response = await fetch(resourceUrl, {
    method: "POST",
    headers,
    body: JSON.stringify({
      jsonrpc: "2.0",
      id: crypto.randomUUID(),
      method,
      params,
    }),
  });
  const text = await response.text();
  const body = parseMcpBody(text);
  return {
    ok: response.ok && !body?.error,
    body,
    error: !response.ok
      ? `HTTP ${response.status}: ${text.slice(0, 300)}`
      : body?.error
        ? JSON.stringify(body.error).slice(0, 300)
        : null,
    sessionId:
      response.headers.get("Mcp-Session-Id") ??
      response.headers.get("MCP-Session-Id"),
  };
}

async function mcpNotify(
  resourceUrl: string,
  accessToken: string,
  method: string,
  version: string,
  sessionId: string | null,
) {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json",
    Accept: "application/json, text/event-stream",
    "MCP-Protocol-Version": version,
  };
  if (sessionId) headers["Mcp-Session-Id"] = sessionId;
  await fetch(resourceUrl, {
    method: "POST",
    headers,
    body: JSON.stringify({ jsonrpc: "2.0", method }),
  });
}

function parseMcpBody(text: string): any {
  const trimmed = text.trim();
  if (!trimmed) return {};
  if (trimmed.startsWith("{")) return JSON.parse(trimmed);
  const payloads = trimmed
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trim())
    .filter((line) => line.startsWith("{"));
  return payloads.length ? JSON.parse(payloads[payloads.length - 1]) : {};
}

function randomUrlSafe(byteCount: number): string {
  const bytes = new Uint8Array(byteCount);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

async function pkceChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(verifier),
  );
  return base64Url(new Uint8Array(digest));
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

function html(body: string, status = 200) {
  const content = body.trim().startsWith("<")
    ? body
    : `<div dir="rtl" style="font-family:system-ui;max-width:640px;margin:48px auto;padding:24px"><p>${body}</p></div>`;
  return new Response(
    `<!doctype html><html lang="ar"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><body>${content}</body></html>`,
    {
      status,
      headers: {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "no-store",
      },
    },
  );
}

function escapeHtml(value: string): string {
  return value.replace(
    /[&<>"']/g,
    (char) =>
      ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
      })[char] ?? char,
  );
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
