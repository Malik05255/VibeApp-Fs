import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const FUNCTION_NAME = "h-cloud-backup-config";
const BUCKET_NAME = "h-backups";
const BACKUP_CLOUD_ID = "h_backup_supabase_storage";
const BACKUP_CREDENTIAL_ID = "h_backup_supabase_storage";
const MAX_KEY_LENGTH = 4096;

type DbClient = any;

type ValidatedSetup = {
  ok: true;
  rawToken: string;
  tokenHash: string;
  expiresAt: string;
};

Deno.serve(async (req: Request) => {
  const supabaseUrl = safeEnv("SUPABASE_URL").replace(/\/$/, "");
  const serviceRole = safeEnv("SUPABASE_SERVICE_ROLE_KEY").trim();
  if (!supabaseUrl || !serviceRole) return json({ ok: false, error: "runtime_unavailable" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const url = new URL(req.url);
  const path = routePath(url.pathname);

  try {
    if (req.method === "GET" && path === "/connect") {
      const setup = await validateSetupToken(db, url.searchParams.get("setup") || "");
      if (!setup.ok) return html(errorPage(setup.error), 400);
      return html(connectPage(publicBase(supabaseUrl), setup.rawToken));
    }

    if (req.method === "POST" && path === "/save") {
      const setup = await validateSetupToken(db, url.searchParams.get("setup") || "");
      if (!setup.ok) return html(errorPage(setup.error), 400);
      const form = await req.formData();
      const endpoint = normalizeSupabaseEndpoint(String(form.get("project_url") || ""));
      const backupKey = String(form.get("service_role_key") || "").trim();
      if (!endpoint) return html(errorPage("رابط مشروع Supabase الاحتياطي غير صالح."), 400);
      if (endpoint === supabaseUrl) return html(errorPage("يجب أن تكون السحابة الاحتياطية مشروعًا مختلفًا عن H Cloud الأساسي."), 400);
      if (backupKey.length < 40 || backupKey.length > MAX_KEY_LENGTH) {
        return html(errorPage("صيغة Service Role Key غير صالحة."), 400);
      }

      const probe = await validateWritableBackupStorage(endpoint, backupKey);
      if (!probe.ok) return html(errorPage(`فشل اختبار السحابة الاحتياطية: ${probe.error}`), 400);

      const encrypted = await encryptCloudCredential("supabase", backupKey, serviceRole);
      const now = new Date().toISOString();
      const credentialMetadata = {
        purpose: "h_storage_backup",
        bucket: BUCKET_NAME,
        validated_at: now,
        validation: "storage_list_create_write_delete_probe",
        raw_secret_exposed: false,
      };

      const { error: credentialError } = await db.from("h_runtime_cloud_credentials").upsert({
        id: BACKUP_CREDENTIAL_ID,
        provider: "supabase",
        secret_ciphertext: encrypted.ciphertext,
        secret_iv: encrypted.iv,
        secret_version: 1,
        metadata: credentialMetadata,
        updated_at: now,
      }, { onConflict: "id" });
      if (credentialError) throw credentialError;

      const { error: registryError } = await db.from("h_runtime_cloud_registry").upsert({
        id: BACKUP_CLOUD_ID,
        provider: "supabase",
        cloud_role: "backup",
        endpoint,
        credential_id: BACKUP_CREDENTIAL_ID,
        enabled: true,
        ready: true,
        priority: 100,
        last_health_at: now,
        last_health_ok: true,
        last_error_code: null,
        metadata: {
          managed_by: "h",
          storage_backup_ready: true,
          bucket: BUCKET_NAME,
          connection_validated: true,
          write_probe_ok: true,
          auto_failover_eligible: false,
          standby_runtime_ready: false,
          validated_at: now,
        },
        updated_at: now,
      }, { onConflict: "id" });
      if (registryError) throw registryError;

      const { error: tokenError } = await db.from("h_runtime_cloud_setup")
        .update({ used_at: now })
        .eq("token_hash", setup.tokenHash)
        .is("used_at", null);
      if (tokenError) throw tokenError;

      return html(successPage(endpoint));
    }

    return json({ ok: false, error: "not_found" }, 404);
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, errorMessage(error));
    if (["/connect", "/save"].includes(path)) return html(errorPage("تعذر إكمال إعداد السحابة الاحتياطية."), 500);
    return json({ ok: false, error: "backup_config_failed" }, 500);
  }
});

async function validateSetupToken(db: DbClient, rawToken: string): Promise<ValidatedSetup | { ok: false; error: string }> {
  const token = String(rawToken || "").trim();
  if (!token) return { ok: false, error: "الرابط ناقص." };
  const tokenHash = await setupTokenHash(token);
  const { data, error } = await db.from("h_runtime_cloud_setup")
    .select("provider,expires_at,used_at")
    .eq("token_hash", tokenHash)
    .maybeSingle();
  if (error) throw error;
  if (!data || String(data.provider || "") !== "supabase") return { ok: false, error: "الرابط غير صالح." };
  if (data.used_at) return { ok: false, error: "تم استخدام هذا الرابط مسبقًا." };
  const expiresAt = String(data.expires_at || "");
  if (!expiresAt || Date.parse(expiresAt) <= Date.now()) return { ok: false, error: "انتهت صلاحية الرابط. أنشئ رابطًا جديدًا من التطبيق." };
  return { ok: true, rawToken: token, tokenHash, expiresAt };
}

async function validateWritableBackupStorage(
  endpoint: string,
  serviceRoleKey: string,
): Promise<{ ok: true } | { ok: false; error: string }> {
  const headers = {
    apikey: serviceRoleKey,
    Authorization: `Bearer ${serviceRoleKey}`,
    Accept: "application/json",
  };

  const list = await fetch(`${endpoint}/storage/v1/bucket`, { headers });
  if (!list.ok) return { ok: false, error: `storage_admin_${list.status}` };
  const buckets = await list.json().catch(() => []);
  const exists = Array.isArray(buckets) && buckets.some((item: any) => String(item?.id || item?.name || "") === BUCKET_NAME);

  if (!exists) {
    const create = await fetch(`${endpoint}/storage/v1/bucket`, {
      method: "POST",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify({ id: BUCKET_NAME, name: BUCKET_NAME, public: false }),
    });
    if (!create.ok) return { ok: false, error: `bucket_create_${create.status}` };
  }

  const probePath = `_h_probe/${crypto.randomUUID()}.txt`;
  const objectUrl = `${endpoint}/storage/v1/object/${BUCKET_NAME}/${probePath}`;
  const upload = await fetch(objectUrl, {
    method: "POST",
    headers: {
      ...headers,
      "Content-Type": "text/plain; charset=utf-8",
      "x-upsert": "true",
    },
    body: `H backup validation ${new Date().toISOString()}`,
  });
  if (!upload.ok) return { ok: false, error: `write_probe_${upload.status}` };

  const remove = await fetch(objectUrl, { method: "DELETE", headers });
  if (!remove.ok) return { ok: false, error: `delete_probe_${remove.status}` };
  return { ok: true };
}

async function encryptCloudCredential(provider: string, value: string, rootSecret: string) {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(`h-cloud-credential-aes-v1:${provider}:${rootSecret}`),
  );
  const key = await crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    key,
    new TextEncoder().encode(value),
  );
  return {
    ciphertext: base64Url(new Uint8Array(encrypted)),
    iv: base64Url(iv),
  };
}

