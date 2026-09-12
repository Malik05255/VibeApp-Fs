export type HMemoryDb = any;

export type ExplicitMemoryMutation =
  | { action: "correct"; oldBody: string; newBody: string }
  | { action: "forget"; body: string };

const MAX_MEMORY_BODY = 280;

export function normalizeMemoryBody(value: unknown): string {
  return String(value ?? "").trim().replace(/\s+/g, " ").slice(0, MAX_MEMORY_BODY);
}

export function hasExplicitMemorySaveIntent(text: unknown): boolean {
  const normalized = String(text ?? "").trim().replace(/\s+/g, " ");
  if (!normalized) return false;
  return /(?:^|\s)(?:احفظ|إحفظ|تذكر|تذكّر|خزن|سجل|سجّل)(?:\s|$)|(?:^|\s)(?:remember|save|store)(?:\s+this|\s+that|\s+for\s+later|\s+in\s+memory|\s|$)/iu.test(normalized)
    && !/(?:ذكرني|ذكّرني|remind\s+me)/iu.test(normalized);
}

export function parseExplicitMemoryMutation(text: unknown): ExplicitMemoryMutation | null {
  const normalized = String(text ?? "").trim().replace(/\s+/g, " ");
  if (!normalized) return null;

  const correctionPatterns = [
    /^(?:يا\s*h\s*)?(?:صحح|صحّح|صَحّح|عدل|عدّل|بدل|بدّل|غير|غيّر)\s+(?:ذاكرتك\s+)?(?:من\s+)?["“”']?(.+?)["“”']?\s+(?:إلى|الى|لـ|ل|بـ|ب)\s+["“”']?(.+?)["“”']?$/i,
    /^(?:h\s*,?\s*)?(?:correct|replace|change)\s+["“”']?(.+?)["“”']?\s+(?:to|with)\s+["“”']?(.+?)["“”']?$/i,
  ];
  for (const pattern of correctionPatterns) {
    const match = normalized.match(pattern);
    const oldBody = normalizeMemoryBody(match?.[1]);
    const newBody = normalizeMemoryBody(match?.[2]);
    if (oldBody && newBody) return { action: "correct", oldBody, newBody };
  }

  const forgetPatterns = [
    /^(?:يا\s*h\s*)?(?:انس|انسَ|انسى|إنس|إنسَ|احذف|إحذف)\s+(?:من\s+ذاكرتك\s+)?["“”']?(.+?)["“”']?$/i,
    /^(?:h\s*,?\s*)?(?:forget|remove from memory|delete from memory)\s+["“”']?(.+?)["“”']?$/i,
  ];
  for (const pattern of forgetPatterns) {
    const match = normalized.match(pattern);
    const body = normalizeMemoryBody(match?.[1]);
    if (body) return { action: "forget", body };
  }

  return null;
}

function unwrapRpc(data: unknown) {
  return data && typeof data === "object" ? data as Record<string, unknown> : {};
}

export async function saveHMemory(
  db: HMemoryDb,
  userKey: string,
  category: string,
  body: string,
  originalText?: string | null,
) {
  const normalized = normalizeMemoryBody(body);
  if (!normalized) throw new Error("invalid_memory_body");
  const { data, error } = await db.rpc("h_runtime_save_memory", {
    p_user_key: userKey,
    p_category: category || "note",
    p_body: normalized,
    p_original_text: originalText || null,
  });
  if (error) throw error;
  return unwrapRpc(data);
}

export async function correctHMemory(
  db: HMemoryDb,
  userKey: string,
  oldBody: string,
  newBody: string,
  category?: string | null,
  originalText?: string | null,
) {
  const oldNormalized = normalizeMemoryBody(oldBody);
  const newNormalized = normalizeMemoryBody(newBody);
  if (!oldNormalized || !newNormalized) throw new Error("memory_correction_body_required");
  const { data, error } = await db.rpc("h_runtime_correct_memory", {
    p_user_key: userKey,
    p_old_body: oldNormalized,
    p_new_body: newNormalized,
    p_category: category || null,
    p_original_text: originalText || null,
  });
  if (error) throw error;
  return unwrapRpc(data);
}

export async function forgetHMemory(db: HMemoryDb, userKey: string, body: string) {
  const normalized = normalizeMemoryBody(body);
  if (!normalized) throw new Error("memory_forget_body_required");
  const { data, error } = await db.rpc("h_runtime_forget_memory", {
    p_user_key: userKey,
    p_body: normalized,
  });
  if (error) throw error;
  return unwrapRpc(data);
}

export function memoryMutationReply(result: Record<string, unknown>, action: "correct" | "forget") {
  if (result?.matched === false || result?.ok === false) {
    return action === "correct"
      ? "ما لقيت هذه المعلومة محفوظة حرفيًا، لذلك ما غيّرت أي شيء. اعرض ذاكرتي وحدد العبارة التي تريد تصحيحها."
      : "ما لقيت هذه المعلومة محفوظة حرفيًا، لذلك ما حذفت أي شيء. اعرض ذاكرتي وحدد العبارة التي تريد نسيانها.";
  }
  return action === "correct" ? "تم تصحيح المعلومة في ذاكرتي." : "تم نسيان هذه المعلومة.";
}
