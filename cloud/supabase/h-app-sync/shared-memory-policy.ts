const MAX_MEMORY_CHARS = 280;
const ALLOWED_CATEGORIES = new Set([
  "identity",
  "preference",
  "relationship",
  "idea",
  "note",
  "general",
]);

const SENSITIVE_PATTERNS = [
  /\b(password|passcode|pin|cvv|cvc|otp|api[ _-]?key|access[ _-]?token|refresh[ _-]?token|secret)\b/iu,
  /(كلمة\s*المرور|الرقم\s*السري|رمز\s*سري|رمز\s*التحقق|كود\s*التحقق|رمز\s*الدخول|المفتاح\s*السري|توكن|رمز\s*otp|رقم\s*البطاقة|رقم\s*بطاقة)/iu,
  /(?<![0-9٠-٩۰-۹])(?:[0-9٠-٩۰-۹][ -]?){13,19}(?![0-9٠-٩۰-۹])/u,
  /(?<![0-9٠-٩۰-۹])[0-9٠-٩۰-۹]{6}(?![0-9٠-٩۰-۹])/u,
];

export type SharedMemoryInput = {
  text: string;
  originalText: string | null;
  category: string;
};

export function normalizeSharedMemoryInput(value: unknown): SharedMemoryInput | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const body = value as Record<string, unknown>;
  const text = normalizeText(body.text, MAX_MEMORY_CHARS);
  if (!text || isSensitiveSharedMemory(text)) return null;

  const originalText = normalizeText(body.original_text, 500);
  if (originalText && isSensitiveSharedMemory(originalText)) return null;

  const requestedCategory = String(body.category || "general").trim().toLowerCase();
  const category = ALLOWED_CATEGORIES.has(requestedCategory) ? requestedCategory : "general";
  return { text, originalText, category };
}

export function isSensitiveSharedMemory(text: string): boolean {
  const value = String(text || "").trim();
  if (!value) return false;
  return SENSITIVE_PATTERNS.some((pattern) => pattern.test(value));
}

function normalizeText(value: unknown, maxChars: number): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.replace(/\s+/gu, " ").trim().slice(0, maxChars);
  return normalized || null;
}
