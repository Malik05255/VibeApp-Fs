import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";
import { ownerContinuityHandle } from "../h-app-sync/owner-continuity.ts";
import { decryptRuntimeUserKey } from "../h-whatsapp-inbox/runtime-user-key.ts";

const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const FUNCTION_NAME = "h-owner-continuity";

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);

  const bearer = bearerToken(req.headers.get("authorization"));
  if (!bearer) return reply({ ok: false, error: "google_sign_in_required" }, 401);

  let google;
  try {
    google = await verifyGoogleIdToken(bearer);
  } catch (error) {
    return reply({ ok: false, error: errorMessage(error) }, 401);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim() || "";
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim() || "";
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });

  try {
    const identitySecret = await loadIdentitySecret(db);
    const subjectFingerprint = await secretFingerprint(identitySecret, GOOGLE_SUB_LABEL, google.subject);
    const { data, error } = await db.from("h_runtime_app_identities")
      .select("google_audience,runtime_user_key_ciphertext")
      .eq("google_subject_fingerprint", subjectFingerprint)
      .eq("active", true)
      .maybeSingle();
    if (error) throw error;
    if (!data?.runtime_user_key_ciphertext || data.google_audience !== google.audience) {
      return reply({
        ok: true,
        service: FUNCTION_NAME,
        linked: false,
        resumeExistingH: false,
        continuityHandle: null,
        pairingRequired: true,
        rawRuntimeUserKeyReturned: false,
        rawWhatsAppOwnerReturned: false,
        rawGoogleSubjectReturned: false,
      });
    }

    const runtimeUserKey = await decryptRuntimeUserKey(
      String(data.runtime_user_key_ciphertext),
      identitySecret,
    );
    const continuityHandle = await ownerContinuityHandle(identitySecret, runtimeUserKey);

    return reply({
      ok: true,
      service: FUNCTION_NAME,
      linked: true,
      resumeExistingH: true,
      continuityHandle,
      continuityVersion: "h_owner_continuity_v1",
      sameRuntimeAsWhatsApp: true,
      pairingRequired: false,
      portableSnapshotAvailable: true,
      rawRuntimeUserKeyReturned: false,
      rawWhatsAppOwnerReturned: false,
      rawGoogleSubjectReturned: false,
      providerCredentialsReturned: false,
      rawMediaReturned: false,
    });
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactError(error));
    return reply({ ok: false, error: "owner_continuity_failed" }, 500);
  }
});

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
  return [...new Uint8Array(signature)]
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function compactError(error: unknown): string {
  return errorMessage(error)
    .toLowerCase()
    .replace(/[^a-z0-9_:-]+/g, "_")
    .slice(0, 120) || "owner_continuity_failed";
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error || "unknown_error");
}

function reply(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
