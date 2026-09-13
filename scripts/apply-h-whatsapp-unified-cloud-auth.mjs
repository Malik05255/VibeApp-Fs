import { readFileSync, writeFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf8");
}

function write(path, value) {
  writeFileSync(path, value, "utf8");
}

function replaceOnce(text, from, to, label) {
  const index = text.indexOf(from);
  if (index < 0) throw new Error(`Missing patch anchor: ${label}`);
  if (text.indexOf(from, index + from.length) >= 0) throw new Error(`Ambiguous patch anchor: ${label}`);
  return text.slice(0, index) + to + text.slice(index + from.length);
}

function replaceRange(text, functionName, startMarker, endMarker, replacement) {
  const functionIndex = text.indexOf(functionName);
  if (functionIndex < 0) throw new Error(`Missing function: ${functionName}`);
  const start = text.indexOf(startMarker, functionIndex);
  if (start < 0) throw new Error(`Missing start marker in ${functionName}`);
  const end = text.indexOf(endMarker, start);
  if (end < 0) throw new Error(`Missing end marker in ${functionName}`);
  return text.slice(0, start) + replacement + text.slice(end);
}

// 1) Make the H identity store the reusable authority for every WhatsApp transport.
{
  const path = "cloud/supabase/h-whatsapp-inbox/owner-identity.ts";
  let text = read(path);
  text = replaceOnce(text,
`export type HPeachDeliveryContext = {
  channel: "peach";
  allowed: boolean;
  senderRole: "owner" | "friend";
  canSendExternal: boolean;
};`,
`export type HWhatsAppAccessContext = {
  allowed: boolean;
  senderRole: "owner" | "friend";
  canSendExternal: boolean;
};

export type HPeachDeliveryContext = HWhatsAppAccessContext & {
  channel: "peach";
};`,
"owner identity context type");

  text = replaceOnce(text,
`export async function resolvePeachDeliveryContext(
  db: DbClient,
  waId: unknown,
): Promise<HPeachDeliveryContext> {
  const secret = await loadIdentitySecret(db);
  const owner = await hasActiveFingerprint(
    db,
    "h_runtime_owner_identities",
    await ownerFingerprint(waId, secret),
  );
  if (owner) {
    return {
      channel: "peach",
      allowed: true,
      senderRole: "owner",
      canSendExternal: true,
    };
  }

  const friend = await hasActiveFingerprint(
    db,
    "h_runtime_friend_identities",
    await friendFingerprint(waId, secret),
  );
  return {
    channel: "peach",
    allowed: friend,
    senderRole: "friend",
    canSendExternal: false,
  };
}`,
`export async function resolveWhatsAppAccessContext(
  db: DbClient,
  waId: unknown,
): Promise<HWhatsAppAccessContext> {
  const secret = await loadIdentitySecret(db);
  const owner = await hasActiveFingerprint(
    db,
    "h_runtime_owner_identities",
    await ownerFingerprint(waId, secret),
  );
  if (owner) {
    return {
      allowed: true,
      senderRole: "owner",
      canSendExternal: true,
    };
  }

  const friend = await hasActiveFingerprint(
    db,
    "h_runtime_friend_identities",
    await friendFingerprint(waId, secret),
  );
  return {
    allowed: friend,
    senderRole: "friend",
    canSendExternal: false,
  };
}

export async function resolvePeachDeliveryContext(
  db: DbClient,
  waId: unknown,
): Promise<HPeachDeliveryContext> {
  return {
    channel: "peach",
    ...await resolveWhatsAppAccessContext(db, waId),
  };
}`,
"shared WhatsApp access resolver");
  write(path, text);
}

// 2) Parsing transport payloads must never grant owner/external capability.
{
  const path = "cloud/supabase/h-whatsapp-inbox/channel-message.ts";
  let text = read(path);
  text = replaceOnce(text,
`  receivedAt: string | null;
  senderRole: "owner" | "friend";
  canSendExternal: boolean;
};`,
`  receivedAt: string | null;
};`,
"channel input capability fields");
  text = replaceOnce(text,
`  const senderRole: "owner" | "friend" = value.sender_role === "owner" ? "owner" : "friend";
  const canSendExternal = senderRole === "owner" && value.can_send_external === true;

`,
``,
"channel capability parsing");
  text = replaceOnce(text,
`    sourceType,
    receivedAt,
    senderRole,
    canSendExternal,
  };`,
`    sourceType,
    receivedAt,
  };`,
"channel parser return");
  write(path, text);
}

