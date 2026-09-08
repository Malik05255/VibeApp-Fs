import { decodeTextDocument, type HMediaMessageInput } from "../h-whatsapp-inbox/media-bridge.ts";

const OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models";
const OPENROUTER_CHAT_URL = "https://openrouter.ai/api/v1/chat/completions";
const CREDENTIAL_ID = "openrouter_default";

type DbClient = any;

type AiCredential = {
  apiKey: string;
  preferredModel: string | null;
  source: "oauth_encrypted" | "legacy_env";
};

export async function completeFreeOpenRouterMediaAnalysis(
  db: DbClient,
  input: HMediaMessageInput,
): Promise<{ content: string; model: string } | null> {
  try {
    const credential = await loadCredential(db);
    if (!credential) return null;

    const models = await loadOpenRouterModels(credential.apiKey);
    const requiredInput = input.kind === "image" ? "image" : "text";
    const model = selectStrictlyFreeMediaModel(models, credential.preferredModel, requiredInput);
    if (!model) {
      await recordMediaAiState(db, {
        connected: true,
        provider: "openrouter",
        free_only: true,
        ready: false,
        kind: input.kind,
        mime_type: input.mimeType,
        error: `no_strictly_zero_priced_${requiredInput}_model`,
      });
      return null;
    }

    const prompt = [
      "Analyze ONLY the WhatsApp media supplied in this request.",
      "Return concise plain text, not JSON and not markdown.",
      "Extract visible/readable text, names, dates, amounts, labels, and other useful facts when present.",
      "If something is unreadable or uncertain, say that explicitly. Never invent missing content.",
      "Respond in Arabic unless the user's caption clearly uses another language.",
      input.caption ? `User caption/instruction: ${input.caption}` : "User caption/instruction: none.",
      input.fileName ? `Filename: ${input.fileName}` : "Filename: unavailable.",
    ].join("\n");

    let messages: any[];
    let plugins: any[] | undefined;
    if (input.kind === "image") {
      messages = [{
        role: "user",
        content: [
          { type: "text", text: prompt },
          {
            type: "image_url",
            image_url: { url: `data:${input.mimeType};base64,${input.base64}` },
          },
        ],
      }];
    } else if (input.mimeType === "application/pdf") {
      messages = [{
        role: "user",
        content: [
          { type: "text", text: prompt },
          {
            type: "file",
            file: {
              filename: input.fileName || "document.pdf",
              file_data: `data:application/pdf;base64,${input.base64}`,
            },
          },
        ],
      }];
      // Explicitly pin the free PDF parser. Never allow the paid OCR default.
      plugins = [{ id: "file-parser", pdf: { engine: "cloudflare-ai" } }];
    } else {
      const text = decodeTextDocument(input);
      if (!text) return null;
      messages = [{
        role: "user",
        content: `${prompt}\n\nDocument text:\n${text}`,
      }];
    }

    const content = await callMediaModel(credential.apiKey, model, messages, plugins);
    const verifiedAt = new Date().toISOString();
    await recordMediaAiState(db, {
      connected: true,
      provider: "openrouter",
      free_only: true,
      ready: true,
      kind: input.kind,
      mime_type: input.mimeType,
      selected_model: model,
      model_verified_at: verifiedAt,
      credential_source: credential.source,
      pdf_parser: input.mimeType === "application/pdf" ? "cloudflare-ai" : null,
      last_success_at: verifiedAt,
    });
    return { content, model };
  } catch (error) {
    console.error("H free OpenRouter media adapter failed", error);
    await recordMediaAiState(db, {
      connected: true,
      provider: "openrouter",
      free_only: true,
      ready: false,
      kind: input.kind,
      mime_type: input.mimeType,
      error: errorMessage(error).slice(0, 300),
    }).catch(() => undefined);
    return null;
  }
}

async function callMediaModel(
  apiKey: string,
  model: string,
  messages: any[],
  plugins?: any[],
): Promise<string> {
  const response = await fetch(OPENROUTER_CHAT_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "HTTP-Referer": Deno.env.get("H_PUBLIC_BASE_URL") || Deno.env.get("SUPABASE_URL") || "https://supabase.com",
      "X-Title": "H WhatsApp Media Runtime",
    },
    body: JSON.stringify({
      model,
      temperature: 0,
      messages,
      ...(plugins?.length ? { plugins } : {}),
    }),
  });
  const bodyText = await response.text();
  if (!response.ok) {
    throw new Error(`OpenRouter media analysis failed (${response.status}): ${bodyText.slice(0, 300)}`);
  }
  const body = JSON.parse(bodyText);
  const content = String(body?.choices?.[0]?.message?.content || "").trim();
  if (!content) throw new Error("OpenRouter media analysis returned empty content");
  return content.slice(0, 9000);
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

export function selectStrictlyFreeMediaModel(
  models: any[],
  preferred: string | null,
  requiredInput: "text" | "image" | "file",
): string | null {
  const free = models
    .filter((model) =>
      typeof model?.id === "string" &&
      isStrictlyZeroPricedMediaModel(model?.pricing) &&
      modelSupportsInput(model, requiredInput)
    )
    .map((model) => ({ id: String(model.id), contextLength: Number(model.context_length || 0) }));
  if (!free.length) return null;

  if (preferred && free.some((model) => model.id === preferred)) return preferred;
  if (free.some((model) => model.id === "openrouter/free")) return "openrouter/free";

  free.sort((a, b) => b.contextLength - a.contextLength || a.id.localeCompare(b.id));
  return free[0]?.id ?? null;
}

function modelSupportsInput(model: any, requiredInput: "text" | "image" | "file"): boolean {
  const modalities = Array.isArray(model?.architecture?.input_modalities)
    ? model.architecture.input_modalities.map((value: unknown) => String(value).toLowerCase())
    : [];
  if (modalities.length) return modalities.includes(requiredInput);
  return requiredInput === "text";
}

export function isStrictlyZeroPricedMediaModel(pricing: unknown): boolean {
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

async function recordMediaAiState(db: DbClient, value: Record<string, unknown>) {
  await db.from("h_runtime_state").upsert({
    key: "openrouter_media",
    value,
    updated_at: new Date().toISOString(),
  }, { onConflict: "key" });
}

async function getEncryptionKey(): Promise<CryptoKey> {
  const root = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!root) throw new Error("SUPABASE_SERVICE_ROLE_KEY is not configured");
  const digest = await crypto.subtle.digest(
    "SHA-256",
    toArrayBuffer(new TextEncoder().encode(`h-openrouter-aes-v1:${root}`)),
  );
  return crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["decrypt"]);
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
