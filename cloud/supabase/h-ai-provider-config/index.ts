import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import {
  isPotentiallyPaidPricing,
  normalizePricingSnapshot,
  parseOwnerPaidSetup,
  pricingWithinAuthorizedCeiling,
  type HOwnerPaidSetup,
  type PricingSnapshot,
} from "../h-whatsapp-inbox/owner-paid-policy.ts";

const FUNCTION_NAME = "h-ai-provider-config";
const SETUP_TTL_MS = 10 * 60 * 1000;
const CREDENTIAL_VERSION = 1;
const ROUTE_ID = "openrouter_owner_paid";
const CREDENTIAL_ID = "openrouter_owner_paid";
const OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models";
const MAX_PAID_OUTPUT_TOKENS = 1_200;
const MAX_PAID_TEXT_CHARS = 60_000;
const MAX_PAID_MEDIA_DATA_CHARS = 8_000_000;

type DbClient = any;
type PendingReview = {
  ciphertext: string;
  iv: string;
  pricing: PricingSnapshot;
  pricingVerifiedAt: string;
};

type ValidatedSetup = {
  ok: true;
  rawToken: string;
  tokenHash: string;
  setup: HOwnerPaidSetup;
  pending: PendingReview | null;
};

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
        explicitPriceReview: true,
        exactModelOnly: true,
        paidRetries: 0,
        freeFallbackDefault: false,
        maxOutputTokens: MAX_PAID_OUTPUT_TOKENS,
        maxTextChars: MAX_PAID_TEXT_CHARS,
        maxMediaDataChars: MAX_PAID_MEDIA_DATA_CHARS,
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
      if (!validated.ok) return html(errorPage(validated.error), 400);
      return validated.pending
        ? html(priceReviewPage(publicBase, validated.rawToken, validated.setup, validated.pending.pricing, validated.pending.pricingVerifiedAt))
        : html(connectPage(publicBase, validated.rawToken, validated.setup));
    }

    // Step 1: validate the owner's key and exact model, then persist only an encrypted
    // pending key + live price snapshot. Nothing paid is enabled at this stage.
    if (req.method === "POST" && path === "/save") {
      const validated = await validateSetupToken(db, url.searchParams.get("setup") || "");
      if (!validated.ok) return html(errorPage(validated.error), 400);
      const form = await req.formData();
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

      const reviewedAt = new Date().toISOString();
      const encrypted = await encryptOwnerPaidSecret(validated.setup.provider, apiKey);
      const { error } = await db.from("h_runtime_ai_owner_paid_setup").update({
        pending_secret_ciphertext: encrypted.ciphertext,
        pending_secret_iv: encrypted.iv,
        pricing_ceiling: pricing,
        pricing_verified_at: reviewedAt,
      }).eq("token_hash", validated.tokenHash).is("used_at", null);
      if (error) throw error;

      return html(priceReviewPage(publicBase, validated.rawToken, validated.setup, pricing, reviewedAt));
    }

    // Step 2: only this explicit second confirmation enables a paid route. Re-check the
    // catalog using the encrypted pending key so a price increase between review and click
    // cannot slip through silently.
    if (req.method === "POST" && path === "/activate") {
      const validated = await validateSetupToken(db, url.searchParams.get("setup") || "");
      if (!validated.ok) return html(errorPage(validated.error), 400);
      if (!validated.pending) return html(errorPage("تحقق من المفتاح والسعر أولًا قبل التفعيل."), 400);
      const form = await req.formData();
      if (String(form.get("confirm_paid") || "") !== "yes") {
        return html(errorPage("لم يتم التفعيل لأن الموافقة الصريحة على السعر والحد اليومي غير موجودة."), 400);
      }

      const apiKey = (await decryptOwnerPaidSecret(
        validated.setup.provider,
        validated.pending.ciphertext,
        validated.pending.iv,
      )).trim();
      if (!apiKey) return html(errorPage("تعذر قراءة المفتاح المؤقت المشفر. أنشئ رابط إعداد جديدًا."), 400);

      const catalog = await loadOpenRouterModels(apiKey);
      const model = catalog.find((item: any) => String(item?.id || "") === validated.setup.selectedModel);
      if (!model) return html(errorPage("النموذج لم يعد متاحًا. لم يتم التفعيل."), 400);
      const livePricing = normalizePricingSnapshot(model?.pricing);
      if (!livePricing) return html(errorPage("تعذر إعادة التحقق من السعر، لذلك لم يتم التفعيل."), 400);
      const priceCheck = pricingWithinAuthorizedCeiling(livePricing, validated.pending.pricing);
      if (!priceCheck.ok) {
        const refreshedAt = new Date().toISOString();
        const { error: refreshError } = await db.from("h_runtime_ai_owner_paid_setup").update({
          pricing_ceiling: livePricing,
          pricing_verified_at: refreshedAt,
        }).eq("token_hash", validated.tokenHash).is("used_at", null);
        if (refreshError) throw refreshError;
        return html(priceReviewPage(
          publicBase,
          validated.rawToken,
          validated.setup,
          livePricing,
          refreshedAt,
          "تغير السعر منذ المراجعة السابقة. لم أفعّل النموذج. راجع السعر الجديد ثم وافق مرة أخرى.",
        ));
      }

      await ensureNoOtherPaidHelper(db);
      const now = new Date().toISOString();
      const metadata = {
        owner_paid: true,
        byok: true,
        exact_model_only: true,
        paid_retries: 0,
        pricing_ceiling: validated.pending.pricing,
        pricing_verified_at: validated.pending.pricingVerifiedAt,
        activation_price_verified_at: now,
        hard_tasks_only: validated.setup.hardTasksOnly,
        allow_free_fallback: validated.setup.allowFreeFallback,
        max_output_tokens: MAX_PAID_OUTPUT_TOKENS,
        max_text_chars: MAX_PAID_TEXT_CHARS,
        max_media_data_chars: MAX_PAID_MEDIA_DATA_CHARS,
        encryption_source: "supabase_service_role_derived_v1",
      };

      const { error: credentialError } = await db.from("h_runtime_ai_credentials").upsert({
        id: CREDENTIAL_ID,
        provider: validated.setup.provider,
        secret_ciphertext: validated.pending.ciphertext,
        secret_iv: validated.pending.iv,
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
          cost_policy: "reviewed_live_price_ceiling",
        },
        updated_at: now,
      }, { onConflict: "id" });
      if (registryError) throw registryError;

      const { error: tokenError } = await db.from("h_runtime_ai_owner_paid_setup").update({
        used_at: now,
        pending_secret_ciphertext: null,
        pending_secret_iv: null,
      }).eq("token_hash", validated.tokenHash).is("used_at", null);
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
          explicit_price_review: true,
          owner_enabled_at: now,
          max_output_tokens: MAX_PAID_OUTPUT_TOKENS,
        },
        updated_at: now,
      }, { onConflict: "key" });

      return html(successPage(validated.setup, validated.pending.pricing));
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
    if (["/connect", "/save", "/activate"].includes(path)) return html(errorPage(message), 500);
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
    .select("calls,prompt_tokens,completion_tokens,cost_usd,last_used_at")
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
    costUsdToday: Number(usage?.cost_usd || 0),
    lastUsedAt: usage?.last_used_at ?? null,
    priceGuard: route?.metadata?.pricing_ceiling ? true : false,
    explicitPriceReview: route?.metadata?.pricing_verified_at ? true : false,
    updatedAt: route.updated_at,
  };
}

