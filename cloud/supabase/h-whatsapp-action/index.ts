import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const MCP_URL = "https://app.trypeach.ai/api/mcp";
const MCP_STATELESS = "2026-07-28";
const MCP_LEGACY = "2025-11-25";
const TOKEN_SKEW_MS = 60_000;

Deno.serve(async (req: Request) => {
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "Supabase runtime credentials unavailable" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });

  const { data: config } = await db.from("h_runtime_config").select("secret_value").eq("key", "poll_secret").maybeSingle();
  if (!config?.secret_value || req.headers.get("x-h-runtime-secret") !== config.secret_value) return reply({ ok: false, error: "Unauthorized" }, 401);
  if (req.method !== "POST") return reply({ ok: false, error: "Method not allowed" }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    if (body?.action !== "reply") return reply({ ok: false, error: "Unsupported action" }, 400);
    const conversationId = Number(body.conversation_id);
    const text = typeof body.text === "string" ? body.text.trim() : "";
    if (!Number.isInteger(conversationId) || conversationId <= 0) return reply({ ok: false, error: "Invalid conversation_id" }, 400);
    if (!text || text.length > 3000) return reply({ ok: false, error: "Invalid text" }, 400);

    const credentials = await loadValidCredentials(db);
    const result = await callMcpTool(credentials.access_token, "peach_reply_to_conversation", {
      conversation_id: conversationId,
      text,
    });
    return reply({ ok: true, conversation_id: conversationId, result: summarizeResult(result) });
  } catch (error) {
    console.error("H WhatsApp action failed", error);
    return reply({ ok: false, error: errorMessage(error) }, 500);
  }
});

async function loadValidCredentials(db: any): Promise<any> {
  const { data: creds, error } = await db.from("h_runtime_credentials").select("*").eq("id", "peach_default").single();
  if (error || !creds) throw new Error("H cloud is not connected to Peach");
  const expiry = creds.expires_at ? new Date(creds.expires_at).getTime() : Number.MAX_SAFE_INTEGER;
  if (expiry - Date.now() > TOKEN_SKEW_MS || !creds.refresh_token) return creds;

  const response = await fetch(creds.token_endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "refresh_token",
      refresh_token: creds.refresh_token,
      client_id: creds.client_id,
      resource: MCP_URL,
    }),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`Peach token refresh failed (${response.status}): ${text.slice(0, 300)}`);
  const token = JSON.parse(text);
  if (!token.access_token) throw new Error("Peach refresh response did not include access_token");

  const next = {
    ...creds,
    access_token: token.access_token,
    refresh_token: token.refresh_token ?? creds.refresh_token,
    token_type: token.token_type ?? creds.token_type ?? "Bearer",
    scope: token.scope ?? creds.scope,
    expires_at: Number.isFinite(Number(token.expires_in)) ? new Date(Date.now() + Number(token.expires_in) * 1000).toISOString() : creds.expires_at,
    updated_at: new Date().toISOString(),
  };
  await db.from("h_runtime_credentials").upsert(next, { onConflict: "id" });
  return next;
}

async function callMcpTool(accessToken: string, name: string, args: Record<string, unknown>) {
  let result = await mcpRequest(accessToken, "tools/call", { name, arguments: args }, MCP_STATELESS, null);
  if (result.ok) return result.body?.result ?? result.body;

  const init = await mcpRequest(accessToken, "initialize", {
    protocolVersion: MCP_LEGACY,
    capabilities: {},
    clientInfo: { name: "H Cloud Runtime", version: "1.0.0" },
  }, MCP_LEGACY, null);
  if (!init.ok) throw new Error(`Peach MCP initialize failed: ${init.error}`);
  await mcpNotify(accessToken, "notifications/initialized", MCP_LEGACY, init.sessionId);
  result = await mcpRequest(accessToken, "tools/call", { name, arguments: args }, MCP_LEGACY, init.sessionId);
  if (!result.ok) throw new Error(`Peach MCP ${name} failed: ${result.error}`);
  return result.body?.result ?? result.body;
}

async function mcpRequest(accessToken: string, method: string, params: unknown, version: string, sessionId: string | null) {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json",
    Accept: "application/json, text/event-stream",
    "MCP-Protocol-Version": version,
    "Mcp-Method": method,
  };
  if (sessionId) headers["Mcp-Session-Id"] = sessionId;
  const response = await fetch(MCP_URL, {
    method: "POST",
    headers,
    body: JSON.stringify({ jsonrpc: "2.0", id: crypto.randomUUID(), method, params }),
  });
  const raw = await response.text();
  const body = parseMcpBody(raw);
  return {
    ok: response.ok && !body?.error,
    body,
    error: !response.ok ? `HTTP ${response.status}: ${raw.slice(0, 300)}` : body?.error ? JSON.stringify(body.error).slice(0, 300) : null,
    sessionId: response.headers.get("Mcp-Session-Id") ?? response.headers.get("MCP-Session-Id"),
  };
}

async function mcpNotify(accessToken: string, method: string, version: string, sessionId: string | null) {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json",
    Accept: "application/json, text/event-stream",
    "MCP-Protocol-Version": version,
  };
  if (sessionId) headers["Mcp-Session-Id"] = sessionId;
  await fetch(MCP_URL, { method: "POST", headers, body: JSON.stringify({ jsonrpc: "2.0", method }) });
}

function parseMcpBody(text: string): any {
  const trimmed = text.trim();
  if (!trimmed) return {};
  if (trimmed.startsWith("{")) return JSON.parse(trimmed);
  const payloads = trimmed.split(/\r?\n/).map((line) => line.trim()).filter((line) => line.startsWith("data:")).map((line) => line.slice(5).trim()).filter((line) => line.startsWith("{"));
  return payloads.length ? JSON.parse(payloads[payloads.length - 1]) : {};
}

function summarizeResult(result: any) {
  if (result?.structuredContent) return result.structuredContent;
  if (Array.isArray(result?.content)) {
    return result.content.map((item: any) => item?.type === "text" ? String(item.text ?? "").slice(0, 500) : item?.type).slice(0, 3);
  }
  return result ?? null;
}

function errorMessage(error: unknown): string { return error instanceof Error ? error.message : String(error); }
function reply(value: unknown, status = 200) { return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } }); }
