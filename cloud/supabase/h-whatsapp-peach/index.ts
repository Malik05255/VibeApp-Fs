import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const SERVER_URL = "https://app.trypeach.ai/api/mcp";
const OAUTH_TTL_MS = 10 * 60 * 1000;
const MCP_STATELESS = "2026-07-28";
const MCP_LEGACY = "2025-11-25";

Deno.serve(async (req) => {
  const url = new URL(req.url);
  const path = routePath(url.pathname);
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRole) return json({ ok: false, error: "Supabase runtime credentials unavailable" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });

  try {
    if (req.method === "GET" && ["/", "/health", "/status"].includes(path)) {
      const { data: creds } = await db.from("h_runtime_credentials").select("id,expires_at,resource_url,updated_at").eq("id", "peach_default").maybeSingle();
      const { data: tools } = await db.from("h_runtime_mcp_tools").select("name").order("name");
      return json({ ok: true, service: "h-whatsapp-peach", connected: Boolean(creds), tokenExpiresAt: creds?.expires_at ?? null, resourceUrl: creds?.resource_url ?? null, toolCount: tools?.length ?? 0, tools: (tools ?? []).map((t) => t.name), updatedAt: creds?.updated_at ?? null });
    }

    if (req.method === "GET" && path === "/connect") {
      const setup = url.searchParams.get("setup")?.trim();
      if (!setup) return html("رابط إعداد H غير صالح أو ناقص.", 400);
      const { data: setupRow } = await db.from("h_runtime_setup_links").select("token,expires_at,used_at").eq("token", setup).maybeSingle();
      if (!setupRow || setupRow.used_at || new Date(setupRow.expires_at).getTime() <= Date.now()) return html("انتهت صلاحية رابط إعداد H. اطلب رابطًا جديدًا.", 403);

      try {
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
          resource_url: SERVER_URL,
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
        auth.searchParams.set("resource", SERVER_URL);
        if (metadata.scope) auth.searchParams.set("scope", metadata.scope);
        return Response.redirect(auth.toString(), 302);
      } catch (error) {
        const message = errorMessage(error);
        console.error("H Peach connect failed:", message);
        return html(`<div dir="rtl" style="font-family:system-ui;max-width:700px;margin:48px auto;padding:24px"><h2>تعذر بدء ربط H مع Peach</h2><p>${escapeHtml(message)}</p><p>صوّر هذه الرسالة فقط إذا ظهرت مرة أخرى.</p></div>`, 500);
      }
    }

    if (req.method === "GET" && path === "/callback") {
      const state = url.searchParams.get("state")?.trim();
      const code = url.searchParams.get("code")?.trim();
      const oauthError = url.searchParams.get("error")?.trim();
      const oauthDescription = url.searchParams.get("error_description")?.trim();
      if (!state) return html("Peach لم يُرجع حالة OAuth المطلوبة.", 400);
      const { data: pending } = await db.from("h_runtime_oauth_pending").select("*").eq("state", state).maybeSingle();
      if (!pending || new Date(pending.expires_at).getTime() <= Date.now()) return html("جلسة ربط Peach انتهت صلاحيتها. أعد المحاولة من رابط جديد.", 403);
      if (oauthError) return html(`Peach رفض التفويض: ${escapeHtml(oauthError)}${oauthDescription ? ` — ${escapeHtml(oauthDescription)}` : ""}`, 400);
      if (!code) return html("Peach لم يُرجع رمز التفويض.", 400);

      try {
        const tokenResponse = await fetch(pending.token_endpoint, {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({ grant_type: "authorization_code", code, redirect_uri: pending.redirect_uri, client_id: pending.client_id, code_verifier: pending.verifier, resource: SERVER_URL }),
        });
        const tokenText = await tokenResponse.text();
        if (!tokenResponse.ok) throw new Error(`Peach token exchange failed (${tokenResponse.status}): ${tokenText.slice(0, 300)}`);
        const token = JSON.parse(tokenText);
        if (!token.access_token) throw new Error("Peach token response did not include access_token");
        const expiresAt = Number.isFinite(Number(token.expires_in)) ? new Date(Date.now() + Number(token.expires_in) * 1000).toISOString() : null;

        const { error: credError } = await db.from("h_runtime_credentials").upsert({
          id: "peach_default",
          access_token: token.access_token,
          refresh_token: token.refresh_token ?? null,
          token_type: token.token_type ?? "Bearer",
          scope: token.scope ?? pending.scope ?? null,
          expires_at: expiresAt,
          client_id: pending.client_id,
          token_endpoint: pending.token_endpoint,
          resource_url: SERVER_URL,
          oauth_metadata: {},
          updated_at: new Date().toISOString(),
        }, { onConflict: "id" });
        if (credError) throw credError;

        await db.from("h_runtime_oauth_pending").delete().eq("state", state);
        const { data: latestSetup } = await db.from("h_runtime_setup_links").select("token").is("used_at", null).order("created_at", { ascending: false }).limit(1).maybeSingle();
        if (latestSetup?.token) await db.from("h_runtime_setup_links").update({ used_at: new Date().toISOString() }).eq("token", latestSetup.token);

        let tools: any[] = [];
        let probeError: string | null = null;
        try {
          tools = await listMcpTools(token.access_token);
          await db.from("h_runtime_mcp_tools").delete().neq("name", "__never__");
          const rows = tools.map((t) => ({ name: String(t.name ?? ""), description: String(t.description ?? ""), input_schema: t.inputSchema ?? t.input_schema ?? {}, discovered_at: new Date().toISOString() })).filter((t) => t.name);
          if (rows.length) await db.from("h_runtime_mcp_tools").upsert(rows, { onConflict: "name" });
        } catch (e) {
          probeError = errorMessage(e);
        }

        return html(`<div dir="rtl" style="font-family:system-ui;max-width:640px;margin:48px auto;padding:24px"><h2>تم ربط H السحابي مع Peach ✅</h2><p>أدوات Peach المكتشفة: <strong>${tools.length}</strong></p>${probeError ? `<p style="color:#9a6700">تم الربط، لكن فحص الأدوات يحتاج إعادة محاولة: ${escapeHtml(probeError)}</p>` : ""}<p>ارجع إلى ChatGPT واكتب: <strong>تم</strong>.</p></div>`);
      } catch (error) {
        return html(`<div dir="rtl" style="font-family:system-ui;max-width:700px;margin:48px auto;padding:24px"><h2>تعذر إكمال ربط H مع Peach</h2><p>${escapeHtml(errorMessage(error))}</p></div>`, 500);
      }
    }

    return json({ ok: false, error: "Not found" }, 404);
  } catch (error) {
    console.error(error);
    return json({ ok: false, error: errorMessage(error) }, 500);
  }
});

