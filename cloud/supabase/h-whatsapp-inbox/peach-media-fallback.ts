export type PeachUnsupportedMediaKind = "audio" | "image" | "document" | "video" | "sticker";

export type PeachUnsupportedMedia = {
  kind: PeachUnsupportedMediaKind;
  reply: string;
};

const MEDIA_ALIASES: Record<string, PeachUnsupportedMediaKind> = {
  audio: "audio",
  voice: "audio",
  voice_note: "audio",
  ptt: "audio",
  image: "image",
  photo: "image",
  document: "document",
  file: "document",
  pdf: "document",
  video: "video",
  sticker: "sticker",
};

export function peachUnsupportedMediaFallback(
  messageType: unknown,
  body: unknown,
): PeachUnsupportedMedia | null {
  if (typeof body === "string" && body.trim()) return null;
  const normalized = String(messageType || "").trim().toLowerCase().replace(/[\s-]+/g, "_");
  const kind = MEDIA_ALIASES[normalized];
  if (!kind) return null;
  return { kind, reply: fallbackReply(kind) };
}

export function peachUnsupportedMediaEnvelope(
  kind: PeachUnsupportedMediaKind,
  messageId: unknown,
): { body: string; raw: Record<string, unknown> } {
  const id = typeof messageId === "string" || typeof messageId === "number" ? String(messageId) : null;
  return {
    body: `[unsupported_media:${kind}]`,
    raw: {
      source: "peach_unsupported_media",
      redacted: true,
      content_type: kind,
      peach_message_id: id,
      media_reference_available: false,
    },
  };
}

function fallbackReply(kind: PeachUnsupportedMediaKind): string {
  switch (kind) {
    case "audio":
      return "وصلني التسجيل الصوتي، لكن اتصال واتساب الحالي لا يتيح لـH تحميل ملف الصوت. أرسل محتواه كنص الآن.";
    case "image":
      return "وصلتني الصورة، لكن اتصال واتساب الحالي لا يتيح لـH تحميل ملف الصورة. أرسل وصفها أو النص الموجود فيها الآن.";
    case "document":
      return "وصلني الملف، لكن اتصال واتساب الحالي لا يتيح لـH تحميل المرفق. أرسل النص أو أهم محتواه الآن.";
    case "video":
      return "وصلني الفيديو، لكن اتصال واتساب الحالي لا يتيح لـH تحميل ملف الفيديو. أرسل المطلوب منه كنص الآن.";
    case "sticker":
      return "وصلني الملصق، لكن H لا يحتاج معالجته كطلب. أرسل طلبك كنص.";
  }
}
