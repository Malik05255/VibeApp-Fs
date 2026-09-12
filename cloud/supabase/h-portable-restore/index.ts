import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";
import {
  isPortableRestoreValidationError,
  validatePortableRestoreSnapshot,
} from "../h-app-sync/portable-restore.ts";
import {
  type PortableV3RestoreManifest,
  validatePortableV3RestoreManifest,
  validatePortableV3RestorePage,
} from "../h-app-sync/portable-v3-restore.ts";
import { decryptRuntimeUserKey } from "../h-whatsapp-inbox/runtime-user-key.ts";

const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const RESTORE_CONFIRMATION = "RESTORE_H_PORTABLE_V1";
const RESTORE_V3_CONFIRMATION = "RESTORE_H_PORTABLE_V3";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

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
    const linked = await linkedIdentity(db, googleSubjectFingerprint, google.audience, identitySecret);
    if (!linked) return reply({ ok: false, error: "app_not_linked", linked: false }, 403);

    const body = await req.json().catch(() => ({}));
    const mode = String(body?.mode || "validate").trim().toLowerCase();

    if (mode === "begin_v3") {
      const manifest = await validatePortableV3RestoreManifest(body?.manifest);
      const { data, error } = await db.rpc("h_begin_portable_restore_v3", {
        p_user_key: linked.userKey,
        p_export_session_id: manifest.sessionId,
        p_manifest_digest: manifest.digest,
        p_counts: manifest.counts,
        p_manifest_pages: manifest.pages,
      });
      if (error) throw error;
      return reply({
        ok: true,
        linked: true,
        valid: true,
        restoreReady: false,
        schemaVersion: 3,
        importSession: data,
        counts: manifest.counts,
        pagesExpected: manifest.pages.length,
        mergeOnly: true,
        confirmationRequired: RESTORE_V3_CONFIRMATION,
      });
    }

    if (mode === "page_v3") {
      const importSessionId = uuidValue(body?.import_session_id ?? body?.importSessionId, "portable_v3_import_session_invalid");
      const { data: session, error: sessionError } = await db.from("h_runtime_portable_import_sessions")
        .select("export_session_id,manifest_digest,counts,manifest_pages,created_at,expires_at,status")
        .eq("id", importSessionId)
        .eq("user_key", linked.userKey)
        .maybeSingle();
      if (sessionError) throw sessionError;
      if (!session) return reply({ ok: false, error: "portable_v3_import_session_not_found" }, 404);
      if (session.status !== "staging") return reply({ ok: false, error: "portable_v3_import_session_closed" }, 409);
      if (Date.parse(String(session.expires_at)) <= Date.now()) {
        return reply({ ok: false, error: "portable_v3_import_session_expired" }, 410);
      }

      const manifest: PortableV3RestoreManifest = {
        sessionId: String(session.export_session_id),
        createdAt: String(session.created_at),
        expiresAt: String(session.expires_at),
        counts: session.counts as Record<string, number>,
        pages: Array.isArray(session.manifest_pages) ? session.manifest_pages : [],
        digest: String(session.manifest_digest),
      };
      const page = await validatePortableV3RestorePage(body?.page, manifest);
      const { data, error } = await db.rpc("h_stage_portable_restore_v3_page", {
        p_user_key: linked.userKey,
        p_import_session_id: importSessionId,
        p_section: page.section,
        p_page_index: page.pageIndex,
        p_page_digest: page.digest,
        p_items: page.items,
      });
      if (error) throw error;
      return reply({
        ok: true,
        linked: true,
        schemaVersion: 3,
        staged: true,
        section: page.section,
        pageIndex: page.pageIndex,
        result: data,
      });
    }

    if (mode === "restore_v3") {
      const importSessionId = uuidValue(body?.import_session_id ?? body?.importSessionId, "portable_v3_import_session_invalid");
      if (String(body?.confirmation || "") !== RESTORE_V3_CONFIRMATION) {
        return reply({
          ok: false,
          error: "portable_restore_confirmation_required",
          confirmationRequired: RESTORE_V3_CONFIRMATION,
        }, 409);
      }
      const { data, error } = await db.rpc("h_restore_portable_snapshot_v3", {
        p_user_key: linked.userKey,
        p_import_session_id: importSessionId,
      });
      if (error) throw error;
      return restoreReply(3, data);
    }

    if (mode !== "validate" && mode !== "restore") {
      return reply({ ok: false, error: "portable_restore_mode_invalid" }, 400);
    }

    // Backward-compatible v1/v2 path remains byte-for-byte compatible at the API level.
    const plan = await validatePortableRestoreSnapshot(body?.snapshot);
    if (mode === "validate") {
      return reply({
        ok: true,
        valid: true,
        linked: true,
        restoreReady: true,
        schemaVersion: plan.schemaVersion,
        snapshotDigest: plan.digest,
        counts: plan.counts,
        mergeOnly: true,
        deletesExistingState: false,
        providerCredentialsImported: false,
        routingIdentityImported: false,
        rawMediaImported: false,
        confirmationRequired: RESTORE_CONFIRMATION,
      });
    }

    if (String(body?.confirmation || "") !== RESTORE_CONFIRMATION) {
      return reply({
        ok: false,
        error: "portable_restore_confirmation_required",
        confirmationRequired: RESTORE_CONFIRMATION,
      }, 409);
    }

    const rpcName = plan.schemaVersion === 2
      ? "h_restore_portable_snapshot_v2"
      : "h_restore_portable_snapshot_v1";
    const { data, error } = await db.rpc(rpcName, {
      p_user_key: linked.userKey,
      p_snapshot_digest: plan.digest,
      p_payload: plan.payload,
    });
    if (error) throw error;
    return restoreReply(plan.schemaVersion, data);
  } catch (error) {
    if (isPortableRestoreValidationError(error)) {
      const integrityFailure = error.code === "portable_snapshot_integrity_failed";
      return reply({ ok: false, error: error.code }, integrityFailure ? 409 : 400);
    }
    const code = compactErrorCode(errorMessage(error));
    if (code.startsWith("portable_v3_")) {
      const conflict = code.includes("integrity") || code.includes("mismatch") || code.includes("incomplete") || code.includes("conflict");
      return reply({ ok: false, error: code }, conflict ? 409 : 400);
    }
    console.error("H portable restore failed", code);
    return reply({ ok: false, error: "portable_restore_failed" }, 500);
  }
});