async function validateSetupToken(db: DbClient, rawToken: string): Promise<ValidatedSetup | { ok: false; error: string }> {
  const token = String(rawToken || "").trim();
  if (!token) return { ok: false, error: "الرابط ناقص." };
  const tokenHash = await setupTokenHash(token);
  const { data, error } = await db.from("h_runtime_ai_owner_paid_setup")
    .select("provider,selected_model,daily_call_limit,hard_tasks_only,allow_free_fallback,expires_at,used_at,pending_secret_ciphertext,pending_secret_iv,pricing_ceiling,pricing_verified_at")
    .eq("token_hash", tokenHash)
    .maybeSingle();
  if (error) throw error;
  if (!data) return { ok: false, error: "الرابط غير صالح." };
  if (data.used_at) return { ok: false, error: "تم استخدام هذا الرابط مسبقًا." };
  if (new Date(data.expires_at).getTime() <= Date.now()) return { ok: false, error: "انتهت صلاحية الرابط. أنشئ رابطًا جديدًا." };
  const setup = parseOwnerPaidSetup(data);
  if (!setup) return { ok: false, error: "بيانات إعداد النموذج المدفوع غير صالحة." };

  const ciphertext = String(data.pending_secret_ciphertext || "").trim();
  const iv = String(data.pending_secret_iv || "").trim();
  const pricing = normalizePricingSnapshot(data.pricing_ceiling);
  const pricingVerifiedAt = String(data.pricing_verified_at || "").trim();
  const pending = ciphertext && iv && pricing && pricingVerifiedAt
    ? { ciphertext, iv, pricing, pricingVerifiedAt }
    : null;
  return { ok: true, rawToken: token, tokenHash, setup, pending };
}