{
  const path = "cloud/supabase/h-whatsapp-inbox/voice-bridge.ts";
  let text = read(path);
  text = replaceOnce(text,
`  receivedAt: string | null;
  senderRole: "owner" | "friend";
  canSendExternal: boolean;
};`,
`  receivedAt: string | null;
};`,
"voice input capability fields");
  text = replaceOnce(text,
`  const senderRole: "owner" | "friend" = value.sender_role === "owner" ? "owner" : "friend";
  const canSendExternal = senderRole === "owner" && value.can_send_external === true;

`,
``,
"voice capability parsing");
  text = replaceOnce(text,
`  return { waId, messageId, transcript, receivedAt, senderRole, canSendExternal };`,
`  return { waId, messageId, transcript, receivedAt };`,
"voice parser return");
  write(path, text);
}

// 3) The media adapter supplies content only. H Cloud supplies identity/capability.
{
  const path = "cloud/supabase/h-whatsapp-media/index.ts";
  let text = read(path);
  text = replaceOnce(text,
`        source_type: input.kind,
        sender_role: "friend",
        can_send_external: false,
        received_at: input.receivedAt || new Date().toISOString(),`,
`        source_type: input.kind,
        received_at: input.receivedAt || new Date().toISOString(),`,
"media bridge capability payload");
  write(path, text);
}

