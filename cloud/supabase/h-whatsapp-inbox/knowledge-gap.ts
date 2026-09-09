export type KnowledgeGapReason =
  | "explicit_uncertainty"
  | "research_no_evidence"
  | "verifier_rejected"
  | "tool_unavailable";

export type KnowledgeGapPriority = "simple" | "medium" | "important";

export type KnowledgeGapAssessmentInput = {
  query: string;
  reply?: string | null;
  researchActive?: boolean;
  evidenceCount?: number;
  providerTrace?: string[];
  verifierOk?: boolean | null;
  priority?: KnowledgeGapPriority;
};

export type KnowledgeGapAssessment = {
  shouldQueue: boolean;
  reason: KnowledgeGapReason | null;
  query: string;
  priority: KnowledgeGapPriority;
  sensitive: boolean;
};

const MAX_GAP_QUERY_CHARS = 600;

/**
 * Detects learnable factual gaps without treating ordinary chat, actions, or secrets as
 * durable learning material. This is intentionally conservative: a gap is queued only
 * when H has an explicit uncertainty/verification failure signal.
 */
export function assessKnowledgeGap(input: KnowledgeGapAssessmentInput): KnowledgeGapAssessment {
  const query = normalizeGapQuery(input.query);
  const priority = input.priority ?? "medium";
  const sensitive = isSensitiveKnowledgeGap(query);

  if (!query || sensitive || !isLearnableFactualRequest(query)) {
    return { shouldQueue: false, reason: null, query, priority, sensitive };
  }

  if (input.verifierOk === false) {
    return { shouldQueue: true, reason: "verifier_rejected", query, priority, sensitive: false };
  }

  const trace = (input.providerTrace ?? []).map((value) => String(value || "").toLowerCase());
  const toolUnavailable = trace.some((value) =>
    /(tool_missing|not_connected|unavailable|quota|blocked|free_only_guard|provider_missing|no_provider)/.test(value)
  );
  if (input.researchActive && toolUnavailable && Number(input.evidenceCount ?? 0) === 0) {
    return { shouldQueue: true, reason: "tool_unavailable", query, priority, sensitive: false };
  }

  if (input.researchActive && Number(input.evidenceCount ?? 0) === 0) {
    return { shouldQueue: true, reason: "research_no_evidence", query, priority, sensitive: false };
  }

  if (hasExplicitUncertainty(input.reply ?? "")) {
    return { shouldQueue: true, reason: "explicit_uncertainty", query, priority, sensitive: false };
  }

  return { shouldQueue: false, reason: null, query, priority, sensitive: false };
}

/**
 * Persists only the unresolved question and compact reason metadata. Candidate answers,
 * chain-of-thought, provider prompts, credentials, and raw attachment content never enter
 * the Learning Queue through this path.
 */
export async function enqueueKnowledgeGap(
  db: any,
  userKey: string,
  assessment: KnowledgeGapAssessment,
): Promise<{ queued: boolean; id?: string; status?: string; occurrences?: number; reason?: string }> {
  if (!assessment.shouldQueue || !assessment.reason || assessment.sensitive) {
    return { queued: false, reason: "not_queueable" };
  }

  const normalizedUserKey = String(userKey || "").trim();
  if (!normalizedUserKey || normalizedUserKey.length > 256) {
    return { queued: false, reason: "invalid_user_key" };
  }

  const query = normalizeGapQuery(assessment.query);
  if (!query || isSensitiveKnowledgeGap(query)) {
    return { queued: false, reason: "sensitive_or_empty" };
  }

  const queryKey = await sha256Hex(normalizeGapKey(query));
  const { data, error } = await db.rpc("h_enqueue_knowledge_gap", {
    p_user_key: normalizedUserKey,
    p_query_key: queryKey,
    p_query_text: query,
    p_reason: assessment.reason,
    p_priority: assessment.priority,
  });
  if (error) throw error;

  const row = Array.isArray(data) ? data[0] : data;
  return {
    queued: true,
    id: row?.id ? String(row.id) : undefined,
    status: row?.status ? String(row.status) : undefined,
    occurrences: Number.isFinite(Number(row?.occurrences)) ? Number(row.occurrences) : undefined,
  };
}

export function normalizeGapQuery(value: string): string {
  return String(value || "").replace(/\s+/g, " ").trim().slice(0, MAX_GAP_QUERY_CHARS);
}

export function isSensitiveKnowledgeGap(value: string): boolean {
  const text = normalizeArabic(String(value || "").toLowerCase());
  if (!text) return false;

  if (/(password|passcode|secret|api\s*key|access\s*token|refresh\s*token|bearer|private\s*key|cvv|cvc|otp|pin\b|كلمة\s*المرور|كلمه\s*المرور|رمز\s*التحقق|رمز\s*الدخول|الرقم\s*السري|مفتاح\s*(api|اي\s*بي\s*اي)|توكن)/i.test(text)) {
    return true;
  }
  if (/\b(?:\d[ -]*?){13,19}\b/.test(text)) return true;
  if (/(otp|رمز\s*التحقق|رمز\s*الدخول)[^\d]{0,12}\d{4,8}/i.test(text)) return true;
  return false;
}

function hasExplicitUncertainty(reply: string): boolean {
  const text = normalizeArabic(String(reply || "").toLowerCase());
  if (!text) return false;
  return /(لا\s*اعرف|ما\s*اعرف|لا\s*ادري|غير\s*متاكد|لست\s*متاكد|لا\s*املك\s*معلوم|ما\s*عندي\s*معلوم|لم\s*اتمكن\s*من\s*التحقق|لا\s*يمكنني\s*التحقق|لا\s*توجد\s*ادله\s*موثوقه|لا\s*يوجد\s*دليل\s*موثوق|i\s*don'?t\s*know|not\s*sure|cannot\s*verify|can'?t\s*verify|no\s*verified\s*evidence|insufficient\s*evidence)/i.test(text);
}

function isLearnableFactualRequest(query: string): boolean {
  const text = normalizeArabic(query.toLowerCase());
  if (!text) return false;

  if (/^(هلا|هلا والله|مرحبا|السلام عليكم|صباح الخير|مساء الخير|شكرا|شكرًا|thanks?|hello|hi)\b/i.test(text)) return false;
  if (/(ذكرني|تذكير|ارسل\s*رساله|ارسلي\s*رساله|اتصل\s*ب|افتح\s*|شغل\s*|اكتب\s*لي|صمم\s*لي|عدل\s*|احذف\s*|remind\s*me|send\s*(a\s*)?message|call\s*|write\s*me|design\s*)/i.test(text)) return false;

  if (/[?؟]/.test(query)) return true;
  return /^(من|ما|ماذا|متى|اين|وين|كيف|كم|ليش|لماذا|هل|وش|ايش|اي\s|who|what|when|where|why|how|is\s|are\s|does\s|do\s|can\s)/i.test(text);
}

function normalizeGapKey(query: string): string {
  return normalizeArabic(query.toLowerCase())
    .replace(/[؟?!.,،؛:;"'`()\[\]{}]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function normalizeArabic(value: string): string {
  return value
    .replace(/[أإآ]/g, "ا")
    .replace(/ى/g, "ي")
    .replace(/ؤ/g, "و")
    .replace(/ئ/g, "ي")
    .replace(/[\u064B-\u065F\u0670]/g, "");
}

async function sha256Hex(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
