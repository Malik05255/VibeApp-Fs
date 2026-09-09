export const REMINDER_TYPES = ["TIME", "LOCATION", "PERSON", "RECURRING", "CONTEXTUAL"] as const;
export const REMINDER_STATUSES = ["ACTIVE", "DEFERRED", "COMPLETED", "DISABLED", "CANCELLED"] as const;
export const REMINDER_SOURCES = ["APP_CHAT", "WHATSAPP", "MANUAL", "IMPORTED"] as const;
export const REMINDER_DOMAINS = ["PERSONAL", "PROGRAMMING"] as const;

export type SharedReminderInput = {
  id: string;
  title: string;
  originalText: string;
  interpretedText: string;
  type: typeof REMINDER_TYPES[number];
  lifecycleStatus: typeof REMINDER_STATUSES[number];
  source: typeof REMINDER_SOURCES[number];
  domain: typeof REMINDER_DOMAINS[number];
  scheduledAt: string | null;
  recurrenceRule: string | null;
  personName: string | null;
  location: Record<string, unknown> | null;
  cooldownUntil: string | null;
  completedAt: string | null;
  updatedAt: string;
};

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function normalizeReminderUpsert(body: any): SharedReminderInput | null {
  const source = body?.reminder && typeof body.reminder === "object" ? body.reminder : body;
  const id = clean(source?.id, 64);
  const title = clean(source?.title, 160);
  const interpretedText = clean(source?.interpreted_text ?? source?.interpretedText ?? source?.body, 4000);
  const originalText = clean(source?.original_text ?? source?.originalText ?? interpretedText, 4000);
  const type = enumValue(source?.type ?? source?.reminder_type, REMINDER_TYPES, "CONTEXTUAL");
  const lifecycleStatus = enumValue(source?.status ?? source?.lifecycle_status, REMINDER_STATUSES, "ACTIVE");
  const reminderSource = enumValue(source?.source, REMINDER_SOURCES, "APP_CHAT");
  const domain = enumValue(source?.domain, REMINDER_DOMAINS, "PERSONAL");
  const scheduledAt = optionalIso(source?.scheduled_at ?? source?.scheduledAt ?? source?.due_at);
  const cooldownUntil = optionalIso(source?.cooldown_until ?? source?.cooldownUntil);
  const completedAt = optionalIso(source?.completed_at ?? source?.completedAt);
  const updatedAt = optionalIso(source?.updated_at ?? source?.updatedAt) ?? new Date().toISOString();
  const recurrenceRule = optionalText(source?.recurrence_rule ?? source?.recurrenceRule, 300);
  const personName = optionalText(source?.person_name ?? source?.personName, 160);
  const location = normalizeLocation(source?.location);

  if (!UUID_RE.test(id) || !title || !interpretedText || !originalText) return null;
  if ((type === "TIME" || type === "RECURRING") && !scheduledAt) return null;
  if (type === "LOCATION" && !location) return null;

  return {
    id,
    title,
    originalText,
    interpretedText,
    type,
    lifecycleStatus,
    source: reminderSource,
    domain,
    scheduledAt,
    recurrenceRule,
    personName,
    location,
    cooldownUntil,
    completedAt,
    updatedAt,
  };
}

export function normalizeReminderId(value: unknown): string | null {
  const id = clean(value, 64);
  return UUID_RE.test(id) ? id : null;
}

export function normalizeReminderStatus(value: unknown): typeof REMINDER_STATUSES[number] | null {
  const normalized = String(value ?? "").trim().toUpperCase();
  return (REMINDER_STATUSES as readonly string[]).includes(normalized)
    ? normalized as typeof REMINDER_STATUSES[number]
    : null;
}

export function deliveryStatusForLifecycle(
  lifecycle: typeof REMINDER_STATUSES[number],
  deliveryChannel: string,
  currentStatus?: string | null,
): string {
  if (lifecycle === "CANCELLED") return "cancelled";
  if (lifecycle === "COMPLETED") return deliveryChannel === "app" ? "app_completed" : "cancelled";
  if (lifecycle === "DISABLED" || lifecycle === "DEFERRED") {
    return deliveryChannel === "app" ? "app_managed" : "paused";
  }
  if (deliveryChannel === "app") return "app_managed";
  if (currentStatus === "paused" || currentStatus === "cancelled") return "pending";
  return currentStatus || "pending";
}

function normalizeLocation(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const item = value as Record<string, unknown>;
  const latitude = Number(item.latitude);
  const longitude = Number(item.longitude);
  const placeNameAr = clean(item.place_name_ar ?? item.placeNameAr, 240);
  if (!placeNameAr || !Number.isFinite(latitude) || latitude < -90 || latitude > 90 || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    return null;
  }
  const radius = finiteNumber(item.radius_meters ?? item.radiusMeters, 180, 25, 5000);
  const dwell = Math.round(finiteNumber(item.dwell_minutes ?? item.dwellMinutes, 1, 0, 1440));
  const triggerRaw = String(item.trigger_mode ?? item.triggerMode ?? "DWELL").trim().toUpperCase();
  const trigger = ["ARRIVE", "DWELL", "DEPART", "NEARBY"].includes(triggerRaw) ? triggerRaw : "DWELL";
  return {
    place_name_ar: placeNameAr,
    address_ar: optionalText(item.address_ar ?? item.addressAr, 400),
    place_id: optionalText(item.place_id ?? item.placeId, 300),
    latitude,
    longitude,
    radius_meters: radius,
    dwell_minutes: dwell,
    trigger_mode: trigger,
  };
}

function clean(value: unknown, max: number): string {
  return String(value ?? "").replace(/\s+/g, " ").trim().slice(0, max);
}

function optionalText(value: unknown, max: number): string | null {
  const text = clean(value, max);
  return text || null;
}

function optionalIso(value: unknown): string | null {
  const text = String(value ?? "").trim();
  if (!text) return null;
  const parsed = Date.parse(text);
  return Number.isFinite(parsed) ? new Date(parsed).toISOString() : null;
}

function enumValue<T extends readonly string[]>(value: unknown, values: T, fallback: T[number]): T[number] {
  const normalized = String(value ?? "").trim().toUpperCase();
  return values.includes(normalized as T[number]) ? normalized as T[number] : fallback;
}

function finiteNumber(value: unknown, fallback: number, min: number, max: number): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, parsed));
}
