import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { completeFreeOpenRouterChat, getOpenRouterAiStatus } from "./openrouter-ai.ts";

const MCP_URL = "https://app.trypeach.ai/api/mcp";
const MCP_STATELESS = "2026-07-28";
const MCP_LEGACY = "2025-11-25";
const TOKEN_SKEW_MS = 60_000;
const DEFAULT_TIME_ZONE = "Asia/Riyadh";
const MAX_INBOX_BATCH = 12;
const MAX_DUE_BATCH = 20;
const HISTORY_LIMIT = 14;

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
    const credentials = await loadValidCredentials(db);
    const poll = await pollPeachInbox(db, credentials.access_token, now);
    const processed = await processNewMessages(db, credentials.access_token, now);
    const reminders = await processDueReminders(db, credentials.access_token, now);
    const aiStatus = await getOpenRouterAiStatus(db);

    await db.from("h_runtime_state").upsert({
      key: "inbox_poll",
      value: {
        last_poll_at: now.toISOString(),
        last_seen_count: poll.seen,
        last_inserted_count: poll.inserted,
        last_processed_count: processed.processed,
        last_failed_count: processed.failed,
        last_reminders_sent: reminders.sent,
        last_reminders_failed: reminders.failed,
        ai_configured: aiStatus.configured,
        ai_provider: aiStatus.provider,
        ai_model: aiStatus.model,
        ai_model_verified_at: aiStatus.modelVerifiedAt,
        ai_credential_source: aiStatus.credentialSource,
        ai_free_only: aiStatus.freeOnly,
      },
      updated_at: now.toISOString(),
    }, { onConflict: "key" });

    return reply({
      ok: true,
      fetched: poll.seen,
      inserted: poll.inserted,
      processed: processed.processed,
      ignored: processed.ignored,
      failed: processed.failed,
      remindersSent: reminders.sent,
      remindersFailed: reminders.failed,
      aiConfigured: aiStatus.configured,
      aiProvider: aiStatus.provider,
      aiModel: aiStatus.model,
      aiCredentialSource: aiStatus.credentialSource,
      aiFreeOnly: aiStatus.freeOnly,
      from: poll.from,
      to: poll.to,
    });
  } catch (error) {
    console.error("H inbox runtime failed", error);
    return reply({ ok: false, error: errorMessage(error) }, 500);
  }
});

