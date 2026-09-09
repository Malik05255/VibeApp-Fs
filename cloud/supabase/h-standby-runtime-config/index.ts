import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const FUNCTION_NAME = "h-standby-runtime-config";
const BACKUP_CLOUD_ID = "h_backup_supabase_storage";
const RUNTIME_SECRET_CREDENTIAL_ID = "h_backup_supabase_runtime_secret";
const STANDBY_BUNDLE_REF = "e2d5d33d9689e6eec92c51666eb6c45be3c9c292";
const GITHUB_RAW_BASE = `https://raw.githubusercontent.com/Malik05255/VibeApp-Fs/${STANDBY_BUNDLE_REF}`;
const MAX_TOKEN_LENGTH = 4096;
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

type SetupRow = {
  token_hash: string;
  expires_at: string;
  used_at: string | null;
  metadata: Record<string, unknown>;
};

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
      if (managementToken.length < 20 || managementToken.length > MAX_TOKEN_LENGTH) {
        return html(errorPage("صيغة Supabase Management Token غير صالحة."), 400);
      }

      const projectRef = projectRefFromEndpoint(backup.endpoint);
      if (!projectRef) return html(errorPage("تعذر تحديد Project Ref للسحابة الاحتياطية."), 400);
      if (projectRef === projectRefFromEndpoint(primaryUrl)) {
        return html(errorPage("لا يمكن تجهيز H Cloud الأساسية كـStandby لنفسها."), 400);
      }

      const schemaCheck = await checkStandbyBaseSchema(projectRef, managementToken);
      if (!schemaCheck.ok) {
        return html(errorPage(schemaCheck.message), 409);
      }

      const [bootstrapSql, replicaSql, healthIndex, healthPolicy] = await Promise.all([
        fetchPinnedText("cloud/standby/supabase/migrations/20260910_h_standby_runtime_bootstrap.sql"),
        fetchPinnedText("cloud/standby/supabase/migrations/20260910_h_standby_replica_protocol.sql"),
        fetchPinnedText("cloud/standby/supabase/h-standby-health/index.ts"),
        fetchPinnedText("cloud/standby/supabase/h-standby-health/standby-health-policy.ts"),
      ]);

      const runtimeSecret = randomUrlSafe(32);
      await runManagementSql(projectRef, managementToken, bootstrapSql);
      await runManagementSql(projectRef, managementToken, replicaSql);
      await runManagementSql(
        projectRef,
        managementToken,
        `insert into public.h_runtime_config (key, secret_value, updated_at) values ('poll_secret', '${runtimeSecret}', now()) on conflict (key) do update set secret_value = excluded.secret_value, updated_at = excluded.updated_at;`,
      );
      await deployStandbyHealth(projectRef, managementToken, healthIndex, healthPolicy);

      const health = await probeStandbyHealth(backup.endpoint, runtimeSecret);
      if (!health.ok) {
        throw new Error(`standby_health_probe_${health.error}`);
      }
      if (health.runtimeRole !== "standby" || health.hIdentity !== "H" || health.promoted === true) {
        throw new Error("standby_health_identity_mismatch");
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
          management_token_persisted: false,
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
          standby_replication_ready: false,
          auto_failover_eligible: false,
          standby_project_ref: projectRef,
          standby_bundle_ref: STANDBY_BUNDLE_REF,
          standby_runtime_configured_at: now,
          management_token_persisted: false,
        },
        updated_at: now,
      }).eq("id", BACKUP_CLOUD_ID).eq("cloud_role", "backup");
      if (cloudUpdateError) throw cloudUpdateError;

      const { error: consumeError } = await db.from("h_runtime_cloud_setup")
        .update({ used_at: now })
        .eq("token_hash", setup.tokenHash)
        .is("used_at", null);
      if (consumeError) throw consumeError;

      return html(successPage(backup.endpoint));
    }

    return json({ ok: false, error: "not_found" }, 404);
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    if (["/connect", "/provision"].includes(path)) {
      return html(errorPage("تعذر تجهيز Standby Runtime. لم يتم حفظ Management Token."), 500);
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

async function checkStandbyBaseSchema(
  projectRef: string,
  managementToken: string,
): Promise<{ ok: true } | { ok: false; message: string }> {
  const checks = REQUIRED_TABLES.map((name) => `to_regclass('public.${name}') is not null`).join(" and ");
  const rows = await runManagementSql(projectRef, managementToken, `select (${checks}) as ready;`, true);
  const ready = Array.isArray(rows) && rows[0]?.ready === true;
  if (ready) return { ok: true };
  return {
    ok: false,
    message: "مشروع Backup لا يحتوي بعد على H Standby base schema. لن يتم تطبيق أي إعداد جزئي عليه.",
  };
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
  form.append("metadata", JSON.stringify({
    name: "h-standby-health",
    entrypoint_path: "index.ts",
    verify_jwt: false,
  }));
  form.append("file", new Blob([indexSource], { type: "application/typescript" }), "index.ts");
  form.append("file", new Blob([policySource], { type: "application/typescript" }), "standby-health-policy.ts");
  const response = await fetch(
    `${MANAGEMENT_API}/projects/${encodeURIComponent(projectRef)}/functions/deploy?slug=h-standby-health`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${managementToken}`, "Cache-Control": "no-store" },
      body: form,
    },
  );
  if (!response.ok) throw new Error(`management_function_deploy_${response.status}`);
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
  if (!response.ok) return { ok: false, error: `http_${response.status}` };
  return body;
}

async function fetchPinnedText(path: string): Promise<string> {
  const response = await fetch(`${GITHUB_RAW_BASE}/${path}`, {
    headers: { Accept: "text/plain", "Cache-Control": "no-store" },
  });
  if (!response.ok) throw new Error(`standby_bundle_fetch_${response.status}`);
  const text = await response.text();
  if (!text.trim()) throw new Error("standby_bundle_empty");
  return text;
}

async function encryptCloudCredential(provider: string, value: string, rootSecret: string) {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(`h-cloud-credential-aes-v1:${provider}:${rootSecret}`),
  );
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

function objectOrEmpty(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function connectPage(base: string, setup: string, endpoint: string) {
  const action = `${base}/provision?setup=${encodeURIComponent(setup)}`;
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("تجهيز Standby Runtime لـ H")}</head><body><main><h1>تجهيز Standby Runtime</h1><p>الهدف: <code>${escapeHtml(endpoint)}</code></p><p>استخدم Supabase Management Token مؤقتًا بصلاحيات <code>database:write</code> و<code>edge_functions:write</code>. سيُستخدم في هذه العملية فقط ولن يُحفظ في H Cloud.</p><p class="warn">لن يتم تفعيل Auto‑Failover بعد هذه الخطوة. يلزم أول Replication ناجحة واختبار صحة حديث أولًا.</p><form method="post" action="${escapeHtml(action)}"><label>Supabase Management Token<input type="password" name="management_token" autocomplete="off" required maxlength="4096"></label><button type="submit">تحقق وجهّز Standby</button></form></main></body></html>`;
}

function successPage(endpoint: string) {
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("Standby Runtime جاهزة للمزامنة")}</head><body><main><h1>تم تجهيز Standby Runtime ✅</h1><p>تم تجهيز <code>${escapeHtml(endpoint)}</code> كـStandby سلبية، ونشر Health Probe وإنشاء Runtime Secret مستقل.</p><p>Auto‑Failover ما زال متوقفًا. H سيعتبرها جاهزة فقط بعد أول Exact‑Mirror Replication ناجحة وصحية.</p></main></body></html>`;
}

function errorPage(message: string) {
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("تعذر تجهيز Standby Runtime")}</head><body><main><h1>تعذر إكمال التجهيز</h1><p>${escapeHtml(message)}</p></main></body></html>`;
}

function pageHead(title: string) {
  return `<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><title>${escapeHtml(title)}</title><style>body{font-family:system-ui;background:#f7f7f7;margin:0;color:#171717}main{max-width:720px;margin:36px auto;padding:24px;background:#fff;border-radius:16px}label{display:block;margin:14px 0}input,button{font:inherit;box-sizing:border-box;padding:12px;margin:7px 0;width:100%}.warn{font-weight:700}code{direction:ltr}</style>`;
}

function publicBase(primaryUrl: string) {
  return `${primaryUrl}/functions/v1/${FUNCTION_NAME}`;
}

function routePath(pathname: string): string {
  let path = pathname || "/";
  for (const marker of [`/functions/v1/${FUNCTION_NAME}`, `/${FUNCTION_NAME}`]) {
    const index = path.indexOf(marker);
    if (index >= 0) {
      path = path.slice(index + marker.length) || "/";
      break;
    }
  }
  return path.startsWith("/") ? path : `/${path}`;
}

function safeEnv(name: string): string {
  return String(Deno.env.get(name) || "");
}

function compactErrorCode(error: unknown): string {
  const raw = (error instanceof Error ? error.message : String(error || "unknown_error"))
    .toLowerCase().replace(/[^a-z0-9_:-]+/g, "_");
  return raw.slice(0, 160) || "standby_runtime_config_failed";
}

function escapeHtml(value: string) {
  return String(value || "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[char] || char));
}

function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

function html(value: string, status = 200) {
  return new Response(value, {
    status,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Frame-Options": "DENY",
      "Referrer-Policy": "no-referrer",
    },
  });
}
