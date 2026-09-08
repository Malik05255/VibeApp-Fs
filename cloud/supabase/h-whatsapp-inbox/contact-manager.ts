export type HExternalDeliveryContext = {
  channel: string;
  canSendExternal?: boolean;
};

export type HSavedContact = {
  display_name: string;
  target_wa_id: string;
};

type DbClient = any;

export function normalizeContactKey(value: unknown): string {
  return String(value || "")
    .trim()
    .toLocaleLowerCase("ar")
    .replace(/[\u064B-\u065F\u0670]/g, "")
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function normalizeWaIdCandidate(value: unknown): string | null {
  const digits = String(value || "").replace(/\D/g, "");
  return /^\d{8,20}$/.test(digits) ? digits : null;
}

export function canUseExternalMessaging(delivery: HExternalDeliveryContext): boolean {
  return (delivery.channel === "meta" || delivery.channel === "peach") && delivery.canSendExternal === true;
}

export function looksLikeContactSaveIntent(text: string): boolean {
  return /^(?:يا\s*h[\s،,:-]*)?(?:احفظ|إحفظ|سجل|سجّل|save)(?:\s|$)[\s\S]*?(?:رقم|جهة\s*اتصال|contact|\+?\d[\d\s().-]{6,}\d)/iu.test(text.trim());
}

export function parseDeterministicContactSave(text: string): { name: string; targetWaId: string } | null {
  const value = text.trim();
  if (!looksLikeContactSaveIntent(value)) return null;

  const phoneMatch = value.match(/(?:\+?\d[\d\s().-]{6,}\d)\s*$/u);
  if (!phoneMatch || phoneMatch.index == null) return null;
  const targetWaId = normalizeWaIdCandidate(phoneMatch[0]);
  if (!targetWaId) return null;

  let name = value.slice(0, phoneMatch.index).trim();
  name = name
    .replace(/^(?:يا\s*h[\s،,:-]*)?/iu, "")
    .replace(/^(?:احفظ|إحفظ|سجل|سجّل|save)\s*/iu, "")
    .replace(/^(?:رقم|جهة\s*اتصال|contact)\s*/iu, "")
    .replace(/(?:باسم|اسم)\s*$/iu, "")
    .trim();

  if (!name || name.length > 120 || !normalizeContactKey(name)) return null;
  return { name, targetWaId };
}

export async function saveRuntimeContact(
  db: DbClient,
  userKey: string,
  name: string,
  targetWaId: string,
): Promise<HSavedContact> {
  const normalizedTarget = normalizeWaIdCandidate(targetWaId);
  const nameKey = normalizeContactKey(name);
  const displayName = name.trim().slice(0, 120);
  if (!normalizedTarget || !nameKey || !displayName) throw new Error("Invalid H contact");

  const { data, error } = await db.from("h_runtime_contacts").upsert({
    user_key: userKey,
    name_key: nameKey,
    display_name: displayName,
    target_wa_id: normalizedTarget,
    updated_at: new Date().toISOString(),
  }, { onConflict: "user_key,name_key" }).select("display_name,target_wa_id").single();
  if (error || !data) throw error || new Error("Failed to save H contact");
  return data as HSavedContact;
}

export async function resolveRuntimeContact(
  db: DbClient,
  userKey: string,
  name: string,
): Promise<HSavedContact | null> {
  const nameKey = normalizeContactKey(name);
  if (!nameKey) return null;
  const { data, error } = await db.from("h_runtime_contacts")
    .select("display_name,target_wa_id")
    .eq("user_key", userKey)
    .eq("name_key", nameKey)
    .maybeSingle();
  if (error) throw error;
  return data as HSavedContact | null;
}
