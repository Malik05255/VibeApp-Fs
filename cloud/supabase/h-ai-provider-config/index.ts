import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import {
  isPotentiallyPaidPricing,
  normalizePricingSnapshot,
  parseOwnerPaidSetup,
  type HOwnerPaidSetup,
} from "../h-whatsapp-inbox/owner-paid-policy.ts";

const FUNCTION_NAME = "h-ai-provider-config";
const SETUP_TTL_MS = 10 * 60 * 1000;
const CREDENTIAL_VERSION = 1;
const ROUTE_ID = "openrouter_owner_paid";
const CREDENTIAL_ID = "openrouter_owner_paid";
const OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models";

type DbClient = any;

Deno.serve(async (req: Request) => {
  const supabaseUrl = safeEnv("SUPABASE_URL").replace(/\/$/, "");
  const serviceRole = safeEnv("SUPABASE_SERVICE_ROLE_KEY").trim();
  if (!supabaseUrl || !serviceRole) return json({ ok: false, error: "runtime credentials unavailable" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const url = new URL(req.url);
  const path = routePath(url.pathname);
  const publicBase = `${supabaseUrl}/functions/v1/${FUNCTION_NAME}`;

  try {
    if (req.method === "GET" && ["/", "/health"].includes(path)) {
      return json({
        ok: true,
        service: FUNCTION_NAME,
        supportedProviders: ["openrouter"],
        byok: true,
        paidByDefault: false,
        exactModelOnly: true,
        paidRetries: 0,
        freeFallbackDefault: false,
        secretsInApk: false,
      });
    }

    if (req.method === "GET" && path === "/status") {
      if (!await isRuntimeAdmin(req, db)) return json({ ok: false, error: "Unauthorized" }, 401);
      return json(await status(db));
    }

    if (req.method === "POST" && path === "/setup-link") {
      if (!await isRuntimeAdmin(req, db)) return json({ ok: false, error: "Unauthorized" }, 401);
      const body = await readJsonBody(req);
      const setup = parseOwnerPaidSetup(body);
      if (!setup) return json({ ok: false, error: "invalid_owner_paid_setup" }, 400);
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
      const connectUrl = new URL(`${publicBase}/connect`);
      connectUrl.searchParams.set("setup", rawToken);
      return json({
        ok: true,
        provider: setup.provider,
        selectedModel: setup.selectedModel,
        dailyCallLimit: setup.dailyCallLimit,
        hardTasksOnly: setup.hardTasksOnly,
        allowFreeFallback: setup.allowFreeFallback,
        expiresAt,
        connectUrl: connectUrl.toString(),
      });
    }

    if (req.method === "GET" && path === "/connect") {
      const validated = await validateSetupToken(db, url.searchParams.get("setup") || "");
      if (!validated.ok) return html(errorPage(validated.error || "الرابط غير صالح أو انتهت صلاحيته."), 400);
      return html(connectPage(publicBase, validated.rawToken, validated.setup));
    }

    if (req.method === "POST" && path === "/save") {
      const validated = await validateSetupToken(db, url.searchParams.get("setup") || "");
      if (!validated.ok) return html(errorPage(validated.error || "الرابط غير صالح أو انتهت صلاحيته."), 400);
      const form = await req.formData();
      if (String(form.get("confirm_paid") || "") !== "yes") {
        return html(errorPage("يجب تأكيد الموافقة على استخدام النموذج المدفوع والحد اليومي قبل التفعيل."), 400);
      }
      const apiKey = String(form.get("api_key") || "").trim();
      if (apiKey.length < 8 || apiKey.length > 2000) return html(errorPage("صيغة API key غير صالحة."), 400);

      const catalog = await loadOpenRouterModels(apiKey);
      const model = catalog.find((item: any) => String(item?.id || "") === validated.setup.selectedModel);
      if (!model) return html(errorPage("النموذج المحدد غير موجود في OpenRouter لهذا المفتاح."), 400);
      const pricing = normalizePricingSnapshot(model?.pricing);
      if (!pricing) return html(errorPage("تعذر التحقق من سعر النموذج بشكل آمن، لذلك لم يتم التفعيل."), 400);
      if (!isPotentiallyPaidPricing(pricing)) {
        return html(errorPage("النموذج المحدد ظاهر حاليًا كمجاني. استخدم مسار H المجاني بدل تفعيله كمساعد مدفوع."), 400);
      }

      const now = new Date().toISOString();
      const encrypted = await encryptOwnerPaidSecret(validated.setup.provider, apiKey);
      const metadata = {
        owner_paid: true,
        byok: true,
        exact_model_only: true,
        paid_retries: 0,
        pricing_ceiling: pricing,
        pricing_verified_at: now,
        hard_tasks_only: validated.setup.hardTasksOnly,
        allow_free_fallback: validated.setup.allowFreeFallback,
        encryption_source: "supabase_service_role_derived_v1",
      };

      const { error: credentialError } = await db.from("h_runtime_ai_credentials").upsert({
        id: CREDENTIAL_ID,
        provider: validated.setup.provider,
        secret_ciphertext: encrypted.ciphertext,
        secret_iv: encrypted.iv,
        secret_version: CREDENTIAL_VERSION,
        selected_model: validated.setup.selectedModel,
        model_verified_at: now,
        oauth_metadata: metadata,
        connected_at: now,
        updated_at: now,
      }, { onConflict: "id" });
      if (credentialError) throw credentialError;

      const { error: registryError } = await db.from("h_runtime_ai_provider_registry").upsert({
        id: ROUTE_ID,
        provider: validated.setup.provider,
        route_class: "owner_paid",
        credential_id: CREDENTIAL_ID,
        selected_model: validated.setup.selectedModel,
        enabled: true,
        owner_enabled_at: now,
        hard_tasks_only: validated.setup.hardTasksOnly,
        allow_free_fallback: validated.setup.allowFreeFallback,
        daily_call_limit: validated.setup.dailyCallLimit,
        priority: 10,
        metadata: {
          ...metadata,
          managed_by: FUNCTION_NAME,
          cost_policy: "exact_live_price_not_above_owner_ceiling",
        },
        updated_at: now,
      }, { onConflict: "id" });
      if (registryError) throw registryError;

      const { error: tokenError } = await db.from("h_runtime_ai_owner_paid_setup")
        .update({ used_at: now })
        .eq("token_hash", validated.tokenHash)
        .is("used_at", null);
      if (tokenError) throw tokenError;

      await db.from("h_runtime_state").upsert({
        key: "owner_paid_ai",
        value: {
          connected: true,
          enabled: true,
          provider: validated.setup.provider,
          model: validated.setup.selectedModel,
          daily_call_limit: validated.setup.dailyCallLimit,
          hard_tasks_only: validated.setup.hardTasksOnly,
          allow_free_fallback: validated.setup.allowFreeFallback,
          exact_model_only: true,
          paid_retries: 0,
          price_guard: true,
          owner_enabled_at: now,
        },
        updated_at: now,
      }, { onConflict: "key" });

      return html(successPage(validated.setup, pricing));
    }

    if (req.method === "POST" && path === "/disable") {
      if (!await isRuntimeAdmin(req, db)) return json({ ok: false, error: "Unauthorized" }, 401);
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
      return json({ ok: true, enabled: false });
    }

    if (req.method === "POST" && path === "/disconnect") {
      if (!await isRuntimeAdmin(req, db)) return json({ ok: false, error: "Unauthorized" }, 401);
      const now = new Date().toISOString();
      await db.from("h_runtime_ai_provider_registry")
        .update({ enabled: false, credential_id: null, selected_model: null, daily_call_limit: null, updated_at: now })
        .eq("id", ROUTE_ID)
        .eq("route_class", "owner_paid");
      await db.from("h_runtime_ai_credentials").delete().eq("id", CREDENTIAL_ID).eq("provider", "openrouter");
      await db.from("h_runtime_state").upsert({
        key: "owner_paid_ai",
        value: { connected: false, enabled: false, owner_paid: true, disconnected_at: now },
        updated_at: now,
      }, { onConflict: "key" });
      return json({ ok: true, connected: false, enabled: false });
    }

    return json({ ok: false, error: "Not found", path }, 404);
  } catch (error) {
    const message = errorMessage(error);
    console.error("h-ai-provider-config failed", message);
    if (path === "/connect" || path === "/save") return html(errorPage(message), 500);
    return json({ ok: false, error: message }, 500);
  }
});

async function status(db: DbClient) {
  const { data: route, error } = await db.from("h_runtime_ai_provider_registry")
    .select("id,provider,selected_model,enabled,owner_enabled_at,hard_tasks_only,allow_free_fallback,daily_call_limit,metadata,updated_at")
    .eq("id", ROUTE_ID)
    .maybeSingle();
  if (error) throw error;
  if (!route) return { ok: true, connected: false, enabled: false, ownerPaid: true };
  const today = new Date().toISOString().slice(0, 10);
  const { data: usage } = await db.from("h_runtime_ai_paid_usage_daily")
    .select("calls,prompt_tokens,completion_tokens,last_used_at")
    .eq("route_id", ROUTE_ID)
    .eq("usage_date", today)
    .maybeSingle();
  return {
    ok: true,
    connected: Boolean(route.selected_model),
    enabled: route.enabled === true,
    ownerPaid: true,
    provider: route.provider,
    selectedModel: route.selected_model,
    ownerEnabledAt: route.owner_enabled_at,
    hardTasksOnly: route.hard_tasks_only === true,
    allowFreeFallback: route.allow_free_fallback === true,
    dailyCallLimit: route.daily_call_limit,
    callsUsedToday: Number(usage?.calls || 0),
    promptTokensToday: Number(usage?.prompt_tokens || 0),
    completionTokensToday: Number(usage?.completion_tokens || 0),
    lastUsedAt: usage?.last_used_at ?? null,
    priceGuard: route?.metadata?.pricing_ceiling ? true : false,
    updatedAt: route.updated_at,
  };
}

async function validateSetupToken(db: DbClient, rawToken: string): Promise<
  | { ok: true; rawToken: string; tokenHash: string; setup: HOwnerPaidSetup }
  | { ok: false; error: string }
> {
  const token = String(rawToken || "").trim();
  if (!token) return { ok: false, error: "الرابط ناقص." };
  const tokenHash = await setupTokenHash(token);
  const { data, error } = await db.from("h_runtime_ai_owner_paid_setup")
    .select("provider,selected_model,daily_call_limit,hard_tasks_only,allow_free_fallback,expires_at,used_at")
    .eq("token_hash", tokenHash)
    .maybeSingle();
  if (error) throw error;
  if (!data) return { ok: false, error: "الرابط غير صالح." };
  if (data.used_at) return { ok: false, error: "تم استخدام هذا الرابط مسبقًا." };
  if (new Date(data.expires_at).getTime() <= Date.now()) return { ok: false, error: "انتهت صلاحية الرابط. أنشئ رابطًا جديدًا." };
  const setup = parseOwnerPaidSetup(data);
  if (!setup) return { ok: false, error: "بيانات إعداد النموذج المدفوع غير صالحة." };
  return { ok: true, rawToken: token, tokenHash, setup };
}

async function loadOpenRouterModels(apiKey: string): Promise<any[]> {
  const response = await fetch(OPENROUTER_MODELS_URL, {
    headers: { Authorization: `Bearer ${apiKey}`, Accept: "application/json" },
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`OpenRouter rejected the key/catalog request (${response.status}): ${text.slice(0, 180)}`);
  const body = JSON.parse(text);
  if (!Array.isArray(body?.data)) throw new Error("OpenRouter model catalog returned an unexpected response");
  return body.data;
}

async function isRuntimeAdmin(req: Request, db: DbClient): Promise<boolean> {
  const provided = req.headers.get("x-h-runtime-secret")?.trim() || "";
  if (!provided) return false;
  const { data, error } = await db.from("h_runtime_config").select("secret_value").eq("key", "poll_secret").maybeSingle();
  if (error) return false;
  const expected = String(data?.secret_value || "");
  return expected.length > 0 && constantTimeEquals(expected, provided);
}

async function encryptOwnerPaidSecret(provider: string, value: string): Promise<{ ciphertext: string; iv: string }> {
  const root = safeEnv("SUPABASE_SERVICE_ROLE_KEY").trim();
  if (!root) throw new Error("SUPABASE_SERVICE_ROLE_KEY is not configured");
  const digest = await crypto.subtle.digest(
    "SHA-256",
    toArrayBuffer(new TextEncoder().encode(`h-owner-paid-ai-aes-v1:${provider}:${root}`)),
  );
  const key = await crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["encrypt"]);
  const iv = new Uint8Array(12);
  crypto.getRandomValues(iv);
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv: toArrayBuffer(iv) },
    key,
    toArrayBuffer(new TextEncoder().encode(value)),
  );
  return { ciphertext: base64Url(new Uint8Array(encrypted)), iv: base64Url(iv) };
}

