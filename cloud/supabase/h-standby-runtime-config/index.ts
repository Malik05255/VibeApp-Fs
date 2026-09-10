import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { deployAndVerifyStandbyFunctionInventory } from "./function-inventory.ts";

const FUNCTION_NAME = "h-standby-runtime-config";
const BACKUP_CLOUD_ID = "h_backup_supabase_storage";
const RUNTIME_SECRET_CREDENTIAL_ID = "h_backup_supabase_runtime_secret";
const STANDBY_BUNDLE_REF = "b6f9771151ddd81067410bec81bdf2ee667cf5fe";
const GITHUB_CONTENTS_BASE = "https://api.github.com/repos/Malik05255/VibeApp-Fs/contents";
const MAX_TOKEN_LENGTH = 4096;
const MAX_GITHUB_TOKEN_LENGTH = 512;
const MANAGEMENT_API = "https://api.supabase.com/v1";
const REQUIRED_TABLES = [
  "h_runtime_state",
  "h_runtime_config",
  "h_runtime_inbox",
  "h_runtime_memories",
  "h_runtime_contacts",
  "h_runtime_tasks",
  "h_runtime_reminders",
  "h_runtime_learning_state",
  "h_runtime_knowledge_gaps",
  "h_runtime_verified_knowledge",
] as const;

type DbClient = any;
type StandbySchemaState = "empty" | "complete" | "partial";

