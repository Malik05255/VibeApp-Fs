const OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models";
const OPENROUTER_CHAT_URL = "https://openrouter.ai/api/v1/chat/completions";
const CREDENTIAL_ID = "openrouter_default";

type DbClient = any;

type AiCredential = {
  apiKey: string;
  preferredModel: string | null;
  source: "oauth_encrypted" | "legacy_env";
};

export type HOpenRouterStatus = {
  configured: boolean;
  provider: "openrouter";
  model: string | null;
  modelVerifiedAt: string | null;
  credentialSource: "oauth_encrypted" | "legacy_env" | "none";
  freeOnly: true;
};

export async function getOpenRouterAiStatus(db: DbClient): Promise<HOpenRouterStatus> {
  const { data: row } = await db.from("h_runtime_ai_credentials")
    .select("provider,selected_model,model_verified_at")
    .eq("id", CREDENTIAL_ID)
    .maybeSingle();

  if (row) {
    return {
      configured: encryptionSecretConfigured(),
      provider: "openrouter",
      model: row.selected_model ?? null,
      modelVerifiedAt: row.model_verified_at ?? null,
      credentialSource: encryptionSecretConfigured() ? "oauth_encrypted" : "none",
      freeOnly: true,
    };
  }

  const legacyKey = String(Deno.env.get("OPENROUTER_API_KEY") || "").trim();
  return {
    configured: Boolean(legacyKey),
    provider: "openrouter",
    model: String(Deno.env.get("H_MODEL") || "").trim() || null,
    modelVerifiedAt: null,
    credentialSource: legacyKey ? "legacy_env" : "none",
    freeOnly: true,
  };
}

