export type HMediaKind = "image" | "document";

export type HMediaMessageInput = {
  waId: string;
  messageId: string;
  kind: HMediaKind;
  mimeType: string;
  fileName: string | null;
  caption: string | null;
  base64: string;
  sizeBytes: number;
  receivedAt: string | null;
};

const MAX_MEDIA_BYTES = 8 * 1024 * 1024;
const MAX_TEXT_DOCUMENT_BYTES = 512 * 1024;
const MAX_CAPTION_LENGTH = 2000;
const MAX_FILE_NAME_LENGTH = 160;

const IMAGE_MIME_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
]);

const TEXT_DOCUMENT_MIME_TYPES = new Set([
  "text/plain",
  "text/csv",
  "text/markdown",
  "text/xml",
  "application/json",
  "application/xml",
  "application/csv",
]);

export function parseMediaMessagePayload(payload: unknown): HMediaMessageInput | null {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return null;
  const value = payload as Record<string, unknown>;
  if (value.mode !== "media_message") return null;

  const waId = String(value.wa_id || "").replace(/\D/g, "").slice(0, 32);
  const messageId = String(value.message_id || "").trim().slice(0, 200);
  const kind = value.kind === "image" || value.kind === "document" ? value.kind : null;
  const mimeType = normalizeMimeType(value.mime_type);
  const base64 = String(value.base64 || "").replace(/\s+/g, "");
  if (!waId || waId.length < 6 || !messageId || !kind || !mimeType || !base64) return null;
  if (!isCanonicalBase64(base64)) return null;

  const sizeBytes = decodedBase64Size(base64);
  if (!Number.isFinite(sizeBytes) || sizeBytes <= 0 || sizeBytes > MAX_MEDIA_BYTES) return null;
  if (!isSupportedMedia(kind, mimeType, sizeBytes)) return null;

  const fileName = sanitizeFileName(value.file_name);
  const caption = sanitizeCaption(value.caption);
  const receivedAt = normalizeDate(value.received_at);

  return {
    waId,
    messageId,
    kind,
    mimeType,
    fileName,
    caption,
    base64,
    sizeBytes,
    receivedAt,
  };
}

export function isSupportedMedia(kind: HMediaKind, mimeType: string, sizeBytes: number): boolean {
  if (!Number.isFinite(sizeBytes) || sizeBytes <= 0 || sizeBytes > MAX_MEDIA_BYTES) return false;
  if (kind === "image") return IMAGE_MIME_TYPES.has(mimeType);
  if (mimeType === "application/pdf") return true;
  return TEXT_DOCUMENT_MIME_TYPES.has(mimeType) && sizeBytes <= MAX_TEXT_DOCUMENT_BYTES;
}

export function isTextDocumentMime(mimeType: string): boolean {
  return TEXT_DOCUMENT_MIME_TYPES.has(normalizeMimeType(mimeType));
}

export function decodeTextDocument(input: HMediaMessageInput): string | null {
  if (input.kind !== "document" || !isTextDocumentMime(input.mimeType)) return null;
  if (input.sizeBytes > MAX_TEXT_DOCUMENT_BYTES) return null;
  try {
    const binary = atob(input.base64);
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
    const text = new TextDecoder("utf-8", { fatal: false }).decode(bytes).replace(/\u0000/g, "").trim();
    return text ? text.slice(0, 12000) : null;
  } catch (_) {
    return null;
  }
}

export function buildMediaConversationText(input: HMediaMessageInput, analysis: string): string {
  const label = input.kind === "image" ? "صورة واتساب" : "ملف واتساب";
  const parts = [`[${label}]`];
  if (input.fileName) parts.push(`اسم الملف: ${input.fileName}`);
  if (input.caption) parts.push(`تعليق المستخدم: ${input.caption}`);
  parts.push(`المحتوى الذي تم استخراجه/فهمه: ${String(analysis || "").trim().slice(0, 9000)}`);
  return parts.join("\n").slice(0, 12000);
}

export function mediaStorageMetadata(input: HMediaMessageInput, model: string | null) {
  return {
    source: "meta_media_bridge",
    message_id: input.messageId,
    wa_id: input.waId,
    kind: input.kind,
    mime_type: input.mimeType,
    file_name: input.fileName,
    caption_present: Boolean(input.caption),
    size_bytes: input.sizeBytes,
    analysis_model: model,
    raw_media_persisted: false,
  };
}

export function maxMediaBytes(): number {
  return MAX_MEDIA_BYTES;
}

function normalizeMimeType(value: unknown): string {
  return String(value || "").split(";", 1)[0].trim().toLowerCase();
}

function sanitizeCaption(value: unknown): string | null {
  const text = String(value || "").replace(/[\u0000-\u001F\u007F]/g, " ").replace(/\s+/g, " ").trim();
  return text ? text.slice(0, MAX_CAPTION_LENGTH) : null;
}

function sanitizeFileName(value: unknown): string | null {
  const text = String(value || "")
    .replace(/[\u0000-\u001F\u007F]/g, "")
    .replace(/[\\/]+/g, "_")
    .trim();
  return text ? text.slice(0, MAX_FILE_NAME_LENGTH) : null;
}

function normalizeDate(value: unknown): string | null {
  const text = String(value || "").trim();
  if (!text) return null;
  const date = new Date(text);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function isCanonicalBase64(value: string): boolean {
  if (!value || value.length % 4 === 1) return false;
  return /^[A-Za-z0-9+/]+={0,2}$/.test(value);
}

function decodedBase64Size(value: string): number {
  const padding = value.endsWith("==") ? 2 : value.endsWith("=") ? 1 : 0;
  return Math.floor(value.length * 3 / 4) - padding;
}