async function ensureNoOtherPaidHelper(db: DbClient) {
  const { data, error } = await db.from("h_runtime_ai_provider_registry")
    .select("id")
    .eq("route_class", "owner_paid")
    .eq("enabled", true);
  if (error) throw error;
  const conflicting = (Array.isArray(data) ? data : []).find((row: any) => String(row?.id || "") !== ROUTE_ID);
  if (conflicting) throw new Error("يوجد مساعد مدفوع آخر مفعّل. عطّله أولًا؛ H لا يخلط أكثر من مزود مدفوع.");
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

async function decryptOwnerPaidSecret(provider: string, ciphertext: string, iv: string): Promise<string> {
  const root = safeEnv("SUPABASE_SERVICE_ROLE_KEY").trim();
  if (!root) throw new Error("SUPABASE_SERVICE_ROLE_KEY is not configured");
  const digest = await crypto.subtle.digest(
    "SHA-256",
    toArrayBuffer(new TextEncoder().encode(`h-owner-paid-ai-aes-v1:${provider}:${root}`)),
  );
  const key = await crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["decrypt"]);
  const decrypted = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: toArrayBuffer(decodeBase64Url(iv)) },
    key,
    toArrayBuffer(decodeBase64Url(ciphertext)),
  );
  return new TextDecoder().decode(decrypted);
}

async function setupTokenHash(token: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", toArrayBuffer(new TextEncoder().encode(`h-owner-paid-ai-v1:${token}`)));
  return base64Url(new Uint8Array(digest));
}

function connectPage(publicBase: string, rawToken: string, setup: HOwnerPaidSetup): string {
  const action = `${publicBase}/save?setup=${encodeURIComponent(rawToken)}`;
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("ربط مساعد مدفوع مع H")}</head><body><main><h1>ربط مساعد BYOK مع H</h1>${policyBox(setup)}<p><b>الخطوة 1 من 2:</b> أدخل مفتاح OpenRouter. H سيتحقق من النموذج والسعر، ولن يفعّل أي استدعاء مدفوع في هذه الخطوة.</p><form method="post" action="${escapeHtml(action)}"><label>OpenRouter API Key<input type="password" name="api_key" autocomplete="off" required></label><button type="submit">تحقق من السعر</button></form></main></body></html>`;
}

function priceReviewPage(
  publicBase: string,
  rawToken: string,
  setup: HOwnerPaidSetup,
  pricing: PricingSnapshot,
  reviewedAt: string,
  notice = "",
): string {
  const action = `${publicBase}/activate?setup=${encodeURIComponent(rawToken)}`;
  const rows = Object.entries(pricing).sort(([a], [b]) => a.localeCompare(b))
    .map(([key, value]) => `<tr><td>${escapeHtml(key)}</td><td><code>${escapeHtml(String(value))}</code></td></tr>`).join("");
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("مراجعة سعر المساعد المدفوع")}</head><body><main><h1>راجع السعر قبل التفعيل</h1>${notice ? `<p class="warn">${escapeHtml(notice)}</p>` : ""}${policyBox(setup)}<p><b>الخطوة 2 من 2:</b> هذه قيم التسعير التي أعادها OpenRouter للنموذج عند ${escapeHtml(reviewedAt)}. ستُحفظ كسقف؛ أي زيادة أو بند تكلفة جديد يوقف H قبل الاستدعاء.</p><table><thead><tr><th>بند التسعير</th><th>القيمة كما يعيدها OpenRouter</th></tr></thead><tbody>${rows}</tbody></table><p>لمنع الاستجابة المكلفة بلا حدود: أقصى إخراج مدفوع ${MAX_PAID_OUTPUT_TOKENS} token، وأقصى نص ${MAX_PAID_TEXT_CHARS.toLocaleString("en-US")} حرف. الطلب البحثي المتحقق قد يستخدم استدعاءين: إجابة + تحقق، وكلاهما يُحسب ضمن حدك اليومي.</p><form method="post" action="${escapeHtml(action)}"><label class="confirm"><input type="checkbox" name="confirm_paid" value="yes" required> أوافق صراحة على النموذج والسعر المعروضين والحد اليومي أعلاه، وأفهم أن H لن يستخدم نموذجًا مدفوعًا آخر أو يعيد محاولة مدفوعة تلقائيًا.</label><button type="submit">أوافق وفعّل</button></form></main></body></html>`;
}

