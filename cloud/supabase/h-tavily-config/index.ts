import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const FUNCTION_NAME = "h-tavily-config";
const CREDENTIAL_ID = "tavily_default";
const PROVIDER = "tavily";
const PURPOSE = "tavily";
const SETUP_TTL_MS = 10 * 60 * 1000;
const TAVILY_USAGE_URL = "https://api.tavily.com/usage";
const FREE_PLAN = "researcher";

Deno.serve(async (req: Request) => {
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRole) return json({ ok: false, error: "Supabase runtime credentials unavailable" }, 500);

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const url = new URL(req.url);
  const path = normalizePath(url.pathname);

  try {
    if (req.method === "GET" && (path === "/" || path === "/status" || path === "/health")) {
      return json(await getStatus(db));
    }

    if (req.method === "POST" && path === "/setup-link") {
      if (!(await isAdminRequest(db, req))) return json({ ok: false, error: "Unauthorized" }, 401);
      await cleanupSetupLinks(db);
      const token = randomToken(32);
      const tokenHash = await sha256Base64Url(token);
      const expiresAt = new Date(Date.now() + SETUP_TTL_MS).toISOString();
      const { error } = await db.from("h_runtime_ai_setup_links").insert({
        token_hash: tokenHash,
        purpose: PURPOSE,
        expires_at: expiresAt,
      });
      if (error) throw error;
      const connectUrl = `${functionBaseUrl(url)}/connect?setup=${encodeURIComponent(token)}`;
      return json({ ok: true, connectUrl, expiresAt, singleUse: true, provider: PROVIDER, freeOnly: true });
    }

    if (req.method === "GET" && path === "/connect") {
      const setup = String(url.searchParams.get("setup") || "").trim();
      const validation = await validateSetupToken(db, setup);
      if (!validation.ok) return html(renderError(validation.error || "الرابط غير صالح أو منتهي."), 400);
      return html(renderConnectPage(setup));
    }

    if (req.method === "POST" && path === "/save") {
      const setup = String(url.searchParams.get("setup") || "").trim();
      const validation = await validateSetupToken(db, setup);
      if (!validation.ok) return html(renderError(validation.error || "الرابط غير صالح أو منتهي."), 400);

      const form = await req.formData();
      const apiKey = String(form.get("api_key") || "").trim();
      if (apiKey.length < 12 || apiKey.length > 500) return html(renderError("مفتاح Tavily غير صالح."), 400);

      const usage = await loadTavilyUsage(apiKey);
      const plan = String(usage?.account?.current_plan || "").trim();
      if (plan.toLowerCase() !== FREE_PLAN) {
        return html(renderError(`H مضبوط على البحث المجاني فقط. خطة Tavily الحالية: ${escapeHtml(plan || "غير معروفة")}. استخدم خطة Researcher المجانية.`), 400);
      }

      const encrypted = await encryptSecret(apiKey);
      const now = new Date().toISOString();
      const keyUsage = finiteNumber(usage?.key?.usage, usage?.account?.plan_usage, 0);
      const keyLimit = finiteNumber(usage?.key?.limit, usage?.account?.plan_limit, 1000);
      const { error: credentialError } = await db.from("h_runtime_ai_credentials").upsert({
        id: CREDENTIAL_ID,
        provider: PROVIDER,
        secret_ciphertext: encrypted.ciphertext,
        secret_iv: encrypted.iv,
        secret_version: 1,
        selected_model: null,
        model_verified_at: null,
        oauth_metadata: {
          free_only: true,
          plan,
          usage_at_connect: keyUsage,
          limit_at_connect: keyLimit,
          encryption_source: "supabase_service_role_derived_v1",
        },
        connected_at: now,
        updated_at: now,
      }, { onConflict: "id" });
      if (credentialError) throw credentialError;

      const { error: usedError } = await db.from("h_runtime_ai_setup_links")
        .update({ used_at: now })
        .eq("token_hash", validation.tokenHash)
        .eq("purpose", PURPOSE)
        .is("used_at", null);
      if (usedError) throw usedError;

      await db.from("h_runtime_state").upsert({
        key: "web_search",
        value: {
          provider: PROVIDER,
          connected: true,
          ready: keyUsage < keyLimit,
          free_only: true,
          plan,
          usage: keyUsage,
          limit: keyLimit,
          connected_at: now,
          encryption_source: "supabase_service_role_derived_v1",
        },
        updated_at: now,
      }, { onConflict: "key" });

      return html(renderSuccess(plan, keyUsage, keyLimit));
    }

    if (req.method === "POST" && path === "/disconnect") {
      if (!(await isAdminRequest(db, req))) return json({ ok: false, error: "Unauthorized" }, 401);
      await db.from("h_runtime_ai_credentials").delete().eq("id", CREDENTIAL_ID).eq("provider", PROVIDER);
      await db.from("h_runtime_state").upsert({
        key: "web_search",
        value: { provider: PROVIDER, connected: false, ready: false, free_only: true },
        updated_at: new Date().toISOString(),
      }, { onConflict: "key" });
      return json({ ok: true, disconnected: true });
    }

    return json({ ok: false, error: "Not found" }, 404);
  } catch (error) {
    console.error("H Tavily config failed", error);
    const message = errorMessage(error);
    if (path === "/connect" || path === "/save") return html(renderError(message), 500);
    return json({ ok: false, error: message }, 500);
  }
});