function routePath(pathname: string): string {
  const marker = "/functions/v1/h-whatsapp-peach";
  const i = pathname.indexOf(marker);
  return i < 0 ? (pathname || "/") : (pathname.slice(i + marker.length) || "/");
}

async function discoverOAuth() {
  const resourceCandidates = [
    "https://app.trypeach.ai/.well-known/oauth-protected-resource/api/mcp",
    "https://app.trypeach.ai/.well-known/oauth-protected-resource",
  ];
  let resourceMeta: any = null;
  for (const candidate of resourceCandidates) {
    try {
      const r = await fetch(candidate, { headers: { Accept: "application/json" } });
      if (r.ok) { resourceMeta = await r.json(); break; }
    } catch (_) {}
  }
  if (!resourceMeta) resourceMeta = await discoverResourceMetadataFromChallenge();
  if (!resourceMeta) throw new Error("Peach MCP OAuth metadata could not be discovered");

  const authorizationServer = Array.isArray(resourceMeta.authorization_servers)
    ? resourceMeta.authorization_servers[0]
    : resourceMeta.authorization_server;
  if (!authorizationServer) throw new Error("Peach did not advertise an OAuth authorization server");

  const candidates = authorizationMetadataCandidates(String(authorizationServer));
  let authMeta: any = null;
  for (const candidate of candidates) {
    try {
      const r = await fetch(candidate, { headers: { Accept: "application/json" } });
      if (r.ok) { authMeta = await r.json(); break; }
    } catch (_) {}
  }
  if (!authMeta) throw new Error("Peach OAuth authorization-server metadata could not be loaded");
  if (!authMeta.authorization_endpoint) throw new Error("Peach OAuth authorization endpoint is missing");
  if (!authMeta.token_endpoint) throw new Error("Peach OAuth token endpoint is missing");
  if (!authMeta.registration_endpoint) throw new Error("Peach OAuth dynamic registration endpoint is missing");

  const resourceScopes = Array.isArray(resourceMeta.scopes_supported) ? resourceMeta.scopes_supported : [];
  const authScopes = Array.isArray(authMeta.scopes_supported) ? authMeta.scopes_supported : [];
  const scope = (resourceScopes.length ? resourceScopes : authScopes).filter((s: string) => String(s).toLowerCase() !== "offline_access").join(" ");
  return { authorizationEndpoint: String(authMeta.authorization_endpoint), tokenEndpoint: String(authMeta.token_endpoint), registrationEndpoint: String(authMeta.registration_endpoint), scope };
}

