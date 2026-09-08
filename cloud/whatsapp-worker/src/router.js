import legacyWorker from "./index.js";

const MAX_MESSAGE_LENGTH = 4096;
const BLOCKED_BODY = "[blocked]";
const UNIFIED_TEXT_TYPES = new Set(["text", "button", "interactive", "location"]);

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if ((url.pathname === "/health" || url.pathname === "/setup/status") && request.method !== "POST") {
      return augmentHealth(await legacyWorker.fetch(request, env, ctx), env);
    }

    if (url.pathname !== "/webhook" || request.method !== "POST") {
      return legacyWorker.fetch(request, env, ctx);
    }

    const rawBody = await request.text();
    if (!(await verifyMetaSignature(request, rawBody, env.WHATSAPP_APP_SECRET))) {
      return new Response("Invalid signature", { status: 401 });
    }

    let payload;
    try {
      payload = JSON.parse(rawBody);
    } catch {
      return new Response("Invalid JSON", { status: 400 });
    }

    const partitioned = partitionWebhookPayload(payload, env);
    const tasks = [];

    for (const item of partitioned.blocked) {
      tasks.push(processBlockedMessage(env, item));
    }
    for (const item of partitioned.unified) {
      tasks.push(processUnifiedMessage(env, item));
    }
    if (tasks.length) {
      ctx.waitUntil(Promise.all(tasks.map((task) => task.catch((error) => {
        console.error("H ingress router task failed", error);
      }))));
    }

    if (hasMessages(partitioned.delegatedPayload)) {
      const delegatedBody = JSON.stringify(partitioned.delegatedPayload);
      const signature = await hmacSha256Hex(String(env.WHATSAPP_APP_SECRET || ""), delegatedBody);
      const headers = new Headers(request.headers);
      headers.set("Content-Type", "application/json");
      headers.set("x-hub-signature-256", `sha256=${signature}`);
      const delegatedRequest = new Request(request.url, {
        method: "POST",
        headers,
        body: delegatedBody,
      });
      return legacyWorker.fetch(delegatedRequest, env, ctx);
    }

    return new Response("EVENT_RECEIVED", { status: 200 });
  },

  async scheduled(event, env, ctx) {
    return legacyWorker.scheduled(event, env, ctx);
  },
};

export function partitionWebhookPayload(payload, env) {
  const delegatedPayload = cloneJson(payload);
  const blocked = [];
  const unified = [];
  const sourceEntries = Array.isArray(payload?.entry) ? payload.entry : [];
  const delegatedEntries = Array.isArray(delegatedPayload?.entry) ? delegatedPayload.entry : [];

  for (let entryIndex = 0; entryIndex < sourceEntries.length; entryIndex += 1) {
    const sourceChanges = Array.isArray(sourceEntries[entryIndex]?.changes) ? sourceEntries[entryIndex].changes : [];
    const delegatedChanges = Array.isArray(delegatedEntries[entryIndex]?.changes)
      ? delegatedEntries[entryIndex].changes
      : [];

    for (let changeIndex = 0; changeIndex < sourceChanges.length; changeIndex += 1) {
      const sourceValue = sourceChanges[changeIndex]?.value || {};
      const messages = Array.isArray(sourceValue.messages) ? sourceValue.messages : null;
      if (!messages) continue;

      const delegatedMessages = [];
      for (const message of messages) {
        const decision = routeDecisionForMessage(message, env);
        if (decision.kind === "blocked") {
          blocked.push(decision);
        } else if (decision.kind === "unified") {
          unified.push({
            ...decision,
            profileName: profileNameForWaId(sourceValue, decision.from),
          });
        } else {
          delegatedMessages.push(message);
        }
      }

      if (delegatedChanges[changeIndex]?.value) {
        delegatedChanges[changeIndex].value.messages = delegatedMessages;
      }
    }
  }

  return { delegatedPayload, blocked, unified };
}

export function routeDecisionForMessage(message, env) {
  if (!message?.id || !message?.from) return { kind: "delegate", message };
  const from = normalizeWaId(message.from);
  if (!from) return { kind: "delegate", message };

  const access = resolveUserAccess(from, env);
  if (!access.allowed) {
    return { kind: "blocked", message, from };
  }

  const type = String(message.type || "");
  if (UNIFIED_TEXT_TYPES.has(type)) {
    const text = normalizeUnifiedText(message);
    if (access.role === "owner" && text && looksLikeOwnerExternalMessagingIntent(text)) {
      return { kind: "delegate", message, from };
    }
    if (text && unifiedTextBridgeConfigured(env)) {
      return { kind: "unified", message, from, text, sourceType: type };
    }
  }

  return { kind: "delegate", message, from };
}

