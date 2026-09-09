import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";
import {
  deliveryStatusForLifecycle,
  normalizeReminderId,
  normalizeReminderStatus,
  normalizeReminderUpsert,
} from "../h-app-sync/reminder-sync.ts";

const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const USER_KEY_ENCRYPTION_LABEL = "h-app-runtime-user-key-v1";

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ ok: false, error: "method_not_allowed" }, 405);
  const bearer = bearerToken(req.headers.get("authorization"));
  if (!bearer) return json({ ok: false, error: "google_sign_in_required" }, 401);

  let google;
  try {
    google = await verifyGoogleIdToken(bearer);
  } catch (error) {
    return json({ ok: false, error: errorMessage(error) }, 401);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRole) return json({ ok: false, error: "runtime_unavailable" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });

  try {
    const runtimeSecret = await loadRuntimeSecret(db);
    const subjectFingerprint = await secretFingerprint(runtimeSecret, GOOGLE_SUB_LABEL, google.subject);
    const linked = await linkedIdentity(db, subjectFingerprint, google.audience, runtimeSecret);
    if (!linked) return json({ ok: false, error: "app_not_linked", linked: false }, 403);

    const body = await req.json().catch(() => ({}));
    const action = String(body?.action || "pull").trim().toLowerCase();
    const userKey = linked.userKey;

    if (action === "pull") {
      const { data, error } = await db.from("h_runtime_reminders")
        .select("id,title,original_text,interpreted_text,body,reminder_type,lifecycle_status,source,domain,due_at,recurrence_rule,person_name,location,cooldown_until,completed_at,delivery_channel,status,created_at,updated_at")
        .eq("user_key", userKey)
        .order("updated_at", { ascending: false })
        .limit(300);
      if (error) throw error;
      return json({ ok: true, linked: true, reminders: data ?? [] });
    }

    if (action === "upsert") {
      const reminder = normalizeReminderUpsert(body);
      if (!reminder) return json({ ok: false, error: "invalid_reminder" }, 400);

      const { data: collision, error: collisionError } = await db.from("h_runtime_reminders")
        .select("id,user_key,delivery_channel,status,created_at")
        .eq("id", reminder.id)
        .maybeSingle();
      if (collisionError) throw collisionError;
      if (collision && collision.user_key !== userKey) return json({ ok: false, error: "reminder_id_conflict" }, 409);

      const deliveryChannel = String(collision?.delivery_channel || "app") === "whatsapp" ? "whatsapp" : "app";
      const deliveryStatus = deliveryStatusForLifecycle(
        reminder.lifecycleStatus,
        deliveryChannel,
        collision?.status || null,
      );
      const now = new Date().toISOString();
      const row = {
        id: reminder.id,
        user_key: userKey,
        conversation_id: collision ? undefined : null,
        title: reminder.title,
        body: reminder.interpretedText,
        original_text: reminder.originalText,
        interpreted_text: reminder.interpretedText,
        reminder_type: reminder.type,
        lifecycle_status: reminder.lifecycleStatus,
        source: reminder.source,
        domain: reminder.domain,
        due_at: reminder.scheduledAt,
        recurrence_rule: reminder.recurrenceRule,
        person_name: reminder.personName,
        location: reminder.location,
        cooldown_until: reminder.cooldownUntil,
        completed_at: reminder.completedAt,
        delivery_channel: deliveryChannel,
        status: deliveryStatus,
        updated_at: now,
      } as Record<string, unknown>;
      if (collision) delete row.conversation_id;

      const query = collision
        ? db.from("h_runtime_reminders").update(row).eq("id", reminder.id).eq("user_key", userKey)
        : db.from("h_runtime_reminders").insert(row);
      const { data, error } = await query
        .select("id,title,original_text,interpreted_text,body,reminder_type,lifecycle_status,source,domain,due_at,recurrence_rule,person_name,location,cooldown_until,completed_at,delivery_channel,status,created_at,updated_at")
        .single();
      if (error) throw error;
      return json({ ok: true, linked: true, reminder: data });
    }

    if (action === "set_status") {
      const id = normalizeReminderId(body?.id);
      const lifecycleStatus = normalizeReminderStatus(body?.status);
      if (!id || !lifecycleStatus) return json({ ok: false, error: "invalid_reminder_status" }, 400);
      const { data: existing, error: findError } = await db.from("h_runtime_reminders")
        .select("id,delivery_channel,status")
        .eq("id", id).eq("user_key", userKey).maybeSingle();
      if (findError) throw findError;
      if (!existing) return json({ ok: false, error: "reminder_not_found" }, 404);
      const deliveryChannel = String(existing.delivery_channel || "whatsapp");
      const now = new Date().toISOString();
      const patch: Record<string, unknown> = {
        lifecycle_status: lifecycleStatus,
        status: deliveryStatusForLifecycle(lifecycleStatus, deliveryChannel, existing.status),
        updated_at: now,
      };
      if (lifecycleStatus === "COMPLETED") patch.completed_at = now;
      if (lifecycleStatus === "ACTIVE") patch.completed_at = null;
      const { data, error } = await db.from("h_runtime_reminders").update(patch)
        .eq("id", id).eq("user_key", userKey)
        .select("id,lifecycle_status,status,updated_at,completed_at").single();
      if (error) throw error;
      return json({ ok: true, linked: true, reminder: data });
    }

    if (action === "delete") {
      const id = normalizeReminderId(body?.id);
      if (!id) return json({ ok: false, error: "invalid_reminder_id" }, 400);
      const now = new Date().toISOString();
      const { data, error } = await db.from("h_runtime_reminders").update({
        lifecycle_status: "CANCELLED",
        status: "cancelled",
        updated_at: now,
      }).eq("id", id).eq("user_key", userKey)
        .select("id,lifecycle_status,status,updated_at").maybeSingle();
      if (error) throw error;
      return json({ ok: true, linked: true, deleted: Boolean(data), reminder: data ?? null });
    }

    return json({ ok: false, error: "unsupported_action" }, 400);
  } catch (error) {
    console.error("H reminder sync failed", errorMessage(error));
    return json({ ok: false, error: "reminder_sync_failed" }, 500);
  }
});

