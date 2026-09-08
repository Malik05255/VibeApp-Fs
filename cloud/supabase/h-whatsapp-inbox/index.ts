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
  if (!config?.secret_value || req.headers.get("x-h-runtime-secret") !== config.secret_value) {
    return reply({ ok: false, error: "Unauthorized" }, 401);
  }
  if (req.method !== "POST") return reply({ ok: false, error: "Method not allowed" }, 405);

  try {
    const now = new Date();
    const { data: state } = await db.from("h_runtime_state").select("value").eq("key", "inbox_poll").maybeSingle();
    const last = state?.value?.last_poll_at ? new Date(String(state.value.last_poll_at)) : new Date(now.getTime() - 2 * 60 * 60 * 1000);
    const from = new Date(Math.max(last.getTime() - 2 * 60 * 1000, now.getTime() - 24 * 60 * 60 * 1000));

    const credentials = await loadValidCredentials(db);
    const toolResult = await callMcpTool(credentials.access_token, "peach_list_messages", {
      direction: "inbound",
      from: from.toISOString(),
      to: now.toISOString(),
      page: 1,
      per_page: 100,
    });

    const payload = extractToolPayload(toolResult);
    const messages = findMessageArray(payload);
    let inserted = 0;
    let seen = 0;

    for (const message of messages) {
      if (!message || typeof message !== "object" || Array.isArray(message)) continue;
      seen += 1;
      const row = await normalizeMessage(message as Record<string, unknown>);
      const { data, error } = await db.from("h_runtime_inbox")
        .upsert(row, { onConflict: "message_key", ignoreDuplicates: true })
        .select("message_key");
      if (error) throw error;
      if (Array.isArray(data) && data.length) inserted += 1;
    }

    await db.from("h_runtime_state").upsert({
      key: "inbox_poll",
      value: { last_poll_at: now.toISOString(), last_seen_count: seen, last_inserted_count: inserted },
      updated_at: now.toISOString(),
    }, { onConflict: "key" });

    return reply({ ok: true, fetched: seen, inserted, from: from.toISOString(), to: now.toISOString() });
  } catch (error) {
    console.error("H inbox poll failed", error);
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
    expires_at: Number.isFinite(Number(token.expires_in))
      ? new Date(Date.now() + Number(token.expires_in) * 1000).toISOString()
      : creds.expires_at,
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
  const text = await response.text();
  const body = parseMcpBody(text);
  return {
    ok: response.ok && !body?.error,
    body,
    error: !response.ok ? `HTTP ${response.status}: ${text.slice(0, 300)}` : body?.error ? JSON.stringify(body.error).slice(0, 300) : null,
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

function extractToolPayload(result: any): any {
  if (result?.structuredContent != null) return result.structuredContent;
  const content = result?.content;
  if (Array.isArray(content)) {
    for (const item of content) {
      if (item?.type === "text" && typeof item.text === "string") {
        const text = item.text.trim();
        if (!text) continue;
        try { return JSON.parse(text); } catch (_) {}
      }
    }
  }
  return result;
}

function findMessageArray(value: any): any[] {
  if (Array.isArray(value)) return value;
  if (!value || typeof value !== "object") return [];
  for (const key of ["messages", "data", "results", "items"]) {
    if (Array.isArray(value[key])) return value[key];
    if (value[key] && typeof value[key] === "object") {
      const nested = findMessageArray(value[key]);
      if (nested.length) return nested;
    }
  }
  if (value.conversation_id != null || value.id != null || value.message_id != null) return [value];
  return [];
}

async function normalizeMessage(message: Record<string, unknown>) {
  const peachId = firstId(message.id, message.message_id, message.wa_message_id, message.whatsapp_message_id);
  const messageKey = peachId ? `peach:${peachId}` : `sha256:${await sha256Hex(stableStringify(message))}`;
  const conversationId = firstNumber(message.conversation_id, (message.conversation as any)?.id);
  const contact = (message.contact && typeof message.contact === "object") ? message.contact as Record<string, unknown> : {};
  const business = (message.business && typeof message.business === "object") ? message.business as Record<string, unknown> : {};
  const created = firstString(message.created_at, message.timestamp, message.sent_at, message.received_at);

  return {
    message_key: messageKey,
    peach_message_id: peachId,
    conversation_id: conversationId,
    contact_phone: firstString(contact.phone_number, contact.phone, message.phone_number, message.from),
    business_phone_number: firstString(message.business_phone_number, business.phone_number, business.phone, message.to),
    direction: firstString(message.direction) ?? "inbound",
    message_type: firstString(message.content_type, message.message_type, message.type, (message.content as any)?.type),
    body: extractBody(message),
    source_created_at: parseDateOrNull(created),
    raw: message,
    status: "new",
    updated_at: new Date().toISOString(),
  };
}

function extractBody(message: Record<string, unknown>): string | null {
  for (const value of [message.text, message.body, message.message, (message.content as any)?.text, (message.text as any)?.body]) {
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return null;
}

function firstId(...values: unknown[]): string | null {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) return value.trim();
    if (typeof value === "number" && Number.isFinite(value)) return String(value);
  }
  return null;
}

function firstString(...values: unknown[]): string | null {
  for (const value of values) if (typeof value === "string" && value.trim()) return value.trim();
  return null;
}

function firstNumber(...values: unknown[]): number | null {
  for (const value of values) {
    const n = typeof value === "number" ? value : typeof value === "string" ? Number(value) : NaN;
    if (Number.isFinite(n)) return n;
  }
  return null;
}

function parseDateOrNull(value: string | null): string | null {
  if (!value) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

function stableStringify(value: unknown): string {
  if (value == null || typeof value !== "object") return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(",")}]`;
  const obj = value as Record<string, unknown>;
  return `{${Object.keys(obj).sort().map((k) => `${JSON.stringify(k)}:${stableStringify(obj[k])}`).join(",")}}`;
}

async function sha256Hex(value: string): Promise<string> {
  const bytes = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function errorMessage(error: unknown): string { return error instanceof Error ? error.message : String(error); }
function reply(value: unknown, status = 200) { return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } }); }