async function pollPeachInbox(db: any, accessToken: string, now: Date) {
  const { data: state } = await db.from("h_runtime_state").select("value").eq("key", "inbox_poll").maybeSingle();
  const last = state?.value?.last_poll_at ? new Date(String(state.value.last_poll_at)) : new Date(now.getTime() - 10 * 60_000);
  const fromDate = new Date(Math.max(last.getTime() - 2 * 60_000, now.getTime() - 24 * 60 * 60_000));

  const toolResult = await callMcpTool(accessToken, "peach_list_messages", {
    direction: "inbound",
    from: fromDate.toISOString(),
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

  return { seen, inserted, from: fromDate.toISOString(), to: now.toISOString() };
}

async function processNewMessages(db: any, accessToken: string, now: Date) {
  const cutoff = new Date(now.getTime() - 10 * 60_000).toISOString();
  const { data: rows, error } = await db.from("h_runtime_inbox")
    .select("*")
    .eq("status", "new")
    .gte("received_at", cutoff)
    .order("received_at", { ascending: true })
    .limit(MAX_INBOX_BATCH);
  if (error) throw error;

  let processed = 0;
  let ignored = 0;
  let failed = 0;

  for (const row of rows ?? []) {
    const messageKey = String(row.message_key);
    const body = typeof row.body === "string" ? row.body.trim() : "";
    const conversationId = Number(row.conversation_id);
    const userKey = normalizeUserKey(row.contact_phone, conversationId);

    if (!body || !Number.isInteger(conversationId) || conversationId <= 0) {
      await db.from("h_runtime_inbox").update({
        status: "ignored",
        error: !body ? "empty_or_unsupported_message" : "missing_conversation_id",
        processed_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      }).eq("message_key", messageKey);
      ignored += 1;
      continue;
    }

    await db.from("h_runtime_inbox").update({ status: "processing", updated_at: new Date().toISOString() }).eq("message_key", messageKey);

    try {
      await appendChat(db, userKey, conversationId, "user", body, messageKey);
      const response = await decideResponse(db, userKey, conversationId, body, now);
      if (!response.reply) {
        await db.from("h_runtime_inbox").update({
          status: "processed",
          processed_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        }).eq("message_key", messageKey);
        processed += 1;
        continue;
      }

      await sendConversationReply(accessToken, conversationId, response.reply);
      await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
      await db.from("h_runtime_inbox").update({
        status: "processed",
        error: null,
        reply_text: response.reply,
        processed_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      }).eq("message_key", messageKey);
      processed += 1;
    } catch (messageError) {
      const message = errorMessage(messageError);
      console.error("H message processing failed", messageKey, message);
      await db.from("h_runtime_inbox").update({
        status: "failed",
        error: message.slice(0, 1000),
        processed_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      }).eq("message_key", messageKey);
      failed += 1;
    }
  }

  return { processed, ignored, failed };
}

async function decideResponse(db: any, userKey: string, conversationId: number, rawText: string, now: Date) {
  const text = stripWakeWord(rawText);

  if (/^(السلام(?: عليكم)?|سلام|هلا|هلا والله|الو|ألو|hello|hi)$/i.test(text)) {
    return { reply: text.startsWith("السلام") || text === "سلام" ? "وعليكم السلام. معك H، وش تحتاج؟" : "معك H. وش تحتاج؟" };
  }

  if (/^(وينك|وينك يا h|وين h|يا h)$/i.test(text)) {
    return { reply: "موجود. أرسل طلبك مباشرة." };
  }

  if (/^(الغاء|إلغاء|cancel)\s+كل\s*(التذكيرات)?$/i.test(text)) {
    await db.from("h_runtime_reminders").update({ status: "cancelled", updated_at: new Date().toISOString() })
      .eq("user_key", userKey).eq("status", "pending");
    return { reply: "تم إلغاء التذكيرات المعلقة." };
  }

  if (looksLikeReminderListRequest(text)) {
    return { reply: await formatReminderList(db, userKey) };
  }

  if (looksLikeMemoryListRequest(text)) {
    return { reply: await formatMemoryList(db, userKey) };
  }

  const reminder = parseRelativeReminder(text, now);
  if (reminder) {
    await db.from("h_runtime_reminders").insert({
      user_key: userKey,
      conversation_id: conversationId,
      body: reminder.body,
      due_at: reminder.dueAt.toISOString(),
      status: "pending",
    });
    return { reply: formatReminderConfirmation(reminder.body, reminder.dueAt) };
  }

  const memory = parseMemorySave(text);
  if (memory) {
    await db.from("h_runtime_memories").insert({
      user_key: userKey,
      category: memory.category,
      body: memory.body,
      original_text: rawText,
    });
    return { reply: "حفظتها عندي. تقدر ترجع لها لاحقًا." };
  }

  const ai = await interpretWithAi(db, userKey, text, now);
  if (ai) return await executeAiDecision(db, userKey, conversationId, rawText, ai);

  return {
    reply: "وصلتني رسالتك عبر H السحابي. الربط شغال، لكن ما توفر الآن مسار ذكاء سحابي مجاني متحقق منه. التذكيرات والحفظ والأوامر المباشرة ما زالت تعمل.",
  };
}

async function executeAiDecision(db: any, userKey: string, conversationId: number, originalText: string, decision: any) {
  const action = String(decision?.action || "reply");

  if (action === "schedule_self" && decision?.body && decision?.dueAtIso) {
    const dueAt = new Date(String(decision.dueAtIso));
    if (Number.isNaN(dueAt.getTime()) || dueAt.getTime() <= Date.now()) {
      return { reply: "الموعد غير واضح عندي. حدده بوقت أو تاريخ أوضح." };
    }
    await db.from("h_runtime_reminders").insert({
      user_key: userKey,
      conversation_id: conversationId,
      body: String(decision.body),
      due_at: dueAt.toISOString(),
      status: "pending",
    });
    return { reply: String(decision.reply || formatReminderConfirmation(String(decision.body), dueAt)) };
  }

  if (action === "save_memory" && decision?.body) {
    await db.from("h_runtime_memories").insert({
      user_key: userKey,
      category: String(decision.category || "note"),
      body: String(decision.body),
      original_text: originalText,
    });
    return { reply: String(decision.reply || "حفظتها عندي.") };
  }

  if (action === "list_reminders") return { reply: await formatReminderList(db, userKey) };
  if (action === "list_memories") return { reply: await formatMemoryList(db, userKey) };
  return { reply: String(decision?.reply || "تم.") };
}

async function interpretWithAi(db: any, userKey: string, text: string, now: Date): Promise<any | null> {
  const { data: historyRows } = await db.from("h_runtime_chat")
    .select("role,body,created_at")
    .eq("user_key", userKey)
    .order("created_at", { ascending: false })
    .limit(HISTORY_LIMIT);
  const history = (historyRows ?? []).slice().reverse();

  const system = [
    "You are H, a private personal assistant operating through WhatsApp.",
    "Respond naturally and concisely in Saudi Arabic unless the user clearly uses another language.",
    `Current UTC time: ${now.toISOString()}. User timezone: ${DEFAULT_TIME_ZONE}.`,
    "Return ONLY one JSON object. Do not return markdown or chain-of-thought.",
    "Allowed actions:",
    '{"action":"reply","reply":"response"}',
    '{"action":"schedule_self","body":"reminder text","dueAtIso":"absolute ISO-8601 with offset","reply":"confirmation"}',
    '{"action":"save_memory","body":"durable idea/note","category":"idea|note|preference|project","reply":"confirmation"}',
    '{"action":"list_memories"}',
    '{"action":"list_reminders"}',
    "If time/date is ambiguous, ask one short clarification question using action=reply.",
    "Do not claim actions succeeded; the runtime executes them after your JSON decision.",
    "Do not invent facts, prices, contacts, dates, or tool results.",
  ].join("\n");

  const messages: Array<Record<string, string>> = [{ role: "system", content: system }];
  for (const item of history) {
    messages.push({ role: item.role === "assistant" ? "assistant" : "user", content: String(item.body) });
  }
  if (!history.length || String(history[history.length - 1]?.body || "") !== text) messages.push({ role: "user", content: text });

  const completion = await completeFreeOpenRouterChat(db, messages);
  if (!completion) return null;
  return parseJsonObject(completion.content);
}

async function processDueReminders(db: any, accessToken: string, now: Date) {
  const { data: rows, error } = await db.from("h_runtime_reminders")
    .select("*")
    .eq("status", "pending")
    .lte("due_at", now.toISOString())
    .order("due_at", { ascending: true })
    .limit(MAX_DUE_BATCH);
  if (error) throw error;

  let sent = 0;
  let failed = 0;
  for (const reminder of rows ?? []) {
    try {
      await sendConversationReply(accessToken, Number(reminder.conversation_id), `تذكير من H: ${String(reminder.body)}`);
      await db.from("h_runtime_reminders").update({
        status: "sent",
        attempts: Number(reminder.attempts || 0) + 1,
        last_error: null,
        sent_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      }).eq("id", reminder.id);
      sent += 1;
    } catch (error) {
      const message = errorMessage(error);
      const attempts = Number(reminder.attempts || 0) + 1;
      const windowClosed = /template|24.?hour|window|outside.*window/i.test(message);
      await db.from("h_runtime_reminders").update({
        status: windowClosed ? "waiting_template" : attempts >= 3 ? "failed" : "pending",
        attempts,
        last_error: message.slice(0, 1000),
        updated_at: new Date().toISOString(),
      }).eq("id", reminder.id);
      failed += 1;
    }
  }
  return { sent, failed };
}

async function sendConversationReply(accessToken: string, conversationId: number, text: string) {
  if (!Number.isInteger(conversationId) || conversationId <= 0) throw new Error("Invalid conversation_id");
  const result = await callMcpTool(accessToken, "peach_reply_to_conversation", {
    conversation_id: conversationId,
    text: text.slice(0, 3000),
  });
  if (result?.isError === true) throw new Error(extractToolError(result));
  return result;
}

async function appendChat(db: any, userKey: string, conversationId: number, role: "user" | "assistant", body: string, sourceMessageKey: string | null) {
  await db.from("h_runtime_chat").insert({
    user_key: userKey,
    conversation_id: conversationId,
    role,
    body: body.slice(0, 12000),
    source_message_key: sourceMessageKey,
  });
}

async function formatReminderList(db: any, userKey: string) {
  const { data } = await db.from("h_runtime_reminders").select("body,due_at,status")
    .eq("user_key", userKey).in("status", ["pending", "waiting_template"]).order("due_at", { ascending: true }).limit(10);
  if (!data?.length) return "ما عندك تذكيرات معلقة حاليًا.";
  const lines = data.map((item: any, index: number) => `${index + 1}. ${item.body} — ${formatRiyadhDate(new Date(item.due_at))}`);
  return `تذكيراتك الحالية:\n${lines.join("\n")}`;
}

async function formatMemoryList(db: any, userKey: string) {
  const { data } = await db.from("h_runtime_memories").select("body,category,created_at")
    .eq("user_key", userKey).order("created_at", { ascending: false }).limit(10);
  if (!data?.length) return "ما عندي أشياء محفوظة لك إلى الآن.";
  const lines = data.map((item: any, index: number) => `${index + 1}. ${item.body}`);
  return `آخر الأشياء المحفوظة عندي:\n${lines.join("\n")}`;
}

function looksLikeReminderListRequest(text: string) {
  return /(وش|ما|اعطني|عطني|عرض|اظهر|أظهر).*(تذكير|تذكيرات)|(?:تذكيراتي|reminders)/i.test(text);
}

function looksLikeMemoryListRequest(text: string) {
  return /(وش|ما|اعطني|عطني|عرض|اظهر|أظهر).*(فكر|افكار|أفكار|ملاحظ|ذاكر)|(?:افكاري|أفكاري|ذكرياتي)/i.test(text);
}

function parseRelativeReminder(text: string, now: Date) {
  const match = text.match(/(?:ذكرني|ذكّرني|remind me)\s+(?:بعد\s+)?(\d+)\s*(دقيق(?:ة|ه)?|دقائق|ساعة|ساعات|يوم|ايام|أيام|minute|minutes|hour|hours|day|days)\s*(.*)$/i);
  if (!match) return null;
  const amount = Number(match[1]);
  if (!Number.isFinite(amount) || amount <= 0 || amount > 3650) return null;
  const unit = match[2].toLowerCase();
  const body = (match[3] || "التذكير الذي طلبته").trim();
  const multiplier = unit.includes("دقيق") || unit.startsWith("minute")
    ? 60_000
    : unit.includes("ساع") || unit.startsWith("hour")
      ? 60 * 60_000
      : 24 * 60 * 60_000;
  return { body, dueAt: new Date(now.getTime() + amount * multiplier) };
}

function parseMemorySave(text: string) {
  if (/ذكرني|ذكّرني/i.test(text)) return null;
  const match = text.match(/^(?:يا\s*h\s*)?(?:احفظ|إحفظ|تذكر|تذكّر|خزن|سجل)\s+(?:لي\s+)?(.+)$/i);
  if (!match?.[1]?.trim()) return null;
  const body = match[1].trim();
  const category = /(فكرة|مشروع)/i.test(body) ? "idea" : "note";
  return { body, category };
}

function formatReminderConfirmation(body: string, dueAt: Date) {
  return `تم. بذكرك بـ«${body}» ${formatRiyadhDate(dueAt)}.`;
}

function formatRiyadhDate(date: Date) {
  try {
    return new Intl.DateTimeFormat("ar-SA", {
      timeZone: DEFAULT_TIME_ZONE,
      dateStyle: "medium",
      timeStyle: "short",
    }).format(date);
  } catch (_) {
    return date.toISOString();
  }
}

function stripWakeWord(text: string) {
  return text.trim().replace(/^(?:يا\s*)?h[\s،,:-]*/i, "").trim() || text.trim();
}

function normalizeUserKey(phone: unknown, conversationId: number) {
  const value = typeof phone === "string" ? phone.replace(/[^0-9+]/g, "") : "";
  return value || `conversation:${conversationId}`;
}

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
    clientInfo: { name: "H Cloud Runtime", version: "1.2.0" },
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

function extractToolError(result: any) {
  const payload = extractToolPayload(result);
  if (typeof payload === "string") return payload.slice(0, 500);
  if (payload?.error) return typeof payload.error === "string" ? payload.error : JSON.stringify(payload.error).slice(0, 500);
  if (Array.isArray(result?.content)) {
    const text = result.content.find((item: any) => item?.type === "text" && item?.text)?.text;
    if (text) return String(text).slice(0, 500);
  }
  return "Peach rejected the WhatsApp action";
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

function parseJsonObject(content: string) {
  const cleaned = String(content || "").trim().replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();
  try { return JSON.parse(cleaned); } catch (_) {
    const start = cleaned.indexOf("{");
    const end = cleaned.lastIndexOf("}");
    if (start < 0 || end <= start) return null;
    try { return JSON.parse(cleaned.slice(start, end + 1)); } catch (_) { return null; }
  }
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