// 4) Enforce H-owned authorization in the unified inbox, including pairing bootstrap.
{
  const path = "cloud/supabase/h-whatsapp-inbox/index.ts";
  let text = read(path);
  text = replaceOnce(text,
`import { resolvePeachDeliveryContext } from "./owner-identity.ts";`,
`import { resolvePeachDeliveryContext, resolveWhatsAppAccessContext } from "./owner-identity.ts";`,
"inbox identity imports");

  text = replaceOnce(text,
`  const now = new Date();
  const friendPairingEnvelope = await redactFriendPairingForStorage(input.text, runtimeSecret);
  const friendAccessEnvelope = friendPairingEnvelope ? null : await redactFriendAccessForStorage(db, input.text);
  const row = {`,
`  const now = new Date();
  const ownerPairingEnvelope = await redactOwnerPairingForStorage(input.text, runtimeSecret);
  const friendPairingEnvelope = ownerPairingEnvelope
    ? null
    : await redactFriendPairingForStorage(input.text, runtimeSecret);
  const friendAccessEnvelope = ownerPairingEnvelope || friendPairingEnvelope
    ? null
    : await redactFriendAccessForStorage(db, input.text);
  const accessAtIngress = ownerPairingEnvelope || friendPairingEnvelope
    ? null
    : await resolveWhatsAppAccessContext(db, input.waId);
  const blockedAtIngress = !ownerPairingEnvelope && !friendPairingEnvelope && accessAtIngress?.allowed !== true;
  const row = {`,
"channel ingress authorization");

  text = replaceOnce(text,
`    body: friendPairingEnvelope?.body ?? friendAccessEnvelope?.body ?? input.text,`,
`    body: blockedAtIngress
      ? BLOCKED_PEACH_BODY
      : ownerPairingEnvelope?.body ?? friendPairingEnvelope?.body ?? friendAccessEnvelope?.body ?? input.text,`,
"channel redacted body");

  text = replaceOnce(text,
`    raw: friendPairingEnvelope?.raw ?? friendAccessEnvelope?.raw ?? {
      source: "meta_channel_bridge",
      message_id: input.messageId,
      wa_id: input.waId,
      source_type: input.sourceType,
      text_length: input.text.length,
      sender_role: input.senderRole,
      can_send_external: input.canSendExternal,
    },`,
`    raw: blockedAtIngress
      ? { source: "meta_channel_blocked", redacted: true, source_type: input.sourceType }
      : ownerPairingEnvelope?.raw ?? friendPairingEnvelope?.raw ?? friendAccessEnvelope?.raw ?? {
          source: "meta_channel_bridge",
          message_id: input.messageId,
          wa_id: input.waId,
          source_type: input.sourceType,
          text_length: input.text.length,
          authorization_source: "h_cloud_identity_store",
        },`,
"channel trusted raw metadata");

  const channelReplacement = `  const userKey = normalizeUserKey(input.waId, conversationId);
  try {
    const storedOwnerPairing = storedOwnerPairingFingerprint(row.raw);
    const ownerPairing = storedOwnerPairing
      ? await consumeOwnerPairingFingerprint(db, runtimeSecret, input.waId, storedOwnerPairing)
      : "not_pairing";
    const storedFriendPairing = ownerPairing === "not_pairing" ? storedFriendPairingFingerprint(row.raw) : null;
    const friendPairing = storedFriendPairing
      ? await consumeFriendPairingFingerprint(db, runtimeSecret, input.waId, storedFriendPairing)
      : "not_pairing";
    const access = ownerPairing === "not_pairing" && friendPairing === "not_pairing"
      ? accessAtIngress ?? await resolveWhatsAppAccessContext(db, input.waId)
      : null;
    const delivery: VoiceDeliveryContext = {
      channel: "meta",
      targetWaId: input.waId,
      senderRole: access?.senderRole ?? "friend",
      canSendExternal: access?.canSendExternal === true,
    };
    const parsedFriendAccess = friendPairing === "not_pairing" && access?.allowed === true
      ? parseFriendAccessCommand(input.text)
      : null;
    const sensitiveFriendInvite = parsedFriendAccess?.action === "create_invite";
    const storedFriendAccess = friendPairing === "not_pairing" && access?.allowed === true
      ? storedFriendAccessCommand(row.raw)
      : null;
    const friendAccessReply = friendPairing === "not_pairing" && access?.allowed === true
      ? storedFriendAccess
        ? await executeStoredFriendAccess(db, storedFriendAccess, delivery)
        : await maybeExecuteFriendAccessCommand(db, userKey, input.text, delivery)
      : null;
    if (ownerPairing === "not_pairing" && friendPairing === "not_pairing" && access?.allowed === true && !friendAccessReply) {
      await appendChat(db, userKey, conversationId, "user", input.text, messageKey);
    }
    const response = ownerPairing === "enrolled"
      ? { reply: "تم ربط هذا الرقم كمالك H. صلاحيات المالك مفعلة من رسالتك القادمة." }
      : ownerPairing === "invalid_or_expired"
        ? { reply: "رمز ربط المالك غير صالح أو انتهت صلاحيته. أنشئ رمز ربط جديد وحاول مرة أخرى." }
        : friendPairing === "enrolled"
          ? { reply: "تم ربط هذا الرقم كصديق في H. يمكنك استخدام H من رسالتك القادمة." }
          : friendPairing === "invalid_or_expired"
            ? { reply: "رمز ربط الصديق غير صالح أو انتهت صلاحيته. اطلب من مالك H إنشاء كود دعوة جديد." }
            : access?.allowed !== true
              ? { reply: "هذا الرقم غير مصرح له باستخدام H. اطلب من مالك H إضافتك أولاً." }
              : friendAccessReply
                ? { reply: friendAccessReply }
                : await decideResponse(db, userKey, conversationId, input.text, now, delivery);
    if (response.reply && ownerPairing === "not_pairing" && friendPairing === "not_pairing" && access?.allowed === true && !friendAccessReply) {
      await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
    }
    await db.from("h_runtime_inbox").update({
      status: "processed",
      error: null,
      reply_text: sensitiveFriendInvite ? null : response.reply || null,
      processed_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }).eq("message_key", messageKey);
    return { ok: true, duplicate: false, status: "processed", reply: response.reply || null };
`;

  text = replaceRange(
    text,
    "async function processChannelMessage",
    "  const userKey = normalizeUserKey(input.waId, conversationId);",
    "  } catch (error) {",
    channelReplacement,
  );

  text = replaceOnce(text,
`async function processVoiceTranscript(db: any, payload: unknown) {
  const input = parseVoiceTranscriptPayload(payload);
  if (!input) return { ok: false, error: "invalid_voice_transcript_payload" };

  const messageKey = ` + "`meta:${input.messageId}`" + `;`,
`async function processVoiceTranscript(db: any, payload: unknown) {
  const input = parseVoiceTranscriptPayload(payload);
  if (!input) return { ok: false, error: "invalid_voice_transcript_payload" };

  const access = await resolveWhatsAppAccessContext(db, input.waId);
  const messageKey = ` + "`meta:${input.messageId}`" + `;`,
"voice ingress authorization");

  text = replaceOnce(text,
`    body: input.transcript,
    source_created_at: input.receivedAt ?? now.toISOString(),
    raw: {
      source: "meta_voice_bridge",
      message_id: input.messageId,
      wa_id: input.waId,
      transcript_length: input.transcript.length,
    },`,
`    body: access.allowed ? input.transcript : BLOCKED_PEACH_BODY,
    source_created_at: input.receivedAt ?? now.toISOString(),
    raw: access.allowed
      ? {
          source: "meta_voice_bridge",
          message_id: input.messageId,
          wa_id: input.waId,
          transcript_length: input.transcript.length,
          authorization_source: "h_cloud_identity_store",
        }
      : { source: "meta_voice_blocked", redacted: true },`,
"voice blocked storage");

  const voiceReplacement = `  const userKey = normalizeUserKey(input.waId, conversationId);
  const delivery: VoiceDeliveryContext = {
    channel: "meta",
    targetWaId: input.waId,
    senderRole: access.senderRole,
    canSendExternal: access.canSendExternal,
  };
  try {
    let response: { reply?: string | null };
    if (!access.allowed) {
      response = { reply: "هذا الرقم غير مصرح له باستخدام H. اطلب من مالك H إضافتك أولاً." };
    } else {
      await appendChat(db, userKey, conversationId, "user", input.transcript, messageKey);
      response = await decideResponse(db, userKey, conversationId, input.transcript, now, delivery);
      if (response.reply) {
        await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
      }
    }
    await db.from("h_runtime_inbox").update({
      status: "processed",
      error: null,
      reply_text: response.reply || null,
      processed_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }).eq("message_key", messageKey);
    return { ok: true, duplicate: false, status: "processed", reply: response.reply || null };
`;

  text = replaceRange(
    text,
    "async function processVoiceTranscript",
    "  const userKey = normalizeUserKey(input.waId, conversationId);",
    "  } catch (error) {",
    voiceReplacement,
  );
  write(path, text);
}