async function setupTokenHash(token: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", toArrayBuffer(new TextEncoder().encode(`h-owner-paid-ai-v1:${token}`)));
  return base64Url(new Uint8Array(digest));
}

function connectPage(publicBase: string, rawToken: string, setup: HOwnerPaidSetup): string {
  const action = `${publicBase}/save?setup=${encodeURIComponent(rawToken)}`;
  return `<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ربط مساعد مدفوع مع H</title><style>body{font-family:system-ui;max-width:680px;margin:40px auto;padding:0 18px;line-height:1.7}input,button{font:inherit;width:100%;box-sizing:border-box;padding:12px;margin:8px 0}label{display:block;margin-top:12px}.box{border:1px solid #bbb;border-radius:12px;padding:16px}.warn{font-weight:700}</style></head><body><h1>ربط مساعد BYOK مع H</h1><div class="box"><p><b>المزود:</b> OpenRouter</p><p><b>النموذج الوحيد المصرح:</b> ${escapeHtml(setup.selectedModel)}</p><p><b>الحد اليومي:</b> ${setup.dailyCallLimit} استدعاء</p><p><b>للمهام الصعبة فقط:</b> ${setup.hardTasksOnly ? "نعم" : "لا — سيصبح هذا هو مسار AI الأساسي"}</p><p><b>الرجوع للمجاني عند فشل المدفوع:</b> ${setup.allowFreeFallback ? "مسموح صراحة" : "غير مسموح"}</p><p class="warn">H لن يبدل إلى نموذج مدفوع آخر، ولن يعيد محاولة مدفوعة تلقائيًا. إذا ارتفع سعر النموذج عن السعر الذي وافقت عليه الآن، سيتوقف قبل الاستدعاء.</p></div><form method="post" action="${escapeHtml(action)}"><label>OpenRouter API Key<input type="password" name="api_key" autocomplete="off" required></label><label><input style="width:auto" type="checkbox" name="confirm_paid" value="yes" required> أوافق صراحة على استخدام النموذج أعلاه ضمن الحد اليومي المحدد.</label><button type="submit">تحقق وفعّل</button></form></body></html>`;
}