function policyBox(setup: HOwnerPaidSetup): string {
  return `<section class="box"><p><b>المزود:</b> OpenRouter</p><p><b>النموذج الوحيد المصرح:</b> ${escapeHtml(setup.selectedModel)}</p><p><b>الحد اليومي:</b> ${setup.dailyCallLimit} استدعاء</p><p><b>للمهام الصعبة فقط:</b> ${setup.hardTasksOnly ? "نعم" : "لا — يصبح هذا مسار AI المفعّل"}</p><p><b>الرجوع للمجاني عند تعطل المدفوع:</b> ${setup.allowFreeFallback ? "مسموح لأنك اخترته صراحة" : "غير مسموح"}</p><p class="warn">لا paid fallback، لا تبديل لنموذج مدفوع آخر، ولا paid retry.</p></section>`;
}

function successPage(setup: HOwnerPaidSetup, pricing: PricingSnapshot): string {
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("تم الربط")}</head><body><main><h1>تم ربط المساعد المدفوع مع H ✅</h1><p>النموذج: <b>${escapeHtml(setup.selectedModel)}</b></p><p>الحد اليومي: <b>${setup.dailyCallLimit}</b> استدعاء.</p><p>تم تثبيت سعر OpenRouter الذي راجعته كسقف. أي زيادة لاحقة توقف الاستدعاء قبل الدفع.</p><p>H يبقى المساعد الأساسي؛ هذا النموذج مزود مساعد يمكن تعطيله أو فصله.</p><details><summary>سقف التسعير المحفوظ</summary><pre>${escapeHtml(JSON.stringify(pricing, null, 2))}</pre></details></main></body></html>`;
}

function errorPage(message: string): string {
  return `<!doctype html><html lang="ar" dir="rtl"><head>${pageHead("تعذر الربط")}</head><body><main><h1>تعذر إكمال الربط</h1><p>${escapeHtml(message)}</p></main></body></html>`;
}

function pageHead(title: string): string {
  return `<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escapeHtml(title)}</title><style>body{font-family:system-ui;background:#f7f7f7;margin:0;color:#171717}main{max-width:720px;margin:36px auto;padding:24px;background:#fff;border-radius:16px}input,button{font:inherit;box-sizing:border-box;padding:12px;margin:8px 0}input[type=password],button{width:100%}.box{border:1px solid #ccc;border-radius:12px;padding:14px}.warn{font-weight:700}.confirm{display:block;border:1px solid #bbb;border-radius:12px;padding:12px;margin:14px 0}.confirm input{width:auto}table{width:100%;border-collapse:collapse;margin:14px 0}th,td{border:1px solid #ddd;padding:8px;text-align:right}code,pre{direction:ltr;text-align:left;overflow:auto}</style>`;
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

function constantTimeEquals(a: string, b: string): boolean {
  const left = new TextEncoder().encode(a);
  const right = new TextEncoder().encode(b);
  let diff = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let i = 0; i < length; i += 1) {
    diff |= (left[i % Math.max(left.length, 1)] || 0) ^ (right[i % Math.max(right.length, 1)] || 0);
  }
  return diff === 0;
}

function escapeHtml(value: string): string {
  return String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char] || char));
}

function safeEnv(name: string): string {
  try { return String(Deno.env.get(name) || ""); } catch (_) { return ""; }
}

function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });
}

function html(value: string, status = 200): Response {
  return new Response(value, { status, headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error || "unknown_error");
}
