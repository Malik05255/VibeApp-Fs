import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";
import { parseOwnerPaidSetup, type HOwnerPaidSetup } from "../h-whatsapp-inbox/owner-paid-policy.ts";

const FUNCTION_NAME = "h-ai-provider-app";
const PROVIDER_CONFIG_FUNCTION = "h-ai-provider-config";
const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const SETUP_TTL_MS = 10 * 60 * 1000;
const ROUTE_ID = "openrouter_owner_paid";
const CREDENTIAL_ID = "openrouter_owner_paid";

type DbClient = any;

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

  const supabaseUrl = safeEnv("SUPABASE_URL").replace(/\/$/, "");
  const serviceRole = safeEnv("SUPABASE_SERVICE_ROLE_KEY").trim();
  if (!supabaseUrl || !serviceRole) return json({ ok: false, error: "runtime_unavailable" }, 500);

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });

  try {
    const runtimeSecret = await loadRuntimeSecret(db);
    const subjectFingerprint = await secretFingerprint(runtimeSecret, GOOGLE_SUB_LABEL, google.subject);
    const linked = await isLinkedOwner(db, subjectFingerprint, google.audience);
    if (!linked) return json({ ok: false, error: "app_not_linked", linked: false }, 403);

    const body = await req.json().catch(() => ({}));
    const action = String(body?.action || "status").trim().toLowerCase();

    if (action === "status") {
      return json({ ...(await status(db)), linked: true });
    }

    if (action === "setup_link") {
      const setup = parseOwnerPaidSetup(body);
      if (!setup) return json({ ok: false, error: "invalid_owner_paid_setup" }, 400);
      return json(await createSetupLink(db, supabaseUrl, setup));
    }

    if (action === "disable") {
      const now = new Date().toISOString();
      const { error } = await db.from("h_runtime_ai_provider_registry")
        .update({ enabled: false, updated_at: now })
        .eq("id", ROUTE_ID)
        .eq("route_class", "owner_paid");
      if (error) throw error;
      await db.from("h_runtime_state").upsert({
        key: "owner_paid_ai",
        value: { connected: true, enabled: false, owner_paid: true, disabled_at: now },
        updated_at: now,
      }, { onConflict: "key" });
      return json({ ok: true, connected: true, enabled: false, linked: true });
    }

    if (action === "disconnect") {
      const now = new Date().toISOString();
      const { error: routeError } = await db.from("h_runtime_ai_provider_registry")
        .update({
          enabled: false,
          credential_id: null,
          selected_model: null,
          daily_call_limit: null,
          owner_enabled_at: null,
          updated_at: now,
        })
        .eq("id", ROUTE_ID)
        .eq("route_class", "owner_paid");
      if (routeError) throw routeError;

      const { error: credentialError } = await db.from("h_runtime_ai_credentials")
        .delete()
        .eq("id", CREDENTIAL_ID)
        .eq("provider", "openrouter");
      if (credentialError) throw credentialError;

      // A disconnect invalidates every unfinished setup flow so a stale browser tab cannot
      // reconnect a paid route after the owner explicitly removed it from the app.
      const { error: setupError } = await db.from("h_runtime_ai_owner_paid_setup")
        .delete()
        .is("used_at", null);
      if (setupError) throw setupError;

      await db.from("h_runtime_state").upsert({
        key: "owner_paid_ai",
        value: { connected: false, enabled: false, owner_paid: true, disconnected_at: now },
        updated_at: now,
      }, { onConflict: "key" });
      return json({ ok: true, connected: false, enabled: false, linked: true });
    }

    return json({ ok: false, error: "unsupported_action" }, 400);
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, errorMessage(error));
    return json({ ok: false, error: "provider_app_failed" }, 500);
  }
});

async function createSetupLink(db: DbClient, supabaseUrl: string, setup: HOwnerPaidSetup) {
  const now = new Date().toISOString();

  // There is one H owner and one owner-paid route. Invalidate previous unfinished links so
  // only the newest app request can progress to key entry and explicit price approval.
  const { error: cleanupError } = await db.from("h_runtime_ai_owner_paid_setup")
    .delete()
    .is("used_at", null);
  if (cleanupError) throw cleanupError;

  const rawToken = randomUrlSafe(32);
  const tokenHash = await setupTokenHash(rawToken);
  const expiresAt = new Date(Date.now() + SETUP_TTL_MS).toISOString();
  const { error } = await db.from("h_runtime_ai_owner_paid_setup").insert({
    token_hash: tokenHash,
    provider: setup.provider,
    selected_model: setup.selectedModel,
    daily_call_limit: setup.dailyCallLimit,
    hard_tasks_only: setup.hardTasksOnly,
    allow_free_fallback: setup.allowFreeFallback,
    expires_at: expiresAt,
  });
  if (error) throw error;

  const connectUrl = new URL(`${supabaseUrl}/functions/v1/${PROVIDER_CONFIG_FUNCTION}/connect`);
  connectUrl.searchParams.set("setup", rawToken);
  return {
    ok: true,
    linked: true,
    provider: setup.provider,
    selectedModel: setup.selectedModel,
    dailyCallLimit: setup.dailyCallLimit,
    hardTasksOnly: setup.hardTasksOnly,
    allowFreeFallback: setup.allowFreeFallback,
    expiresAt,
    connectUrl: connectUrl.toString(),
    supersedesPreviousSetup: true,
    paidActivated: false,
  };
}

async function status(db: DbClient) {
  const { data: route, error } = await db.from("h_runtime_ai_provider_registry")
    .select("id,provider,selected_model,enabled,owner_enabled_at,hard_tasks_only,allow_free_fallback,daily_call_limit,metadata,updated_at")
    .eq("id", ROUTE_ID)
    .maybeSingle();
  if (error) throw error;
  if (!route?.selected_model) {
    return { ok: true, connected: false, enabled: false, ownerPaid: true };
  }

  const today = new Date().toISOString().slice(0, 10);
  const { data: usage, error: usageError } = await db.from("h_runtime_ai_paid_usage_daily")
    .select("calls,prompt_tokens,completion_tokens,cost_usd,last_used_at")
    .eq("route_id", ROUTE_ID)
    .eq("usage_date", today)
    .maybeSingle();
  if (usageError) throw usageError;

  return {
    ok: true,
    connected: true,
    enabled: route.enabled === true,
    ownerPaid: true,
    provider: route.provider,
    selectedModel: route.selected_model,
    ownerEnabledAt: route.owner_enabled_at,
    hardTasksOnly: route.hard_tasks_only === true,
    allowFreeFallback: route.allow_free_fallback === true,
    dailyCallLimit: Number(route.daily_call_limit || 0),
    callsUsedToday: Number(usage?.calls || 0),
    promptTokensToday: Number(usage?.prompt_tokens || 0),
    completionTokensToday: Number(usage?.completion_tokens || 0),
    costUsdToday: Number(usage?.cost_usd || 0),
    lastUsedAt: usage?.last_used_at ?? null,
    priceGuard: Boolean(route?.metadata?.pricing_ceiling),
    explicitPriceReview: Boolean(route?.metadata?.pricing_verified_at),
    updatedAt: route.updated_at,
  };
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
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${label}:${value}`),
  );
  return [...new Uint8Array(signature)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function setupTokenHash(token: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(`h-owner-paid-ai-v1:${token}`),
  );
  return base64Url(new Uint8Array(digest));
}

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
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

function safeEnv(name: string): string {
  return String(Deno.env.get(name) || "");
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