function successPage(setup: HOwnerPaidSetup, pricing: Record<string, number>): string {
  return `<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>تم الربط</title></head><body style="font-family:system-ui;max-width:680px;margin:40px auto;padding:0 18px;line-height:1.7"><h1>تم ربط المساعد المدفوع مع H ✅</h1><p>النموذج: <b>${escapeHtml(setup.selectedModel)}</b></p><p>الحد اليومي: <b>${setup.dailyCallLimit}</b> استدعاء.</p><p>تم تثبيت سقف السعر الحالي: prompt=${escapeHtml(String(pricing.prompt))}, completion=${escapeHtml(String(pricing.completion))}. أي زيادة لاحقة توقف الاستدعاء قبل الدفع.</p><p>H يبقى هو المساعد الأساسي؛ هذا النموذج مجرد مزود مساعد قابل للاستبدال أو التعطيل.</p></body></html>`;
}

function errorPage(message: string): string {
  return `<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>تعذر الربط</title></head><body style="font-family:system-ui;max-width:680px;margin:40px auto;padding:0 18px"><h1>تعذر إكمال الربط</h1><p>${escapeHtml(message)}</p></body></html>`;
}

function routePath(pathname: string): string {
  let path = pathname || "/";
  for (const marker of [`/functions/v1/${FUNCTION_NAME}`, `/${FUNCTION_NAME}`]) {
    const index = path.indexOf(marker);
    if (index >= 0) { path = path.slice(index + marker.length) || "/"; break; }
  }
  return path.startsWith("/") ? path : `/${path}`;
}

async function readJsonBody(req: Request): Promise<any> {
  try { return await req.json(); } catch (_) { return {}; }
}

function randomUrlSafe(byteCount: number): string {
  const bytes = new Uint8Array(byteCount);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}

function constantTimeEquals(a: string, b: string): boolean {
  const left = new TextEncoder().encode(a);
  const right = new TextEncoder().encode(b);
  let diff = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let i = 0; i < length; i += 1) diff |= (left[i % Math.max(left.length, 1)] || 0) ^ (right[i % Math.max(right.length, 1)] || 0);
  return diff === 0;
}

function escapeHtml(value: string): string {
  return String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char] || char));
}

function safeEnv(name: string): string {
  try { return String(Deno.env.get(name) || ""); } catch (_) { return ""; }
}

function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: { "content-type": "application/json; charset=utf-8" } });
}

function html(value: string, status = 200): Response {
  return new Response(value, { status, headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error || "unknown_error");
}
