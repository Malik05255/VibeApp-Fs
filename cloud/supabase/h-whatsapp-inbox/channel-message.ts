export type HChannelSourceType = "text" | "button" | "interactive" | "location" | "image" | "document";

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
  // Reproduce the previous voice_transcript bridge exactly, including its 200-char
  // pre-prefix truncation, so retries spanning deployment remain idempotent.
  const legacyBridgeMessageId = input.sourceType === "image" || input.sourceType === "document"
    ? `media:${input.messageId}`.slice(0, 200)
    : `channel:${input.sourceType}:${input.messageId}`.slice(0, 200);
  return `meta:${legacyBridgeMessageId}`;
}

export function channelMessageType(sourceType: HChannelSourceType): string {
  return `channel_${sourceType}`;
}