Deno.serve(async (req: Request) => {
  const primaryUrl = safeEnv("SUPABASE_URL").replace(/\/$/, "");
  const primaryServiceRole = safeEnv("SUPABASE_SERVICE_ROLE_KEY").trim();
  if (!primaryUrl || !primaryServiceRole) return json({ ok: false, error: "runtime_unavailable" }, 500);

  const db = createClient(primaryUrl, primaryServiceRole, { auth: { persistSession: false } });
  const url = new URL(req.url);
  const path = routePath(url.pathname);

  try {
    if (req.method === "GET" && path === "/connect") {
      const setup = await validateSetupToken(db, url.searchParams.get("setup") || "");
      if (!setup.ok) return html(errorPage(setup.error), 400);
      const backup = await loadReadyBackup(db);
      if (!backup) return html(errorPage("أضف Backup Cloud صالحة أولًا."), 409);
      if (setup.targetEndpoint && setup.targetEndpoint !== backup.endpoint) {
        return html(errorPage("تغيّرت السحابة الاحتياطية. أنشئ رابط إعداد جديدًا من التطبيق."), 409);
      }
      return html(connectPage(publicBase(primaryUrl), setup.rawToken, backup.endpoint));
    }

    if (req.method === "POST" && path === "/provision") {
      const setup = await validateSetupToken(db, url.searchParams.get("setup") || "");
      if (!setup.ok) return html(errorPage(setup.error), 400);
      const backup = await loadReadyBackup(db);
      if (!backup) return html(errorPage("Backup Cloud غير جاهزة."), 409);
      if (setup.targetEndpoint && setup.targetEndpoint !== backup.endpoint) {
        return html(errorPage("تغيّرت السحابة الاحتياطية. أنشئ رابط إعداد جديدًا."), 409);
      }

      const form = await req.formData();
      const managementToken = String(form.get("management_token") || "").trim();
      const githubToken = String(form.get("github_token") || "").trim();
      if (managementToken.length < 20 || managementToken.length > MAX_TOKEN_LENGTH) {
        return html(errorPage("صيغة Supabase Management Token غير صالحة."), 400);
      }
      if (githubToken.length < 20 || githubToken.length > MAX_GITHUB_TOKEN_LENGTH) {
        return html(errorPage("GitHub token غير صالح. استخدم Fine-grained token مؤقتًا بصلاحية Contents: read للمستودع فقط."), 400);
      }

      const projectRef = projectRefFromEndpoint(backup.endpoint);
      if (!projectRef) return html(errorPage("تعذر تحديد Project Ref للسحابة الاحتياطية."), 400);
      if (projectRef === projectRefFromEndpoint(primaryUrl)) {
        return html(errorPage("لا يمكن تجهيز H Cloud الأساسية كـStandby لنفسها."), 400);
      }

      let schema = await inspectStandbyBaseSchema(projectRef, managementToken);
      let baseSchemaBootstrapped = false;
      if (schema.state === "partial") {
        return html(errorPage(
          `مشروع Backup يحتوي H schema جزئية (${schema.present}/${REQUIRED_TABLES.length}). أوقف التجهيز وأكمل/نظّف المشروع أولًا لتجنب خلط بنية غير متوافقة.`,
        ), 409);
      }
      if (schema.state === "empty") {
        const baseSchemaSql = await fetchPinnedText(
          "cloud/standby/supabase/migrations/20260910_h_standby_base_schema.sql",
          githubToken,
        );
        await runManagementSql(projectRef, managementToken, baseSchemaSql);
        schema = await inspectStandbyBaseSchema(projectRef, managementToken);
        if (schema.state !== "complete") {
          throw new Error(`standby_base_schema_bootstrap_incomplete:${schema.present}`);
        }
        baseSchemaBootstrapped = true;
      }

      const [bootstrapSql, executionContractSql, replicaSql, healthIndex, healthPolicy, identitySecret] = await Promise.all([
        fetchPinnedText("cloud/standby/supabase/migrations/20260910_h_standby_runtime_bootstrap.sql", githubToken),
        fetchPinnedText("cloud/standby/supabase/migrations/20260910_h_standby_execution_contract.sql", githubToken),
        fetchPinnedText("cloud/standby/supabase/migrations/20260910_h_standby_replica_protocol_v2.sql", githubToken),
        fetchPinnedText("cloud/standby/supabase/h-standby-health/index.ts", githubToken),
        fetchPinnedText("cloud/standby/supabase/h-standby-health/standby-health-policy.ts", githubToken),
        loadIdentitySecret(db),
      ]);

      const runtimeSecret = randomUrlSafe(32);
      await runManagementSql(projectRef, managementToken, bootstrapSql);
      await runManagementSql(projectRef, managementToken, executionContractSql);
      await runManagementSql(projectRef, managementToken, replicaSql);
      await runManagementSql(
        projectRef,
        managementToken,
        `insert into public.h_runtime_config (key, secret_value, updated_at) values ('poll_secret', ${sqlLiteral(runtimeSecret)}, now()) on conflict (key) do update set secret_value = excluded.secret_value, updated_at = excluded.updated_at;`,
      );
      await seedAndVerifyStandbyIdentitySecret(projectRef, managementToken, identitySecret);
      await deployStandbyHealth(projectRef, managementToken, healthIndex, healthPolicy);

      const functionInventory = await deployAndVerifyStandbyFunctionInventory({
        projectRef,
        managementToken,
        githubToken,
        bundleRef: STANDBY_BUNDLE_REF,
        managementApi: MANAGEMENT_API,
        githubContentsBase: GITHUB_CONTENTS_BASE,
      });
      await attestStandbyExecutionFoundations(projectRef, managementToken, functionInventory.count);

      const health = await probeStandbyHealth(backup.endpoint, runtimeSecret);
      if (!health.ok) throw new Error(`standby_health_probe_${health.error}`);
      if (health.runtimeRole !== "standby" || health.hIdentity !== "H" || health.promoted === true) {
        throw new Error("standby_health_identity_mismatch");
      }
      if (health.executionContract !== "h_standby_execution_v1" || health.executionContractReady === true) {
        throw new Error("standby_execution_contract_stage_mismatch");
      }
      if (health.coreSchemaReady !== true || health.functionInventoryReady !== true || health.runtimeSecretReady !== true) {
        throw new Error("standby_execution_foundation_attestation_mismatch");
      }
      if (
        health.appIdentityRekeyReady === true ||
        health.whatsappIdentityRekeyReady === true ||
        health.aiCredentialsRekeyReady === true ||
        health.freeAiRouteReady === true ||
        health.paidAiBudgetContinuityReady === true ||
        health.promotionControlsReady === true
      ) {
        throw new Error("standby_execution_future_stage_unexpectedly_ready");
      }
      if (health.schedulerActive === true || health.autonomousOutboundActive === true) {
        throw new Error("standby_execution_passive_guard_violated");
      }
      if (health.executionRuntimeReady === true || health.standbyReady === true) {
        throw new Error("standby_execution_runtime_unexpectedly_enabled");
      }

      const encrypted = await encryptCloudCredential("supabase_runtime", runtimeSecret, primaryServiceRole);
      const now = new Date().toISOString();
      const { error: credentialError } = await db.from("h_runtime_cloud_credentials").upsert({
        id: RUNTIME_SECRET_CREDENTIAL_ID,
        provider: "supabase_runtime",
        secret_ciphertext: encrypted.ciphertext,
        secret_iv: encrypted.iv,
        secret_version: 1,
        metadata: {
          purpose: "h_standby_runtime_health",
          target_project_ref: projectRef,
          generated_by: FUNCTION_NAME,
          standby_base_schema_bootstrapped: baseSchemaBootstrapped,
          standby_execution_contract: "h_standby_execution_v1",
          standby_execution_contract_ready: false,
          standby_identity_secret_seeded: true,
          standby_identity_tables_ready: false,
          standby_replication_protocol_expected: "exact_mirror_v2",
          standby_ai_continuity_protocol_expected: "ai_continuity_v1",
          standby_function_inventory_ready: true,
          standby_function_inventory_count: functionInventory.count,
          standby_function_inventory_bundle_ref: functionInventory.bundleRef,
          management_token_persisted: false,
          github_token_persisted: false,
          bundle_source: "github_contents_api_authenticated",
          bundle_ref: STANDBY_BUNDLE_REF,
          configured_at: now,
        },
        updated_at: now,
      }, { onConflict: "id" });
      if (credentialError) throw credentialError;

      const { data: currentCloud, error: cloudReadError } = await db.from("h_runtime_cloud_registry")
        .select("metadata")
        .eq("id", BACKUP_CLOUD_ID)
        .eq("cloud_role", "backup")
        .maybeSingle();
      if (cloudReadError) throw cloudReadError;
      const currentMetadata = objectOrEmpty(currentCloud?.metadata);
      const { error: cloudUpdateError } = await db.from("h_runtime_cloud_registry").update({
        metadata: {
          ...currentMetadata,
          standby_runtime_provisioned: true,
          standby_runtime_ready: false,
          runtime_health_ok: true,
          standby_health_service_deployed: true,
          standby_execution_contract: "h_standby_execution_v1",
          standby_execution_contract_ready: false,
          standby_execution_runtime_ready: false,
          standby_execution_core_schema_ready: true,
          standby_execution_runtime_secret_ready: true,
          standby_identity_secret_seeded: true,
          standby_identity_tables_ready: false,
          standby_replication_protocol_expected: "exact_mirror_v2",
          standby_ai_continuity_protocol_expected: "ai_continuity_v1",
          standby_function_inventory_ready: true,
          standby_function_inventory_count: functionInventory.count,
          standby_function_inventory_bundle_ref: functionInventory.bundleRef,
          standby_app_identity_rekey_ready: false,
          standby_whatsapp_identity_rekey_ready: false,
          standby_ai_credentials_rekey_ready: false,
          standby_free_ai_route_ready: false,
          standby_paid_ai_budget_continuity_ready: false,
          standby_ai_continuity_fresh: false,
          standby_replication_ready: false,
          auto_failover_eligible: false,
          standby_project_ref: projectRef,
          standby_bundle_ref: STANDBY_BUNDLE_REF,
          standby_bundle_source: "github_contents_api_authenticated",
          standby_base_schema_bootstrapped: baseSchemaBootstrapped,
          standby_runtime_configured_at: now,
          management_token_persisted: false,
          github_token_persisted: false,
        },
        updated_at: now,
      }).eq("id", BACKUP_CLOUD_ID).eq("cloud_role", "backup");
      if (cloudUpdateError) throw cloudUpdateError;

      const { error: consumeError } = await db.from("h_runtime_cloud_setup")
        .update({ used_at: now })
        .eq("token_hash", setup.tokenHash)
        .is("used_at", null);
      if (consumeError) throw consumeError;

      return html(successPage(backup.endpoint, baseSchemaBootstrapped, functionInventory.count));
    }

    return json({ ok: false, error: "not_found" }, 404);
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    if (["/connect", "/provision"].includes(path)) {
      return html(errorPage("تعذر تجهيز Standby. لم يتم حفظ Supabase أو GitHub token ولم يتم تفعيل failover."), 500);
    }
    return json({ ok: false, error: "standby_runtime_config_failed" }, 500);
  }
});