async function getStatus(db: any) {
  const { data: row } = await db.from("h_runtime_ai_credentials")
    .select("secret_ciphertext,secret_iv,secret_version,oauth_metadata,connected_at,updated_at")
    .eq("id", CREDENTIAL_ID)
    .eq("provider", PROVIDER)
    .maybeSingle();
  if (!row) return { ok: true, provider: PROVIDER, connected: false, ready: false, freeOnly: true };

  try {
    if (Number(row.secret_version || 1) !== 1) throw new Error("Unsupported credential version");
    const apiKey = (await decryptSecret(String(row.secret_ciphertext), String(row.secret_iv))).trim();
    const usage = await loadTavilyUsage(apiKey);
    const plan = String(usage?.account?.current_plan || "").trim();
    const used = finiteNumber(usage?.key?.usage, usage?.account?.plan_usage, 0);
    const limit = finiteNumber(usage?.key?.limit, usage?.account?.plan_limit, 1000);
    const freePlan = plan.toLowerCase() === FREE_PLAN;
    return {
      ok: true,
      provider: PROVIDER,
      connected: true,
      ready: freePlan && used < limit,
      freeOnly: true,
      plan,
      usage: used,
      limit,
      remaining: Math.max(0, limit - used),
      connectedAt: row.connected_at,
      updatedAt: row.updated_at,
    };
  } catch (error) {
    return {
      ok: true,
      provider: PROVIDER,
      connected: true,
      ready: false,
      freeOnly: true,
      error: errorMessage(error).slice(0, 300),
    };
  }
}

async function isAdminRequest(db: any, req: Request): Promise<boolean> {
  const supplied = String(req.headers.get("x-h-runtime-secret") || "");
  if (!supplied) return false;
  const { data } = await db.from("h_runtime_config").select("secret_value").eq("key", "poll_secret").maybeSingle();
  return Boolean(data?.secret_value) && safeEqual(supplied, String(data.secret_value));
}

async function validateSetupToken(db: any, token: string): Promise<{ ok: boolean; tokenHash: string; error?: string }> {
  if (!token) return { ok: false, tokenHash: "", error: "الرابط غير مكتمل." };
  const tokenHash = await sha256Base64Url(token);
  const { data, error } = await db.from("h_runtime_ai_setup_links")
    .select("expires_at,used_at,purpose")
    .eq("token_hash", tokenHash)
    .eq("purpose", PURPOSE)
    .maybeSingle();
  if (error) throw error;
  if (!data) return { ok: false, tokenHash, error: "الرابط غير صالح." };
  if (data.used_at) return { ok: false, tokenHash, error: "هذا الرابط استُخدم مسبقًا." };
  if (new Date(data.expires_at).getTime() <= Date.now()) return { ok: false, tokenHash, error: "انتهت صلاحية الرابط. أنشئ رابطًا جديدًا." };
  return { ok: true, tokenHash };
}

async function cleanupSetupLinks(db: any) {
  await db.from("h_runtime_ai_setup_links").delete().eq("purpose", PURPOSE).lt("expires_at", new Date(Date.now() - 24 * 60 * 60_000).toISOString());
}

async function loadTavilyUsage(apiKey: string): Promise<any> {
  const response = await fetch(TAVILY_USAGE_URL, {
    headers: { Authorization: `Bearer ${apiKey}`, Accept: "application/json" },
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`Tavily rejected the API key (${response.status}): ${text.slice(0, 180)}`);
  return JSON.parse(text);
}

async function encryptSecret(value: string): Promise<{ ciphertext: string; iv: string }> {
  const key = await getEncryptionKey();
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv: toArrayBuffer(iv) },
    key,
    toArrayBuffer(new TextEncoder().encode(value)),
  );
  return { ciphertext: encodeBase64Url(new Uint8Array(encrypted)), iv: encodeBase64Url(iv) };
}

