import {
  buildVerifierMessages,
  parseVerifierReply,
  prepareResearchBundle,
  type ResearchBundle,
} from "./research-router.ts";
import { decodeTextDocument, type HMediaMessageInput } from "./media-bridge.ts";

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
    const credential = await loadCredential(db);
    if (!credential) return null;

    // Every call re-checks the live model catalog. No paid model can be selected by a
    // stale preference, research router, verifier, or fallback path.
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

    // The research router decides whether this is news, shopping, local places, routes,
    // market data, deep research, ordinary web research, or normal chat. This replaces
    // the old Tavily-only grounding path.
    const research = await prepareResearchBundle(db, messages);
    const candidateText = await callFreeModel(
      credential.apiKey,
      model,
      research.messages,
      0.12,
      "candidate",
    );
    const candidateDecision = ensureDecisionJson(candidateText);
    let finalDecision = candidateDecision;

    // Any external/fresh factual answer gets a second independent model pass over the
    // evidence and hard constraints. The verifier may shrink the result to a supported
    // subset, but it must never add facts from memory.
    if (research.active && decisionAction(candidateDecision) === "reply") {
      try {
        const verifierText = await callFreeModel(
          credential.apiKey,
          model,
          buildVerifierMessages(research, candidateDecision),
          0,
          "verifier",
        );
        const verified = parseVerifierReply(verifierText);
        if (!verified) {
          finalDecision = strictVerifierFallback(research);
          await recordVerifierState(db, research, {
            ok: false,
            error: "verifier_parse_failed",
          });
        } else {
          finalDecision = JSON.stringify({
            action: "reply",
            reply: verified.reply.slice(0, 3000),
          });
          await recordVerifierState(db, research, {
            ok: verified.ok,
            reason: verified.reason,
          });
        }
      } catch (verifierError) {
        // A current/research answer is not allowed to bypass the verifier merely because
        // the second free-model call failed. Fail closed rather than leak an unchecked claim.
        finalDecision = strictVerifierFallback(research);
        await recordVerifierState(db, research, {
          ok: false,
          error: errorMessage(verifierError).slice(0, 300),
        }).catch(() => undefined);
      }
    }

    await recordAiState(db, {
      connected: true,
      provider: "openrouter",
      free_only: true,
      ready: true,
      selected_model: model,
      model_verified_at: verifiedAt,
      last_success_at: new Date().toISOString(),
      credential_source: credential.source,
      encryption_source: "supabase_service_role_derived_v1",
      research_router_enabled: true,
      research_verifier_enabled: true,
      last_research_intent: research.intent,
      last_research_source_count: research.evidence.length,
    });
    return { content: finalDecision, model };
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

export async function completeFreeOpenRouterMediaAnalysis(
  db: DbClient,
  input: HMediaMessageInput,
): Promise<{ content: string; model: string } | null> {
  try {
    const credential = await loadCredential(db);
    if (!credential) return null;

    const models = await loadOpenRouterModels(credential.apiKey);
    const requiredInput = input.kind === "image" ? "image" : "text";
    const model = selectStrictlyFreeModelForInput(models, credential.preferredModel, requiredInput);
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

async function callFreeModel(
  apiKey: string,
  model: string,
  messages: Array<Record<string, string>>,
  temperature: number,
  stage: "candidate" | "verifier",
): Promise<string> {
  const response = await fetch(OPENROUTER_CHAT_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "HTTP-Referer": Deno.env.get("H_PUBLIC_BASE_URL") || Deno.env.get("SUPABASE_URL") || "https://supabase.com",
      "X-Title": "H WhatsApp Cloud Runtime",
    },
    body: JSON.stringify({ model, temperature, messages }),
  });
  const bodyText = await response.text();
  if (!response.ok) {
    throw new Error(`OpenRouter ${stage} failed (${response.status}): ${bodyText.slice(0, 300)}`);
  }
  const body = JSON.parse(bodyText);
  const content = String(body?.choices?.[0]?.message?.content || "").trim();
  if (!content) throw new Error(`OpenRouter ${stage} returned empty content`);
  return content;
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

export function selectStrictlyFreeModel(models: any[], preferred: string | null): string | null {
  return selectStrictlyFreeModelForInput(models, preferred, "text");
}

export function selectStrictlyFreeModelForInput(
  models: any[],
  preferred: string | null,
  requiredInput: "text" | "image" | "file",
): string | null {
  const free = models
    .filter((model) =>
      typeof model?.id === "string" &&
      isStrictlyZeroPriced(model?.pricing) &&
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
  // Old catalog entries without architecture metadata are accepted only for text.
  return requiredInput === "text";
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
    encryption_source: "supabase_service_role_derived_v1",
  });
}

async function recordAiState(db: DbClient, value: Record<string, unknown>) {
  await db.from("h_runtime_state").upsert({
    key: "openrouter_ai",
    value,
    updated_at: new Date().toISOString(),
  }, { onConflict: "key" });
}

async function recordMediaAiState(db: DbClient, value: Record<string, unknown>) {
  await db.from("h_runtime_state").upsert({
    key: "openrouter_media",
    value,
    updated_at: new Date().toISOString(),
  }, { onConflict: "key" });
}

async function recordVerifierState(
  db: DbClient,
  research: ResearchBundle,
  result: Record<string, unknown>,
) {
  await db.from("h_runtime_state").upsert({
    key: "research_verifier",
    value: {
      intent: research.intent,
      evidence_count: research.evidence.length,
      providers: research.providerTrace,
      hard_constraints: research.hardConstraints,
      ...result,
      verified_at: new Date().toISOString(),
    },
    updated_at: new Date().toISOString(),
  }, { onConflict: "key" });
}

function decisionAction(json: string): string {
  try {
    const parsed = JSON.parse(json);
    return String(parsed?.action || "reply");
  } catch (_) {
    return "reply";
  }
}

function strictVerifierFallback(research: ResearchBundle): string {
  const message = research.evidence.length
    ? "جمعت مصادر للطلب، لكن التحقق النهائي ما اكتمل بشكل موثوق. ما راح أعرض نتيجة غير مؤكدة؛ أعد المحاولة وسأعيد التحقق من المصادر."
    : research.intent === "route"
      ? "هذا الطلب يحتاج محرك مسارات موثوق وموقع/نقطتي بداية ونهاية. ما راح أخمّن المسافة أو وقت الوصول."
      : research.intent === "market_data"
        ? "هذا الطلب يحتاج مصدر أسعار لحظي موثوق. ما راح أعطيك سعرًا حاليًا من الذاكرة."
        : "ما حصلت أدلة كافية تسمح لي بإجابة مؤكدة الآن. ما راح أكمل النتيجة بالتخمين.";
  return JSON.stringify({ action: "reply", reply: message });
}

function encryptionSecretConfigured(): boolean {
  return Boolean(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim());
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

function ensureDecisionJson(content: string): string {
  const cleaned = content.trim().replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();
  if (!cleaned) return JSON.stringify({ action: "reply", reply: "" });
  try {
    const parsed = JSON.parse(cleaned);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) return JSON.stringify(parsed);
  } catch (_) {
    // Some free models ignore JSON-only output; preserve useful text as a reply decision.
  }
  return JSON.stringify({ action: "reply", reply: cleaned.slice(0, 3000) });
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
