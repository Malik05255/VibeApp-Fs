const DAY_MS = 24 * 60 * 60 * 1000;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return json({
        ok: true,
        service: "h-whatsapp-worker",
        metaConfigured: Boolean(env.WHATSAPP_PHONE_NUMBER_ID && env.WHATSAPP_ACCESS_TOKEN),
        aiConfigured: Boolean(env.OPENROUTER_API_KEY && env.H_MODEL),
        templateConfigured: Boolean(env.WHATSAPP_REMINDER_TEMPLATE_NAME),
      });
    }

    if (url.pathname !== "/webhook") {
      return new Response("Not found", { status: 404 });
    }

    if (request.method === "GET") {
      return verifyWebhook(url, env);
    }

    if (request.method === "POST") {
      const rawBody = await request.text();
      const verified = await verifyMetaSignature(request, rawBody, env.WHATSAPP_APP_SECRET);
      if (!verified) return new Response("Invalid signature", { status: 401 });

      let payload;
      try {
        payload = JSON.parse(rawBody);
      } catch {
        return new Response("Invalid JSON", { status: 400 });
      }

      ctx.waitUntil(handleWebhook(payload, env));
      return new Response("EVENT_RECEIVED", { status: 200 });
    }

    return new Response("Method not allowed", { status: 405 });
  },

  async scheduled(_event, env, ctx) {
    ctx.waitUntil(processDueJobs(env));
  },
};

function verifyWebhook(url, env) {
  const mode = url.searchParams.get("hub.mode");
  const token = url.searchParams.get("hub.verify_token");
  const challenge = url.searchParams.get("hub.challenge");

  if (mode === "subscribe" && token && token === env.WHATSAPP_VERIFY_TOKEN) {
    return new Response(challenge || "", { status: 200 });
  }
  return new Response("Forbidden", { status: 403 });
}

