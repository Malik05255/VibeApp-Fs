const DAY_MS = 24 * 60 * 60 * 1000;
const DEFAULT_HISTORY_LIMIT = 14;
const DEFAULT_MAX_MESSAGE_LENGTH = 4096;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (url.pathname === "/health" || url.pathname === "/setup/status") {
      return healthResponse(env);
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

function healthResponse(env) {
  const metaConfigured = Boolean(
    env.WHATSAPP_PHONE_NUMBER_ID &&
      env.WHATSAPP_ACCESS_TOKEN &&
      env.WHATSAPP_APP_SECRET &&
      env.WHATSAPP_VERIFY_TOKEN &&
      env.META_GRAPH_VERSION,
  );
  const ownerConfigured = parseWaIdList(env.CONTROL_WA_IDS).length > 0;
  const aiConfigured = Boolean(env.OPENROUTER_API_KEY && env.H_MODEL);
  const voiceTranscriptionConfigured = Boolean(transcriptionApiKey(env));
  const voiceBridgeConfigured = Boolean(env.H_SUPABASE_VOICE_URL && env.H_RUNTIME_SECRET);
  const voiceConfigured = metaConfigured && voiceTranscriptionConfigured && voiceBridgeConfigured;
  const mediaBridgeConfigured = Boolean(env.H_RUNTIME_SECRET && (env.H_SUPABASE_MEDIA_URL || env.H_SUPABASE_VOICE_URL));
  const mediaConfigured = metaConfigured && mediaBridgeConfigured;
  const templateConfigured = Boolean(env.WHATSAPP_REMINDER_TEMPLATE_NAME);

  return json({
    ok: true,
    service: "h-whatsapp-cloud-runtime",
    mode: "official_whatsapp_cloud_api",
    runtimeReady: metaConfigured && ownerConfigured,
    businessAppRuntimeDependency: false,
    metaConfigured,
    ownerConfigured,
    aiConfigured,
    voiceConfigured,
    voiceTranscriptionConfigured,
    voiceBridgeConfigured,
    mediaConfigured,
    mediaBridgeConfigured,
    templateConfigured,
    friendsConfigured: parseWaIdList(env.H_ALLOWED_WA_IDS).length > 0,
    unknownUsersAllowed: env.ALLOW_UNKNOWN_USERS === "true",
    note: "Do not remove the WhatsApp Business app until the number is migrated/registered for the official Cloud API and an end-to-end webhook test succeeds.",
  });
}

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
        if (!from) continue;

        const access = resolveUserAccess(from, env);
        const inbound = await normalizeInboundMessage(message, env);
        const storedBody = inbound.text || `[${message.type || "unknown"}]`;

        const firstSeen = await recordInbound(
          env,
          message.id,
          from,
          storedBody,
          profileByWaId.get(from) || null,
        );
        if (!firstSeen) continue;

        await appendConversationMessage(env, from, "user", storedBody);

        if (!access.allowed) {
          await sendAssistantText(
            env,
            from,
            "هذا الرقم مخصص لمستخدمي H المصرح لهم. إذا كنت تتوقع أن يكون لك وصول، اطلب من صاحب H إضافتك.",
          );
          continue;
        }

        if (inbound.error) {
          await sendAssistantText(env, from, inbound.error);
          continue;
        }

        if (inbound.media) {
          if (!env.H_RUNTIME_SECRET || (!env.H_SUPABASE_MEDIA_URL && !env.H_SUPABASE_VOICE_URL)) {
            await sendAssistantText(
              env,
              from,
              "وصلتني الوسائط، لكن ربط الصور والملفات بذاكرة H الموحدة غير مفعّل بعد، لذلك لم أحللها.",
            );
            continue;
          }
          try {
            const media = await downloadWhatsAppMediaForH(env, inbound.media);
            const bridged = await bridgeMediaMessage(env, from, message.id, media, message.timestamp);
            if (bridged?.duplicate) continue;
            if (bridged?.reply) {
              await sendAssistantText(env, from, bridged.reply);
            } else {
              await sendAssistantText(env, from, "فهمت الوسائط، لكن H لم يُرجع نتيجة قابلة للإرسال.");
            }
          } catch (error) {
            console.error("Unified H media bridge failed", error);
            const detail = String(error?.message || error);
            if (detail.includes("no_strictly_free_media_analysis_available")) {
              await sendAssistantText(
                env,
                from,
                "وصلتني الصورة أو الملف، لكن ما فيه الآن مسار فهم مجاني متاح. لم أستخدم أي مسار مدفوع.",
              );
            } else {
              await sendAssistantText(
                env,
                from,
                "وصلتني الصورة أو الملف، لكن تعذر تحليله الآن. لم أنفذ أي إجراء بناءً على محتوى غير مؤكد.",
              );
            }
          }
          continue;
        }

        if (!inbound.text) {
          await sendAssistantText(
            env,
            from,
            "وصلتني الرسالة، لكن هذا النوع غير مدعوم في H السحابي حاليًا. أرسل نصًا أو صوتًا أو صورة أو PDF/ملفًا نصيًا مدعومًا.",
          );
          continue;
        }

        if (message.type === "audio") {
          if (!env.H_SUPABASE_VOICE_URL || !env.H_RUNTIME_SECRET) {
            await sendAssistantText(
              env,
              from,
              "وصلني المقطع الصوتي واستطعت قراءته، لكن ربط الصوت بذاكرة H الموحدة غير مفعّل بعد، لذلك لم أنفذ الطلب.",
            );
            continue;
          }
          try {
            const bridged = await bridgeVoiceTranscript(env, from, message.id, inbound.text, message.timestamp);
            if (bridged?.duplicate) continue;
            if (bridged?.reply) {
              await sendAssistantText(env, from, bridged.reply);
            } else {
              await sendAssistantText(env, from, "فهمت المقطع الصوتي، لكن H لم يُرجع نتيجة قابلة للإرسال.");
            }
          } catch (error) {
            console.error("Unified H voice bridge failed", error);
            await sendAssistantText(
              env,
              from,
              "وصلني المقطع الصوتي، لكن تعذر تمريره إلى H الموحد الآن. لم أنفذ أي إجراء لتجنب التكرار أو الخطأ.",
            );
          }
          continue;
        }

        await handleUserInput(env, from, inbound.text, access);
      }
    }
  }
}

