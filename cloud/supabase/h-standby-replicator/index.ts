import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const FUNCTION_NAME = "h-standby-replicator";
const BACKUP_CLOUD_ID = "h_backup_supabase_storage";
const BACKUP_CREDENTIAL_ID = "h_backup_supabase_storage";
const RUNTIME_SECRET_CREDENTIAL_ID = "h_backup_supabase_runtime_secret";
const MAX_ROWS = 1000;
const MAX_DEDUPE_ROWS = 2000;
const DEDUPE_WINDOW_MS = 7 * 24 * 60 * 60 * 1000;
const MAX_REPLICATION_LAG_SECONDS = 120;

type DbClient = any;

type BackupTarget = {
  endpoint: string;
  secretCiphertext: string;
  secretIv: string;
  runtimeSecretCiphertext: string;
  runtimeSecretIv: string;
  metadata: Record<string, unknown>;
};

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);

  const primaryUrl = String(Deno.env.get("SUPABASE_URL") || "").replace(/\/$/, "");
  const primaryServiceRole = String(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "").trim();
  if (!primaryUrl || !primaryServiceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);

  const db = createClient(primaryUrl, primaryServiceRole, { auth: { persistSession: false } });
  const runtimeSecret = await loadRuntimeSecret(db).catch(() => "");
  const provided = String(req.headers.get("x-h-runtime-secret") || "").trim();
  if (!runtimeSecret || !provided || !constantTimeEqual(runtimeSecret, provided)) {
    return reply({ ok: false, error: "Unauthorized" }, 401);
  }

  try {
    const target = await loadReplicationTarget(db);
    if (!target) {
      return reply({ ok: true, skipped: true, reason: "standby_replication_not_ready" });
    }

    const [standbyServiceRole, standbyRuntimeSecret] = await Promise.all([
      decryptCloudCredential("supabase", target.secretCiphertext, target.secretIv, primaryServiceRole),
      decryptCloudCredential(
        "supabase_runtime",
        target.runtimeSecretCiphertext,
        target.runtimeSecretIv,
        primaryServiceRole,
      ),
    ]);

    const snapshot = await buildReplicaSnapshot(db);
    const response = await fetch(`${target.endpoint}/rest/v1/rpc/h_apply_standby_replica_v1`, {
      method: "POST",
      headers: {
        apikey: standbyServiceRole,
        Authorization: `Bearer ${standbyServiceRole}`,
        "Content-Type": "application/json",
        "Cache-Control": "no-store",
      },
      body: JSON.stringify({ p_snapshot: snapshot }),
    });

    const responseText = await response.text();
    let result: any = {};
    try { result = responseText ? JSON.parse(responseText) : {}; } catch (_) {}
    if (!response.ok || result?.ok === false) {
      throw new Error(`standby_replica_apply_${response.status}:${String(result?.message || result?.error || responseText).slice(0, 120)}`);
    }

    const health = await probeStandbyHealth(target.endpoint, standbyRuntimeSecret);
    if (!standbyHealthEligible(health)) {
      throw new Error(`standby_health_not_ready:${compactHealthReason(health)}`);
    }

    const now = new Date().toISOString();
    const lagSeconds = finiteNumber(health?.replicationLagSeconds ?? result?.lagSeconds);
    const metadata = {
      ...target.metadata,
      standby_replication_ready: true,
      standby_replication_last_ok: now,
      standby_replication_protocol: "exact_mirror_v1",
      standby_replication_lag_seconds: lagSeconds,
      standby_replication_digest: snapshot.digest,
      standby_runtime_ready: true,
      runtime_health_ok: true,
      standby_health_last_ok: now,
      auto_failover_eligible: true,
    };
    const { error: updateError } = await db.from("h_runtime_cloud_registry")
      .update({ metadata, updated_at: now })
      .eq("id", BACKUP_CLOUD_ID)
      .eq("cloud_role", "backup");
    if (updateError) throw updateError;

    return reply({
      ok: true,
      skipped: false,
      protocol: "exact_mirror_v1",
      counts: snapshot.counts,
      lagSeconds,
      digestPresent: true,
      standbyRuntimeReady: true,
      runtimeHealthOk: true,
      autoFailoverEligible: true,
      idempotencyMetadataReplicated: true,
      rawMessageBodiesReplicated: false,
      conversationHistoryReplicated: false,
      providerCredentialsReplicated: false,
      runtimeSecretsReplicated: false,
      rawMediaReplicated: false,
    });
  } catch (error) {
    const code = compactErrorCode(error);
    await recordReplicationFailure(db, code);
    console.error(`${FUNCTION_NAME} failed`, code);
    return reply({ ok: false, error: "standby_replication_failed" }, 500);
  }
});