export function normalizeUnifiedText(message) {
  if (message?.type === "text") return String(message?.text?.body || "").trim();
  if (message?.type === "button") return String(message?.button?.text || "").trim();
  if (message?.type === "interactive") {
    return String(
      message?.interactive?.button_reply?.title ||
      message?.interactive?.list_reply?.title ||
      "",
    ).trim();
  }
  if (message?.type === "location") {
    const latitude = Number(message?.location?.latitude);
    const longitude = Number(message?.location?.longitude);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return "";
    const name = String(message?.location?.name || "").trim();
    const address = String(message?.location?.address || "").trim();
    const label = [name, address].filter(Boolean).join(" - ");
    return `شارك المستخدم موقعه: ${latitude}, ${longitude}${label ? ` (${label})` : ""}`;
  }
  return "";
}

export function profileNameForWaId(value, waId) {
  const target = normalizeWaId(waId);
  const contacts = Array.isArray(value?.contacts) ? value.contacts : [];
  for (const contact of contacts) {
    if (normalizeWaId(contact?.wa_id) !== target) continue;
    const name = String(contact?.profile?.name || "").trim();
    return name ? name.slice(0, 200) : null;
  }
  return null;
}

export function looksLikeOwnerExternalMessagingIntent(text) {
  const value = String(text || "").trim();
  if (!value) return false;

  const saveContact = /(?:احفظ|إحفظ|سجل|سجّل|save)\s+(?:(?:رقم|جهة\s*اتصال|contact)\b|[^\n]{0,80}\b\d{8,20}\b)/iu;
  const sendToContact = /(?:ارسل|أرسل|إرسل|رسل|ابعث|ابعت|send)\s+(?:رسالة\s+)?(?:ل|إلى|الى|to)\s*\S+/iu;
  const scheduleToContact = /(?:ذكرني\s+)?(?:ارسل|أرسل|إرسل|رسل|ابعث|ابعت|send)\b[^\n]{0,120}(?:ل|إلى|الى|to)\s*\S+/iu;
  return saveContact.test(value) || sendToContact.test(value) || scheduleToContact.test(value);
}

function unifiedTextBridgeConfigured(env) {
  return Boolean(String(env.H_SUPABASE_VOICE_URL || "").trim() && String(env.H_RUNTIME_SECRET || "").trim());
}

function resolveUserAccess(waId, env) {
  const owners = parseWaIdList(env.CONTROL_WA_IDS);
  if (owners.includes(waId)) return { allowed: true, role: "owner" };

  const friends = parseWaIdList(env.H_ALLOWED_WA_IDS);
  if (friends.includes(waId)) return { allowed: true, role: "friend" };

  if (env.ALLOW_UNKNOWN_USERS === "true") return { allowed: true, role: "friend" };
  return { allowed: false, role: "blocked" };
}

async function processBlockedMessage(env, item) {
  const firstSeen = await recordBlockedEnvelope(env, item.message.id, item.from);
  if (!firstSeen) return;
  await sendText(
    env,
    item.from,
    "هذا الرقم مخصص لمستخدمي H المصرح لهم. إذا كنت تتوقع أن يكون لك وصول، اطلب من صاحب H إضافتك.",
  );
}

async function recordBlockedEnvelope(env, messageId, waId) {
  if (!env.DB?.prepare) return true;
  try {
    const inserted = await env.DB.prepare(
      "INSERT OR IGNORE INTO inbound_messages(message_id, wa_id, body, received_at) VALUES (?, ?, ?, ?)",
    ).bind(String(messageId), waId, BLOCKED_BODY, Date.now()).run();
    return (inserted?.meta?.changes || 0) > 0;
  } catch (error) {
    console.error("Could not persist blocked WhatsApp envelope", error);
    return true;
  }
}

async function processUnifiedMessage(env, item) {
  try {
    await recordAuthorizedContactActivity(env, item);
  } catch (error) {
    console.error("Could not update authorized WhatsApp service-window activity", error);
  }

  let bridged;
  try {
    bridged = await bridgeUnifiedMessage(env, item);
  } catch (error) {
    console.error("Unified H text bridge failed before a confirmed H result", error);
    await sendText(
      env,
      item.from,
      "تعذر تمرير الرسالة إلى H الموحد الآن. لم أعد تنفيذها عبر المسار المحلي لتجنب تكرار أي تذكير أو إجراء.",
    );
    return;
  }

  if (bridged?.duplicate) return;

  const reply = bridged?.reply
    ? String(bridged.reply)
    : "استقبل H رسالتك، لكن لم يُرجع ردًا قابلاً للإرسال.";
  try {
    await sendText(env, item.from, reply);
  } catch (error) {
    console.error("H completed the unified request but Meta reply delivery failed", error);
    try {
      await sendText(
        env,
        item.from,
        "نفذ H معالجة رسالتك، لكن تعذر إرسال الرد النهائي. لم أكرر تنفيذ الطلب لتجنب تكرار أي إجراء.",
      );
    } catch (deliveryError) {
      console.error("Could not deliver H execution-status fallback", deliveryError);
    }
  }
}