async function setupTokenHash(token: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(`h-cloud-setup-v1:${token}`),
  );
  return base64Url(new Uint8Array(digest));
}

function normalizeSupabaseEndpoint(raw: string): string | null {
  try {
    const url = new URL(raw.trim());
    if (url.protocol !== "https:") return null;
    if (!url.hostname || !url.hostname.endsWith(".supabase.co")) return null;
    if (url.username || url.password || url.search || url.hash) return null;
    return `${url.protocol}//${url.host}`;
  } catch {
    return null;
  }
}

function connectPage(base: string, setup: string) {
  const action = `${base}/save?setup=${encodeURIComponent(setup)}`;
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("إضافة سحابة احتياطية لـ H")}</head><body><main><h1>إضافة Backup Cloud</h1><p>أدخل بيانات مشروع Supabase احتياطي منفصل. H سيختبر صلاحية التخزين بإنشاء/استخدام bucket خاصة ثم كتابة وحذف ملف اختبار صغير. لن تُسجل السحابة كجاهزة إذا فشل الاختبار.</p><p class="warn">لن يدخل Service Role Key إلى التطبيق. يُرسل عبر HTTPS لهذه الصفحة، ثم يُشفّر في H Cloud بعد نجاح الاختبار.</p><form method="post" action="${escapeHtml(action)}"><label>Project URL<input type="url" name="project_url" placeholder="https://xxxx.supabase.co" required></label><label>Service Role Key<input type="password" name="service_role_key" autocomplete="off" required></label><button type="submit">اختبر وأضف السحابة</button></form><p>هذه المرحلة للنسخ الاحتياطي فقط. Auto‑Failover لن يُفعّل حتى يكون هناك Standby Runtime متحقق منه.</p></main></body></html>`;
}

function successPage(endpoint: string) {
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("تمت إضافة Backup Cloud")}</head><body><main><h1>تمت إضافة السحابة الاحتياطية ✅</h1><p>نجح اختبار الاتصال والكتابة والحذف على <code>${escapeHtml(endpoint)}</code>.</p><p>السحابة جاهزة الآن لاستقبال نسخ H الاحتياطية. Auto‑Failover ما زال مقفولًا حتى تجهيز Standby Runtime والتحقق منه.</p></main></body></html>`;
}

function errorPage(message: string) {
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("تعذر إضافة Backup Cloud")}</head><body><main><h1>تعذر إكمال الربط</h1><p>${escapeHtml(message)}</p></main></body></html>`;
}

function pageHead(title: string) {
  return `<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escapeHtml(title)}</title><style>body{font-family:system-ui;background:#f7f7f7;margin:0;color:#171717}main{max-width:720px;margin:36px auto;padding:24px;background:#fff;border-radius:16px}label{display:block;margin:14px 0}input,button{font:inherit;box-sizing:border-box;padding:12px;margin:7px 0;width:100%}.warn{font-weight:700}code{direction:ltr}</style>`;
}

function publicBase(supabaseUrl: string) {
  return `${supabaseUrl}/functions/v1/${FUNCTION_NAME}`;
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

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function escapeHtml(value: string) {
  return String(value || "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  }[char] || char));
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
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

function html(value: string, status = 200) {
  return new Response(value, {
    status,
    headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", "X-Frame-Options": "DENY" },
  });
}
