import {
  isSensitiveKnowledgeGap,
  isVolatileKnowledgeGap,
  knowledgeGapQueryKey,
} from "./knowledge-gap.ts";

const MAX_QUERY_CHARS = 600;
const MAX_ANSWER_CHARS = 3000;

/**
 * Returns only owner-scoped, recently verified knowledge for the same normalized factual
 * question. Any lookup failure falls through to H's normal AI/research path.
 */
export async function recallVerifiedKnowledge(
  db: any,
  userKey: string,
  rawQuery: string,
): Promise<string | null> {
  const normalizedUserKey = String(userKey || "").trim();
  const query = String(rawQuery || "").replace(/\s+/g, " ").trim().slice(0, MAX_QUERY_CHARS);
  if (!normalizedUserKey || normalizedUserKey.length > 256 || !query) return null;
  if (!looksLikeFactualQuestion(query)) return null;
  if (isSensitiveKnowledgeGap(query) || isVolatileKnowledgeGap(query)) return null;

  try {
    const queryKey = await knowledgeGapQueryKey(query);
    if (!queryKey) return null;
    const { data, error } = await db.rpc("h_recall_verified_knowledge", {
      p_user_key: normalizedUserKey,
      p_query_key: queryKey,
    });
    if (error) return null;
    const row = Array.isArray(data) ? data[0] : data;
    const answer = String(row?.answer_text || "").replace(/\s+/g, " ").trim().slice(0, MAX_ANSWER_CHARS);
    return answer || null;
  } catch (_) {
    return null;
  }
}

function looksLikeFactualQuestion(query: string): boolean {
  const text = normalizeArabic(query.toLowerCase());
  if (/[?؟]/.test(query)) return true;
  return /^(من|ما|ماذا|متى|اين|وين|كيف|كم|ليش|لماذا|هل|وش|ايش|اي\s|who|what|when|where|why|how|is\s|are\s|does\s|do\s|can\s)/i.test(text);
}

function normalizeArabic(value: string): string {
  return value
    .replace(/[أإآ]/g, "ا")
    .replace(/ى/g, "ي")
    .replace(/ؤ/g, "و")
    .replace(/ئ/g, "ي")
    .replace(/[\u064B-\u065F\u0670]/g, "");
}