async function validateSetupToken(
  db: DbClient,
  rawToken: string,
): Promise<({ ok: true; rawToken: string; tokenHash: string; targetEndpoint: string | null }) | { ok: false; error: string }> {
  const token = String(rawToken || "").trim();
  if (!token) return { ok: false, error: "الرابط ناقص." };
  const tokenHash = await setupTokenHash(token);
  const { data, error } = await db.from("h_runtime_cloud_setup")
    .select("token_hash,provider,expires_at,used_at,metadata")
    .eq("token_hash", tokenHash)
    .maybeSingle();
  if (error) throw error;
  if (!data || String(data.provider || "") !== "supabase") return { ok: false, error: "الرابط غير صالح." };
  if (data.used_at) return { ok: false, error: "تم استخدام هذا الرابط مسبقًا." };
  if (Date.parse(String(data.expires_at || "")) <= Date.now()) return { ok: false, error: "انتهت صلاحية الرابط." };
  const metadata = objectOrEmpty(data.metadata);
  if (String(metadata.purpose || "") !== "standby_runtime") return { ok: false, error: "الرابط ليس لإعداد Standby Runtime." };
  return {
    ok: true,
    rawToken: token,
    tokenHash,
    targetEndpoint: normalizeSupabaseEndpoint(String(metadata.target_endpoint || "")),
  };
}

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

