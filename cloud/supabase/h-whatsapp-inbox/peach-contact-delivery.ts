export type PeachToolCaller = (
  name: string,
  args: Record<string, unknown>,
) => Promise<any>;

export type FreePeachDeliveryResult =
  | { ok: true; conversationId: number }
  | {
      ok: false;
      reason: "no_conversation" | "window_closed" | "provider_error";
      detail: string;
    };

export async function sendFreePeachContactMessage(
  callTool: PeachToolCaller,
  targetWaId: string,
  text: string,
): Promise<FreePeachDeliveryResult> {
  const phone = normalizePeachPhone(targetWaId);
  const body = String(text || "").trim().slice(0, 4096);
  if (!phone || !body) {
    return { ok: false, reason: "provider_error", detail: "invalid_contact_or_message" };
  }

  let conversations: any;
  try {
    conversations = await callTool("peach_list_conversations", {
      phone_number: phone,
      per_page: 10,
    });
  } catch (error) {
    return classifyPeachDeliveryFailure(error);
  }

  const conversationId = findPeachConversationId(conversations);
  if (!conversationId) {
    return {
      ok: false,
      reason: "no_conversation",
      detail: "no_existing_peach_conversation_for_contact",
    };
  }

  try {
    const result = await callTool("peach_reply_to_conversation", {
      conversation_id: conversationId,
      text: body,
    });
    const toolError = extractToolError(result);
    if (toolError) return classifyPeachDeliveryFailure(toolError);
    return { ok: true, conversationId };
  } catch (error) {
    return classifyPeachDeliveryFailure(error);
  }
}

export function normalizePeachPhone(value: unknown): string | null {
  const digits = String(value || "").replace(/\D/g, "");
  if (!/^\d{8,20}$/.test(digits)) return null;
  return `+${digits}`;
}

export function findPeachConversationId(value: unknown): number | null {
  const payload = unwrapToolPayload(value);
  const rows = findConversationRows(payload);
  for (const row of rows) {
    const id = positiveInteger((row as any)?.conversation_id ?? (row as any)?.id);
    if (id) return id;
  }
  return null;
}

export function classifyPeachDeliveryFailure(error: unknown): FreePeachDeliveryResult {
  const detail = errorMessage(error).slice(0, 500) || "unknown_peach_delivery_error";
  if (/24.?hour|customer\s*service\s*window|reply\s*window|window\s*(?:is\s*)?closed|template\s*(?:is\s*)?required|outside.*window/i.test(detail)) {
    return { ok: false, reason: "window_closed", detail };
  }
  return { ok: false, reason: "provider_error", detail };
}

function findConversationRows(value: unknown): any[] {
  if (Array.isArray(value)) return value;
  if (!value || typeof value !== "object") return [];
  const record = value as Record<string, unknown>;

  for (const key of ["conversations", "data", "results", "items"]) {
    const nested = record[key];
    if (Array.isArray(nested)) return nested;
    if (nested && typeof nested === "object") {
      const rows = findConversationRows(nested);
      if (rows.length) return rows;
    }
  }

  if (positiveInteger(record.conversation_id ?? record.id)) return [record];
  return [];
}

function unwrapToolPayload(value: unknown): unknown {
  if (!value || typeof value !== "object") return parseJsonText(value);
  const record = value as Record<string, unknown>;
  if (record.structuredContent != null) return record.structuredContent;
  if (Array.isArray(record.content)) {
    for (const item of record.content) {
      if (!item || typeof item !== "object") continue;
      const text = (item as any).text;
      const parsed = parseJsonText(text);
      if (parsed !== text) return parsed;
    }
  }
  return value;
}

function extractToolError(value: unknown): string | null {
  if (!value || typeof value !== "object") return null;
  const record = value as Record<string, unknown>;
  if (record.isError === true) {
    return toolText(record) || "Peach tool returned an error";
  }
  const payload = unwrapToolPayload(value);
  if (payload && typeof payload === "object" && !Array.isArray(payload)) {
    const error = (payload as any).error;
    if (error) return typeof error === "string" ? error : JSON.stringify(error).slice(0, 500);
  }
  return null;
}

function toolText(record: Record<string, unknown>): string | null {
  if (!Array.isArray(record.content)) return null;
  for (const item of record.content) {
    if (item && typeof item === "object" && typeof (item as any).text === "string") {
      const text = String((item as any).text).trim();
      if (text) return text.slice(0, 500);
    }
  }
  return null;
}

function parseJsonText(value: unknown): unknown {
  if (typeof value !== "string") return value;
  const text = value.trim();
  if (!text || (!text.startsWith("{") && !text.startsWith("["))) return value;
  try {
    return JSON.parse(text);
  } catch (_) {
    return value;
  }
}

function positiveInteger(value: unknown): number | null {
  const number = Number(value);
  return Number.isInteger(number) && number > 0 ? number : null;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error || "");
}