async function linkedIdentity(db: any, subjectFingerprint: string, audience: string, runtimeSecret: string) {
  const { data, error } = await db.from("h_runtime_app_identities")
    .select("google_audience,runtime_user_key_ciphertext")
    .eq("google_subject_fingerprint", subjectFingerprint)
    .eq("active", true)
    .maybeSingle();
  if (error) throw error;
  if (!data?.runtime_user_key_ciphertext || data.google_audience !== audience) return null;
  return { userKey: await decryptRuntimeUserKey(String(data.runtime_user_key_ciphertext), runtimeSecret) };
}

async function loadRuntimeSecret(db: any): Promise<string> {
  const { data, error } = await db.from("h_runtime_config")
    .select("secret_value").eq("key", "poll_secret").maybeSingle();
  if (error) throw error;
  const value = String(data?.secret_value || "").trim();
  if (!value) throw new Error("runtime_secret_missing");
  return value;
}

async function secretFingerprint(secret: string, label: string, value: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "HMAC", key, new TextEncoder().encode(`${label}:${value}`),
  );
  return [...new Uint8Array(signature)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function encryptionKey(secret: string): Promise<CryptoKey> {
  const material = await crypto.subtle.digest(
    "SHA-256", new TextEncoder().encode(`${USER_KEY_ENCRYPTION_LABEL}:${secret}`),
  );
  return crypto.subtle.importKey("raw", material, "AES-GCM", false, ["decrypt"]);
}

async function decryptRuntimeUserKey(ciphertext: string, secret: string): Promise<string> {
  const [ivText, dataText] = String(ciphertext || "").split(".");
  if (!ivText || !dataText) throw new Error("invalid_app_identity_ciphertext");
  const decrypted = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: base64UrlDecode(ivText) },
    await encryptionKey(secret),
    base64UrlDecode(dataText),
  );
  const value = new TextDecoder().decode(decrypted).trim();
  if (!/^\+?\d{8,20}$/.test(value)) throw new Error("invalid_app_identity_user_key");
  return value;
}

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function base64UrlDecode(value: string): Uint8Array<ArrayBuffer> {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error || "unknown_error");
}

function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}
