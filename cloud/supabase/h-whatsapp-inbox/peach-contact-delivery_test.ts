import {
  classifyPeachDeliveryFailure,
  findPeachConversationId,
  normalizePeachPhone,
  sendFreePeachContactMessage,
} from "./peach-contact-delivery.ts";

Deno.test("Peach contact phone is normalized to E.164", () => {
  const phone = normalizePeachPhone("+966 55 123 4567");
  if (phone !== "+966551234567") throw new Error(`unexpected phone ${phone}`);
  if (normalizePeachPhone("123") !== null) throw new Error("short phone was accepted");
});

Deno.test("Peach conversation id is found in common nested response shapes", () => {
  const first = findPeachConversationId({ conversations: [{ id: 321 }] });
  const second = findPeachConversationId({ structuredContent: { data: [{ conversation_id: 654 }] } });
  const third = findPeachConversationId({ content: [{ type: "text", text: JSON.stringify({ results: [{ id: 987 }] }) }] });
  if (first !== 321 || second !== 654 || third !== 987) throw new Error("conversation id extraction failed");
});

Deno.test("free Peach delivery never supplies a WhatsApp template", async () => {
  const calls: Array<{ name: string; args: Record<string, unknown> }> = [];
  const result = await sendFreePeachContactMessage(async (name, args) => {
    calls.push({ name, args });
    if (name === "peach_list_conversations") return { conversations: [{ id: 42 }] };
    if (name === "peach_reply_to_conversation") return { ok: true };
    throw new Error(`unexpected tool ${name}`);
  }, "966551234567", "وصلت");

  if (!result.ok || result.conversationId !== 42) throw new Error("delivery did not succeed");
  const reply = calls.find((call) => call.name === "peach_reply_to_conversation");
  if (!reply) throw new Error("reply tool was not called");
  if ("whats_app_template_id" in reply.args) throw new Error("paid/template fallback was supplied");
  if (reply.args.text !== "وصلت") throw new Error("message text changed");
});

Deno.test("free Peach delivery refuses contact without an existing conversation", async () => {
  const result = await sendFreePeachContactMessage(async (name) => {
    if (name === "peach_list_conversations") return { conversations: [] };
    throw new Error("reply should not be attempted without a conversation");
  }, "966551234567", "وصلت");
  if (result.ok || result.reason !== "no_conversation") throw new Error("missing conversation did not fail closed");
});

Deno.test("Peach closed reply window is classified without paid fallback", async () => {
  const result = await sendFreePeachContactMessage(async (name) => {
    if (name === "peach_list_conversations") return { data: [{ id: 55 }] };
    return {
      isError: true,
      content: [{ type: "text", text: "24-hour customer service window is closed; template is required" }],
    };
  }, "966551234567", "وصلت");
  if (result.ok || result.reason !== "window_closed") throw new Error("closed window was not detected");
});

Deno.test("Peach thrown closed-window error is classified", () => {
  const result = classifyPeachDeliveryFailure(new Error("reply window closed; template required"));
  if (result.ok || result.reason !== "window_closed") throw new Error("thrown window error was not detected");
});