export async function completeFreeOpenRouterChat(
  db: DbClient,
  messages: Array<Record<string, string>>,
): Promise<{ content: string; model: string } | null> {
  try {
    // Credential loading is inside the safety boundary deliberately. If an encrypted row
    // exists but the server encryption key is missing/invalid, WhatsApp must fall back to
    // H's direct reminders/memory commands instead of failing the whole inbound message.
    const credential = await loadCredential(db);
    if (!credential) return null;

    // Re-read the live model catalog immediately before every model call. If pricing cannot
    // be proven zero, fail closed. H never silently moves from a free route to a paid route.
    const models = await loadOpenRouterModels(credential.apiKey);
    const model = selectStrictlyFreeModel(models, credential.preferredModel);
    if (!model) {
      await recordAiState(db, {
        connected: true,
        provider: "openrouter",
        free_only: true,
        ready: false,
        error: "no_strictly_zero_priced_model",
      });
      return null;
    }

    const verifiedAt = new Date().toISOString();
    await noteVerifiedModel(db, credential, model, verifiedAt);

    const response = await fetch(OPENROUTER_CHAT_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${credential.apiKey}`,
        "Content-Type": "application/json",
        "HTTP-Referer": Deno.env.get("H_PUBLIC_BASE_URL") || Deno.env.get("SUPABASE_URL") || "https://supabase.com",
        "X-Title": "H WhatsApp Cloud Runtime",
      },
      body: JSON.stringify({
        model,
        temperature: 0.15,
        messages,
      }),
    });

    const bodyText = await response.text();
    if (!response.ok) {
      console.error(`H OpenRouter free model call failed (${response.status}): ${bodyText.slice(0, 300)}`);
      await recordAiState(db, {
        connected: true,
        provider: "openrouter",
        free_only: true,
        ready: false,
        selected_model: model,
        model_verified_at: verifiedAt,
        error: `chat_http_${response.status}`,
      });
      return null;
    }

    const body = JSON.parse(bodyText);
    const content = String(body?.choices?.[0]?.message?.content || "").trim();
    if (!content) return null;

    await recordAiState(db, {
      connected: true,
      provider: "openrouter",
      free_only: true,
      ready: true,
      selected_model: model,
      model_verified_at: verifiedAt,
      last_success_at: new Date().toISOString(),
      credential_source: credential.source,
    });
    return { content, model };
  } catch (error) {
    console.error("H free OpenRouter adapter failed", error);
    await recordAiState(db, {
      connected: true,
      provider: "openrouter",
      free_only: true,
      ready: false,
      error: errorMessage(error).slice(0, 300),
    }).catch(() => undefined);
    return null;
  }
}

async function loadCredential(db: DbClient): Promise<AiCredential | null> {
  const { data: row } = await db.from("h_runtime_ai_credentials")
    .select("secret_ciphertext,secret_iv,secret_version,selected_model")
    .eq("id", CREDENTIAL_ID)
    .maybeSingle();

  if (row) {
    if (Number(row.secret_version || 1) !== 1) throw new Error("Unsupported H cloud credential version");
    const apiKey = (await decryptSecret(String(row.secret_ciphertext), String(row.secret_iv))).trim();
    if (!apiKey) throw new Error("Decrypted OpenRouter credential is empty");
    return {
      apiKey,
      preferredModel: String(row.selected_model || "").trim() || null,
      source: "oauth_encrypted",
    };
  }

  // Transitional compatibility for an already-deployed runtime. Even this path is forced
  // through the live zero-price catalog check; H_MODEL can never force a paid route.
  const legacyKey = String(Deno.env.get("OPENROUTER_API_KEY") || "").trim();
  if (!legacyKey) return null;
  return {
    apiKey: legacyKey,
    preferredModel: String(Deno.env.get("H_MODEL") || "").trim() || null,
    source: "legacy_env",
  };
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

export function selectStrictlyFreeModel(models: any[], preferred: string | null): string | null {
  const free = models
    .filter((model) => typeof model?.id === "string" && isStrictlyZeroPriced(model?.pricing))
    .map((model) => ({ id: String(model.id), contextLength: Number(model.context_length || 0) }));
  if (!free.length) return null;

  if (preferred && free.some((model) => model.id === preferred)) return preferred;
  if (free.some((model) => model.id === "openrouter/free")) return "openrouter/free";

  free.sort((a, b) => b.contextLength - a.contextLength || a.id.localeCompare(b.id));
  return free[0]?.id ?? null;
}

export function isStrictlyZeroPriced(pricing: unknown): boolean {
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

async function noteVerifiedModel(db: DbClient, credential: AiCredential, model: string, verifiedAt: string) {
  if (credential.source === "oauth_encrypted") {
    const { error } = await db.from("h_runtime_ai_credentials").update({
      selected_model: model,
      model_verified_at: verifiedAt,
      updated_at: verifiedAt,
    }).eq("id", CREDENTIAL_ID);
    if (error) throw error;
  }

  await recordAiState(db, {
    connected: true,
    provider: "openrouter",
    free_only: true,
    ready: true,
    selected_model: model,
    model_verified_at: verifiedAt,
    credential_source: credential.source,
  });
}

async function recordAiState(db: DbClient, value: Record<string, unknown>) {
  await db.from("h_runtime_state").upsert({
    key: "openrouter_ai",
    value,
    updated_at: new Date().toISOString(),
  }, { onConflict: "key" });
}

function encryptionSecretConfigured(): boolean {
  return Boolean(Deno.env.get("H_CREDENTIAL_ENCRYPTION_KEY")?.trim());
}

async function getEncryptionKey(): Promise<CryptoKey> {
  const encoded = Deno.env.get("H_CREDENTIAL_ENCRYPTION_KEY")?.trim();
  if (!encoded) throw new Error("H_CREDENTIAL_ENCRYPTION_KEY is not configured");
  const bytes = decodeBase64Url(encoded);
  if (bytes.length !== 32) throw new Error("H_CREDENTIAL_ENCRYPTION_KEY must decode to exactly 32 random bytes");
  return crypto.subtle.importKey("raw", toArrayBuffer(bytes), { name: "AES-GCM" }, false, ["decrypt"]);
}

async function decryptSecret(ciphertext: string, iv: string): Promise<string> {
  const decrypted = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: toArrayBuffer(decodeBase64Url(iv)) },
    await getEncryptionKey(),
    toArrayBuffer(decodeBase64Url(ciphertext)),
  );
  return new TextDecoder().decode(decrypted);
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}

function decodeBase64Url(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