// 5) Make the official Meta worker transport text/button/location into the same H Cloud brain.
{
  const path = "cloud/whatsapp-worker/src/index.js";
  let text = read(path);
  text = replaceOnce(text,
`        const access = resolveUserAccess(from, env);
        const inbound = await normalizeInboundMessage(message, env);`,
`        const inbound = await normalizeInboundMessage(message, env);`,
"worker local access authority");

  text = replaceOnce(text,
`        if (!access.allowed) {
          await sendAssistantText(
            env,
            from,
            "هذا الرقم مخصص لمستخدمي H المصرح لهم. إذا كنت تتوقع أن يكون لك وصول، اطلب من صاحب H إضافتك.",
          );
          continue;
        }

`,
``,
"worker pre-cloud access block");

  text = replaceOnce(text,
`            const bridged = await bridgeVoiceTranscript(env, from, message.id, inbound.text, message.timestamp, access);`,
`            const bridged = await bridgeVoiceTranscript(env, from, message.id, inbound.text, message.timestamp);`,
"voice bridge trusted capability removal");

  text = replaceOnce(text,
`        await handleUserInput(env, from, inbound.text, access);`,
`        if (!env.H_SUPABASE_VOICE_URL || !env.H_RUNTIME_SECRET) {
          await sendAssistantText(
            env,
            from,
            "H السحابي غير متصل حاليًا، لذلك لم أنفذ الطلب حتى لا أستخدم مساعدًا منفصلًا بذاكرة مختلفة.",
          );
          continue;
        }
        try {
          const bridged = await bridgeChannelMessage(
            env,
            from,
            message.id,
            inbound.text,
            message.type || "text",
            message.timestamp,
          );
          if (bridged?.duplicate) continue;
          if (bridged?.reply) {
            await sendAssistantText(env, from, bridged.reply);
          } else {
            await sendAssistantText(env, from, "H استقبل الرسالة، لكنه لم يُرجع ردًا قابلاً للإرسال.");
          }
        } catch (error) {
          console.error("Unified H channel bridge failed", error);
          await sendAssistantText(
            env,
            from,
            "تعذر الوصول إلى H السحابي الآن. لم أستخدم مسار ذكاء منفصل ولم أنفذ الطلب لتجنب اختلاف الذاكرة أو الصلاحيات.",
          );
        }`,
"worker unified text channel");

  text = replaceOnce(text,
`async function bridgeVoiceTranscript(env, waId, messageId, transcript, timestamp, access = {}) {`,
`async function bridgeChannelMessage(env, waId, messageId, textValue, sourceType, timestamp) {
  const endpoint = String(env.H_SUPABASE_VOICE_URL || "").trim();
  const secret = String(env.H_RUNTIME_SECRET || "").trim();
  if (!endpoint || !secret) throw new Error("Unified H channel bridge is not configured");

  const receivedAtMs = Number(timestamp) * 1000;
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-h-runtime-secret": secret,
    },
    body: JSON.stringify({
      mode: "channel_message",
      wa_id: normalizeWaId(waId),
      message_id: String(messageId || "").slice(0, 200),
      text: String(textValue || "").slice(0, 12000),
      source_type: String(sourceType || "text"),
      received_at: Number.isFinite(receivedAtMs) && receivedAtMs > 0
        ? new Date(receivedAtMs).toISOString()
        : new Date().toISOString(),
    }),
  });
  const responseText = await response.text();
  let data = {};
  try { data = responseText ? JSON.parse(responseText) : {}; } catch (_) {}
  if (!response.ok || data?.ok === false) {
    throw new Error(`H channel bridge rejected request (${response.status}): ${String(data?.error || responseText).slice(0, 300)}`);
  }
  return data;
}

async function bridgeVoiceTranscript(env, waId, messageId, transcript, timestamp) {`,
"generic channel bridge helper");

  text = replaceOnce(text,
`      transcript: String(transcript || "").slice(0, 12000),
      sender_role: access?.role === "owner" ? "owner" : "friend",
      can_send_external: access?.canSendExternal === true,
      received_at: Number.isFinite(receivedAtMs) && receivedAtMs > 0`,
`      transcript: String(transcript || "").slice(0, 12000),
      received_at: Number.isFinite(receivedAtMs) && receivedAtMs > 0`,
"voice bridge capability body");
  write(path, text);
}