async function normalizeInboundMessage(message, env) {
  if (message.type === "text") {
    return { text: message?.text?.body?.trim() || "" };
  }
  if (message.type === "button") {
    return { text: message?.button?.text?.trim() || "" };
  }
  if (message.type === "interactive") {
    return {
      text: (
        message?.interactive?.button_reply?.title ||
        message?.interactive?.list_reply?.title ||
        ""
      ).trim(),
    };
  }
  if (message.type === "location") {
    const latitude = Number(message?.location?.latitude);
    const longitude = Number(message?.location?.longitude);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return { text: "" };
    const name = String(message?.location?.name || "").trim();
    const address = String(message?.location?.address || "").trim();
    const label = [name, address].filter(Boolean).join(" - ");
    return {
      text: `شارك المستخدم موقعه: ${latitude}, ${longitude}${label ? ` (${label})` : ""}`,
    };
  }
  if (message.type === "image" || message.type === "document") {
    const value = message?.[message.type] || {};
    const mediaId = value?.id;
    if (!mediaId) {
      return {
        text: "",
        error: message.type === "image"
          ? "وصلتني الصورة لكن لم يصل معرّف الوسائط من واتساب."
          : "وصلني الملف لكن لم يصل معرّف الوسائط من واتساب.",
      };
    }
    return {
      text: "",
      media: {
        kind: message.type,
        mediaId: String(mediaId),
        declaredMimeType: String(value?.mime_type || "").trim(),
        fileName: String(value?.filename || "").trim(),
        caption: String(value?.caption || "").trim(),
      },
    };
  }
  if (message.type === "audio") {
    const mediaId = message?.audio?.id;
    if (!mediaId) return { text: "", error: "وصلني المقطع الصوتي لكن لم يصل معرّف الوسائط من واتساب." };
    if (!transcriptionApiKey(env)) {
      return {
        text: "",
        error: "وصلني المقطع الصوتي، لكن تحويل الصوت إلى نص غير مفعّل في H السحابي بعد.",
      };
    }
    try {
      const transcript = await transcribeWhatsAppAudio(env, mediaId);
      if (!transcript) {
        return { text: "", error: "تعذر استخراج كلام واضح من المقطع الصوتي. جرّب إرساله مرة أخرى." };
      }
      return { text: transcript };
    } catch (error) {
      console.error("Audio transcription failed", error);
      return { text: "", error: "تعذر تحليل المقطع الصوتي الآن. جرّب بعد قليل أو أرسل الطلب كتابةً." };
    }
  }
  return { text: "" };
}