async function recordAuthorizedContactActivity(env, item) {
  if (!env.DB?.prepare) return;
  const timestampSeconds = Number(item.message?.timestamp);
  const inboundAt = Number.isFinite(timestampSeconds) && timestampSeconds > 0
    ? Math.floor(timestampSeconds * 1000)
    : Date.now();
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO contacts(wa_id, profile_name, last_inbound_at, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(wa_id) DO UPDATE SET
       profile_name=COALESCE(NULLIF(excluded.profile_name, ''), contacts.profile_name),
       last_inbound_at=CASE
         WHEN excluded.last_inbound_at > COALESCE(contacts.last_inbound_at, 0)
         THEN excluded.last_inbound_at ELSE contacts.last_inbound_at END,
       updated_at=CASE
         WHEN excluded.last_inbound_at > COALESCE(contacts.last_inbound_at, 0)
         THEN excluded.updated_at ELSE contacts.updated_at END`,
  ).bind(item.from, item.profileName || null, inboundAt, now, now).run();
}

async function bridgeUnifiedMessage(env, item) {
  const endpoint = String(env.H_SUPABASE_VOICE_URL || "").trim();
  const secret = String(env.H_RUNTIME_SECRET || "").trim();
  if (!endpoint || !secret) throw new Error("Unified H text bridge is not configured");

  const receivedAtMs = Number(item.message?.timestamp) * 1000;
  const messageId = `channel:${item.sourceType}:${String(item.message?.id || "")}`.slice(0, 200);
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-h-runtime-secret": secret,
    },
    body: JSON.stringify({
      mode: "voice_transcript",
      wa_id: item.from,
      message_id: messageId,
      transcript: String(item.text || "").slice(0, 12000),
      received_at: Number.isFinite(receivedAtMs) && receivedAtMs > 0
        ? new Date(receivedAtMs).toISOString()
        : new Date().toISOString(),
    }),
  });

  const responseText = await response.text();
  let data = {};
  try { data = responseText ? JSON.parse(responseText) : {}; } catch (_) {}
  if (!response.ok || data?.ok === false) {
    throw new Error(`H unified text bridge rejected request (${response.status}): ${String(data?.error || responseText).slice(0, 300)}`);
  }
  return data;
}

async function augmentHealth(response, env) {
  if (!response.ok) return response;
  try {
    const body = await response.json();
    return json({
      ...body,
      unifiedTextBridgeConfigured: unifiedTextBridgeConfigured(env),
      textRuntime: unifiedTextBridgeConfigured(env) ? "supabase_h_unified" : "legacy_d1_fallback",
      blockedIngressGuard: true,
      ownerExternalMessagingRuntime: "legacy_guarded",
      serviceWindowActivityMirror: true,
    }, response.status);
  } catch {
    return response;
  }
}

function hasMessages(payload) {
  const entries = Array.isArray(payload?.entry) ? payload.entry : [];
  return entries.some((entry) => {
    const changes = Array.isArray(entry?.changes) ? entry.changes : [];
    return changes.some((change) => Array.isArray(change?.value?.messages) && change.value.messages.length > 0);
  });
}

async function verifyMetaSignature(request, rawBody, appSecret) {
  const secret = String(appSecret || "");
  if (!secret) return false;
  const header = request.headers.get("x-hub-signature-256") || "";
  if (!header.startsWith("sha256=")) return false;
  const expected = await hmacSha256Hex(secret, rawBody);
  return constantTimeEqual(header.slice(7).toLowerCase(), expected.toLowerCase());
}

async function hmacSha256Hex(secret, text) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(text));
  return [...new Uint8Array(signature)].map((value) => value.toString(16).padStart(2, "0")).join("");
}

function constantTimeEqual(left, right) {
  if (left.length !== right.length) return false;
  let diff = 0;
  for (let index = 0; index < left.length; index += 1) {
    diff |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return diff === 0;
}

function parseWaIdList(csv) {
  return String(csv || "")
    .split(",")
    .map(normalizeWaId)
    .filter(Boolean);
}

function normalizeWaId(value) {
  return String(value || "").replace(/\D/g, "");
}

async function sendText(env, to, body) {
  const version = String(env.META_GRAPH_VERSION || "").trim();
  if (!version || !env.WHATSAPP_PHONE_NUMBER_ID || !env.WHATSAPP_ACCESS_TOKEN) {
    throw new Error("WhatsApp Cloud API environment is incomplete");
  }
  const response = await fetch(
    `https://graph.facebook.com/${version}/${env.WHATSAPP_PHONE_NUMBER_ID}/messages`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.WHATSAPP_ACCESS_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        messaging_product: "whatsapp",
        recipient_type: "individual",
        to: normalizeWaId(to),
        type: "text",
        text: { preview_url: false, body: String(body || "").slice(0, MAX_MESSAGE_LENGTH) },
      }),
    },
  );
  const responseText = await response.text();
  if (!response.ok) {
    throw new Error(`Meta send failed (${response.status}): ${responseText.slice(0, 500)}`);
  }
  return responseText ? JSON.parse(responseText) : {};
}

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value));
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