async function inspectStandbyBaseSchema(
  projectRef: string,
  managementToken: string,
): Promise<{ state: StandbySchemaState; present: number }> {
  const countExpression = REQUIRED_TABLES
    .map((name) => `case when to_regclass('public.${name}') is not null then 1 else 0 end`)
    .join(" + ");
  const rows = await runManagementSql(
    projectRef,
    managementToken,
    `select (${countExpression})::integer as present;`,
    true,
  );
  const present = Number(Array.isArray(rows) ? rows[0]?.present : NaN);
  if (!Number.isInteger(present) || present < 0 || present > REQUIRED_TABLES.length) {
    throw new Error("standby_base_schema_preflight_invalid");
  }
  return {
    state: present === 0 ? "empty" : present === REQUIRED_TABLES.length ? "complete" : "partial",
    present,
  };
}

async function seedAndVerifyStandbyIdentitySecret(
  projectRef: string,
  managementToken: string,
  identitySecret: string,
): Promise<void> {
  const value = String(identitySecret || "").trim();
  if (!value) throw new Error("primary_identity_secret_missing");
  const literal = sqlLiteral(value);
  await runManagementSql(
    projectRef,
    managementToken,
    `insert into public.h_runtime_config (key, secret_value, updated_at) values ('identity_secret', ${literal}, now()) on conflict (key) do nothing;`,
  );
  const rows = await runManagementSql(
    projectRef,
    managementToken,
    `select coalesce(secret_value = ${literal}, false) as matches from public.h_runtime_config where key = 'identity_secret';`,
    true,
  );
  if (!Array.isArray(rows) || rows[0]?.matches !== true) {
    throw new Error("standby_identity_secret_mismatch");
  }
}

async function attestStandbyExecutionFoundations(
  projectRef: string,
  managementToken: string,
  functionCount: number,
): Promise<void> {
  if (!Number.isInteger(functionCount) || functionCount <= 0) throw new Error("standby_function_inventory_count_invalid");
  await runManagementSql(
    projectRef,
    managementToken,
    `update public.h_runtime_state
        set value = coalesce(value, '{}'::jsonb) || jsonb_build_object(
          'core_schema_ready', true,
          'function_inventory_ready', true,
          'runtime_secret_ready', true,
          'identity_secret_seeded', true,
          'identity_tables_ready', false,
          'function_inventory_count', ${functionCount},
          'function_inventory_bundle_ref', '${STANDBY_BUNDLE_REF}',
          'function_inventory_validated_at', now(),
          'scheduler_active', false,
          'autonomous_outbound_active', false,
          'execution_runtime_ready', false
        ),
        updated_at = now()
      where key = 'standby_execution'
        and value->>'contract' = 'h_standby_execution_v1'
        and value->>'mode' = 'passive_preflight';`,
  );
  const rows = await runManagementSql(
    projectRef,
    managementToken,
    `select
       coalesce((value->>'core_schema_ready')::boolean, false) as core_schema_ready,
       coalesce((value->>'function_inventory_ready')::boolean, false) as function_inventory_ready,
       coalesce((value->>'runtime_secret_ready')::boolean, false) as runtime_secret_ready,
       coalesce((value->>'identity_secret_seeded')::boolean, false) as identity_secret_seeded,
       coalesce((value->>'identity_tables_ready')::boolean, false) as identity_tables_ready,
       coalesce((value->>'scheduler_active')::boolean, false) as scheduler_active,
       coalesce((value->>'autonomous_outbound_active')::boolean, false) as autonomous_outbound_active,
       coalesce((value->>'execution_runtime_ready')::boolean, false) as execution_runtime_ready
     from public.h_runtime_state
     where key = 'standby_execution';`,
    true,
  );
  const row = Array.isArray(rows) ? rows[0] : null;
  if (
    row?.core_schema_ready !== true ||
    row?.function_inventory_ready !== true ||
    row?.runtime_secret_ready !== true ||
    row?.identity_secret_seeded !== true ||
    row?.identity_tables_ready === true ||
    row?.scheduler_active === true ||
    row?.autonomous_outbound_active === true ||
    row?.execution_runtime_ready === true
  ) {
    throw new Error("standby_execution_foundation_attestation_failed");
  }
}

