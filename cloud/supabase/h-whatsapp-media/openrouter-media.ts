import { decodeTextDocument, type HMediaMessageInput } from "../h-whatsapp-inbox/media-bridge.ts";
import { completeWithOwnerPaidHelper } from "../h-whatsapp-inbox/owner-paid-ai.ts";
import type { HOwnerPaidCapability } from "../h-whatsapp-inbox/owner-paid-policy.ts";

const OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models";
const OPENROUTER_CHAT_URL = "https://openrouter.ai/api/v1/chat/completions";
const CREDENTIAL_ID = "openrouter_default";

type DbClient = any;
type RequiredInput = "text" | "image" | "audio" | "video";

type AiCredential = {
  apiKey: string;
  preferredModel: string | null;
  source: "oauth_encrypted" | "legacy_env";
};

/**
 * H's transient media orchestrator.
 *
 * When an owner-paid/BYOK route is enabled it is authoritative for every media AI turn.
 * The strictly-free media credential is reachable only when no paid route is configured.
 */
export async function completeFreeOpenRouterMediaAnalysis(
  db: DbClient,
  input: HMediaMessageInput,
): Promise<{ content: string; model: string } | null> {
  try {
    const requiredInput = requiredInputFor(input);
    const paidCapability = ownerPaidCapabilityFor(input);
    const prompt = [
      "Analyze ONLY the transient media supplied in this request.",
      "Return concise plain text, not JSON and not markdown.",
      "Extract useful facts needed to answer the user's request. For audio, transcribe relevant speech accurately. For video, describe relevant scenes/actions/text/audio when supported.",
      "If something is unreadable, inaudible, unsupported, or uncertain, say that explicitly. Never invent missing content.",
      "The raw attachment is transient working data and must not be treated as durable H memory.",
      "Respond in Arabic unless the user's caption clearly uses another language.",
      input.caption ? `User caption/instruction: ${input.caption}` : "User caption/instruction: none.",
      input.fileName ? `Filename: ${input.fileName}` : "Filename: unavailable.",
      input.durationMs != null ? `Duration seconds: ${Math.ceil(input.durationMs / 1000)}` : "Duration: not applicable.",
    ].join("\n");

    let messages: any[];
    let freePlugins: any[] | undefined;
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
    } else if (input.kind === "audio") {
      const format = audioFormat(input.mimeType);
      if (!format) return null;
      messages = [{
        role: "user",
        content: [
          { type: "text", text: prompt },
          {
            type: "input_audio",
            input_audio: {
              data: input.base64,
              format,
            },
          },
        ],
      }];
    } else if (input.kind === "video") {
      messages = [{
        role: "user",
        content: [
          { type: "text", text: prompt },
          {
            type: "video_url",
            video_url: { url: `data:${input.mimeType};base64,${input.base64}` },
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
      // This parser is used only by the strictly-free route. The paid route must support
      // the file modality itself; H never mixes the selected paid model with a free AI parser.
      freePlugins = [{ id: "file-parser", pdf: { engine: "cloudflare-ai" } }];
    } else {
      const text = decodeTextDocument(input);
      if (!text) return null;
      messages = [{
        role: "user",
        content: `${prompt}\n\nDocument text:\n${text}`,
      }];
    }

    const paid = await completeWithOwnerPaidHelper({
      db,
      messages,
      temperature: 0,
      stage: "media",
      taskClass: "hard",
      capability: paidCapability,
      // Deliberately do not pass the free PDF parser plugin into owner-paid inference.
      plugins: undefined,
    });
    if (paid.status === "success") {
      const verifiedAt = new Date().toISOString();
      await recordMediaAiState(db, {
        connected: true,
        provider: paid.provider,
        owner_paid: true,
        free_only: false,
        exclusive_ai_routing: true,
        ready: true,
        kind: input.kind,
        mime_type: input.mimeType,
        selected_model: paid.model,
        route_id: paid.routeId,
        calls_used: paid.callsUsed,
        daily_limit: paid.dailyLimit,
        paid_retries: 0,
        free_fallback_used: false,
        raw_media_persisted: false,
        last_success_at: verifiedAt,
      });
      return { content: paid.content.slice(0, 9000), model: paid.model };
    }
    if (paid.status === "blocked") {
      await recordMediaAiState(db, {
        connected: true,
        owner_paid: true,
        free_only: false,
        exclusive_ai_routing: true,
        ready: false,
        kind: input.kind,
        mime_type: input.mimeType,
        route_id: paid.routeId,
        error: paid.reason,
        free_fallback_used: false,
        raw_media_persisted: false,
      });
      return null;
    }
    if (paid.status !== "not_configured") return null;

    const credential = await loadCredential(db);
    if (!credential) return null;

    const models = await loadOpenRouterModels(credential.apiKey);
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

    const content = await callMediaModel(credential.apiKey, model, messages, freePlugins);
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
      raw_media_persisted: false,
      last_success_at: verifiedAt,
    });
    return { content, model };
  } catch (error) {
    console.error("H OpenRouter media adapter failed", error);
    await recordMediaAiState(db, {
      connected: true,
      provider: "openrouter",
      ready: false,
      kind: input.kind,
      mime_type: input.mimeType,
      error: errorMessage(error).slice(0, 300),
    }).catch(() => undefined);
    return null;
  }
}

function requiredInputFor(input: HMediaMessageInput): RequiredInput {
  if (input.kind === "image") return "image";
  if (input.kind === "audio") return "audio";
  if (input.kind === "video") return "video";
  // PDFs use the explicitly free Cloudflare parser only when no paid route exists.
  return "text";
}

function ownerPaidCapabilityFor(input: HMediaMessageInput): HOwnerPaidCapability {
  if (input.kind === "image") return "image";
  if (input.kind === "audio") return "audio";
  if (input.kind === "video") return "video";
  if (input.mimeType === "application/pdf") return "file";
  return "text";
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
      "X-Title": "H Ephemeral Media Runtime",
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
  requiredInput: RequiredInput,
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
  // The generic free router currently advertises text/image only. Select it only when
  // the catalog itself says it supports the required input; otherwise use a concrete
  // zero-priced modality-capable model.
  if (free.some((model) => model.id === "openrouter/free")) return "openrouter/free";

  free.sort((a, b) => b.contextLength - a.contextLength || a.id.localeCompare(b.id));
  return free[0]?.id ?? null;
}

function modelSupportsInput(model: any, requiredInput: RequiredInput): boolean {
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

function audioFormat(mimeType: string): string | null {
  switch (mimeType) {
    case "audio/mpeg":
    case "audio/mp3": return "mp3";
    case "audio/wav":
    case "audio/x-wav": return "wav";
    case "audio/flac": return "flac";
    case "audio/mp4": return "m4a";
    case "audio/aac": return "aac";
    case "audio/ogg": return "ogg";
    case "audio/webm": return "webm";
    default: return null;
  }
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
