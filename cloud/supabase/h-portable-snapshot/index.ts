import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";
import {
  buildPortableV3Manifest,
  buildPortableV3PageEnvelope,
  parsePortableV3PageRequest,
  parsePortableV3PageSize,
  type PortableV3Section,
} from "../h-app-sync/portable-page.ts";
import {
  createPortableSnapshot,
  isPortableSnapshotLimitError,
} from "../h-app-sync/portable-snapshot.ts";
import { decryptRuntimeUserKey } from "../h-whatsapp-inbox/runtime-user-key.ts";

const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
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
    const mode = String(body?.mode || "snapshot").trim().toLowerCase();

    if (mode === "begin_v3" || mode === "begin") {
      const pageSize = parsePortableV3PageSize(body);
      const { data, error } = await db.rpc("h_prepare_portable_export_v3", {
        p_user_key: linked.userKey,
        p_page_size: pageSize,
      });
      if (error) throw error;
      return reply({
        ok: true,
        linked: true,
        pagedExport: true,
        portableProtocolVersion: 3,
        session: data,
        rawRuntimeUserKeyReturned: false,
        providerCredentialsIncluded: false,
        rawMediaIncluded: false,
      });
    }

    if (mode === "page_v3" || mode === "page") {
      const parsed = parsePortableV3PageRequest(body);
      const { data, error } = await db.rpc("h_read_portable_export_v3_page", {
        p_user_key: linked.userKey,
        p_session_id: parsed.sessionId,
        p_section: parsed.section,
        p_page_index: parsed.pageIndex,
      });
      if (error) throw error;
      if (data?.found !== true) {
        return reply({ ok: false, error: "portable_v3_page_not_found" }, 404);
      }
      const page = await buildPortableV3PageEnvelope({
        sessionId: String(data.sessionId),
        section: String(data.section) as PortableV3Section,
        pageIndex: Number(data.pageIndex),
        items: Array.isArray(data.items) ? data.items : [],
        counts: data.counts,
        expiresAt: String(data.expiresAt),
      });
      return reply({
        ok: true,
        linked: true,
        pagedExport: true,
        portableProtocolVersion: 3,
        page,
        rawRuntimeUserKeyReturned: false,
        providerCredentialsIncluded: false,
        rawMediaIncluded: false,
      });
    }

    if (mode === "manifest_v3" || mode === "manifest") {
      const sessionId = validSessionId(body?.session_id ?? body?.sessionId);
      const { data: session, error: sessionError } = await db.from("h_runtime_portable_export_sessions")
        .select("id,counts,created_at,expires_at")
        .eq("id", sessionId)
        .eq("user_key", linked.userKey)
        .maybeSingle();
      if (sessionError) throw sessionError;
      if (!session) return reply({ ok: false, error: "portable_v3_session_not_found" }, 404);
      if (Date.parse(String(session.expires_at)) <= Date.now()) {
        return reply({ ok: false, error: "portable_v3_session_expired" }, 410);
      }

      const { data: storedPages, error: pagesError } = await db.from("h_runtime_portable_export_pages")
        .select("section,page_index,item_count,items")
        .eq("session_id", sessionId)
        .order("section", { ascending: true })
        .order("page_index", { ascending: true });
      if (pagesError) throw pagesError;

      const pages = [];
      for (const stored of storedPages ?? []) {
        pages.push(await buildPortableV3PageEnvelope({
          sessionId,
          section: String(stored.section) as PortableV3Section,
          pageIndex: Number(stored.page_index),
          items: Array.isArray(stored.items) ? stored.items : [],
          counts: session.counts,
          expiresAt: String(session.expires_at),
        }));
      }
      const manifest = await buildPortableV3Manifest({
        sessionId,
        pages,
        counts: session.counts,
        createdAt: String(session.created_at),
        expiresAt: String(session.expires_at),
        restoreSupported: true,
      });
      return reply({
        ok: true,
        linked: true,
        pagedExport: true,
        portableProtocolVersion: 3,
        manifest,
        rawRuntimeUserKeyReturned: false,
        providerCredentialsIncluded: false,
        rawMediaIncluded: false,
      });
    }

    if (mode !== "snapshot" && mode !== "full") {
      return reply({ ok: false, error: "portable_snapshot_mode_invalid" }, 400);
    }

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
    const code = errorMessage(error);
    if (code.startsWith("portable_v3_") || code.startsWith("portable_export_")) {
      return reply({ ok: false, error: compactErrorCode(code) }, 400);
    }
    if (isPortableSnapshotLimitError(error)) {
      return reply({
        ok: false,
        error: "portable_snapshot_requires_pagination",
        section: error.section,
        limit: error.limit,
        partialSnapshotReturned: false,
        pagedExportAvailable: true,
        portableProtocolVersion: 3,
      }, 409);
    }
    console.error("H portable snapshot failed", compactErrorCode(code));
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
    userKey: await decryptRuntimeUserKey(String(data.runtime_user_key_ciphertext), identitySecret),
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

function validSessionId(value: unknown): string {
  const id = String(value || "").trim().toLowerCase();
  if (!UUID.test(id)) throw new Error("portable_v3_session_invalid");
  return id;
}

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function compactErrorCode(value: string): string {
  const compact = String(value || "unknown_error")
    .toLowerCase()
    .replace(/[^a-z0-9_:-]+/g, "_")
    .slice(0, 120);
  return compact || "unknown_error";
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