const H_MEDIA_MAX_BYTES = 8 * 1024 * 1024;

async function downloadWhatsAppMediaForH(env, mediaRef) {
  const version = requireMetaGraphVersion(env);
  const metadataResponse = await fetch(
    `https://graph.facebook.com/${version}/${encodeURIComponent(mediaRef.mediaId)}`,
    { headers: { Authorization: `Bearer ${env.WHATSAPP_ACCESS_TOKEN}` } },
  );
  const metadataText = await metadataResponse.text();
  if (!metadataResponse.ok) {
    throw new Error(`Meta media metadata failed (${metadataResponse.status}): ${metadataText.slice(0, 300)}`);
  }
  const metadata = metadataText ? JSON.parse(metadataText) : {};
  if (!metadata.url) throw new Error("Meta media metadata did not include a download URL");

  const declaredSize = Number(metadata.file_size || 0);
  if (Number.isFinite(declaredSize) && declaredSize > H_MEDIA_MAX_BYTES) {
    throw new Error(`WhatsApp media exceeds H safe bridge limit (${H_MEDIA_MAX_BYTES} bytes)`);
  }

  const mediaResponse = await fetch(metadata.url, {
    headers: { Authorization: `Bearer ${env.WHATSAPP_ACCESS_TOKEN}` },
  });
  if (!mediaResponse.ok) throw new Error(`Meta media download failed (${mediaResponse.status})`);
  const blob = await mediaResponse.blob();
  if (!blob.size || blob.size > H_MEDIA_MAX_BYTES) {
    throw new Error(`WhatsApp media size is outside H safe bridge limit (${blob.size} bytes)`);
  }

  const mimeType = String(metadata.mime_type || blob.type || mediaRef.declaredMimeType || "")
    .split(";", 1)[0]
    .trim()
    .toLowerCase();
  if (!mimeType) throw new Error("WhatsApp media MIME type is unavailable");

  return {
    kind: mediaRef.kind,
    mimeType,
    fileName: String(mediaRef.fileName || "").slice(0, 160),
    caption: String(mediaRef.caption || "").slice(0, 2000),
    sizeBytes: blob.size,
    base64: arrayBufferToBase64(await blob.arrayBuffer()),
  };
}

function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  const chunks = [];
  const chunkSize = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    chunks.push(String.fromCharCode(...bytes.subarray(offset, offset + chunkSize)));
  }
  return btoa(chunks.join(""));
}

function mediaBridgeEndpoint(env) {
  const explicit = String(env.H_SUPABASE_MEDIA_URL || "").trim();
  if (explicit) return explicit;
  const voice = String(env.H_SUPABASE_VOICE_URL || "").trim();
  if (!voice) throw new Error("Unified H media bridge is not configured");
  if (/\/h-whatsapp-inbox\/?(?:\?.*)?$/.test(voice)) {
    return voice.replace(/\/h-whatsapp-inbox\/?(?=\?|$)/, "/h-whatsapp-media");
  }
  throw new Error("H_SUPABASE_MEDIA_URL is required when the inbox URL cannot be derived");
}

async function bridgeMediaMessage(env, waId, messageId, media, timestamp) {
  const endpoint = mediaBridgeEndpoint(env);
  const secret = String(env.H_RUNTIME_SECRET || "").trim();
  if (!secret) throw new Error("Unified H media bridge secret is not configured");

  const receivedAtMs = Number(timestamp) * 1000;
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-h-runtime-secret": secret,
    },
    body: JSON.stringify({
      mode: "media_message",
      wa_id: normalizeWaId(waId),
      message_id: String(messageId || "").slice(0, 200),
      kind: media.kind,
      mime_type: media.mimeType,
      file_name: media.fileName || null,
      caption: media.caption || null,
      base64: media.base64,
      received_at: Number.isFinite(receivedAtMs) && receivedAtMs > 0
        ? new Date(receivedAtMs).toISOString()
        : new Date().toISOString(),
    }),
  });
  const text = await response.text();
  let data = {};
  try { data = text ? JSON.parse(text) : {}; } catch (_) {}
  if (!response.ok || data?.ok === false) {
    throw new Error(`H media bridge rejected request (${response.status}): ${String(data?.error || text).slice(0, 300)}`);
  }
  return data;
}

