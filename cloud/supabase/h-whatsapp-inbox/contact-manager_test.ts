import {
  canUseExternalMessaging,
  looksLikeContactSaveIntent,
  normalizeContactKey,
  normalizeWaIdCandidate,
  parseDeterministicContactSave,
} from "./contact-manager.ts";

Deno.test("contact save parser accepts Arabic owner commands", () => {
  const samples = [
    ["احفظ رقم محمد 966551234567", "محمد", "966551234567"],
    ["احفظ محمد +966 55 123 4567", "محمد", "966551234567"],
    ["سجل جهة اتصال محمد عمر 966551234567", "محمد عمر", "966551234567"],
  ];
  for (const [text, name, target] of samples) {
    const parsed = parseDeterministicContactSave(text);
    if (!parsed) throw new Error(`expected contact save: ${text}`);
    if (parsed.name !== name || parsed.targetWaId !== target) {
      throw new Error(`unexpected parsed contact for ${text}: ${JSON.stringify(parsed)}`);
    }
  }
});

Deno.test("ordinary memory save is not treated as contact save", () => {
  const text = "احفظ هذه الفكرة عن السباكة";
  if (looksLikeContactSaveIntent(text)) throw new Error("memory intent was classified as contact save");
  if (parseDeterministicContactSave(text) !== null) throw new Error("memory intent parsed as contact");
});

Deno.test("contact normalization is stable for Arabic names and phone formatting", () => {
  if (normalizeContactKey("  مُحَمَّد  عمر ") !== "محمد عمر") throw new Error("Arabic contact key normalization failed");
  if (normalizeWaIdCandidate("+966 (55) 123-4567") !== "966551234567") throw new Error("WA id normalization failed");
  if (normalizeWaIdCandidate("123") !== null) throw new Error("short number accepted");
});

Deno.test("external messaging capability requires trusted Meta or Peach owner claim", () => {
  if (!canUseExternalMessaging({ channel: "meta", canSendExternal: true })) throw new Error("trusted Meta owner capability rejected");
  if (!canUseExternalMessaging({ channel: "peach", canSendExternal: true })) throw new Error("trusted Peach owner capability rejected");
  if (canUseExternalMessaging({ channel: "meta", canSendExternal: false })) throw new Error("Meta friend capability accepted");
  if (canUseExternalMessaging({ channel: "peach", canSendExternal: false })) throw new Error("Peach friend capability accepted");
  if (canUseExternalMessaging({ channel: "unknown", canSendExternal: true })) throw new Error("unknown channel capability accepted");
});
