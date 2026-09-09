import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { normalizeWaIdCandidate } from "../h-whatsapp-inbox/contact-manager.ts";
import { ownerFingerprint } from "../h-whatsapp-inbox/owner-identity.ts";
import { createOwnerPairingChallenge, pairingCodeFingerprint } from "../h-whatsapp-inbox/owner-pairing.ts";
import { verifyGoogleIdToken } from "./google-id-token.ts";
import { normalizeSharedMemoryInput } from "./shared-memory-policy.ts";

const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const USER_KEY_ENCRYPTION_LABEL = "h-app-runtime-user-key-v1";
const MAX_PAIRING_AGE_MS = 20 * 60_000;

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

  const runtimeSecret = await loadRuntimeSecret(db).catch(() => "");
  if (!runtimeSecret) return json({ ok: false, error: "runtime_unavailable" }, 500);
  const googleSubjectFingerprint = await secretFingerprint(runtimeSecret, GOOGLE_SUB_LABEL, google.subject);

  const body = await req.json().catch(() => ({}));
  const action = String(body?.action || "status").trim().toLowerCase();

  try {
    if (action === "create_pairing") {
      const challenge = await createOwnerPairingChallenge(db, runtimeSecret);
      const codeFingerprint = await pairingCodeFingerprint(challenge.code, runtimeSecret);
      const { error } = await db.from("h_runtime_owner_pairing").update({
        google_subject_fingerprint: googleSubjectFingerprint,
        google_audience: google.audience,
      }).eq("code_fingerprint", codeFingerprint).is("consumed_at", null);
      if (error) throw error;
      return json({
        ok: true,
        linked: false,
        pairingCode: challenge.code,
        expiresAt: challenge.expiresAt,
        whatsappCommand: `اربطني كمالك ${challenge.code}`,
        next: "send_command_from_owner_whatsapp_then_finalize",
        rawGoogleSubjectStored: false,
        rawWaIdStoredInAppIdentity: false,
      });
    }

    if (action === "finalize_pairing") {
      const code = String(body?.pairing_code || "").trim();
      const waId = normalizeWaIdCandidate(body?.wa_id);
      if (!/^\d{8}$/.test(code) || !waId) return json({ ok: false, error: "invalid_pairing_input" }, 400);

      const codeFingerprint = await pairingCodeFingerprint(code, runtimeSecret);
      const { data: pairing, error: pairingError } = await db.from("h_runtime_owner_pairing")
        .select("consumed_at,created_at,google_audience,consumed_wa_fingerprint")
        .eq("code_fingerprint", codeFingerprint)
        .eq("google_subject_fingerprint", googleSubjectFingerprint)
        .eq("google_audience", google.audience)
        .not("consumed_at", "is", null)
        .maybeSingle();
      if (pairingError) throw pairingError;
      if (!pairing?.consumed_at) return json({ ok: false, error: "pairing_not_confirmed_on_whatsapp" }, 409);
      const createdAtMs = Date.parse(String(pairing.created_at || ""));
      if (!Number.isFinite(createdAtMs) || Date.now() - createdAtMs > MAX_PAIRING_AGE_MS) {
        return json({ ok: false, error: "pairing_expired" }, 409);
      }

      const waFingerprint = await ownerFingerprint(waId, runtimeSecret);
      if (!waFingerprint) return json({ ok: false, error: "invalid_wa_id" }, 400);
      if (String(pairing.consumed_wa_fingerprint || "") !== waFingerprint) {
        return json({ ok: false, error: "pairing_owner_mismatch" }, 403);
      }

      const encryptedUserKey = await encryptRuntimeUserKey(waId, runtimeSecret);
      const now = new Date().toISOString();
      const { error: identityError } = await db.from("h_runtime_app_identities").upsert({
        google_subject_fingerprint: googleSubjectFingerprint,
        google_audience: google.audience,
        runtime_user_key_ciphertext: encryptedUserKey,
        active: true,
        linked_at: now,
        updated_at: now,
      }, { onConflict: "google_subject_fingerprint" });
      if (identityError) throw identityError;
      const { error: markError } = await db.from("h_runtime_owner_pairing")
        .update({ app_linked_at: now })
        .eq("code_fingerprint", codeFingerprint);
      if (markError) throw markError;

      return json({
        ok: true,
        linked: true,
        sameRuntimeAsWhatsApp: true,
        rawGoogleSubjectStored: false,
        rawWaIdStoredInAppIdentity: false,
      });
    }

    const linked = await linkedIdentity(db, googleSubjectFingerprint, google.audience, runtimeSecret);
    if (action === "status") {
      return json({
        ok: true,
        linked: Boolean(linked),
        sameRuntimeAsWhatsApp: Boolean(linked),
      });
    }

    if (action === "remember") {
      if (!linked) return json({ ok: false, error: "app_not_linked", linked: false }, 403);
      const memory = normalizeSharedMemoryInput(body);
      if (!memory) return json({ ok: false, error: "memory_rejected" }, 400);

      const now = new Date().toISOString();
      const { data: existing, error: existingError } = await db.from("h_runtime_memories")
        .select("id,category,body,original_text,created_at,updated_at")
        .eq("user_key", linked.userKey)
        .eq("body", memory.text)
        .order("updated_at", { ascending: false })
        .limit(1)
        .maybeSingle();
      if (existingError) throw existingError;

      if (existing?.id) {
        const { data: updated, error: updateError } = await db.from("h_runtime_memories")
          .update({
            category: memory.category,
            original_text: memory.originalText ?? existing.original_text,
            updated_at: now,
          })
          .eq("id", existing.id)
          .eq("user_key", linked.userKey)
          .select("id,category,body,original_text,created_at,updated_at")
          .single();
        if (updateError) throw updateError;
        return json({ ok: true, linked: true, saved: true, duplicate: true, memory: updated });
      }

      const { data: inserted, error: insertError } = await db.from("h_runtime_memories")
        .insert({
          user_key: linked.userKey,
          category: memory.category,
          body: memory.text,
          original_text: memory.originalText,
          created_at: now,
          updated_at: now,
        })
        .select("id,category,body,original_text,created_at,updated_at")
        .single();
      if (insertError) throw insertError;
      return json({ ok: true, linked: true, saved: true, duplicate: false, memory: inserted });
    }

    if (action === "snapshot") {
      if (!linked) return json({ ok: false, error: "app_not_linked", linked: false }, 403);
      const userKey = linked.userKey;
      const [memories, tasks, reminders] = await Promise.all([
        db.from("h_runtime_memories")
          .select("id,category,body,original_text,created_at,updated_at")
          .eq("user_key", userKey)
          .order("updated_at", { ascending: false })
          .limit(100),
        db.from("h_runtime_tasks")
          .select("id,title,body,task_type,priority,status,due_at,metadata,created_at,updated_at")
          .eq("user_key", userKey)
          .in("status", ["active", "paused"])
          .order("updated_at", { ascending: false })
          .limit(100),
        db.from("h_runtime_reminders")
          .select("id,body,due_at,status,priority_class,task_id,created_at,updated_at")
          .eq("user_key", userKey)
          .in("status", ["pending", "waiting_template"])
          .order("due_at", { ascending: true })
          .limit(100),
      ]);
      if (memories.error || tasks.error || reminders.error) throw memories.error || tasks.error || reminders.error;
      return json({
        ok: true,
        linked: true,
        memories: memories.data ?? [],
        tasks: tasks.data ?? [],
        reminders: reminders.data ?? [],
      });
    }

    return json({ ok: false, error: "unsupported_action" }, 400);
  } catch (error) {
    console.error("H app sync failed", action, errorMessage(error));
    return json({ ok: false, error: "app_sync_failed" }, 500);
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
    .select("secret_value")
    .eq("key", "poll_secret")
    .maybeSingle();
  if (error) throw error;
  const value = String(data?.secret_value || "").trim();
  if (!value) throw new Error("runtime_secret_missing");
  return value;
}

async function secretFingerprint(secret: string, label: string, value: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${label}:${value}`),
  );
  return [...new Uint8Array(signature)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function encryptionKey(secret: string): Promise<CryptoKey> {
  const material = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(`${USER_KEY_ENCRYPTION_LABEL}:${secret}`),
  );
  return crypto.subtle.importKey("raw", material, "AES-GCM", false, ["encrypt", "decrypt"]);
}

async function encryptRuntimeUserKey(userKey: string, secret: string): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    await encryptionKey(secret),
    new TextEncoder().encode(userKey),
  );
  return `${base64Url(iv)}.${base64Url(new Uint8Array(encrypted))}`;
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
  if (!normalizeWaIdCandidate(value)) throw new Error("invalid_app_identity_user_key");
  return value;
}

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlDecode(value: string): Uint8Array<ArrayBuffer> {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error || "unknown_error");
}

function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