async function transcribeWhatsAppAudio(env, mediaId) {
  const version = requireMetaGraphVersion(env);
  const metadataResponse = await fetch(`https://graph.facebook.com/${version}/${encodeURIComponent(mediaId)}`, {
    headers: { Authorization: `Bearer ${env.WHATSAPP_ACCESS_TOKEN}` },
  });
  const metadataText = await metadataResponse.text();
  if (!metadataResponse.ok) {
    throw new Error(`Meta media metadata failed (${metadataResponse.status}): ${metadataText.slice(0, 300)}`);
  }
  const metadata = metadataText ? JSON.parse(metadataText) : {};
  if (!metadata.url) throw new Error("Meta media metadata did not include a download URL");

  const mediaResponse = await fetch(metadata.url, {
    headers: { Authorization: `Bearer ${env.WHATSAPP_ACCESS_TOKEN}` },
  });
  if (!mediaResponse.ok) {
    throw new Error(`Meta media download failed (${mediaResponse.status})`);
  }
  const blob = await mediaResponse.blob();

  const endpoint = String(env.TRANSCRIPTION_API_URL || "https://api.groq.com/openai/v1/audio/transcriptions").trim();
  const form = new FormData();
  const fileName = `whatsapp-audio.${extensionForMime(metadata.mime_type || blob.type)}`;
  form.append("file", blob, fileName);
  form.append("model", String(env.TRANSCRIPTION_MODEL || "whisper-large-v3-turbo"));
  form.append("response_format", "json");

  const response = await fetch(endpoint, {
    method: "POST",
    headers: { Authorization: `Bearer ${transcriptionApiKey(env)}` },
    body: form,
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`Transcription failed (${response.status}): ${text.slice(0, 300)}`);
  }
  const data = text ? JSON.parse(text) : {};
  return String(data.text || "").trim();
}

function transcriptionApiKey(env) {
  return String(env.TRANSCRIPTION_API_KEY || env.GROQ_API_KEY || "").trim();
}

function extensionForMime(mime) {
  const value = String(mime || "").toLowerCase();
  if (value.includes("ogg")) return "ogg";
  if (value.includes("mpeg") || value.includes("mp3")) return "mp3";
  if (value.includes("mp4") || value.includes("m4a")) return "m4a";
  if (value.includes("wav")) return "wav";
  return "bin";
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

function resolveUserAccess(waId, env) {
  const owners = parseWaIdList(env.CONTROL_WA_IDS);
  if (owners.includes(waId)) {
    return { allowed: true, role: "owner", canSendExternal: true };
  }

  const friends = parseWaIdList(env.H_ALLOWED_WA_IDS);
  if (friends.includes(waId)) {
    return { allowed: true, role: "friend", canSendExternal: false };
  }

  if (env.ALLOW_UNKNOWN_USERS === "true") {
    return { allowed: true, role: "friend", canSendExternal: false };
  }

  return { allowed: false, role: "blocked", canSendExternal: false };
}

function parseWaIdList(csv) {
  return String(csv || "")
    .split(",")
    .map(normalizeWaId)
    .filter(Boolean);
}

async function bridgeVoiceTranscript(env, waId, messageId, transcript, timestamp) {
  const endpoint = String(env.H_SUPABASE_VOICE_URL || "").trim();
  const secret = String(env.H_RUNTIME_SECRET || "").trim();
  if (!endpoint || !secret) throw new Error("Unified H voice bridge is not configured");

  const receivedAtMs = Number(timestamp) * 1000;
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-h-runtime-secret": secret,
    },
    body: JSON.stringify({
      mode: "voice_transcript",
      wa_id: normalizeWaId(waId),
      message_id: String(messageId || "").slice(0, 200),
      transcript: String(transcript || "").slice(0, 12000),
      received_at: Number.isFinite(receivedAtMs) && receivedAtMs > 0
        ? new Date(receivedAtMs).toISOString()
        : new Date().toISOString(),
    }),
  });
  const text = await response.text();
  let data = {};
  try { data = text ? JSON.parse(text) : {}; } catch (_) {}
  if (!response.ok || data?.ok === false) {
    throw new Error(`H voice bridge rejected request (${response.status}): ${String(data?.error || text).slice(0, 300)}`);
  }
  return data;
}

