export type HChannelSourceType =
  | "text"
  | "button"
  | "interactive"
  | "location"
  | "image"
  | "document"
  | "audio"
  | "video";

export type HChannelMessageInput = {
  waId: string;
  messageId: string;
  text: string;
  sourceType: HChannelSourceType;
  receivedAt: string | null;
  senderRole: "owner" | "friend";
  canSendExternal: boolean;
};

const SOURCE_TYPES = new Set<HChannelSourceType>([
  "text",
  "button",
  "interactive",
  "location",
  "image",
  "document",
  "audio",
  "video",
]);

export function parseChannelMessagePayload(payload: unknown): HChannelMessageInput | null {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return null;
  const value = payload as Record<string, unknown>;
  if (value.mode !== "channel_message") return null;

  const waId = String(value.wa_id || "").replace(/\D/g, "");
  const messageId = String(value.message_id || "").trim();
  const text = String(value.text || "").trim();
  const sourceType = SOURCE_TYPES.has(value.source_type as HChannelSourceType)
    ? value.source_type as HChannelSourceType
    : null;

  if (!/^\d{8,20}$/.test(waId)) return null;
  if (!messageId || messageId.length > 200) return null;
  if (!text || text.length > 12_000) return null;
  if (!sourceType) return null;

  const senderRole: "owner" | "friend" = value.sender_role === "owner" ? "owner" : "friend";
  const canSendExternal = senderRole === "owner" && value.can_send_external === true;

  let receivedAt: string | null = null;
  if (value.received_at != null && String(value.received_at).trim()) {
    const parsed = new Date(String(value.received_at));
    if (Number.isNaN(parsed.getTime())) return null;
    receivedAt = parsed.toISOString();
  }

  return {
    waId,
    messageId,
    text,
    sourceType,
    receivedAt,
    senderRole,
    canSendExternal,
  };
}

export function channelMessageKey(input: Pick<HChannelMessageInput, "sourceType" | "messageId">): string {
  // Media paths keep the historical media namespace so one original WhatsApp item is
  // idempotent even if its processing implementation changes later.
  const mediaSource = input.sourceType === "image" ||
    input.sourceType === "document" ||
    input.sourceType === "audio" ||
    input.sourceType === "video";
  const bridgeMessageId = mediaSource
    ? `media:${input.messageId}`.slice(0, 200)
    : `channel:${input.sourceType}:${input.messageId}`.slice(0, 200);
  return `meta:${bridgeMessageId}`;
}

export function channelMessageType(sourceType: HChannelSourceType): string {
  return `channel_${sourceType}`;
}
