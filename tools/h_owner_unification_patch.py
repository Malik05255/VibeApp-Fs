from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))


# Cloudflare ingress: all configured text goes to H; capability is carried over the authenticated bridge.
replace_once(
    "cloud/whatsapp-worker/src/router.js",
    '''    if (access.role === "owner" && text && looksLikeOwnerExternalMessagingIntent(text)) {\n      return { kind: "delegate", message, from };\n    }\n    if (text && unifiedTextBridgeConfigured(env)) {\n      return { kind: "unified", message, from, text, sourceType: type };\n    }''',
    '''    if (text && unifiedTextBridgeConfigured(env)) {\n      return {\n        kind: "unified",\n        message,\n        from,\n        text,\n        sourceType: type,\n        senderRole: access.role,\n        canSendExternal: access.canSendExternal,\n      };\n    }''',
)
replace_once(
    "cloud/whatsapp-worker/src/router.js",
    '''  if (owners.includes(waId)) return { allowed: true, role: "owner" };\n\n  const friends = parseWaIdList(env.H_ALLOWED_WA_IDS);\n  if (friends.includes(waId)) return { allowed: true, role: "friend" };\n\n  if (env.ALLOW_UNKNOWN_USERS === "true") return { allowed: true, role: "friend" };\n  return { allowed: false, role: "blocked" };''',
    '''  if (owners.includes(waId)) return { allowed: true, role: "owner", canSendExternal: true };\n\n  const friends = parseWaIdList(env.H_ALLOWED_WA_IDS);\n  if (friends.includes(waId)) return { allowed: true, role: "friend", canSendExternal: false };\n\n  if (env.ALLOW_UNKNOWN_USERS === "true") return { allowed: true, role: "friend", canSendExternal: false };\n  return { allowed: false, role: "blocked", canSendExternal: false };''',
)
replace_once(
    "cloud/whatsapp-worker/src/router.js",
    '''      transcript: String(item.text || "").slice(0, 12000),\n      received_at: Number.isFinite(receivedAtMs) && receivedAtMs > 0''',
    '''      transcript: String(item.text || "").slice(0, 12000),\n      sender_role: item.senderRole === "owner" ? "owner" : "friend",\n      can_send_external: item.canSendExternal === true,\n      received_at: Number.isFinite(receivedAtMs) && receivedAtMs > 0''',
)
replace_once(
    "cloud/whatsapp-worker/src/router.js",
    '''      ownerExternalMessagingRuntime: "legacy_guarded",''',
    '''      ownerExternalMessagingRuntime: unifiedTextBridgeConfigured(env) ? "supabase_h_unified" : "legacy_guarded_fallback",''',
)

# Voice bridge from the legacy media/transcription worker also carries the trusted capability claim.
replace_once(
    "cloud/whatsapp-worker/src/index.js",
    '''            const bridged = await bridgeVoiceTranscript(env, from, message.id, inbound.text, message.timestamp);''',
    '''            const bridged = await bridgeVoiceTranscript(env, from, message.id, inbound.text, message.timestamp, access);''',
)
replace_once(
    "cloud/whatsapp-worker/src/index.js",
    '''async function bridgeVoiceTranscript(env, waId, messageId, transcript, timestamp) {''',
    '''async function bridgeVoiceTranscript(env, waId, messageId, transcript, timestamp, access = {}) {''',
)
replace_once(
    "cloud/whatsapp-worker/src/index.js",
    '''      transcript: String(transcript || "").slice(0, 12000),\n      received_at: Number.isFinite(receivedAtMs) && receivedAtMs > 0''',
    '''      transcript: String(transcript || "").slice(0, 12000),\n      sender_role: access?.role === "owner" ? "owner" : "friend",\n      can_send_external: access?.canSendExternal === true,\n      received_at: Number.isFinite(receivedAtMs) && receivedAtMs > 0''',
)

