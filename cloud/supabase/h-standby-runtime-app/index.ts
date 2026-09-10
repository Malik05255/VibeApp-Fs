import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";

const FUNCTION_NAME = "h-standby-runtime-app";
const CONFIG_FUNCTION = "h-standby-runtime-config";
const BACKUP_CLOUD_ID = "h_backup_supabase_storage";
const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const SETUP_TTL_MS = 10 * 60 * 1000;

type DbClient = any;

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);

  const bearer = bearerToken(req.headers.get("authorization"));
  if (!bearer) return reply({ ok: false, error: "google_sign_in_required" }, 401);

  let google;
  try {
    google = await verifyGoogleIdToken(bearer);
  } catch (error) {
    return reply({ ok: false, error: compactErrorCode(error) }, 401);
  }

  const supabaseUrl = String(Deno.env.get("SUPABASE_URL") || "").replace(/\/$/, "");
  const serviceRole = String(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "").trim();
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });

  try {
    const runtimeSecret = await loadRuntimeSecret(db);
    const fingerprint = await secretFingerprint(runtimeSecret, GOOGLE_SUB_LABEL, google.subject);
    if (!await isLinkedOwner(db, fingerprint, google.audience)) {
      return reply({ ok: false, error: "app_not_linked", linked: false }, 403);
    }

    const body = await req.json().catch(() => ({}));
    const action = String(body?.action || "create_setup_link").trim().toLowerCase();
    if (action !== "create_setup_link") return reply({ ok: false, error: "unsupported_action" }, 400);

    const backup = await loadReadyBackup(db);
    if (!backup) return reply({ ok: false, error: "backup_cloud_not_ready" }, 409);

    await invalidatePendingSetup(db);
    const rawToken = randomUrlSafe(32);
    const tokenHash = await setupTokenHash(rawToken);
    const expiresAt = new Date(Date.now() + SETUP_TTL_MS).toISOString();
    const { error: insertError } = await db.from("h_runtime_cloud_setup").insert({
      token_hash: tokenHash,
      provider: "supabase",
      expires_at: expiresAt,
      metadata: {
        requested_by: "android_owner",
        purpose: "standby_runtime",
        target_endpoint: backup.endpoint,
        management_token_persisted: false,
      },
    });
    if (insertError) throw insertError;

    const connectUrl = new URL(`${supabaseUrl}/functions/v1/${CONFIG_FUNCTION}/connect`);
    connectUrl.searchParams.set("setup", rawToken);
    return reply({
      ok: true,
      linked: true,
      connectUrl: connectUrl.toString(),
      expiresAt,
      targetEndpoint: backup.endpoint,
      managementTokenInApk: false,
      managementTokenPersisted: false,
      autoFailoverEnabled: false,
    });
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return reply({ ok: false, error: "standby_runtime_app_failed" }, 500);
  }
});

async function loadReadyBackup(db: DbClient): Promise<{ endpoint: string } | null> {
  const { data, error } = await db.from("h_runtime_cloud_registry")
    .select("endpoint,enabled,ready,last_health_ok,credential_id,metadata")
    .eq("id", BACKUP_CLOUD_ID)
    .eq("cloud_role", "backup")
    .maybeSingle();
  if (error) throw error;
  const metadata = objectOrEmpty(data?.metadata);
  if (!data?.enabled || !data?.ready || data?.last_health_ok !== true) return null;
  if (String(data.credential_id || "") !== BACKUP_CLOUD_ID) return null;
  if (metadata.storage_backup_ready !== true || metadata.connection_validated !== true) return null;
  const endpoint = normalizeSupabaseEndpoint(String(data.endpoint || ""));
  return endpoint ? { endpoint } : null;
}

async function invalidatePendingSetup(db: DbClient): Promise<void> {
  const { error } = await db.from("h_runtime_cloud_setup").delete().is("used_at", null);
  if (error) throw error;
}

async function isLinkedOwner(db: DbClient, subjectFingerprint: string, audience: string): Promise<boolean> {
  const { data, error } = await db.from("h_runtime_app_identities")
    .select("google_audience,active")
    .eq("google_subject_fingerprint", subjectFingerprint)
    .eq("active", true)
    .maybeSingle();
  if (error) throw error;
  return data?.active === true && String(data.google_audience || "") === audience;
}

async function loadRuntimeSecret(db: DbClient): Promise<string> {
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
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${label}:${value}`));
  return [...new Uint8Array(signature)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function setupTokenHash(token: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`h-cloud-setup-v1:${token}`));
  return base64Url(new Uint8Array(digest));
}

function normalizeSupabaseEndpoint(raw: string): string | null {
  try {
    const url = new URL(raw.trim());
    if (url.protocol !== "https:" || !url.hostname.endsWith(".supabase.co")) return null;
    if (url.username || url.password || url.search || url.hash) return null;
    return `${url.protocol}//${url.host}`;
  } catch {
    return null;
  }
}

function randomUrlSafe(byteCount: number): string {
  const bytes = new Uint8Array(byteCount);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function objectOrEmpty(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function compactErrorCode(error: unknown): string {
  const raw = (error instanceof Error ? error.message : String(error || "unknown_error"))
    .toLowerCase().replace(/[^a-z0-9_:-]+/g, "_");
  return raw.slice(0, 120) || "standby_runtime_app_failed";
}

function reply(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}