async function loadReplicationTarget(db: DbClient): Promise<BackupTarget | null> {
  const { data: cloud, error } = await db.from("h_runtime_cloud_registry")
    .select("endpoint,credential_id,enabled,ready,last_health_ok,metadata")
    .eq("id", BACKUP_CLOUD_ID)
    .eq("cloud_role", "backup")
    .maybeSingle();
  if (error) throw error;
  const metadata = cloud?.metadata && typeof cloud.metadata === "object" && !Array.isArray(cloud.metadata)
    ? cloud.metadata as Record<string, unknown>
    : {};
  if (!cloud?.enabled || !cloud?.ready || cloud?.last_health_ok !== true) return null;
  if (String(cloud.credential_id || "") !== BACKUP_CREDENTIAL_ID) return null;
  if (metadata.storage_backup_ready !== true || metadata.connection_validated !== true) return null;
  if (metadata.standby_runtime_provisioned !== true || metadata.standby_health_service_deployed !== true) return null;

  const endpoint = normalizeSupabaseEndpoint(String(cloud.endpoint || ""));
  if (!endpoint) return null;
  const [{ data: credential, error: credentialError }, { data: runtimeCredential, error: runtimeCredentialError }] =
    await Promise.all([
      db.from("h_runtime_cloud_credentials")
        .select("provider,secret_ciphertext,secret_iv")
        .eq("id", BACKUP_CREDENTIAL_ID)
        .maybeSingle(),
      db.from("h_runtime_cloud_credentials")
        .select("provider,secret_ciphertext,secret_iv")
        .eq("id", RUNTIME_SECRET_CREDENTIAL_ID)
        .maybeSingle(),
    ]);
  if (credentialError) throw credentialError;
  if (runtimeCredentialError) throw runtimeCredentialError;
  if (!credential || String(credential.provider || "") !== "supabase") return null;
  if (!runtimeCredential || String(runtimeCredential.provider || "") !== "supabase_runtime") return null;
  return {
    endpoint,
    secretCiphertext: String(credential.secret_ciphertext || ""),
    secretIv: String(credential.secret_iv || ""),
    runtimeSecretCiphertext: String(runtimeCredential.secret_ciphertext || ""),
    runtimeSecretIv: String(runtimeCredential.secret_iv || ""),
    metadata,
  };
}