async function decryptSecret(ciphertext: string, iv: string): Promise<string> {
  const decrypted = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: toArrayBuffer(decodeBase64Url(iv)) },
    await getEncryptionKey(),
    toArrayBuffer(decodeBase64Url(ciphertext)),
  );
  return new TextDecoder().decode(decrypted);
}

async function getEncryptionKey(): Promise<CryptoKey> {
  const root = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!root) throw new Error("SUPABASE_SERVICE_ROLE_KEY is not configured");
  const digest = await crypto.subtle.digest(
    "SHA-256",
    toArrayBuffer(new TextEncoder().encode(`h-tavily-aes-v1:${root}`)),
  );
  return crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["encrypt", "decrypt"]);
}

function renderConnectPage(setup: string): string {
  const action = `save?setup=${encodeURIComponent(setup)}`;
  return `<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ربط H بالبحث العميق</title><style>${styles()}</style></head><body><main><h1>ربط H بالبحث العميق</h1><p>ألصق مفتاح Tavily من خطة <strong>Researcher المجانية</strong>. سيتحقق H من الخطة قبل الحفظ، ولن يقبل خطة مدفوعة.</p><form method="post" action="${action}"><label for="api_key">Tavily API Key</label><input id="api_key" name="api_key" type="password" autocomplete="off" required placeholder="tvly-…"><button type="submit">ربط البحث بالإنترنت</button></form><p class="note">المفتاح يُشفّر على الخادم ولا يُحفظ داخل التطبيق أو GitHub.</p></main></body></html>`;
}

function renderSuccess(plan: string, used: number, limit: number): string {
  return `<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>تم ربط H</title><style>${styles()}</style></head><body><main><h1>تم ربط H بالبحث العميق ✅</h1><p>الخطة: <strong>${escapeHtml(plan)}</strong></p><p>الاستهلاك الحالي: <strong>${used}</strong> من <strong>${limit}</strong> رصيد.</p><p>سيستخدم H البحث المجاني فقط، ولن ينتقل تلقائيًا إلى Pay‑As‑You‑Go.</p></main></body></html>`;
}

function renderError(message: string): string {
  return `<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>تعذر الربط</title><style>${styles()}</style></head><body><main><h1>تعذر ربط البحث</h1><p>${escapeHtml(message)}</p></main></body></html>`;
}

function styles(): string {
  return "body{font-family:system-ui,-apple-system,sans-serif;background:#f6f7f9;color:#15171a;margin:0;padding:24px}main{max-width:560px;margin:8vh auto;background:white;padding:28px;border-radius:18px;box-shadow:0 8px 30px #00000012}h1{font-size:24px}p{line-height:1.7}label{display:block;margin:20px 0 8px;font-weight:700}input{box-sizing:border-box;width:100%;padding:14px;border:1px solid #cfd4da;border-radius:12px;font-size:16px;direction:ltr}button{width:100%;margin-top:14px;padding:14px;border:0;border-radius:12px;background:#111;color:#fff;font-size:16px;font-weight:700}.note{font-size:13px;color:#626a73}";
}

function functionBaseUrl(url: URL): string {
  const marker = `/functions/v1/${FUNCTION_NAME}`;
  const index = url.pathname.indexOf(marker);
  const path = index >= 0 ? url.pathname.slice(0, index + marker.length) : url.pathname.replace(/\/(setup-link|status|health|connect|save|disconnect)\/?$/, "");
  return `${url.origin}${path.replace(/\/$/, "")}`;
}

function normalizePath(pathname: string): string {
  const marker = `/functions/v1/${FUNCTION_NAME}`;
  const index = pathname.indexOf(marker);
  if (index >= 0) {
    const rest = pathname.slice(index + marker.length);
    return rest ? (rest.startsWith("/") ? rest : `/${rest}`) : "/";
  }
  return pathname || "/";
}

function randomToken(bytes: number): string {
  return encodeBase64Url(crypto.getRandomValues(new Uint8Array(bytes)));
}

async function sha256Base64Url(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", toArrayBuffer(new TextEncoder().encode(value)));
  return encodeBase64Url(new Uint8Array(digest));
}

function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i += 1) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function finiteNumber(...values: unknown[]): number {
  for (const value of values) {
    const number = Number(value);
    if (Number.isFinite(number)) return number;
  }
  return 0;
}

function encodeBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function decodeBase64Url(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}

function escapeHtml(value: string): string {
  return String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char] || char));
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
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
    headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" },
  });
}