async function runManagementSql(projectRef: string, managementToken: string, query: string, readOnly = false): Promise<any> {
  const response = await fetch(`${MANAGEMENT_API}/projects/${encodeURIComponent(projectRef)}/database/query`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${managementToken}`,
      "Content-Type": "application/json",
      Accept: "application/json",
      "Cache-Control": "no-store",
    },
    body: JSON.stringify({ query, read_only: readOnly }),
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error(`management_sql_${response.status}`);
  return body;
}

async function deployStandbyHealth(
  projectRef: string,
  managementToken: string,
  indexSource: string,
  policySource: string,
): Promise<void> {
  const form = new FormData();
  form.append("metadata", JSON.stringify({ name: "h-standby-health", entrypoint_path: "index.ts", verify_jwt: false }));
  form.append("file", new Blob([indexSource], { type: "application/typescript" }), "index.ts");
  form.append("file", new Blob([policySource], { type: "application/typescript" }), "standby-health-policy.ts");
  const response = await fetch(`${MANAGEMENT_API}/projects/${encodeURIComponent(projectRef)}/functions/deploy?slug=h-standby-health`, {
    method: "POST",
    headers: { Authorization: `Bearer ${managementToken}`, "Cache-Control": "no-store" },
    body: form,
  });
  if (!response.ok) throw new Error(`management_function_deploy_${response.status}`);
}

async function probeStandbyHealth(endpoint: string, runtimeSecret: string): Promise<any> {
  const response = await fetch(`${endpoint}/functions/v1/h-standby-health`, {
    method: "POST",
    headers: { "x-h-runtime-secret": runtimeSecret, "Content-Type": "application/json", "Cache-Control": "no-store" },
    body: "{}",
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) return { ok: false, error: `http_${response.status}` };
  return body;
}

async function fetchPinnedText(path: string, githubToken: string): Promise<string> {
  const encodedPath = path.split("/").map((segment) => encodeURIComponent(segment)).join("/");
  const response = await fetch(`${GITHUB_CONTENTS_BASE}/${encodedPath}?ref=${STANDBY_BUNDLE_REF}`, {
    headers: {
      Authorization: `Bearer ${githubToken}`,
      Accept: "application/vnd.github.raw+json",
      "X-GitHub-Api-Version": "2022-11-28",
      "Cache-Control": "no-store",
    },
  });
  if (!response.ok) throw new Error(`standby_bundle_fetch_${response.status}`);
  const text = await response.text();
  if (!text.trim()) throw new Error("standby_bundle_empty");
  return text;
}

async function encryptCloudCredential(provider: string, value: string, rootSecret: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`h-cloud-credential-aes-v1:${provider}:${rootSecret}`));
  const key = await crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(value));
  return { ciphertext: base64Url(new Uint8Array(encrypted)), iv: base64Url(iv) };
}

function projectRefFromEndpoint(endpoint: string): string | null {
  try {
    const url = new URL(endpoint);
    const match = url.hostname.match(/^([a-z0-9-]{8,64})[.]supabase[.]co$/);
    return match?.[1] || null;
  } catch {
    return null;
  }
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

async function setupTokenHash(token: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`h-cloud-setup-v1:${token}`));
  return base64Url(new Uint8Array(digest));
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

function sqlLiteral(value: string): string {
  return `'${String(value || "").replace(/'/g, "''")}'`;
}

