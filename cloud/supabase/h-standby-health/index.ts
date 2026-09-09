import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const SERVICE = "h-standby-health";
const MAX_REPLICATION_LAG_SECONDS = 120;
const MAX_REPLICATION_OBSERVATION_AGE_SECONDS = 180;

type DbClient = any;
type StateRow = { key: string; value: Record<string, unknown> | null; updated_at: string | null };

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);
  const supabaseUrl = String(Deno.env.get("SUPABASE_URL") || "").replace(/\/$/, "");
  const serviceRole = String(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "").trim();
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const runtimeSecret = await loadRuntimeSecret(db).catch(() => "");
  const provided = String(req.headers.get("x-h-runtime-secret") || "").trim();
  if (!runtimeSecret || !provided || !constantTimeEqual(runtimeSecret, provided)) return reply({ ok: false, error: "Unauthorized" }, 401);

  try {
    const rows = await loadStandbyState(db);
    const runtime = stateValue(rows, "standby_runtime");
    const replication = stateValue(rows, "standby_replication");
    const runtimeRoleOk = stringValue(runtime.runtime_role) === "standby";
    const hIdentityOk = stringValue(runtime.h_identity) === "H";
    const runtimeReady = runtime.runtime_ready === true;
    const restoreVerified = runtime.restore_verified === true;
    const replicationMode = stringValue(replication.mode);
    const replicationContinuous = replicationMode === "continuous";
    const replicationLagSeconds = nonNegativeNumber(replication.lag_seconds);
    const lastReplicationAt = parseDate(replication.last_replicated_at);
    const observationAgeSeconds = lastReplicationAt == null ? null : Math.max(0, (Date.now() - lastReplicationAt) / 1000);
    const replicationFresh = replicationContinuous && replicationLagSeconds != null && replicationLagSeconds <= MAX_REPLICATION_LAG_SECONDS && observationAgeSeconds != null && observationAgeSeconds <= MAX_REPLICATION_OBSERVATION_AGE_SECONDS;
    const standbyReady = runtimeRoleOk && hIdentityOk && runtimeReady && restoreVerified && replicationFresh;

    return reply({ ok: true, service: SERVICE, checkedAt: new Date().toISOString(), runtimeRole: runtimeRoleOk ? "standby" : "unknown", hIdentity: hIdentityOk ? "H" : "unknown", runtimeReady, restoreVerified, replicationMode: replicationMode || "unknown", replicationLagSeconds, replicationObservationAgeSeconds: observationAgeSeconds, maxReplicationLagSeconds: MAX_REPLICATION_LAG_SECONDS, maxReplicationObservationAgeSeconds: MAX_REPLICATION_OBSERVATION_AGE_SECONDS, standbyReady, storageOnlyBackupAccepted: false, secretsExposed: false });
  } catch (error) {
    console.error(`${SERVICE} failed`, errorMessage(error));
    return reply({ ok: false, error: "standby_health_failed", standbyReady: false }, 500);
  }
});

async function loadRuntimeSecret(db: DbClient): Promise<string> {
  const { data, error } = await db.from("h_runtime_config").select("secret_value").eq("key", "poll_secret").maybeSingle();
  if (error) throw error;
  const value = String(data?.secret_value || "").trim();
  if (!value) throw new Error("runtime_secret_missing");
  return value;
}

async function loadStandbyState(db: DbClient): Promise<StateRow[]> {
  const { data, error } = await db.from("h_runtime_state").select("key,value,updated_at").in("key", ["standby_runtime", "standby_replication"]);
  if (error) throw error;
  return Array.isArray(data) ? data as StateRow[] : [];
}

function stateValue(rows: StateRow[], key: string): Record<string, unknown> {
  const value = rows.find((row) => row.key === key)?.value;
  return value && typeof value === "object" && !Array.isArray(value) ? value : {};
}
function stringValue(value: unknown): string { return typeof value === "string" ? value.trim() : ""; }
function nonNegativeNumber(value: unknown): number | null { const numeric = Number(value); return Number.isFinite(numeric) && numeric >= 0 ? numeric : null; }
function parseDate(value: unknown): number | null { if (typeof value !== "string" || !value.trim()) return null; const parsed = Date.parse(value); return Number.isFinite(parsed) ? parsed : null; }
function constantTimeEqual(left: string, right: string): boolean { if (left.length !== right.length) return false; let diff = 0; for (let index = 0; index < left.length; index += 1) diff |= left.charCodeAt(index) ^ right.charCodeAt(index); return diff === 0; }
function errorMessage(error: unknown): string { return error instanceof Error ? error.message : String(error || "unknown_error"); }
function reply(value: unknown, status = 200) { return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } }); }
