import { assertEquals } from "jsr:@std/assert@1";
import {
  deliveryStatusForLifecycle,
  normalizeReminderId,
  normalizeReminderStatus,
  normalizeReminderUpsert,
} from "./reminder-sync.ts";

Deno.test("normalizes an app time reminder", () => {
  const value = normalizeReminderUpsert({
    reminder: {
      id: "550e8400-e29b-41d4-a716-446655440000",
      title: "موعد",
      original_text: "ذكرني بكرة",
      interpreted_text: "موعد بكرة",
      type: "TIME",
      status: "ACTIVE",
      source: "APP_CHAT",
      domain: "PERSONAL",
      scheduled_at: "2026-09-10T08:00:00+03:00",
    },
  });
  assertEquals(value?.id, "550e8400-e29b-41d4-a716-446655440000");
  assertEquals(value?.type, "TIME");
  assertEquals(value?.lifecycleStatus, "ACTIVE");
  assertEquals(value?.scheduledAt, "2026-09-10T05:00:00.000Z");
});

Deno.test("rejects time reminder without due time", () => {
  assertEquals(normalizeReminderUpsert({
    id: "550e8400-e29b-41d4-a716-446655440000",
    title: "موعد",
    original_text: "موعد",
    interpreted_text: "موعد",
    type: "TIME",
  }), null);
});

Deno.test("normalizes location reminder and bounds values", () => {
  const value = normalizeReminderUpsert({
    id: "550e8400-e29b-41d4-a716-446655440001",
    title: "شطه",
    original_text: "ذكرني إذا وصلت يارا",
    interpreted_text: "لا تنسى شطه",
    type: "LOCATION",
    location: {
      placeNameAr: "يارا",
      latitude: 18.5,
      longitude: 42.4,
      radiusMeters: 99999,
      dwellMinutes: 1,
      triggerMode: "DWELL",
    },
  });
  assertEquals(value?.location?.place_name_ar, "يارا");
  assertEquals(value?.location?.radius_meters, 5000);
});

Deno.test("validates ids and lifecycle status", () => {
  assertEquals(normalizeReminderId("550e8400-e29b-41d4-a716-446655440000"), "550e8400-e29b-41d4-a716-446655440000");
  assertEquals(normalizeReminderId("bad"), null);
  assertEquals(normalizeReminderStatus("completed"), "COMPLETED");
  assertEquals(normalizeReminderStatus("gone"), null);
});

Deno.test("delivery lifecycle never lets app reminders enter WhatsApp queue", () => {
  assertEquals(deliveryStatusForLifecycle("ACTIVE", "app", "pending"), "app_managed");
  assertEquals(deliveryStatusForLifecycle("COMPLETED", "app", "app_managed"), "app_completed");
  assertEquals(deliveryStatusForLifecycle("CANCELLED", "app", "app_managed"), "cancelled");
});
