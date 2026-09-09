import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { evaluateAppCloudReadiness, evaluatePeachReadiness } from "./readiness-policy.ts";

type Readiness = {
  configured: boolean;
  aliases: string[];
};

Deno.serve(async (req: Request) => {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim() || "";
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim() || "";
  if (!supabaseUrl || !serviceRole) {
    return reply({ ok: false, error: "Supabase runtime credentials unavailable" }, 500);
  }

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const { data: config, error } = await db.from("h_runtime_config")
    .select("secret_value")
    .eq("key", "poll_secret")
    .maybeSingle();
  if (error) return reply({ ok: false, error: "Runtime authorization unavailable" }, 500);
  if (!config?.secret_value || req.headers.get("x-h-runtime-secret") !== config.secret_value) {
    return reply({ ok: false, error: "Unauthorized" }, 401);
  }
  if (req.method !== "POST") return reply({ ok: false, error: "Method not allowed" }, 405);

  const metaAccessToken = readiness("META_ACCESS_TOKEN", "WHATSAPP_ACCESS_TOKEN");
  const metaPhoneNumberId = readiness("WA_PHONE_NUMBER_ID", "WHATSAPP_PHONE_NUMBER_ID");
  const metaGraphVersion = readiness("WHATSAPP_API_VERSION", "META_GRAPH_VERSION");
  const metaAppSecret = readiness("WHATSAPP_APP_SECRET");
  const metaVerifyToken = readiness("WHATSAPP_VERIFY_TOKEN");
  const voiceTranscription = readiness("GROQ_API_KEY", "TRANSCRIPTION_API_KEY");
  const hRuntimeSecret = readiness("H_RUNTIME_SECRET");
  const paidTemplateEnabled = Deno.env.get("H_ALLOW_PAID_WHATSAPP_TEMPLATE") === "true";
  const templateNameConfigured = Boolean(Deno.env.get("WHATSAPP_REMINDER_TEMPLATE_NAME")?.trim());

  const [
    peachResult,
    ownerResult,
    schedulerResult,
    pollResult,
    appIdentityResult,
    pairingHandoffResult,
    mediaCredentialResult,
    mediaStateResult,
  ] = await Promise.all([
    db.from("h_runtime_credentials")
      .select("access_token,refresh_token,expires_at")
      .eq("id", "peach_default")
      .maybeSingle(),
    db.from("h_runtime_owner_identities")
      .select("wa_fingerprint", { count: "exact", head: true })
      .eq("active", true),
    db.from("h_runtime_state")
      .select("value,updated_at")
      .eq("key", "inbox_scheduler")
      .maybeSingle(),
    db.from("h_runtime_state")
      .select("value,updated_at")
      .eq("key", "inbox_poll")
      .maybeSingle(),
    db.from("h_runtime_app_identities")
      .select("google_subject_fingerprint", { count: "exact", head: true })
      .eq("active", true),
    // Selecting the handoff column is an explicit schema readiness check. A missing
    // migration fails this query even when the pairing table currently has zero rows.
    db.from("h_runtime_owner_pairing")
      .select("consumed_user_key_ciphertext")
      .limit(1),
    db.from("h_runtime_ai_credentials")
      .select("id", { count: "exact", head: true })
      .eq("id", "openrouter_default"),
    db.from("h_runtime_state")
      .select("value,updated_at")
      .eq("key", "openrouter_media")
      .maybeSingle(),
  ]);

  const peachStateReadable = !peachResult.error && !ownerResult.error && !schedulerResult.error && !pollResult.error;
  const peachCredential = peachResult.data as any;
  const schedulerState = schedulerResult.data as any;
  const pollState = pollResult.data as any;
  const peach = evaluatePeachReadiness({
    accessTokenPresent: Boolean(peachCredential?.access_token),
    refreshTokenPresent: Boolean(peachCredential?.refresh_token),
    expiresAt: peachCredential?.expires_at ? String(peachCredential.expires_at) : null,
    ownerIdentityCount: ownerResult.count ?? 0,
    schedulerCadence: schedulerState?.value?.cadence,
    schedulerOverlapGuard: schedulerState?.value?.overlap_guard,
    schedulerUpdatedAt: schedulerState?.updated_at ? String(schedulerState.updated_at) : null,
    pollUpdatedAt: pollState?.updated_at ? String(pollState.updated_at) : null,
  });

  const mediaStateRow = mediaStateResult.data as any;
  const mediaState = mediaStateRow?.value && typeof mediaStateRow.value === "object" && !Array.isArray(mediaStateRow.value)
    ? mediaStateRow.value as Record<string, unknown>
    : null;
  const appCloud = evaluateAppCloudReadiness({
    appIdentityCount: appIdentityResult.count ?? 0,
    appIdentityStateReadable: !appIdentityResult.error,
    encryptedPairingHandoffReadable: !pairingHandoffResult.error,
    mediaCredentialCount: mediaCredentialResult.count ?? 0,
    mediaCredentialStateReadable: !mediaCredentialResult.error,
    mediaStateReadable: !mediaStateResult.error,
    mediaState,
  });

  const outboundMetaReady = metaAccessToken.configured && metaPhoneNumberId.configured && metaGraphVersion.configured;
  const webhookMetaReady = metaAppSecret.configured && metaVerifyToken.configured;

  return reply({
    ok: true,
    service: "h-runtime-readiness",
    checkedAt: new Date().toISOString(),
    outboundMetaReady,
    webhookMetaReady,
    voiceTranscriptionReady: voiceTranscription.configured,
    internalBridgeSecretReady: hRuntimeSecret.configured,
    peachStateReadable,
    peachCredentialReady: peach.peachCredentialReady,
    peachOwnerIdentityConfigured: peach.ownerIdentityConfigured,
    peachSchedulerConfigured: peach.schedulerConfigured,
    peachSchedulerRecent: peach.schedulerRecent,
    peachPollingReady: peach.peachPollingReady,
    peachOwnerMessagingReady: peach.peachOwnerMessagingReady,
    appCloudStateReadable: appCloud.appCloudStateReadable,
    appLinkInfrastructureReady: appCloud.appLinkInfrastructureReady,
    appOwnerLinked: appCloud.appOwnerLinked,
    appLinkRequired: appCloud.appLinkRequired,
    appCloudReady: appCloud.appCloudReady,
    freeMediaCredentialStateReadable: appCloud.freeMediaCredentialStateReadable,
    freeMediaCredentialConfigured: appCloud.freeMediaCredentialConfigured,
    freeMediaStateReadable: appCloud.freeMediaStateReadable,
    freeMediaStateObserved: appCloud.freeMediaStateObserved,
    freeMediaLastReady: appCloud.freeMediaLastReady,
    appEphemeralMediaConfigured: appCloud.appEphemeralMediaConfigured,
    appEphemeralMediaOwnerEligible: appCloud.appEphemeralMediaOwnerEligible,
    appEphemeralMediaObservedReady: appCloud.appEphemeralMediaObservedReady,
    freeOnlyMediaPolicy: true,
    mediaObservation: sanitizeMediaObservation(mediaState, mediaStateRow?.updated_at),
    paidTemplateEnabled,
    templateNameConfigured,
    freeOnlyWhatsAppPolicy: !paidTemplateEnabled,
    requirements: {
      metaAccessToken,
      metaPhoneNumberId,
      metaGraphVersion,
      metaAppSecret,
      metaVerifyToken,
      voiceTranscription,
      hRuntimeSecret,
    },
  });
});

function sanitizeMediaObservation(value: Record<string, unknown> | null, updatedAt: unknown) {
  if (!value) return null;
  return {
    provider: boundedString(value.provider, 40),
    freeOnly: value.free_only === true,
    ready: value.ready === true,
    kind: boundedString(value.kind, 30),
    mimeType: boundedString(value.mime_type, 100),
    selectedModel: boundedString(value.selected_model, 160),
    credentialSource: boundedString(value.credential_source, 40),
    pdfParser: boundedString(value.pdf_parser, 40),
    lastSuccessAt: boundedString(value.last_success_at, 80),
    modelVerifiedAt: boundedString(value.model_verified_at, 80),
    error: boundedString(value.error, 300),
    updatedAt: boundedString(updatedAt, 80),
  };
}

function boundedString(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const text = value.trim();
  return text ? text.slice(0, max) : null;
}

function readiness(...aliases: string[]): Readiness {
  return {
    configured: aliases.some((name) => Boolean(Deno.env.get(name)?.trim())),
    aliases,
  };
}

function reply(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
