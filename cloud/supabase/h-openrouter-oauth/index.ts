import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const FUNCTION_NAME = "h-openrouter-oauth";
const OPENROUTER_AUTH_URL = "https://openrouter.ai/auth";
const OPENROUTER_KEY_EXCHANGE_URL = "https://openrouter.ai/api/v1/auth/keys";
const OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models";
const SETUP_TTL_MS = 10 * 60 * 1000;
const OAUTH_TTL_MS = 10 * 60 * 1000;
const CREDENTIAL_ID = "openrouter_default";
const CREDENTIAL_VERSION = 1;

Deno.serve(async (req: Request) => {
  const url = new URL(req.url);
  const path = routePath(url.pathname);
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRole) {
    return json({ ok: false, error: "Supabase runtime credentials unavailable" }, 500);
  }

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const publicFunctionBase = `${supabaseUrl}/functions/v1/${FUNCTION_NAME}`;

  try {
    if (req.method === "GET" && ["/", "/health", "/status"].includes(path)) {
      const { data: credential } = await db.from("h_runtime_ai_credentials")
        .select("provider,selected_model,model_verified_at,connected_at,updated_at")
        .eq("id", CREDENTIAL_ID)
        .maybeSingle();

      return json({
        ok: true,
        service: FUNCTION_NAME,
        provider: credential?.provider ?? "openrouter",
        connected: Boolean(credential),
        selectedModel: credential?.selected_model ?? null,
        modelVerifiedAt: credential?.model_verified_at ?? null,
        connectedAt: credential?.connected_at ?? null,
        updatedAt: credential?.updated_at ?? null,
        credentialEncryptionReady: encryptionSecretConfigured(),
        paidModelFallback: false,
      });
    }

    if (req.method === "POST" && path === "/setup-link") {
      if (!await isRuntimeAdmin(req, db)) return json({ ok: false, error: "Unauthorized" }, 401);
      requireEncryptionSecret();

      const token = randomUrlSafe(32);
      const tokenHash = await sha256Base64Url(token);
      const expiresAt = new Date(Date.now() + SETUP_TTL_MS).toISOString();
      const { error } = await db.from("h_runtime_ai_setup_links").insert({
        token_hash: tokenHash,
        expires_at: expiresAt,
      });
      if (error) throw error;

      return json({
        ok: true,
        expiresAt,
        connectUrl: `${publicFunctionBase}/connect?setup=${encodeURIComponent(token)}`,
      });
    }

    if (req.method === "GET" && path === "/connect") {
      requireEncryptionSecret();
      const setup = url.searchParams.get("setup")?.trim();
      if (!setup) return text("رابط إعداد H غير صالح أو ناقص.", 400);

      const tokenHash = await sha256Base64Url(setup);
      const { data: setupRow } = await db.from("h_runtime_ai_setup_links")
        .select("token_hash,expires_at,used_at")
        .eq("token_hash", tokenHash)
        .maybeSingle();

      if (!setupRow || setupRow.used_at || new Date(setupRow.expires_at).getTime() <= Date.now()) {
        return text("انتهت صلاحية رابط ربط H مع OpenRouter. اطلب رابطًا جديدًا.", 403);
      }

      const state = randomUrlSafe(32);
      const verifier = randomUrlSafe(64);
      const challenge = await pkceChallenge(verifier);
      const redirectBase = `${publicFunctionBase}/callback`;
      const callback = new URL(redirectBase);
      callback.searchParams.set("state", state);
      const redirectUri = callback.toString();
      const encryptedVerifier = await encryptSecret(verifier);
      const stateHash = await sha256Base64Url(state);

      const { error: pendingError } = await db.from("h_runtime_ai_oauth_pending").insert({
        state_hash: stateHash,
        verifier_ciphertext: encryptedVerifier.ciphertext,
        verifier_iv: encryptedVerifier.iv,
        redirect_uri: redirectUri,
        expires_at: new Date(Date.now() + OAUTH_TTL_MS).toISOString(),
      });
      if (pendingError) throw pendingError;

      const { error: setupError } = await db.from("h_runtime_ai_setup_links")
        .update({ used_at: new Date().toISOString() })
        .eq("token_hash", tokenHash);
      if (setupError) throw setupError;

      const auth = new URL(OPENROUTER_AUTH_URL);
      auth.searchParams.set("callback_url", redirectUri);
      auth.searchParams.set("code_challenge", challenge);
      auth.searchParams.set("code_challenge_method", "S256");
      return Response.redirect(auth.toString(), 302);
    }

    if (req.method === "GET" && path === "/callback") {
      requireEncryptionSecret();
      const state = url.searchParams.get("state")?.trim();
      const code = url.searchParams.get("code")?.trim();
      const oauthError = url.searchParams.get("error")?.trim();
      const oauthDescription = url.searchParams.get("error_description")?.trim();
      if (!state) return text("OpenRouter لم يُرجع حالة OAuth المطلوبة.", 400);

      const stateHash = await sha256Base64Url(state);
      const { data: pending } = await db.from("h_runtime_ai_oauth_pending")
        .select("*")
        .eq("state_hash", stateHash)
        .maybeSingle();

      if (!pending || new Date(pending.expires_at).getTime() <= Date.now()) {
        return text("جلسة ربط OpenRouter انتهت صلاحيتها. أعد المحاولة من رابط جديد.", 403);
      }

      if (oauthError) {
        await db.from("h_runtime_ai_oauth_pending").delete().eq("state_hash", stateHash);
        return text(`OpenRouter رفض التفويض: ${oauthError}${oauthDescription ? ` — ${oauthDescription}` : ""}`, 400);
      }
      if (!code) return text("OpenRouter لم يُرجع رمز التفويض.", 400);

      try {
        const verifier = await decryptSecret(pending.verifier_ciphertext, pending.verifier_iv);
        const apiKey = await exchangeOpenRouterCode(code, verifier);

        // Fail closed: a credential is never persisted until OpenRouter's live catalog proves
        // at least one model has zero pricing. We never auto-fallback to a paid model.
        const catalog = await loadOpenRouterModels(apiKey);
        const selectedModel = selectStrictlyFreeModel(catalog, Deno.env.get("H_MODEL")?.trim() || null);
        if (!selectedModel) {
          throw new Error("OpenRouter did not advertise a strictly zero-priced model; credential was not stored");
        }

        const encryptedKey = await encryptSecret(apiKey);
        const now = new Date().toISOString();
        const { error: credentialError } = await db.from("h_runtime_ai_credentials").upsert({
          id: CREDENTIAL_ID,
          provider: "openrouter",
          secret_ciphertext: encryptedKey.ciphertext,
          secret_iv: encryptedKey.iv,
          secret_version: CREDENTIAL_VERSION,
          selected_model: selectedModel,
          model_verified_at: now,
          oauth_metadata: {
            auth: "openrouter-pkce",
            callback_origin: new URL(pending.redirect_uri).origin,
            free_only: true,
          },
          connected_at: now,
          updated_at: now,
        }, { onConflict: "id" });
        if (credentialError) throw credentialError;

        await db.from("h_runtime_ai_oauth_pending").delete().eq("state_hash", stateHash);
        await db.from("h_runtime_state").upsert({
          key: "openrouter_ai",
          value: {
            connected: true,
            provider: "openrouter",
            selected_model: selectedModel,
            model_verified_at: now,
            free_only: true,
          },
          updated_at: now,
        }, { onConflict: "key" });

        return text(
          `تم ربط H السحابي مع OpenRouter ✅\nالنموذج المجاني المتحقق منه الآن: ${selectedModel}\nلن يستخدم H نموذجًا مدفوعًا تلقائيًا.\n\nارجع إلى H.`,
        );
      } catch (error) {
        await db.from("h_runtime_ai_oauth_pending").delete().eq("state_hash", stateHash);
        return text(`تعذر إكمال ربط H مع OpenRouter\n\n${errorMessage(error)}`, 500);
      }
    }

    if (req.method === "POST" && path === "/disconnect") {
      if (!await isRuntimeAdmin(req, db)) return json({ ok: false, error: "Unauthorized" }, 401);
      await db.from("h_runtime_ai_credentials").delete().eq("id", CREDENTIAL_ID);
      await db.from("h_runtime_ai_oauth_pending").delete().neq("state_hash", "__never__");
      await db.from("h_runtime_state").upsert({
        key: "openrouter_ai",
        value: { connected: false, provider: "openrouter", free_only: true },
        updated_at: new Date().toISOString(),
      }, { onConflict: "key" });
      return json({ ok: true, connected: false });
    }

    return json({ ok: false, error: "Not found", receivedPath: url.pathname, normalizedPath: path }, 404);
  } catch (error) {
    console.error("H OpenRouter OAuth failed", error);
    return json({ ok: false, error: errorMessage(error) }, 500);
  }
});