async function probeStandbyHealth(endpoint: string, runtimeSecret: string): Promise<any> {
  const response = await fetch(`${endpoint}/functions/v1/h-standby-health`, {
    method: "POST",
    headers: {
      "x-h-runtime-secret": runtimeSecret,
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
    body: "{}",
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`standby_health_http_${response.status}`);
  return body;
}

export function standbyHealthEligible(health: any): boolean {
  const lag = finiteNumber(health?.replicationLagSeconds);
  return health?.ok === true &&
    health?.service === "h-standby-health" &&
    health?.standbyReady === true &&
    health?.runtimeRole === "standby" &&
    health?.hIdentity === "H" &&
    health?.promoted !== true &&
    health?.restoreVerified === true &&
    health?.replicationMode === "continuous" &&
    health?.replicationProtocol === "exact_mirror_v1" &&
    health?.replicationFresh === true &&
    lag != null &&
    lag <= MAX_REPLICATION_LAG_SECONDS;
}

function compactHealthReason(health: any): string {
  if (!health || typeof health !== "object") return "invalid_response";
  if (health?.standbyReady !== true) return "standby_not_ready";
  const lag = finiteNumber(health?.replicationLagSeconds);
  if (lag == null || lag > MAX_REPLICATION_LAG_SECONDS) return "replication_stale";
  return "contract_mismatch";
}

async function buildReplicaSnapshot(db: DbClient) {
  const dedupeCutoff = new Date(Date.now() - DEDUPE_WINDOW_MS).toISOString();
  const [memories, tasks, reminders, contacts, learning, gaps, verified, idempotency] = await Promise.all([
    boundedQuery(db.from("h_runtime_memories").select("id,user_key,category,body,original_text,created_at,updated_at").order("created_at").order("id"), "memories"),
    boundedQuery(db.from("h_runtime_tasks").select("id,user_key,title,body,task_type,priority,priority_source,status,due_at,execution_plan,metadata,result_text,paused_at,completed_at,cancelled_at,created_at,updated_at").order("id"), "tasks"),
    boundedQuery(db.from("h_runtime_reminders").select("id,user_key,body,due_at,status,attempts,last_error,created_at,updated_at,sent_at,priority_class,priority_source,classification_reason,paused_at,task_id,title,original_text,interpreted_text,reminder_type,lifecycle_status,source,domain,recurrence_rule,person_name,location,cooldown_until,completed_at,delivery_channel").order("created_at").order("id"), "reminders"),
    boundedQuery(db.from("h_runtime_contacts").select("id,user_key,name_key,display_name,target_wa_id,created_at,updated_at").order("created_at").order("id"), "contacts"),
    boundedQuery(db.from("h_runtime_learning_state").select("user_key,first_met_at,last_interaction_at,turn_count,directness_score,technical_depth_score,programming_interest_score,solution_breadth_score,arabic_preference_score,concise_preference_score,code_replacement_preference_score,interaction_samples,interest_tags,updated_at").order("user_key"), "learningState"),
    boundedQuery(db.from("h_runtime_knowledge_gaps").select("id,user_key,query_key,query_text,status,first_reason,last_reason,priority,occurrences,research_attempts,next_research_at,last_researched_at,verified_at,verification_summary,created_at,updated_at,last_seen_at,candidate_answer,candidate_model,research_error,candidate_at,verification_attempts,verification_error,last_verification_attempt_at").order("created_at").order("id"), "knowledgeGaps"),
    boundedQuery(db.from("h_runtime_verified_knowledge").select("id,user_key,query_key,query_text,answer_text,source_gap_id,verification_method,verification_model,verified_at,updated_at,last_used_at,use_count").order("verified_at").order("id"), "verifiedKnowledge"),
    boundedQuery(
      db.from("h_runtime_inbox")
        .select("message_key,status,error,received_at,updated_at,processed_at,reply_text")
        .gte("received_at", dedupeCutoff)
        .in("status", ["processing", "processed", "failed"])
        .order("received_at")
        .order("message_key"),
      "idempotency",
      MAX_DEDUPE_ROWS,
    ),
  ]);

  const generatedAt = new Date().toISOString();
  const data = {
    assistantIdentity: "H",
    generatedAt,
    memories,
    tasks: tasks.map((row: any) => ({ ...row, conversation_id: null })),
    reminders: reminders.map((row: any) => ({ ...row, conversation_id: null })),
    contacts,
    learningState: learning,
    knowledgeGaps: gaps,
    verifiedKnowledge: verified,
    idempotency,
  };
  const digest = await sha256Hex(JSON.stringify(data));
  return {
    format: "h-standby-replica",
    version: 1,
    ...data,
    digest,
    counts: {
      memories: memories.length,
      tasks: tasks.length,
      reminders: reminders.length,
      contacts: contacts.length,
      learningState: learning.length,
      knowledgeGaps: gaps.length,
      verifiedKnowledge: verified.length,
      idempotency: idempotency.length,
    },
  };
}

async function boundedQuery(builder: any, section: string, maxRows = MAX_ROWS): Promise<any[]> {
  const { data, error } = await builder.limit(maxRows + 1);
  if (error) throw error;
  const rows = Array.isArray(data) ? data : [];
  if (rows.length > maxRows) throw new Error(`standby_replica_requires_pagination:${section}`);
  return rows;
}

async function recordReplicationFailure(db: DbClient, code: string): Promise<void> {
  try {
    const { data } = await db.from("h_runtime_cloud_registry").select("metadata").eq("id", BACKUP_CLOUD_ID).maybeSingle();
    const metadata = data?.metadata && typeof data.metadata === "object" && !Array.isArray(data.metadata)
      ? data.metadata as Record<string, unknown>
      : {};
    await db.from("h_runtime_cloud_registry").update({
      metadata: {
        ...metadata,
        standby_replication_ready: false,
        standby_runtime_ready: false,
        runtime_health_ok: false,
        standby_replication_last_error: code,
        standby_replication_last_error_at: new Date().toISOString(),
        auto_failover_eligible: false,
      },
      updated_at: new Date().toISOString(),
    }).eq("id", BACKUP_CLOUD_ID);
  } catch (_) {
    // Preserve the original replication failure.
  }
}

async function loadRuntimeSecret(db: DbClient): Promise<string> {
  const { data, error } = await db.from("h_runtime_config").select("secret_value").eq("key", "poll_secret").maybeSingle();
  if (error) throw error;
  const value = String(data?.secret_value || "").trim();
  if (!value) throw new Error("runtime_secret_missing");
  return value;
}

async function decryptCloudCredential(provider: string, ciphertext: string, ivText: string, rootSecret: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`h-cloud-credential-aes-v1:${provider}:${rootSecret}`));
  const key = await crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["decrypt"]);
  const decrypted = await crypto.subtle.decrypt({ name: "AES-GCM", iv: base64UrlDecode(ivText) }, key, base64UrlDecode(ciphertext));
  const value = new TextDecoder().decode(decrypted).trim();
  if (value.length < 32) throw new Error("standby_credential_invalid");
  return value;
}

function normalizeSupabaseEndpoint(raw: string): string | null {
  try {
    const url = new URL(raw.trim());
    if (url.protocol !== "https:" || !url.hostname.endsWith(".supabase.co") || url.username || url.password || url.search || url.hash) return null;
    return `${url.protocol}//${url.host}`;
  } catch { return null; }
}

function base64UrlDecode(value: string): Uint8Array<ArrayBuffer> {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error("invalid_cloud_ciphertext");
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
function finiteNumber(value: unknown): number | null { const n = Number(value); return Number.isFinite(n) ? n : null; }
function constantTimeEqual(left: string, right: string): boolean { if (left.length !== right.length) return false; let diff = 0; for (let i = 0; i < left.length; i += 1) diff |= left.charCodeAt(i) ^ right.charCodeAt(i); return diff === 0; }
function compactErrorCode(error: unknown): string { const raw = (error instanceof Error ? error.message : String(error || "unknown_error")).toLowerCase().replace(/[^a-z0-9_:-]+/g, "_"); return raw.slice(0, 160) || "standby_replication_failed"; }
function reply(value: unknown, status = 200) { return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } }); }
