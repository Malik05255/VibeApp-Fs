from pathlib import Path

path = Path("cloud/supabase/h-whatsapp-inbox/index.ts")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    '} from "./contact-manager.ts";\nimport {\n  completeTask,',
    '} from "./contact-manager.ts";\nimport { sendFreePeachContactMessage } from "./peach-contact-delivery.ts";\nimport {\n  completeTask,',
    "Peach adapter import",
)

replace_once(
    '          delivery_channel: "meta",\n          delivery_purpose: "external_message",',
    '          delivery_channel: "peach_contact",\n          delivery_purpose: "external_message",\n          delivery_policy: "free_window_only",',
    "scheduled external delivery metadata",
)

replace_once(
    '''    try {\n      await sendMetaReminder(contact.target_wa_id, body);\n      return { reply: String(decision.reply || `تم إرسال الرسالة إلى ${contact.display_name}.`) };\n    } catch (error) {\n      const detail = errorMessage(error);\n      if (/24.?hour|window|template/i.test(detail)) {\n        return { reply: "ما قدرت أرسل الرسالة لأن نافذة واتساب المجانية مغلقة لهذا الرقم. لم أستخدم قالبًا مدفوعًا تلقائيًا." };\n      }\n      return { reply: "تعذر إرسال الرسالة الآن، ولم أكرر الإرسال لتجنب التكرار." };\n    }''',
    '''    const credentials = await loadValidCredentials(db);\n    const deliveryResult = await sendFreePeachContactMessage(\n      (name, args) => callMcpTool(credentials.access_token, name, args),\n      contact.target_wa_id,\n      body,\n    );\n    if (deliveryResult.ok) {\n      return { reply: String(decision.reply || `تم إرسال الرسالة إلى ${contact.display_name}.`) };\n    }\n    if (deliveryResult.reason === "no_conversation") {\n      return { reply: `ما قدرت أرسل إلى ${contact.display_name} لأن ما فيه محادثة واتساب سابقة متاحة لهذا الرقم. لازم يرسل للرقم التجاري أولًا حتى تنفتح نافذة الرد المجانية؛ ما استخدمت قالبًا مدفوعًا.` };\n    }\n    if (deliveryResult.reason === "window_closed") {\n      return { reply: "ما قدرت أرسل الرسالة لأن نافذة واتساب المجانية مغلقة لهذا الرقم. لم أستخدم قالبًا مدفوعًا تلقائيًا." };\n    }\n    return { reply: "تعذر إرسال الرسالة عبر المسار المجاني الآن، ولم أستخدم مسارًا مدفوعًا أو أكرر الإرسال." };''',
    "immediate external delivery",
)

replace_once(
    '''      if (taskMetadata?.delivery_channel === "meta") {\n        const targetWaId = String(taskMetadata?.target_wa_id || "").replace(/\\D/g, "");\n        if (!targetWaId) throw new Error("Meta reminder target is missing");\n        await sendMetaReminder(targetWaId, text);\n      } else {\n        await sendConversationReply(accessToken, Number(reminder.conversation_id), text);\n      }''',
    '''      if (taskMetadata?.delivery_channel === "peach_contact") {\n        const targetWaId = String(taskMetadata?.target_wa_id || "").replace(/\\D/g, "");\n        if (!targetWaId) throw new Error("Peach contact target is missing");\n        const deliveryResult = await sendFreePeachContactMessage(\n          (name, args) => callMcpTool(accessToken, name, args),\n          targetWaId,\n          text,\n        );\n        if (!deliveryResult.ok) {\n          if (deliveryResult.reason === "no_conversation") {\n            throw new Error("WhatsApp 24-hour reply window unavailable because no Peach conversation exists; paid/template fallback disabled");\n          }\n          if (deliveryResult.reason === "window_closed") {\n            throw new Error("WhatsApp 24-hour reply window closed; paid/template fallback disabled");\n          }\n          throw new Error(`Peach free contact delivery failed: ${deliveryResult.detail}`);\n        }\n      } else if (taskMetadata?.delivery_channel === "meta") {\n        const targetWaId = String(taskMetadata?.target_wa_id || "").replace(/\\D/g, "");\n        if (!targetWaId) throw new Error("Meta reminder target is missing");\n        await sendMetaReminder(targetWaId, text);\n      } else {\n        await sendConversationReply(accessToken, Number(reminder.conversation_id), text);\n      }''',
    "scheduled Peach contact delivery",
)

path.write_text(text)