async function verifyMetaSignature(request, rawBody, appSecret) {
  if (!appSecret) return false;
  const header = request.headers.get("x-hub-signature-256") || "";
  if (!header.startsWith("sha256=")) return false;

  const expected = await hmacSha256Hex(appSecret, rawBody);
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
  return [...new Uint8Array(signature)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function constantTimeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i += 1) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

async function handleWebhook(payload, env) {
  const entries = Array.isArray(payload?.entry) ? payload.entry : [];
  for (const entry of entries) {
    const changes = Array.isArray(entry?.changes) ? entry.changes : [];
    for (const change of changes) {
      const value = change?.value || {};
      const contacts = Array.isArray(value.contacts) ? value.contacts : [];
      const profileByWaId = new Map(
        contacts.map((contact) => [normalizeWaId(contact?.wa_id), contact?.profile?.name || null]),
      );
      const messages = Array.isArray(value.messages) ? value.messages : [];

      for (const message of messages) {
        if (!message?.id || !message?.from) continue;
        const from = normalizeWaId(message.from);
        const text = extractText(message);
        if (!from || !text) continue;

        const firstSeen = await recordInbound(
          env,
          message.id,
          from,
          text,
          profileByWaId.get(from) || null,
        );
        if (!firstSeen) continue;

        if (!isControllerAllowed(from, env.CONTROL_WA_IDS)) {
          await sendText(env, from, "هذا الرقم مخصص للمستخدمين المصرح لهم في H.");
          continue;
        }

        await handleUserText(env, from, text);
      }
    }
  }
}

function extractText(message) {
  if (message.type === "text") return message?.text?.body?.trim() || "";
  if (message.type === "button") return message?.button?.text?.trim() || "";
  if (message.type === "interactive") {
    return (
      message?.interactive?.button_reply?.title ||
      message?.interactive?.list_reply?.title ||
      ""
    ).trim();
  }
  return "";
}

async function recordInbound(env, messageId, waId, body, profileName) {
  const now = Date.now();
  const inserted = await env.DB.prepare(
    "INSERT OR IGNORE INTO inbound_messages(message_id, wa_id, body, received_at) VALUES (?, ?, ?, ?)",
  ).bind(messageId, waId, body, now).run();

  if ((inserted?.meta?.changes || 0) === 0) return false;

  await env.DB.prepare(
    `INSERT INTO contacts(wa_id, profile_name, last_inbound_at, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(wa_id) DO UPDATE SET
       profile_name = excluded.profile_name,
       last_inbound_at = excluded.last_inbound_at,
       updated_at = excluded.updated_at`,
  ).bind(waId, profileName, now, now, now).run();

  return true;
}

function isControllerAllowed(waId, csv) {
  const allowed = String(csv || "")
    .split(",")
    .map(normalizeWaId)
    .filter(Boolean);
  return allowed.length === 0 || allowed.includes(waId);
}

async function handleUserText(env, waId, text) {
  if (/^(الغاء|إلغاء|cancel)\s+كل\s*(التذكيرات)?$/i.test(text.trim())) {
    await env.DB.prepare(
      "UPDATE scheduled_jobs SET status='CANCELLED', updated_at=? WHERE owner_wa_id=? AND status='PENDING'",
    ).bind(Date.now(), waId).run();
    await sendText(env, waId, "تم إلغاء التذكيرات المعلقة.");
    return;
  }

  const deterministic = parseRelativeReminder(text);
  if (deterministic) {
    await createScheduledReminder(env, waId, deterministic.body, deterministic.dueAtMs);
    await sendText(env, waId, formatConfirmation(deterministic.body, deterministic.dueAtMs));
    return;
  }

  const aiResult = await interpretWithAi(env, text);
  if (aiResult?.action === "schedule_self" && aiResult?.body && aiResult?.dueAtIso) {
    const dueAtMs = Date.parse(aiResult.dueAtIso);
    if (Number.isFinite(dueAtMs) && dueAtMs > Date.now()) {
      await createScheduledReminder(env, waId, aiResult.body, dueAtMs);
      await sendText(env, waId, aiResult.reply || formatConfirmation(aiResult.body, dueAtMs));
      return;
    }
  }

  if (aiResult?.reply) {
    await sendText(env, waId, aiResult.reply);
    return;
  }

  await sendText(
    env,
    waId,
    "تم ربط H بالواتساب. جرّب مثلًا: «ذكرني بعد ساعة أشتري الدواء». وللتذكيرات بصياغة أوسع يلزم تفعيل نموذج H السحابي.",
  );
}

function parseRelativeReminder(text) {
  const normalized = text.trim();
  const match = normalized.match(
    /(?:ذكرني|ذكّرني|remind me)\s+(?:بعد\s+)?(\d+)\s*(دقيق(?:ة|ه|ايق)?|دقائق|ساعة|ساعات|يوم|ايام|أيام|minute|minutes|hour|hours|day|days)\s*(.*)$/i,
  );
  if (!match) return null;

  const amount = Number(match[1]);
  if (!Number.isFinite(amount) || amount <= 0 || amount > 3650) return null;

  const unit = match[2].toLowerCase();
  const body = (match[3] || "التذكير الذي طلبته").trim();
  let multiplier;
  if (unit.includes("دقيق") || unit.startsWith("minute")) multiplier = 60 * 1000;
  else if (unit.includes("ساع") || unit.startsWith("hour")) multiplier = 60 * 60 * 1000;
  else multiplier = DAY_MS;

  return { body, dueAtMs: Date.now() + amount * multiplier };
}

async function interpretWithAi(env, text) {
  if (!env.OPENROUTER_API_KEY || !env.H_MODEL) return null;

  const now = new Date().toISOString();
  const timeZone = env.DEFAULT_TIME_ZONE || "Asia/Riyadh";
  const system = [
    "You are the WhatsApp command interpreter for the personal assistant H.",
    `Current UTC time: ${now}. User timezone: ${timeZone}.`,
    "Return ONLY one JSON object, no markdown.",
    "Allowed shapes:",
    '{"action":"reply","reply":"Arabic response"}',
    '{"action":"schedule_self","body":"reminder text","dueAtIso":"absolute ISO-8601 timestamp with offset","reply":"Arabic confirmation"}',
    "Use schedule_self only when the user clearly asks for a future reminder.",
    "Never invent a date/time if the request is ambiguous; ask a concise follow-up via action=reply.",
  ].join("\n");

  try {
    const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.OPENROUTER_API_KEY}`,
        "Content-Type": "application/json",
        "HTTP-Referer": env.PUBLIC_BASE_URL || "https://example.invalid",
        "X-Title": "H WhatsApp Assistant",
      },
      body: JSON.stringify({
        model: env.H_MODEL,
        temperature: 0.1,
        messages: [
          { role: "system", content: system },
          { role: "user", content: text },
        ],
      }),
    });
    if (!response.ok) return null;
    const data = await response.json();
    const content = data?.choices?.[0]?.message?.content || "";
    return parseJsonObject(content);
  } catch {
    return null;
  }
}

function parseJsonObject(content) {
  const cleaned = String(content || "")
    .trim()
    .replace(/^```(?:json)?/i, "")
    .replace(/```$/, "")
    .trim();
  try {
    return JSON.parse(cleaned);
  } catch {
    const start = cleaned.indexOf("{");
    const end = cleaned.lastIndexOf("}");
    if (start < 0 || end <= start) return null;
    try {
      return JSON.parse(cleaned.slice(start, end + 1));
    } catch {
      return null;
    }
  }
}

async function createScheduledReminder(env, ownerWaId, body, dueAtMs) {
  const now = Date.now();
  const id = crypto.randomUUID();
  await env.DB.prepare(
    `INSERT INTO scheduled_jobs(
      id, owner_wa_id, target_wa_id, body, due_at, status, attempts, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)`,
  ).bind(id, ownerWaId, ownerWaId, body, dueAtMs, now, now).run();
}

function formatConfirmation(body, dueAtMs) {
  const when = new Date(dueAtMs).toISOString();
  return `تم. سأذكرك: ${body}\nالموعد: ${when}`;
}

async function processDueJobs(env) {
  const now = Date.now();
  const due = await env.DB.prepare(
    `SELECT id, owner_wa_id, target_wa_id, body, due_at, attempts
     FROM scheduled_jobs
     WHERE status='PENDING' AND due_at <= ?
     ORDER BY due_at ASC
     LIMIT 50`,
  ).bind(now).all();

  for (const job of due?.results || []) {
    try {
      const contact = await env.DB.prepare(
        "SELECT last_inbound_at FROM contacts WHERE wa_id=? LIMIT 1",
      ).bind(job.target_wa_id).first();
      const lastInboundAt = Number(contact?.last_inbound_at || 0);
      const insideServiceWindow = lastInboundAt > 0 && now - lastInboundAt < DAY_MS;

      if (insideServiceWindow) {
        await sendText(env, job.target_wa_id, `تذكير من H: ${job.body}`);
      } else if (env.WHATSAPP_REMINDER_TEMPLATE_NAME) {
        await sendReminderTemplate(env, job.target_wa_id, job.body);
      } else {
        await env.DB.prepare(
          `UPDATE scheduled_jobs
           SET status='WAITING_TEMPLATE', attempts=attempts+1,
               last_error=?, updated_at=?
           WHERE id=?`,
        ).bind(
          "Outside WhatsApp 24-hour service window and no reminder template is configured",
          now,
          job.id,
        ).run();
        continue;
      }

      await env.DB.prepare(
        "UPDATE scheduled_jobs SET status='SENT', attempts=attempts+1, last_error=NULL, updated_at=? WHERE id=?",
      ).bind(now, job.id).run();
    } catch (error) {
      await env.DB.prepare(
        `UPDATE scheduled_jobs
         SET attempts=attempts+1, last_error=?, updated_at=?
         WHERE id=?`,
      ).bind(String(error?.message || error).slice(0, 500), now, job.id).run();
    }
  }
}

async function sendText(env, to, body) {
  return sendWhatsApp(env, {
    messaging_product: "whatsapp",
    recipient_type: "individual",
    to: normalizeWaId(to),
    type: "text",
    text: { preview_url: false, body: body.slice(0, 4096) },
  });
}

async function sendReminderTemplate(env, to, body) {
  return sendWhatsApp(env, {
    messaging_product: "whatsapp",
    to: normalizeWaId(to),
    type: "template",
    template: {
      name: env.WHATSAPP_REMINDER_TEMPLATE_NAME,
      language: { code: env.WHATSAPP_REMINDER_TEMPLATE_LANGUAGE || "ar" },
      components: [
        {
          type: "body",
          parameters: [{ type: "text", text: body.slice(0, 1024) }],
        },
      ],
    },
  });
}

async function sendWhatsApp(env, payload) {
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
      body: JSON.stringify(payload),
    },
  );

  const text = await response.text();
  if (!response.ok) {
    throw new Error(`Meta send failed (${response.status}): ${text.slice(0, 500)}`);
  }
  return text ? JSON.parse(text) : {};
}

function normalizeWaId(value) {
  return String(value || "").replace(/\D/g, "");
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
