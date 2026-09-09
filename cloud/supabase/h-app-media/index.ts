import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";
import { completeFreeOpenRouterMediaAnalysis } from "../h-whatsapp-media/openrouter-media.ts";
import {
  chooseEphemeralMediaStrategy,
  type HEphemeralMediaKind,
} from "../h-whatsapp-media/ephemeral-media-policy.ts";
import {
  isSupportedMedia,
  maxAudioVideoDurationMs,
  maxMediaBytes,
  type HMediaKind,
  type HMediaMessageInput,
} from "../h-whatsapp-inbox/media-bridge.ts";

const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const MAX_CAPTION_CHARS = 2_000;
const MAX_FILE_NAME_CHARS = 160;

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ ok: false, error: "method_not_allowed" }, 405);

  const bearer = bearerToken(req.headers.get("authorization"));
  if (!bearer) return json({ ok: false, error: "google_sign_in_required" }, 401);

  let google;
  try {
    google = await verifyGoogleIdToken(bearer);
  } catch (error) {
    return json({ ok: false, error: errorMessage(error) }, 401);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim() || "";
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim() || "";
  if (!supabaseUrl || !serviceRole) return json({ ok: false, error: "runtime_unavailable" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });

  const runtimeSecret = await loadRuntimeSecret(db).catch(() => "");
  if (!runtimeSecret) return json({ ok: false, error: "runtime_unavailable" }, 500);
  const subjectFingerprint = await secretFingerprint(runtimeSecret, GOOGLE_SUB_LABEL, google.subject);
  const linked = await hasLinkedAppIdentity(db, subjectFingerprint, google.audience).catch(() => false);
  if (!linked) {
    return json({
      ok: false,
      error: "app_not_linked",
      message: "Transient cloud media requires the signed-in app to be linked to its H Cloud owner.",
    }, 403);
  }

  const body = await req.json().catch(() => null);
  const input = parseAppMedia(body);
  if (!input) return json({ ok: false, error: "invalid_or_unsupported_media_payload" }, 400);

  const policyKind: HEphemeralMediaKind = input.kind === "document" ? "pdf" : input.kind;
  const decision = chooseEphemeralMediaStrategy({
    kind: policyKind,
    source: "app",
    sizeBytes: input.sizeBytes,
    durationMs: input.durationMs,
  }, {
    // Local compression/long-text extraction already happens in Android before this
    // endpoint. This endpoint exists only for media that still needs a remote specialist.
    localDerivation: false,
    remoteReference: false,
    inlineFreeHelper: true,
    temporaryCloudFree: false,
  });

  if (!decision.allowed) {
    input.base64 = "";
    return json({
      ok: false,
      error: decision.reason,
      media_strategy: decision.strategy,
      raw_media_persisted: false,
      durable_media_memory: false,
      paid_fallback_used: false,
    }, 422);
  }

  try {
    const analysis = await completeFreeOpenRouterMediaAnalysis(db, input);
    if (!analysis?.content) {
      return json({
        ok: false,
        error: "no_strictly_free_media_analysis_available",
        media_strategy: decision.strategy,
        raw_media_persisted: false,
        durable_media_memory: false,
        paid_fallback_used: false,
      }, 503);
    }

    return json({
      ok: true,
      analysis: analysis.content,
      media_model: analysis.model,
      media_strategy: decision.strategy,
      raw_media_persisted: false,
      durable_media_memory: false,
      paid_fallback_used: false,
    });
  } catch (error) {
    console.error("H app transient media analysis failed", errorMessage(error));
    return json({
      ok: false,
      error: "transient_media_analysis_failed",
      raw_media_persisted: false,
      durable_media_memory: false,
      paid_fallback_used: false,
    }, 500);
  } finally {
    // No H object is created by this endpoint. Drop the raw in-memory reference as soon
    // as the analysis attempt ends so it cannot accidentally flow into later state.
    input.base64 = "";
  }
});

function parseAppMedia(payload: unknown): HMediaMessageInput | null {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return null;
  const value = payload as Record<string, unknown>;
  const kind = normalizeKind(value.kind);
  const mimeType = String(value.mime_type || "").split(";", 1)[0].trim().toLowerCase();
  const base64 = String(value.base64 || "").replace(/\s+/g, "");
  if (!kind || !mimeType || !isCanonicalBase64(base64)) return null;

  const sizeBytes = decodedBase64Size(base64);
  if (!Number.isFinite(sizeBytes) || sizeBytes <= 0 || sizeBytes > maxMediaBytes()) return null;
  if (!isSupportedMedia(kind, mimeType, sizeBytes)) return null;

  const durationMs = normalizeDuration(value.duration_ms);
  if (kind === "audio" || kind === "video") {
    if (durationMs == null || durationMs > maxAudioVideoDurationMs()) return null;
  }

  return {
    waId: "",
    messageId: "app-transient",
    kind,
    mimeType,
    fileName: sanitizeFileName(value.file_name),
    caption: sanitizeCaption(value.caption),
    base64,
    sizeBytes,
    durationMs,
    receivedAt: null,
  };
}

function normalizeKind(value: unknown): HMediaKind | null {
  if (value === "image" || value === "audio" || value === "video") return value;
  if (value === "pdf" || value === "document") return "document";
  return null;
}

function normalizeDuration(value: unknown): number | null {
  if (value == null || value === "") return null;
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) return null;
  return Math.trunc(numeric);
}

function sanitizeCaption(value: unknown): string | null {
  const text = String(value || "").replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim();
  return text ? text.slice(0, MAX_CAPTION_CHARS) : null;
}

function sanitizeFileName(value: unknown): string | null {
  const text = String(value || "")
    .replace(/[\u0000-\u001f\u007f]/g, "")
    .replace(/[\\/]+/g, "_")
    .trim();
  return text ? text.slice(0, MAX_FILE_NAME_CHARS) : null;
}

async function hasLinkedAppIdentity(db: any, subjectFingerprint: string, audience: string): Promise<boolean> {
  const { data, error } = await db.from("h_runtime_app_identities")
    .select("google_audience,active")
    .eq("google_subject_fingerprint", subjectFingerprint)
    .eq("active", true)
    .maybeSingle();
  if (error) throw error;
  return Boolean(data?.active) && String(data?.google_audience || "") === audience;
}

async function loadRuntimeSecret(db: any): Promise<string> {
  const { data, error } = await db.from("h_runtime_config")
    .select("secret_value")
    .eq("key", "poll_secret")
    .maybeSingle();
  if (error) throw error;
  const value = String(data?.secret_value || "").trim();
  if (!value) throw new Error("runtime_secret_missing");
  return value;
}

async function secretFingerprint(secret: string, label: string, value: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${label}:${value}`),
  );
  return [...new Uint8Array(signature)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function isCanonicalBase64(value: string): boolean {
  if (!value || value.length % 4 === 1) return false;
  return /^[A-Za-z0-9+/]+={0,2}$/.test(value);
}

function decodedBase64Size(value: string): number {
  const padding = value.endsWith("==") ? 2 : value.endsWith("=") ? 1 : 0;
  return Math.floor(value.length * 3 / 4) - padding;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error || "unknown_error");
}

function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