async function isRuntimeAdmin(req: Request, db: any): Promise<boolean> {
  const provided = req.headers.get("x-h-runtime-secret")?.trim();
  if (!provided) return false;
  const { data: config } = await db.from("h_runtime_config")
    .select("secret_value")
    .eq("key", "poll_secret")
    .maybeSingle();
  const expected = String(config?.secret_value || "");
  return expected.length > 0 && constantTimeEquals(expected, provided);
}

async function exchangeOpenRouterCode(code: string, verifier: string): Promise<string> {
  const response = await fetch(OPENROUTER_KEY_EXCHANGE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({
      code,
      code_verifier: verifier,
      code_challenge_method: "S256",
    }),
  });
  const bodyText = await response.text();
  if (!response.ok) throw new Error(`OpenRouter OAuth exchange failed (${response.status}): ${bodyText.slice(0, 300)}`);
  const body = JSON.parse(bodyText);
  const key = String(body?.key || "").trim();
  if (!key) throw new Error("OpenRouter returned an empty API key");
  return key;
}

async function loadOpenRouterModels(apiKey: string): Promise<any[]> {
  const response = await fetch(OPENROUTER_MODELS_URL, {
    headers: { Authorization: `Bearer ${apiKey}`, Accept: "application/json" },
  });
  const bodyText = await response.text();
  if (!response.ok) throw new Error(`OpenRouter model catalog failed (${response.status}): ${bodyText.slice(0, 300)}`);
  const body = JSON.parse(bodyText);
  if (!Array.isArray(body?.data)) throw new Error("OpenRouter model catalog returned an unexpected response");
  return body.data;
}