// 6) Update parser/identity tests to guard the trust boundary.
{
  const path = "cloud/supabase/h-whatsapp-inbox/channel-message_test.ts";
  let text = read(path);
  text = replaceOnce(text,
`Deno.test("channel message parser keeps source semantics and trusted owner capability", () => {`,
`Deno.test("channel message parser keeps source semantics but never grants transport capability", () => {`,
"channel test name");
  text = replaceOnce(text,
`  assert(input?.sourceType === "text");
  assert(input?.senderRole === "owner");
  assert(input?.canSendExternal === true);
  assert(input?.receivedAt === "2026-09-08T21:00:00.000Z");`,
`  assert(input?.sourceType === "text");
  assert(!("senderRole" in (input ?? {})), "transport role must not enter H input");
  assert(!("canSendExternal" in (input ?? {})), "transport capability must not enter H input");
  assert(input?.receivedAt === "2026-09-08T21:00:00.000Z");`,
"channel capability assertion");
  text = replaceOnce(text,
`Deno.test("friend cannot forge external capability", () => {
  const input = parseChannelMessagePayload({
    mode: "channel_message",
    wa_id: "966551234567",
    message_id: "wamid.friend",
    text: "أرسل رسالة إلى محمد",
    source_type: "interactive",
    sender_role: "friend",
    can_send_external: true,
  });
  assert(input?.senderRole === "friend");
  assert(input?.canSendExternal === false);
});`,
`Deno.test("transport capability claims are ignored for every sender", () => {
  const input = parseChannelMessagePayload({
    mode: "channel_message",
    wa_id: "966551234567",
    message_id: "wamid.forged-owner",
    text: "أرسل رسالة إلى محمد",
    source_type: "interactive",
    sender_role: "owner",
    can_send_external: true,
  });
  assert(Boolean(input));
  assert(!("senderRole" in (input ?? {})));
  assert(!("canSendExternal" in (input ?? {})));
});`,
"channel forgery test");
  write(path, text);
}