# Supabase bridge parser: default-deny and only accept external capability for an owner claim.
replace_once(
    "cloud/supabase/h-whatsapp-inbox/voice-bridge.ts",
    '''export type VoiceDeliveryContext =\n  | { channel: "peach" }\n  | { channel: "meta"; targetWaId: string };''',
    '''export type VoiceDeliveryContext =\n  | { channel: "peach" }\n  | {\n      channel: "meta";\n      targetWaId: string;\n      senderRole: "owner" | "friend";\n      canSendExternal: boolean;\n    };''',
)
replace_once(
    "cloud/supabase/h-whatsapp-inbox/voice-bridge.ts",
    '''  receivedAt: string | null;\n};''',
    '''  receivedAt: string | null;\n  senderRole: "owner" | "friend";\n  canSendExternal: boolean;\n};''',
)
replace_once(
    "cloud/supabase/h-whatsapp-inbox/voice-bridge.ts",
    '''  let receivedAt: string | null = null;''',
    '''  const senderRole: "owner" | "friend" = value.sender_role === "owner" ? "owner" : "friend";\n  const canSendExternal = senderRole === "owner" && value.can_send_external === true;\n\n  let receivedAt: string | null = null;''',
)
replace_once(
    "cloud/supabase/h-whatsapp-inbox/voice-bridge.ts",
    '''  return { waId, messageId, transcript, receivedAt };''',
    '''  return { waId, messageId, transcript, receivedAt, senderRole, canSendExternal };''',
)
replace_once(
    "cloud/supabase/h-whatsapp-inbox/voice-bridge.ts",
    '''    base.delivery_channel = "meta";\n    base.target_wa_id = delivery.targetWaId.replace(/\\D/g, "");''',
    '''    base.delivery_channel = "meta";\n    base.target_wa_id = delivery.targetWaId.replace(/\\D/g, "");\n    base.sender_role = delivery.senderRole;\n    base.can_send_external = delivery.canSendExternal;''',
)

# H inbox imports and delivery context.
replace_once(
    "cloud/supabase/h-whatsapp-inbox/index.ts",
    '''import {\n  completeTask,''',
    '''import {\n  canUseExternalMessaging,\n  looksLikeContactSaveIntent,\n  normalizeWaIdCandidate,\n  parseDeterministicContactSave,\n  resolveRuntimeContact,\n  saveRuntimeContact,\n} from "./contact-manager.ts";\nimport {\n  completeTask,''',
)
replace_once(
    "cloud/supabase/h-whatsapp-inbox/index.ts",
    '''  const delivery: VoiceDeliveryContext = { channel: "meta", targetWaId: input.waId };''',
    '''  const delivery: VoiceDeliveryContext = {\n    channel: "meta",\n    targetWaId: input.waId,\n    senderRole: input.senderRole,\n    canSendExternal: input.canSendExternal,\n  };''',
)

# Deterministic contact save must run before generic memory save.
replace_once(
    "cloud/supabase/h-whatsapp-inbox/index.ts",
    '''  const memory = parseMemorySave(text);''',
    '''  const deterministicContact = parseDeterministicContactSave(text);\n  if (deterministicContact) {\n    if (!canUseExternalMessaging(delivery)) {\n      return { reply: "حفظ أرقام للإرسال الخارجي متاح لصاحب H فقط." };\n    }\n    const saved = await saveRuntimeContact(db, userKey, deterministicContact.name, deterministicContact.targetWaId);\n    return { reply: `تم حفظ ${saved.display_name}.` };\n  }\n\n  const memory = parseMemorySave(text);''',
)
replace_once(
    "cloud/supabase/h-whatsapp-inbox/index.ts",
    '''  const ai = await interpretWithAi(db, userKey, text, now);''',
    '''  const ai = await interpretWithAi(db, userKey, text, now, delivery);''',
)