async function handleUserInput(env, waId, text, access) {
  const trimmed = text.trim();

  if (/^(الغاء|إلغاء|cancel)\s+كل\s*(التذكيرات)?$/i.test(trimmed)) {
    await env.DB.prepare(
      "UPDATE scheduled_jobs SET status='CANCELLED', updated_at=? WHERE owner_wa_id=? AND status IN ('PENDING','WAITING_TEMPLATE')",
    ).bind(Date.now(), waId).run();
    await sendAssistantText(env, waId, "تم إلغاء التذكيرات المعلقة.");
    return;
  }

  if (looksLikeReminderListRequest(trimmed)) {
    await sendAssistantText(env, waId, await formatReminderList(env, waId));
    return;
  }

  if (looksLikeMemoryListRequest(trimmed)) {
    await sendAssistantText(env, waId, await formatMemoryList(env, waId));
    return;
  }

  const deterministic = parseRelativeReminder(trimmed);
  if (deterministic) {
    await createScheduledReminder(env, waId, waId, deterministic.body, deterministic.dueAtMs);
    await sendAssistantText(env, waId, formatConfirmation(deterministic.body, deterministic.dueAtMs));
    return;
  }

  const aiResult = await interpretWithAi(env, waId, trimmed, access);
  if (!aiResult) {
    await sendAssistantText(
      env,
      waId,
      "أنا H. استقبلت طلبك، لكن مسار الذكاء السحابي غير متاح الآن. التذكيرات المباشرة مثل «ذكرني بعد ساعة أشتري الدواء» ما زالت تعمل.",
    );
    return;
  }

  await executeAiAction(env, waId, aiResult, access);
}

async function executeAiAction(env, waId, result, access) {
  const action = String(result?.action || "reply");

  if (action === "schedule_self" && result?.body && result?.dueAtIso) {
    const dueAtMs = Date.parse(result.dueAtIso);
    if (!Number.isFinite(dueAtMs) || dueAtMs <= Date.now()) {
      await sendAssistantText(env, waId, "الموعد غير واضح عندي. حدده بوقت أو تاريخ أوضح.");
      return;
    }
    await createScheduledReminder(env, waId, waId, String(result.body), dueAtMs);
    await sendAssistantText(env, waId, result.reply || formatConfirmation(String(result.body), dueAtMs));
    return;
  }

  if (action === "save_memory" && result?.body) {
    await saveMemory(env, waId, String(result.body), String(result.category || "note"));
    await sendAssistantText(env, waId, result.reply || "حفظتها عندي. تقدر ترجع لها لاحقًا حتى بعد مدة طويلة.");
    return;
  }

  if (action === "list_memories") {
    await sendAssistantText(env, waId, await formatMemoryList(env, waId));
    return;
  }

  if (action === "list_reminders") {
    await sendAssistantText(env, waId, await formatReminderList(env, waId));
    return;
  }

  if (action === "save_contact" && result?.name && result?.phone) {
    if (!access.canSendExternal) {
      await sendAssistantText(env, waId, "أقدر أساعدك داخل H، لكن حفظ أرقام للإرسال الخارجي متاح لصاحب H فقط.");
      return;
    }
    const target = normalizeWaId(result.phone);
    if (!target) {
      await sendAssistantText(env, waId, "رقم الجوال غير واضح. أرسله مع رمز الدولة.");
      return;
    }
    await saveNamedContact(env, waId, String(result.name), target);
    await sendAssistantText(env, waId, result.reply || `تم حفظ ${String(result.name)}.`);
    return;
  }

  if ((action === "send_contact" || action === "schedule_contact") && result?.contactName && result?.body) {
    if (!access.canSendExternal) {
      await sendAssistantText(env, waId, "الإرسال إلى أرقام واتساب أخرى متاح لصاحب H فقط.");
      return;
    }
    const contact = await resolveNamedContact(env, waId, String(result.contactName));
    if (!contact) {
      await sendAssistantText(
        env,
        waId,
        `ما عندي رقم ${String(result.contactName)} محفوظ. احفظه أولًا بقولك مثلًا: «احفظ محمد عمر 9665…».`,
      );
      return;
    }

    if (action === "schedule_contact") {
      const dueAtMs = Date.parse(result.dueAtIso || "");
      if (!Number.isFinite(dueAtMs) || dueAtMs <= Date.now()) {
        await sendAssistantText(env, waId, "موعد الإرسال غير واضح. حدده بشكل أوضح.");
        return;
      }
      await createScheduledReminder(env, waId, contact.target_wa_id, String(result.body), dueAtMs);
      await sendAssistantText(
        env,
        waId,
        result.reply || `تم. سأحاول إرسال الرسالة إلى ${contact.display_name} في الموعد المحدد وفق سياسة واتساب.`,
      );
      return;
    }

    const outcome = await sendOutboundWithPolicy(env, contact.target_wa_id, String(result.body));
    if (outcome.sent) {
      await sendAssistantText(env, waId, result.reply || `تم إرسال الرسالة إلى ${contact.display_name}.`);
    } else {
      await sendAssistantText(env, waId, outcome.reason);
    }
    return;
  }

  await sendAssistantText(env, waId, String(result?.reply || "تم."));
}