{
  const path = "cloud/supabase/h-whatsapp-inbox/voice-bridge_test.ts";
  let text = read(path);
  text = replaceOnce(text,
`  if (value.transcript !== "ذكرني بعد ساعة أشرب ماء") throw new Error("transcript was not trimmed");
  if (value.canSendExternal) throw new Error("legacy payload unexpectedly gained external capability");`,
`  if (value.transcript !== "ذكرني بعد ساعة أشرب ماء") throw new Error("transcript was not trimmed");
  if ("senderRole" in value || "canSendExternal" in value) throw new Error("transport capability leaked into H input");`,
"voice base trust assertion");

  const capabilityTestsStart = text.indexOf(`Deno.test("voice bridge accepts trusted owner external capability"`);
  if (capabilityTestsStart < 0) throw new Error("Missing voice capability tests");
  const replacement = `Deno.test("voice bridge ignores forged owner/external capability", () => {
  const value = parseVoiceTranscriptPayload({
    mode: "voice_transcript",
    wa_id: "966551234567",
    message_id: "wamid.forged-owner",
    transcript: "أرسل لمحمد وصلت",
    sender_role: "owner",
    can_send_external: true,
  });
  if (!value) throw new Error("voice payload should remain valid");
  if ("senderRole" in value || "canSendExternal" in value) {
    throw new Error("transport capability was accepted");
  }
});
`;
  text = text.slice(0, capabilityTestsStart) + replacement;
  write(path, text);
}

{
  const path = "cloud/supabase/h-whatsapp-inbox/owner-identity_test.ts";
  let text = read(path);
  text = replaceOnce(text,
`  ownerFingerprint,
  resolvePeachDeliveryContext,`,
`  ownerFingerprint,
  resolvePeachDeliveryContext,
  resolveWhatsAppAccessContext,`,
"identity test import");
  text = replaceOnce(text,
`  const owner = await resolvePeachDeliveryContext(db, ownerWa);
  const friend = await resolvePeachDeliveryContext(db, friendWa);
  const stranger = await resolvePeachDeliveryContext(db, "966500000099");

  assert(owner.allowed === true && owner.senderRole === "owner" && owner.canSendExternal === true);
  assert(friend.allowed === true && friend.senderRole === "friend" && friend.canSendExternal === false);
  assert(stranger.allowed === false && stranger.senderRole === "friend" && stranger.canSendExternal === false);`,
`  const owner = await resolvePeachDeliveryContext(db, ownerWa);
  const friend = await resolvePeachDeliveryContext(db, friendWa);
  const stranger = await resolvePeachDeliveryContext(db, "966500000099");
  const metaOwner = await resolveWhatsAppAccessContext(db, ownerWa);
  const metaFriend = await resolveWhatsAppAccessContext(db, friendWa);
  const metaStranger = await resolveWhatsAppAccessContext(db, "966500000099");

  assert(owner.allowed === true && owner.senderRole === "owner" && owner.canSendExternal === true);
  assert(friend.allowed === true && friend.senderRole === "friend" && friend.canSendExternal === false);
  assert(stranger.allowed === false && stranger.senderRole === "friend" && stranger.canSendExternal === false);
  assert(metaOwner.allowed === true && metaOwner.senderRole === "owner" && metaOwner.canSendExternal === true);
  assert(metaFriend.allowed === true && metaFriend.senderRole === "friend" && metaFriend.canSendExternal === false);
  assert(metaStranger.allowed === false && metaStranger.senderRole === "friend" && metaStranger.canSendExternal === false);`,
"identity transport parity assertions");
  write(path, text);
}

console.log("Applied unified H WhatsApp cloud authorization patch.");