# Execute owner-only external actions in the same cloud runtime.
replace_once(
    "cloud/supabase/h-whatsapp-inbox/index.ts",
    '''  if (action === "list_reminders") return { reply: await formatReminderList(db, userKey) };''',
    '''  if (action === "save_contact" && decision?.name && decision?.phone) {\n    if (!canUseExternalMessaging(delivery)) return { reply: "حفظ أرقام للإرسال الخارجي متاح لصاحب H فقط." };\n    const targetWaId = normalizeWaIdCandidate(decision.phone);\n    if (!targetWaId) return { reply: "رقم الجوال غير واضح. أرسله مع رمز الدولة." };\n    const saved = await saveRuntimeContact(db, userKey, String(decision.name), targetWaId);\n    return { reply: String(decision.reply || `تم حفظ ${saved.display_name}.`) };\n  }\n  if ((action === "send_contact" || action === "schedule_contact") && decision?.contactName && decision?.body) {\n    if (!canUseExternalMessaging(delivery)) return { reply: "الإرسال إلى أرقام واتساب أخرى متاح لصاحب H فقط." };\n    const contact = await resolveRuntimeContact(db, userKey, String(decision.contactName));\n    if (!contact) {\n      return { reply: `ما عندي رقم ${String(decision.contactName)} محفوظ. احفظه أولًا بقولك مثلًا: «احفظ رقم محمد 9665…».` };\n    }\n    const body = String(decision.body).trim().slice(0, 4096);\n    if (!body) return { reply: "نص الرسالة غير واضح." };\n\n    if (action === "schedule_contact") {\n      const dueAt = new Date(String(decision.dueAtIso || ""));\n      if (Number.isNaN(dueAt.getTime()) || dueAt.getTime() <= Date.now()) {\n        return { reply: "موعد الإرسال غير واضح. حدده بشكل أوضح." };\n      }\n      const task = await createTask(db, userKey, conversationId, body, {\n        taskType: "external_message",\n        dueAt,\n        explicitPriority: detectExplicitPriority(originalText),\n        metadata: {\n          ...deliveryMetadata(delivery, "whatsapp_external_message", originalText),\n          delivery_channel: "meta",\n          delivery_purpose: "external_message",\n          target_wa_id: contact.target_wa_id,\n          contact_name: contact.display_name,\n        },\n      });\n      const { error } = await db.from("h_runtime_reminders").insert({\n        user_key: userKey,\n        conversation_id: conversationId,\n        body,\n        due_at: dueAt.toISOString(),\n        status: "pending",\n        task_id: task.id,\n      });\n      if (error) throw error;\n      return { reply: String(decision.reply || `تم جدولة الرسالة إلى ${contact.display_name} ${formatRiyadhDate(dueAt)}.`) };\n    }\n\n    try {\n      await sendMetaReminder(contact.target_wa_id, body);\n      return { reply: String(decision.reply || `تم إرسال الرسالة إلى ${contact.display_name}.`) };\n    } catch (error) {\n      const detail = errorMessage(error);\n      if (/24.?hour|window|template/i.test(detail)) {\n        return { reply: "ما قدرت أرسل الرسالة لأن نافذة واتساب المجانية مغلقة لهذا الرقم. لم أستخدم قالبًا مدفوعًا تلقائيًا." };\n      }\n      return { reply: "تعذر إرسال الرسالة الآن، ولم أكرر الإرسال لتجنب التكرار." };\n    }\n  }\n  if (action === "list_reminders") return { reply: await formatReminderList(db, userKey) };''',
)

# AI policy exposes external actions only for a trusted owner delivery context.
replace_once(
    "cloud/supabase/h-whatsapp-inbox/index.ts",
    '''async function interpretWithAi(db: any, userKey: string, text: string, now: Date): Promise<any | null> {\n  const { data: historyRows }''',
    '''async function interpretWithAi(\n  db: any,\n  userKey: string,\n  text: string,\n  now: Date,\n  delivery: VoiceDeliveryContext = { channel: "peach" },\n): Promise<any | null> {\n  const canSendExternal = canUseExternalMessaging(delivery);\n  const { data: historyRows }''',
)
replace_once(
    "cloud/supabase/h-whatsapp-inbox/index.ts",
    '''    '{"action":"list_tasks"}',\n    "If time/date is ambiguous, ask one short clarification question using action=reply.",''',
    '''    '{"action":"list_tasks"}',\n    canSendExternal ? '{"action":"save_contact","name":"contact name","phone":"international digits","reply":"confirmation"}' : "",\n    canSendExternal ? '{"action":"send_contact","contactName":"saved contact name","body":"message","reply":"confirmation"}' : "",\n    canSendExternal ? '{"action":"schedule_contact","contactName":"saved contact name","body":"message","dueAtIso":"absolute ISO-8601 with offset","reply":"confirmation"}' : "",\n    canSendExternal\n      ? "This authenticated Meta sender may save contacts and send/schedule messages to saved contacts."\n      : "This sender may not save contacts for external delivery or message third-party WhatsApp numbers.",\n    "If time/date is ambiguous, ask one short clarification question using action=reply.",''',
)
replace_once(
    "cloud/supabase/h-whatsapp-inbox/index.ts",
    '''  ].join("\\n");''',
    '''  ].filter(Boolean).join("\\n");''',
)

# Scheduled external messages reuse the existing scheduler but must not be prefixed as a reminder.
replace_once(
    "cloud/supabase/h-whatsapp-inbox/index.ts",
    '''      const text = `تذكير من H: ${String(reminder.body)}`;\n      const taskMetadata = reminder.task_id != null\n        ? (await db.from("h_runtime_tasks").select("metadata").eq("id", reminder.task_id).maybeSingle()).data?.metadata\n        : null;''',
    '''      const taskMetadata = reminder.task_id != null\n        ? (await db.from("h_runtime_tasks").select("metadata").eq("id", reminder.task_id).maybeSingle()).data?.metadata\n        : null;\n      const externalMessage = taskMetadata?.delivery_purpose === "external_message";\n      const text = externalMessage ? String(reminder.body) : `تذكير من H: ${String(reminder.body)}`;''',
)
replace_once(
    "cloud/supabase/h-whatsapp-inbox/index.ts",
    '''function parseMemorySave(text: string) {\n  if (/ذكرني|ذكّرني/i.test(text)) return null;''',
    '''function parseMemorySave(text: string) {\n  if (/ذكرني|ذكّرني/i.test(text) || looksLikeContactSaveIntent(text)) return null;''',
)

