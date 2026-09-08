export type VoiceDeliveryContext =
  | {
      channel: "peach";
      senderRole?: "owner" | "friend";
      canSendExternal?: boolean;
    }
  | {
      channel: "meta";
      targetWaId: string;
      senderRole: "owner" | "friend";
      canSendExternal: boolean;
    };

export type VoiceTranscriptInput = {
  waId: string;
  messageId: string;
  transcript: string;
  receivedAt: string | null;
  senderRole: "owner" | "friend";
  canSendExternal: boolean;
};

export function parseVoiceTranscriptPayload(payload: unknown): VoiceTranscriptInput | null {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return null;
  const value = payload as Record<string, unknown>;
  if (value.mode !== "voice_transcript") return null;

  const waId = String(value.wa_id || "").replace(/\D/g, "");
  const messageId = String(value.message_id || "").trim();
  const transcript = String(value.transcript || "").trim();
  if (!/^\d{8,20}$/.test(waId)) return null;
  if (!messageId || messageId.length > 200) return null;
  if (!transcript || transcript.length > 12_000) return null;

  const senderRole: "owner" | "friend" = value.sender_role === "owner" ? "owner" : "friend";
  const canSendExternal = senderRole === "owner" && value.can_send_external === true;

  let receivedAt: string | null = null;
  if (value.received_at != null && String(value.received_at).trim()) {
    const parsed = new Date(String(value.received_at));
    if (Number.isNaN(parsed.getTime())) return null;
    receivedAt = parsed.toISOString();
  }
  return { waId, messageId, transcript, receivedAt, senderRole, canSendExternal };
}

export function syntheticMetaConversationId(waId: string): number {
  let hash = 0x811c9dc5;
  for (const char of waId) {
    hash ^= char.charCodeAt(0);
    hash = Math.imul(hash, 0x01000193);
  }
  return 1 + ((hash >>> 0) % 2_000_000_000);
}

export function deliveryMetadata(
  delivery: VoiceDeliveryContext,
  source: string,
  originalText: string,
): Record<string, unknown> {
  const base: Record<string, unknown> = {
    source,
    original_text: originalText.slice(0, 2000),
  };
  if (delivery.channel === "meta") {
    base.delivery_channel = "meta";
    base.target_wa_id = delivery.targetWaId.replace(/\D/g, "");
    base.sender_role = delivery.senderRole;
    base.can_send_external = delivery.canSendExternal;
  } else if (delivery.senderRole) {
    base.delivery_channel = "peach";
    base.sender_role = delivery.senderRole;
    base.can_send_external = delivery.canSendExternal === true;
  }
  return base;
}
