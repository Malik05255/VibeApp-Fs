import { readFileSync, writeFileSync } from "node:fs";

const path = "cloud/whatsapp-worker/src/index.js";
let text = readFileSync(path, "utf8");

function replaceOnce(from, to, label) {
  const index = text.indexOf(from);
  if (index < 0) throw new Error(`Missing patch anchor: ${label}`);
  if (text.indexOf(from, index + from.length) >= 0) throw new Error(`Ambiguous patch anchor: ${label}`);
  text = text.slice(0, index) + to + text.slice(index + from.length);
}

replaceOnce(
  `        const storedBody = inbound.text || \`[\${message.type || "unknown"}]\`;`,
  `        // D1 keeps only transport-level dedupe metadata. H Cloud owns conversation content.\n        const storedBody = \`[\${message.type || "unknown"}]\`;`,
  "transport-only inbound body",
);

replaceOnce(
  `        await appendConversationMessage(env, from, "user", storedBody);\n\n`,
  ``,
  "remove local user conversation copy",
);

const webhookStart = text.indexOf("async function handleWebhook(payload, env) {");
const webhookEnd = text.indexOf("\nasync function normalizeInboundMessage", webhookStart);
if (webhookStart < 0 || webhookEnd < 0) throw new Error("Could not isolate handleWebhook");
let webhook = text.slice(webhookStart, webhookEnd);
if (!webhook.includes("sendAssistantText(")) throw new Error("Expected webhook assistant sends were not found");
webhook = webhook.replaceAll("sendAssistantText(", "sendWebhookText(");
if (webhook.includes("appendConversationMessage(")) throw new Error("Webhook still writes local conversation history");
text = text.slice(0, webhookStart) + webhook + text.slice(webhookEnd);

replaceOnce(
  `async function normalizeInboundMessage(message, env) {`,
  `async function sendWebhookText(env, to, body) {\n  const text = String(body || "").slice(0, DEFAULT_MAX_MESSAGE_LENGTH);\n  return sendText(env, to, text);\n}\n\nasync function normalizeInboundMessage(message, env) {`,
  "transport-only outbound helper",
);

const updatedWebhookStart = text.indexOf("async function handleWebhook(payload, env) {");
const updatedWebhookEnd = text.indexOf("\nasync function sendWebhookText", updatedWebhookStart);
const updatedWebhook = text.slice(updatedWebhookStart, updatedWebhookEnd);
if (updatedWebhook.includes("sendAssistantText(")) throw new Error("Webhook still uses local conversation logging sender");
if (updatedWebhook.includes("appendConversationMessage(")) throw new Error("Webhook still appends local conversation content");
if (updatedWebhook.includes("const storedBody = inbound.text")) throw new Error("Webhook still persists inbound text in D1 dedupe table");
if (!updatedWebhook.includes("bridgeChannelMessage(")) throw new Error("Unified H channel bridge disappeared");

writeFileSync(path, text, "utf8");
console.log("Applied WhatsApp transport-only worker cleanup.");
