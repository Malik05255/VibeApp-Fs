import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";
import {
  buildPortablePageEnvelope,
  encodePortablePageCursor,
  parsePortablePageRequest,
  rawRowCursor,
  sanitizePortablePageRows,
  type PortablePageSection,
} from "../h-app-sync/portable-page.ts";
import {
  createPortableSnapshot,
  isPortableSnapshotLimitError,
} from "../h-app-sync/portable-snapshot.ts";
import { decryptRuntimeUserKey } from "../h-whatsapp-inbox/runtime-user-key.ts";

const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";

const PAGE_SECTION_CONFIG: Record<PortablePageSection, { table: string; select: string }> = {
  memories: {
    table: "h_runtime_memories",
    select: "id,category,body,original_text,created_at,updated_at",
  },
  tasks: {
    table: "h_runtime_tasks",
    select: "id,title,body,task_type,priority,status,due_at,paused_at,completed_at,cancelled_at,created_at,updated_at",
  },
  reminders: {
    table: "h_runtime_reminders",
    select: "id,title,body,original_text,interpreted_text,due_at,status,priority_class,task_id,reminder_type,lifecycle_status,domain,recurrence_rule,person_name,location,cooldown_until,completed_at,delivery_channel,created_at,updated_at",
  },
  contacts: {
    table: "h_runtime_contacts",
    select: "id,name_key,display_name,target_wa_id,created_at,updated_at",
  },
};

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

    const body = await req.json().catch(() => ({}));
    const mode = String(body?.mode || "snapshot").trim().toLowerCase();

    if (mode === "page") {
      const pageRequest = parsePortablePageRequest(body);
      const page = await createPortablePage(db, linked.userKey, pageRequest);
      return reply({
        ok: true,
        linked: true,
        pagedExport: true,
        portableProtocolVersion: 3,
        page,
        rawRuntimeUserKeyReturned: false,
        providerCredentialsIncluded: false,
        rawMediaIncluded: false,
        restoreSupportedDirectly: false,
        legacySnapshotUnaffected: true,
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
    if (code.startsWith("portable_page_")) {
      return reply({ ok: false, error: code }, 400);
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
    console.error("H portable snapshot failed", code);
    return reply({ ok: false, error: "portable_snapshot_failed" }, 500);
  }
});

async function createPortablePage(
  db: any,
  userKey: string,
  request: ReturnType<typeof parsePortablePageRequest>,
) {
  const config = PAGE_SECTION_CONFIG[request.section];
  let query = db.from(config.table)
    .select(config.select)
    .eq("user_key", userKey)
    .order("created_at", { ascending: true })
    .order("id", { ascending: true })
    .limit(request.limit + 1);

  if (request.cursor) {
    const createdAt = request.cursor.createdAt;
    const id = request.cursor.id;
    query = query.or(`created_at.gt.${createdAt},and(created_at.eq.${createdAt},id.gt.${id})`);
  }

  const { data, error } = await query;
  if (error) throw error;
  const rawRows = Array.isArray(data) ? data : [];
  const hasMore = rawRows.length > request.limit;
  const boundedRows = rawRows.slice(0, request.limit);
  const items = await sanitizePortablePageRows(request.section, boundedRows);
  const nextCursor = hasMore && boundedRows.length
    ? encodePortablePageCursor(rawRowCursor(boundedRows[boundedRows.length - 1]))
    : null;
  const startCursor = request.cursor ? encodePortablePageCursor(request.cursor) : null;

  return buildPortablePageEnvelope({
    section: request.section,
    items,
    startCursor,
    nextCursor,
    hasMore,
  });
}

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