function objectOrEmpty(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function connectPage(base: string, setup: string, endpoint: string) {
  const action = `${base}/provision?setup=${encodeURIComponent(setup)}`;
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("تجهيز Standby لـ H")}</head><body><main><h1>تجهيز Standby</h1><p>الهدف: <code>${escapeHtml(endpoint)}</code></p><p>استخدم Supabase Management Token مؤقتًا بصلاحيات <code>database:write</code> و<code>edge_functions:write</code> و<code>edge_functions:read</code>. يستخدم H هذه الصلاحيات للتحقق من schema ونشر Functions ثم قراءة حالتها الفعلية، ولن يُحفظ token.</p><p>استخدم GitHub Fine-grained token مؤقتًا بصلاحية <code>Contents: read</code> على <code>Malik05255/VibeApp-Fs</code> فقط. لن يُحفظ هذا token أيضًا.</p><p>إذا كان المشروع جديدًا وفارغًا من H، سيُنشئ H Base Schema مخصصة للـStandby تلقائيًا. إذا وجد Schema جزئية فسيتوقف بدل خلط بنية غير متوافقة.</p><p class="warn">يُزرع مفتاح الهوية الدائم وتُجهّز بنية <code>exact_mirror_v2</code> للهوية المشفّرة و<code>ai_continuity_v1</code> لإعادة تشفير مزودات AI. بيانات Google/WhatsApp وAI لا تُعتبر جاهزة حتى تنجح أول عملية replication. Scheduler وAutonomous Outbound وAuto‑Failover تبقى مقفلة حتى اكتمال بقية Execution Contract.</p><form method="post" action="${escapeHtml(action)}"><label>Supabase Management Token<input type="password" name="management_token" autocomplete="off" required maxlength="4096"></label><label>GitHub read-only token<input type="password" name="github_token" autocomplete="off" required maxlength="512"></label><button type="submit">تحقق وجهّز Standby</button></form></main></body></html>`;
}

function successPage(endpoint: string, bootstrapped: boolean, functionCount: number) {
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("تم تجهيز Standby")}</head><body><main><h1>تم تجهيز أساس التنفيذ ✅</h1><p>تم تجهيز <code>${escapeHtml(endpoint)}</code> كـStandby سلبية${bootstrapped ? " وإنشاء H Standby Base Schema تلقائيًا" : " باستخدام H schema الموجودة والمتوافقة"}، ونشر Health Probe و${functionCount} Function تنفيذية والتحقق من أنها <code>ACTIVE</code>.</p><p>تم زرع <code>identity_secret</code> وتجهيز RPC <code>exact_mirror_v2</code> وAI continuity re-key. تبقى هويات Google/WhatsApp واعتمادات AI غير جاهزة حتى تنجح أول مزامنة فعلية. Execution Runtime وScheduler وOutbound وAuto‑Failover ما زالت متوقفة.</p><p>تم استخدام Supabase وGitHub tokens لهذه العملية فقط ولم يتم حفظهما.</p></main></body></html>`;
}

function errorPage(message: string) {
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("تعذر تجهيز Standby")}</head><body><main><h1>تعذر إكمال التجهيز</h1><p>${escapeHtml(message)}</p></main></body></html>`;
}

function pageHead(title: string) {
  return `<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><title>${escapeHtml(title)}</title><style>body{font-family:system-ui;background:#f7f7f7;margin:0;color:#171717}main{max-width:720px;margin:36px auto;padding:24px;background:#fff;border-radius:16px}label{display:block;margin:14px 0}input,button{font:inherit;box-sizing:border-box;padding:12px;margin:7px 0;width:100%}.warn{font-weight:700}code{direction:ltr}</style>`;
}

function publicBase(primaryUrl: string) { return `${primaryUrl}/functions/v1/${FUNCTION_NAME}`; }
function routePath(pathname: string): string { let path = pathname || "/"; for (const marker of [`/functions/v1/${FUNCTION_NAME}`, `/${FUNCTION_NAME}`]) { const index = path.indexOf(marker); if (index >= 0) { path = path.slice(index + marker.length) || "/"; break; } } return path.startsWith("/") ? path : `/${path}`; }
function safeEnv(name: string): string { return String(Deno.env.get(name) || ""); }
function compactErrorCode(error: unknown): string { const raw = (error instanceof Error ? error.message : String(error || "unknown_error")).toLowerCase().replace(/[^a-z0-9_:-]+/g, "_"); return raw.slice(0, 160) || "standby_runtime_config_failed"; }
function escapeHtml(value: string) { return String(value || "").replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char] || char)); }
function json(value: unknown, status = 200) { return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } }); }
function html(value: string, status = 200) { return new Response(value, { status, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", "X-Frame-Options": "DENY", "Referrer-Policy": "no-referrer" } }); }