function restoreReply(schemaVersion: number, data: unknown) {
  return reply({
    ok: true,
    linked: true,
    restored: true,
    schemaVersion,
    result: data,
    rawRuntimeUserKeyReturned: false,
    snapshotStoredInLedger: false,
    mergeOnly: true,
    deletedExistingState: false,
    providerCredentialsImported: false,
    routingIdentityImported: false,
    rawMediaImported: false,
  });
}

async function linkedIdentity(db: any, subjectFingerprint: string, audience: string, identitySecret: string) {
  const { data, error } = await db.from("h_runtime_app_identities")
    .select("google_audience,runtime_user_key_ciphertext")
    .eq("google_subject_fingerprint", subjectFingerprint)
    .eq("active", true)
    .maybeSingle();
  if (error) throw error;
  if (!data?.runtime_user_key_ciphertext || data.google_audience !== audience) return null;
  return { userKey: await decryptRuntimeUserKey(String(data.runtime_user_key_ciphertext), identitySecret) };
}

async function secretFingerprint(secret: string, label: string, value: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${label}:${value}`));
  return [...new Uint8Array(signature)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function uuidValue(value: unknown, code: string): string {
  const id = String(value || "").trim().toLowerCase();
  if (!UUID.test(id)) throw new Error(code);
  return id;
}

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function compactErrorCode(value: string): string {
  return String(value || "unknown_error").toLowerCase().replace(/[^a-z0-9_:-]+/g, "_").slice(0, 120) || "unknown_error";
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error || "unknown_error");
}

function reply(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}
