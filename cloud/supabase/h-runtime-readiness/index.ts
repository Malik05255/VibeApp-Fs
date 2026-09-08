import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

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
