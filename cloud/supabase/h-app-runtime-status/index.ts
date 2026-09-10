import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";

const FUNCTION_NAME = "h-app-runtime-status";
const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const HEALTH_TIMEOUT_MS = 1500;

type DbClient = any;

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ ok: false, error: "method_not_allowed" }, 405);

  const bearer = bearerToken(req.headers.get("authorization"));
  if (!bearer) return json({ ok: false, error: "google_sign_in_required" }, 401);

  let google;
  try {
    google = await verifyGoogleIdToken(bearer);
  } catch (error) {
    return json({ ok: false, error: compactErrorCode(error) || "google_token_invalid" }, 401);
  }

  const supabaseUrl = String(Deno.env.get("SUPABASE_URL") || "").replace(/\/$/, "");
  const serviceRole = String(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "").trim();
  if (!supabaseUrl || !serviceRole) return json({ ok: false, error: "runtime_unavailable" }, 500);

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  try {
    const identitySecret = await loadIdentitySecret(db);
    const subjectFingerprint = await secretFingerprint(identitySecret, GOOGLE_SUB_LABEL, google.subject);
    if (!await isLinkedOwner(db, subjectFingerprint, google.audience)) {
      return json({ ok: false, error: "app_not_linked", linked: false }, 403);
    }

    const runtimeSecret = await loadRuntimeSecret(db);
    const health = await fetchStandbyHealth(supabaseUrl, runtimeSecret);
    const activeReady = health?.ok === true &&
      health?.service === "h-standby-health" &&
      health?.runtimeRole === "standby" &&
      health?.hIdentity === "H" &&
      health?.activeReady === true &&
      health?.requestOnlyActive === true &&
      health?.promoted === true &&
      health?.promotionAttested === true &&
      health?.promotionMode === "request_only" &&
      health?.replicaWritesEnabled === false &&
      health?.schedulerActive === false &&
      health?.autonomousOutboundActive === false &&
      health?.restoreVerified === true;

    return json({
      ok: true,
      service: FUNCTION_NAME,
      linked: true,
      activeReady,
      requestOnlyActive: activeReady,
      runtimeRole: String(health?.runtimeRole || "unknown").slice(0, 32),
      hIdentity: String(health?.hIdentity || "unknown").slice(0, 32),
      promoted: health?.promoted === true,
      promotionAttested: health?.promotionAttested === true,
      promotionMode: String(health?.promotionMode || "none").slice(0, 32),
      replicaWritesEnabled: health?.replicaWritesEnabled === true,
      schedulerActive: health?.schedulerActive === true,
      autonomousOutboundActive: health?.autonomousOutboundActive === true,
      credentialsExposed: false,
      runtimeSecretExposed: false,
      serviceRoleExposed: false,
    });
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return json({ ok: false, error: "app_runtime_status_failed" }, 500);
  }
});

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

async function fetchStandbyHealth(endpoint: string, runtimeSecret: string): Promise<any> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), HEALTH_TIMEOUT_MS);
  try {
    const response = await fetch(`${endpoint}/functions/v1/h-standby-health`, {
      method: "POST",
      headers: {
        "x-h-runtime-secret": runtimeSecret,
        "Content-Type": "application/json",
        "Cache-Control": "no-store",
      },
      body: "{}",
      signal: controller.signal,
    });
    if (!response.ok) throw new Error(`standby_health_${response.status}`);
    return await response.json().catch(() => ({}));
  } finally {
    clearTimeout(timeout);
  }
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

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function constantSafeCode(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9_:-]+/g, "_").slice(0, 120);
}

function compactErrorCode(error: unknown): string {
  return constantSafeCode(error instanceof Error ? error.message : String(error || "unknown_error"));
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
