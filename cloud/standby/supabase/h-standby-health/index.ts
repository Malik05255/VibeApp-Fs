import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { runStandbyExecutionProbe } from "./execution-probe.ts";
import { evaluateStandbyHealth } from "./standby-health-policy.ts";

const FUNCTION_NAME = "h-standby-health";

type DbClient = any;

type StateRow = {
  key: string;
  value: Record<string, unknown> | null;
  updated_at: string | null;
};

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);

  const supabaseUrl = String(Deno.env.get("SUPABASE_URL") || "").trim();
  const serviceRole = String(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "").trim();
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const runtimeSecret = await loadRuntimeSecret(db).catch(() => "");
  const provided = String(req.headers.get("x-h-runtime-secret") || "").trim();
  if (!runtimeSecret || !provided || !constantTimeEqual(runtimeSecret, provided)) {
    return reply({ ok: false, error: "Unauthorized" }, 401);
  }

  try {
    const body = await req.json().catch(() => ({}));
    const executionProbeNonce = body && typeof body === "object" && !Array.isArray(body)
      ? (body as Record<string, unknown>).execution_probe_nonce
      : null;

    const [runtimeRow, replicationRow, executionRow, promotionRow] = await Promise.all([
      loadState(db, "standby_runtime"),
      loadState(db, "standby_replication"),
      loadState(db, "standby_execution"),
      loadState(db, "standby_promotion"),
    ]);

    const decision = evaluateStandbyHealth({
      runtime: runtimeRow?.value ?? {},
      replication: replicationRow?.value ?? {},
      execution: executionRow?.value ?? {},
      promotion: promotionRow?.value ?? {},
      replicationObservedAt:
        boundedString(replicationRow?.value?.last_replicated_at, 80) ?? replicationRow?.updated_at ?? null,
    });

    const identityReplicaReady = decision.appIdentityRekeyReady && decision.whatsappIdentityRekeyReady;
    const executionProbe = executionProbeNonce == null
      ? null
      : await runStandbyExecutionProbe(db, executionProbeNonce);

    return reply({
      ok: true,
      service: FUNCTION_NAME,
      checkedAt: new Date().toISOString(),
      ...decision,
      passivePreflightOnly: decision.preflightReady && !decision.activeReady,
      requestOnlyActive: decision.activeReady && decision.promotionMode === "request_only",
      identityFingerprintsReplicated: identityReplicaReady,
      encryptedRuntimeUserKeysReplicated: decision.appIdentityRekeyReady,
      providerCredentialsRekeyed: decision.aiCredentialsRekeyReady && (decision.aiContinuityFresh || decision.activeReady),
      executionProbe,
      rawProviderCredentialsReplicated: false,
      sourceProviderCiphertextsCopiedUnchanged: false,
      aiSetupTokensReplicated: false,
      aiOauthPendingReplicated: false,
      rawRoutingIdentitiesReplicated: false,
      rawMessageBodiesReplicated: false,
      conversationHistoryReplicated: false,
      providerCredentialsReplicated: false,
      runtimeSecretsReplicated: false,
      rawMediaReplicated: false,
    });
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return reply({ ok: false, error: "standby_health_failed" }, 500);
  }
});

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

async function loadState(db: DbClient, key: string): Promise<StateRow | null> {
  const { data, error } = await db.from("h_runtime_state")
    .select("key,value,updated_at")
    .eq("key", key)
    .maybeSingle();
  if (error) throw error;
  if (!data) return null;
  const value = data.value && typeof data.value === "object" && !Array.isArray(data.value)
    ? data.value as Record<string, unknown>
    : {};
  return {
    key: String(data.key || key),
    value,
    updated_at: boundedString(data.updated_at, 80),
  };
}

function boundedString(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const text = value.trim();
  return text ? text.slice(0, max) : null;
}

function constantTimeEqual(left: string, right: string): boolean {
  if (left.length !== right.length) return false;
  let diff = 0;
  for (let index = 0; index < left.length; index += 1) {
    diff |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return diff === 0;
}

function compactErrorCode(error: unknown): string {
  const raw = (error instanceof Error ? error.message : String(error || "unknown_error"))
    .toLowerCase()
    .replace(/[^a-z0-9_:-]+/g, "_");
  return raw.slice(0, 120) || "standby_health_failed";
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