function looksLikeReminderListRequest(text) {
  return /(وش|ما|اعطني|عطني|عرض|اظهر|أظهر).*(تذكير|تذكيرات)|(?:تذكيراتي|reminders)/i.test(text);
}

function looksLikeMemoryListRequest(text) {
  return /(وش|ما|اعطني|عطني|عرض|اظهر|أظهر).*(فكر|افكار|أفكار|ملاحظ|ذاكر)|(?:افكاري|أفكاري|ذكرياتي)/i.test(text);
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

async function interpretWithAi(env, waId, text, access) {
  if (!env.OPENROUTER_API_KEY || !env.H_MODEL) return null;

  const now = new Date().toISOString();
  const timeZone = env.DEFAULT_TIME_ZONE || "Asia/Riyadh";
  const history = await recentConversation(env, waId, DEFAULT_HISTORY_LIMIT);
  const system = [
    "You are H, a private personal assistant controlled through WhatsApp.",
    "Default to concise natural Arabic unless the user clearly uses another language.",
    `Current UTC time: ${now}. User timezone: ${timeZone}. User role: ${access.role}.`,
    "Return ONLY one JSON object. Never return chain-of-thought or markdown.",
    "Allowed actions:",
    '{"action":"reply","reply":"response"}',
    '{"action":"schedule_self","body":"reminder text","dueAtIso":"absolute ISO-8601 with offset","reply":"confirmation"}',
    '{"action":"save_memory","body":"durable idea/note in the user wording","category":"idea|note|preference|project","reply":"confirmation"}',
    '{"action":"list_memories"}',
    '{"action":"list_reminders"}',
    '{"action":"save_contact","name":"contact name","phone":"international digits","reply":"confirmation"}',
    '{"action":"send_contact","contactName":"saved contact name","body":"message","reply":"confirmation"}',
    '{"action":"schedule_contact","contactName":"saved contact name","body":"message","dueAtIso":"absolute ISO-8601 with offset","reply":"confirmation"}',
    "Rules:",
    "- Use save_memory when the user explicitly asks H to remember an idea or durable note without a specific reminder time.",
    "- Use schedule_self only for a future reminder to this user.",
    "- Use send_contact/schedule_contact only when the user clearly asks to message another person.",
    "- If date/time or recipient is ambiguous, use action=reply and ask one short clarification question.",
    "- Do not claim a message, reminder, or save succeeded; the runtime will confirm after execution.",
    "- Do not invent facts, prices, contacts, dates, or tool results.",
    access.canSendExternal
      ? "- This user may request outbound messages to saved contacts."
      : "- This user is not allowed to send messages to third-party WhatsApp numbers; never propose bypassing that restriction.",
    String(env.H_SYSTEM_PROMPT || "").trim(),
  ].filter(Boolean).join("\n");

  try {
    const messages = [{ role: "system", content: system }];
    for (const item of history) {
      messages.push({ role: item.role === "assistant" ? "assistant" : "user", content: item.body });
    }
    if (!history.length || history[history.length - 1]?.body !== text) {
      messages.push({ role: "user", content: text });
    }

    const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.OPENROUTER_API_KEY}`,
        "Content-Type": "application/json",
        "HTTP-Referer": env.PUBLIC_BASE_URL || "https://example.invalid",
        "X-Title": "H WhatsApp Cloud Runtime",
      },
      body: JSON.stringify({
        model: env.H_MODEL,
        temperature: 0.15,
        messages,
      }),
    });
    if (!response.ok) return null;
    const data = await response.json();
    const content = data?.choices?.[0]?.message?.content || "";
    return parseJsonObject(content);
  } catch (error) {
    console.error("H model call failed", error);
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

async function createScheduledReminder(env, ownerWaId, targetWaId, body, dueAtMs) {
  const now = Date.now();
  const id = crypto.randomUUID();
  await env.DB.prepare(
    `INSERT INTO scheduled_jobs(
      id, owner_wa_id, target_wa_id, body, due_at, status, attempts, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)`,
  ).bind(id, ownerWaId, targetWaId, body, dueAtMs, now, now).run();
}

function formatConfirmation(body, dueAtMs) {
  const formatted = new Intl.DateTimeFormat("ar-SA", {
    timeZone: "Asia/Riyadh",
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(dueAtMs));
  return `تم. سأذكرك: ${body}\nالموعد: ${formatted}`;
}

async function formatReminderList(env, ownerWaId) {
  const result = await env.DB.prepare(
    `SELECT body, due_at, status FROM scheduled_jobs
     WHERE owner_wa_id=? AND status IN ('PENDING','WAITING_TEMPLATE')
     ORDER BY due_at ASC LIMIT 10`,
  ).bind(ownerWaId).all();
  const rows = result?.results || [];
  if (!rows.length) return "ما عندك تذكيرات معلقة حاليًا.";
  return [
    "تذكيراتك الحالية:",
    ...rows.map((row, index) => `${index + 1}. ${row.body} — ${new Date(Number(row.due_at)).toISOString()}`),
  ].join("\n");
}

async function saveMemory(env, ownerWaId, body, category) {
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO memory_items(id, owner_wa_id, category, body, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
  ).bind(crypto.randomUUID(), ownerWaId, category.slice(0, 40), body.slice(0, 4000), now, now).run();
}

async function formatMemoryList(env, ownerWaId) {
  const result = await env.DB.prepare(
    `SELECT category, body, created_at FROM memory_items
     WHERE owner_wa_id=? ORDER BY created_at DESC LIMIT 15`,
  ).bind(ownerWaId).all();
  const rows = result?.results || [];
  if (!rows.length) return "ما عندي أفكار أو ملاحظات محفوظة لك حتى الآن.";
  return [
    "المحفوظ عندي لك:",
    ...rows.map((row, index) => `${index + 1}. [${row.category}] ${row.body}`),
  ].join("\n");
}

async function appendConversationMessage(env, ownerWaId, role, body) {
  const text = String(body || "").trim();
  if (!text) return;
  await env.DB.prepare(
    `INSERT INTO conversation_messages(id, owner_wa_id, role, body, created_at)
     VALUES (?, ?, ?, ?, ?)`,
  ).bind(crypto.randomUUID(), ownerWaId, role, text.slice(0, 8000), Date.now()).run();
}

async function recentConversation(env, ownerWaId, limit) {
  const safeLimit = Math.max(1, Math.min(Number(limit) || DEFAULT_HISTORY_LIMIT, 30));
  const result = await env.DB.prepare(
    `SELECT role, body FROM conversation_messages
     WHERE owner_wa_id=? ORDER BY created_at DESC LIMIT ?`,
  ).bind(ownerWaId, safeLimit).all();
  return (result?.results || []).reverse();
}

async function saveNamedContact(env, ownerWaId, name, targetWaId) {
  const now = Date.now();
  const key = normalizeContactKey(name);
  await env.DB.prepare(
    `INSERT INTO named_contacts(owner_wa_id, name_key, display_name, target_wa_id, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?)
     ON CONFLICT(owner_wa_id, name_key) DO UPDATE SET
       display_name=excluded.display_name,
       target_wa_id=excluded.target_wa_id,
       updated_at=excluded.updated_at`,
  ).bind(ownerWaId, key, name.trim(), targetWaId, now, now).run();
}

async function resolveNamedContact(env, ownerWaId, name) {
  const key = normalizeContactKey(name);
  return env.DB.prepare(
    `SELECT display_name, target_wa_id FROM named_contacts
     WHERE owner_wa_id=? AND name_key=? LIMIT 1`,
  ).bind(ownerWaId, key).first();
}

function normalizeContactKey(value) {
  return String(value || "")
    .trim()
    .toLocaleLowerCase("ar")
    .replace(/[\u064B-\u065F\u0670]/g, "")
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

async function processDueJobs(env) {
  const now = Date.now();
  const due = await env.DB.prepare(
    `SELECT id, owner_wa_id, target_wa_id, body, due_at, attempts
     FROM scheduled_jobs
     WHERE status IN ('PENDING','WAITING_TEMPLATE') AND due_at <= ?
     ORDER BY due_at ASC
     LIMIT 50`,
  ).bind(now).all();

  for (const job of due?.results || []) {
    try {
      const outcome = await sendOutboundWithPolicy(env, job.target_wa_id, `تذكير من H: ${job.body}`);
      if (!outcome.sent) {
        await env.DB.prepare(
          `UPDATE scheduled_jobs
           SET status='WAITING_TEMPLATE', attempts=attempts+1,
               last_error=?, updated_at=? WHERE id=?`,
        ).bind(outcome.reason.slice(0, 500), now, job.id).run();
        continue;
      }

      await env.DB.prepare(
        "UPDATE scheduled_jobs SET status='SENT', attempts=attempts+1, last_error=NULL, updated_at=? WHERE id=?",
      ).bind(now, job.id).run();
    } catch (error) {
      await env.DB.prepare(
        `UPDATE scheduled_jobs SET attempts=attempts+1, last_error=?, updated_at=? WHERE id=?`,
      ).bind(String(error?.message || error).slice(0, 500), now, job.id).run();
    }
  }
}

async function sendOutboundWithPolicy(env, to, body) {
  const normalized = normalizeWaId(to);
  const contact = await env.DB.prepare(
    "SELECT last_inbound_at FROM contacts WHERE wa_id=? LIMIT 1",
  ).bind(normalized).first();
  const lastInboundAt = Number(contact?.last_inbound_at || 0);
  const insideServiceWindow = lastInboundAt > 0 && Date.now() - lastInboundAt < DAY_MS;

  if (insideServiceWindow) {
    await sendText(env, normalized, body);
    return { sent: true, mode: "text" };
  }

  if (env.WHATSAPP_REMINDER_TEMPLATE_NAME) {
    await sendReminderTemplate(env, normalized, body);
    return { sent: true, mode: "template" };
  }

  return {
    sent: false,
    reason:
      "لا أقدر أرسل رسالة حرة لهذا الرقم خارج نافذة واتساب المسموحة. يلزم قالب WhatsApp معتمد للإرسال الاستباقي، لذلك لم أرسل شيئًا.",
  };
}

async function sendAssistantText(env, to, body) {
  const text = String(body || "").slice(0, DEFAULT_MAX_MESSAGE_LENGTH);
  await sendText(env, to, text);
  await appendConversationMessage(env, normalizeWaId(to), "assistant", text);
}

async function sendText(env, to, body) {
  return sendWhatsApp(env, {
    messaging_product: "whatsapp",
    recipient_type: "individual",
    to: normalizeWaId(to),
    type: "text",
    text: { preview_url: false, body: String(body).slice(0, DEFAULT_MAX_MESSAGE_LENGTH) },
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
          parameters: [{ type: "text", text: String(body).slice(0, 1024) }],
        },
      ],
    },
  });
}

async function sendWhatsApp(env, payload) {
  const version = requireMetaGraphVersion(env);
  if (!env.WHATSAPP_PHONE_NUMBER_ID || !env.WHATSAPP_ACCESS_TOKEN) {
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

function requireMetaGraphVersion(env) {
  const version = String(env.META_GRAPH_VERSION || "").trim();
  if (!version) throw new Error("META_GRAPH_VERSION is missing");
  return version;
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