function selectStrictlyFreeModel(models: any[], preferred: string | null): string | null {
  const free = models
    .filter((model) => typeof model?.id === "string" && isStrictlyZeroPriced(model?.pricing))
    .map((model) => ({
      id: String(model.id),
      contextLength: Number(model.context_length || 0),
    }));
  if (!free.length) return null;

  if (preferred && free.some((model) => model.id === preferred)) return preferred;
  if (free.some((model) => model.id === "openrouter/free")) return "openrouter/free";

  free.sort((a, b) => b.contextLength - a.contextLength || a.id.localeCompare(b.id));
  return free[0]?.id ?? null;
}

function isStrictlyZeroPriced(pricing: unknown): boolean {
  if (!pricing || typeof pricing !== "object" || Array.isArray(pricing)) return false;
  const values = pricing as Record<string, unknown>;
  for (const required of ["prompt", "completion"]) {
    const number = Number(values[required]);
    if (!Number.isFinite(number) || number !== 0) return false;
  }

  for (const value of Object.values(values)) {
    if (value == null || value === "") continue;
    const number = Number(value);
    if (!Number.isFinite(number) || number !== 0) return false;
  }
  return true;
}

function encryptionSecretConfigured(): boolean {
  return Boolean(Deno.env.get("H_CREDENTIAL_ENCRYPTION_KEY")?.trim());
}

function requireEncryptionSecret(): string {
  const value = Deno.env.get("H_CREDENTIAL_ENCRYPTION_KEY")?.trim();
  if (!value) throw new Error("H_CREDENTIAL_ENCRYPTION_KEY is not configured");
  return value;
}

async function getEncryptionKey(): Promise<CryptoKey> {
  const bytes = decodeBase64Url(requireEncryptionSecret());
  if (bytes.length !== 32) throw new Error("H_CREDENTIAL_ENCRYPTION_KEY must decode to exactly 32 random bytes");
  return crypto.subtle.importKey("raw", bytes, { name: "AES-GCM" }, false, ["encrypt", "decrypt"]);
}

async function encryptSecret(value: string): Promise<{ ciphertext: string; iv: string }> {
  const iv = new Uint8Array(12);
  crypto.getRandomValues(iv);
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    await getEncryptionKey(),
    new TextEncoder().encode(value),
  );
  return { ciphertext: base64Url(new Uint8Array(encrypted)), iv: base64Url(iv) };
}

async function decryptSecret(ciphertext: string, iv: string): Promise<string> {
  const decrypted = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: decodeBase64Url(iv) },
    await getEncryptionKey(),
    decodeBase64Url(ciphertext),
  );
  return new TextDecoder().decode(decrypted);
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
  if (!path.startsWith("/")) path = `/${path}`;
  return path;
}

function randomUrlSafe(byteCount: number): string {
  const bytes = new Uint8Array(byteCount);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

async function pkceChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

async function sha256Base64Url(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return base64Url(new Uint8Array(digest));
}

function constantTimeEquals(expected: string, actual: string): boolean {
  const a = new TextEncoder().encode(expected);
  const b = new TextEncoder().encode(actual);
  if (a.length !== b.length) return false;
  let result = 0;
  for (let i = 0; i < a.length; i += 1) result |= a[i] ^ b[i];
  return result === 0;
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

function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

function text(body: string, status = 200): Response {
  return new Response(body, {
    status,
    headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" },
  });
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
