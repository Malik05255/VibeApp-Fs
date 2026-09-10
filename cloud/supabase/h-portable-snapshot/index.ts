import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";
import {
  createPortableSnapshot,
  isPortableSnapshotLimitError,
} from "../h-app-sync/portable-snapshot.ts";
import { decryptRuntimeUserKey } from "../h-whatsapp-inbox/runtime-user-key.ts";

const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";

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
    const googleSubjectFingerprint = await secretFingerprint(identitySecret, GOOGLE_SUB_LABEL, google.subject);
    const linked = await linkedIdentity(
      db,
      googleSubjectFingerprint,
      google.audience,
      identitySecret,
    );
    if (!linked) return reply({ ok: false, error: "app_not_linked", linked: false }, 403);

    const snapshot = await createPortableSnapshot(db, linked.userKey);
    return reply({
      ok: true,
      linked: true,
      snapshot,
      rawRuntimeUserKeyReturned: false,
      providerCredentialsIncluded: false,
      rawMediaIncluded: false,
      restoreSupported: snapshot.restoreSupported === true,
    });
  } catch (error) {
    if (isPortableSnapshotLimitError(error)) {
      return reply({
        ok: false,
        error: "portable_snapshot_requires_pagination",
        section: error.section,
        limit: error.limit,
        partialSnapshotReturned: false,
      }, 409);
    }
    console.error("H portable snapshot failed", errorMessage(error));
    return reply({ ok: false, error: "portable_snapshot_failed" }, 500);
  }
});

async function linkedIdentity(
  db: any,
  subjectFingerprint: string,
  audience: string,
  identitySecret: string,
) {
  const { data, error } = await db.from("h_runtime_app_identities")
    .select("google_audience,runtime_user_key_ciphertext")
    .eq("google_subject_fingerprint", subjectFingerprint)
    .eq("active", true)
    .maybeSingle();
  if (error) throw error;
  if (!data?.runtime_user_key_ciphertext || data.google_audience !== audience) return null;
  return {
    userKey: await decryptRuntimeUserKey(
      String(data.runtime_user_key_ciphertext),
      identitySecret,
    ),
  };
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
  return [...new Uint8Array(signature)]
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
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