# CI covers the new policy module.
replace_once(
    ".github/workflows/h-cloud-runtime-ci.yml",
    '''      - name: Test H WhatsApp voice bridge policy\n        run: deno test cloud/supabase/h-whatsapp-inbox/voice-bridge_test.ts\n''',
    '''      - name: Test H WhatsApp voice bridge policy\n        run: deno test cloud/supabase/h-whatsapp-inbox/voice-bridge_test.ts\n\n      - name: Test H owner contact and external messaging policy\n        run: deno test cloud/supabase/h-whatsapp-inbox/contact-manager_test.ts\n''',
)

# Router regression expectations: owner external text is unified when H bridge exists.
replace_once(
    "cloud/whatsapp-worker/src/router_test.mjs",
    '''test("owner external contact commands stay on guarded legacy execution path", () => {\n  const samples = [\n    "احفظ محمد 966551234567",\n    "احفظ رقم محمد 966551234567",\n    "أرسل رسالة إلى محمد",\n    "ارسل لمحمد الموعد تغير",\n  ];\n  for (const text of samples) {\n    assert.equal(looksLikeOwnerExternalMessagingIntent(text), true, text);\n    const decision = routeDecisionForMessage({\n      id: `wamid.owner-${samples.indexOf(text)}`,\n      from: "966500000001",\n      type: "text",\n      text: { body: text },\n    }, baseEnv);\n    assert.equal(decision.kind, "delegate", text);\n  }\n});''',
    '''test("owner external contact commands use unified H with trusted capability", () => {\n  const samples = [\n    "احفظ محمد 966551234567",\n    "احفظ رقم محمد 966551234567",\n    "أرسل رسالة إلى محمد",\n    "ارسل لمحمد الموعد تغير",\n  ];\n  for (const text of samples) {\n    assert.equal(looksLikeOwnerExternalMessagingIntent(text), true, text);\n    const decision = routeDecisionForMessage({\n      id: `wamid.owner-${samples.indexOf(text)}`,\n      from: "966500000001",\n      type: "text",\n      text: { body: text },\n    }, baseEnv);\n    assert.equal(decision.kind, "unified", text);\n    assert.equal(decision.senderRole, "owner", text);\n    assert.equal(decision.canSendExternal, true, text);\n  }\n});''',
)
replace_once(
    "cloud/whatsapp-worker/src/router_test.mjs",
    '''  assert.equal(decision.kind, "unified");\n});\n\ntest("authorized audio remains delegated''',
    '''  assert.equal(decision.kind, "unified");\n  assert.equal(decision.senderRole, "friend");\n  assert.equal(decision.canSendExternal, false);\n});\n\ntest("authorized audio remains delegated''',
)

# Voice parser regression tests for capability claims.
with Path("cloud/supabase/h-whatsapp-inbox/voice-bridge_test.ts").open("a") as f:
    f.write('''\n\nDeno.test("voice bridge accepts trusted owner external capability", () => {\n  const value = parseVoiceTranscriptPayload({\n    mode: "voice_transcript",\n    wa_id: "966551234567",\n    message_id: "wamid.owner-capability",\n    transcript: "أرسل لمحمد وصلت",\n    sender_role: "owner",\n    can_send_external: true,\n  });\n  if (!value || value.senderRole !== "owner" || !value.canSendExternal) {\n    throw new Error("owner capability was not preserved");\n  }\n});\n\nDeno.test("voice bridge does not allow friend to forge external capability", () => {\n  const value = parseVoiceTranscriptPayload({\n    mode: "voice_transcript",\n    wa_id: "966551234567",\n    message_id: "wamid.friend-capability",\n    transcript: "أرسل لمحمد وصلت",\n    sender_role: "friend",\n    can_send_external: true,\n  });\n  if (!value || value.senderRole !== "friend" || value.canSendExternal) {\n    throw new Error("friend external capability was accepted");\n  }\n});\n\nDeno.test("legacy voice payload defaults to no external capability", () => {\n  const value = parseVoiceTranscriptPayload({\n    mode: "voice_transcript",\n    wa_id: "966551234567",\n    message_id: "wamid.legacy-safe",\n    transcript: "مرحبا",\n  });\n  if (!value || value.canSendExternal || value.senderRole !== "friend") {\n    throw new Error("legacy payload did not default to safe capability");\n  }\n});\n''')

print("H owner messaging unification patch applied")