async function discoverResourceMetadataFromChallenge() {
  try {
    const r = await fetch(SERVER_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json, text/event-stream" },
      body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "tools/list", params: {} }),
    });
    const challenge = r.headers.get("WWW-Authenticate") ?? r.headers.get("www-authenticate") ?? "";
    const match = /resource_metadata="([^"]+)"/.exec(challenge);
    if (!match?.[1]) return null;
    const metadata = await fetch(match[1], { headers: { Accept: "application/json" } });
    return metadata.ok ? await metadata.json() : null;
  } catch (_) {
    return null;
  }
}

function authorizationMetadataCandidates(issuer: string): string[] {
  const normalized = issuer.replace(/\/$/, "");
  const parsed = new URL(normalized);
  const origin = parsed.origin;
  const path = parsed.pathname.replace(/\/$/, "");
  return [...new Set([
    `${normalized}/.well-known/oauth-authorization-server`,
    `${origin}/.well-known/oauth-authorization-server${path}`,
    `${normalized}/.well-known/openid-configuration`,
    `${origin}/.well-known/openid-configuration${path}`,
  ])];
}

async function registerClient(meta: any, redirectUri: string): Promise<string> {
  const r = await fetch(meta.registrationEndpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({
      client_name: "H Cloud Runtime",
      application_type: "native",
      token_endpoint_auth_method: "none",
      redirect_uris: [redirectUri],
      grant_types: ["authorization_code", "refresh_token"],
      response_types: ["code"],
    }),
  });
  const text = await r.text();
  if (!r.ok) throw new Error(`Peach MCP client registration failed (${r.status}): ${text.slice(0, 300)}`);
  const obj = JSON.parse(text);
  if (!obj.client_id) throw new Error("Peach dynamic registration did not return client_id");
  return String(obj.client_id);
}

async function listMcpTools(accessToken: string): Promise<any[]> {
  let result = await mcpRequest(accessToken, "tools/list", {}, MCP_STATELESS, null);
  if (result.ok && result.body?.result?.tools) return result.body.result.tools;
  const init = await mcpRequest(accessToken, "initialize", { protocolVersion: MCP_LEGACY, capabilities: {}, clientInfo: { name: "H Cloud Runtime", version: "1.0.0" } }, MCP_LEGACY, null);
  if (!init.ok) throw new Error(`Peach MCP initialize failed: ${init.error}`);
  await mcpNotify(accessToken, "notifications/initialized", MCP_LEGACY, init.sessionId);
  result = await mcpRequest(accessToken, "tools/list", {}, MCP_LEGACY, init.sessionId);
  if (!result.ok) throw new Error(`Peach MCP tools/list failed: ${result.error}`);
  return result.body?.result?.tools ?? [];
}

async function mcpRequest(accessToken: string, method: string, params: any, version: string, sessionId: string | null) {
  const headers: Record<string, string> = { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json", Accept: "application/json, text/event-stream", "MCP-Protocol-Version": version, "Mcp-Method": method };
  if (sessionId) headers["Mcp-Session-Id"] = sessionId;
  const r = await fetch(SERVER_URL, { method: "POST", headers, body: JSON.stringify({ jsonrpc: "2.0", id: crypto.randomUUID(), method, params }) });
  const text = await r.text();
  const body = parseMcpBody(text);
  return { ok: r.ok && !body?.error, body, error: !r.ok ? `HTTP ${r.status}: ${text.slice(0, 300)}` : body?.error ? JSON.stringify(body.error).slice(0, 300) : null, sessionId: r.headers.get("Mcp-Session-Id") ?? r.headers.get("MCP-Session-Id") };
}

async function mcpNotify(accessToken: string, method: string, version: string, sessionId: string | null) {
  const headers: Record<string, string> = { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json", Accept: "application/json, text/event-stream", "MCP-Protocol-Version": version };
  if (sessionId) headers["Mcp-Session-Id"] = sessionId;
  await fetch(SERVER_URL, { method: "POST", headers, body: JSON.stringify({ jsonrpc: "2.0", method }) });
}

function parseMcpBody(text: string): any {
  const t = text.trim();
  if (!t) return {};
  if (t.startsWith("{")) return JSON.parse(t);
  const payloads = t.split(/\r?\n/).map((l) => l.trim()).filter((l) => l.startsWith("data:")).map((l) => l.slice(5).trim()).filter((l) => l.startsWith("{"));
  return payloads.length ? JSON.parse(payloads[payloads.length - 1]) : {};
}

function randomUrlSafe(byteCount: number): string {
  const bytes = new Uint8Array(byteCount);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

async function pkceChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } });
}

function html(body: string, status = 200) {
  const content = body.trim().startsWith("<") ? body : `<div dir="rtl" style="font-family:system-ui;max-width:640px;margin:48px auto;padding:24px"><p>${body}</p></div>`;
  return new Response(`<!doctype html><html lang="ar"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><body>${content}</body></html>`, { status, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" } });
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c] ?? c));
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
