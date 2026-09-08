import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { completeFreeOpenRouterMediaAnalysis } from "./openrouter-media.ts";
import {
  buildMediaConversationText,
  mediaStorageMetadata,
  parseMediaMessagePayload,
} from "../h-whatsapp-inbox/media-bridge.ts";

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "Method not allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim() || "";
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim() || "";
  if (!supabaseUrl || !serviceRole) {
    return reply({ ok: false, error: "Supabase runtime credentials unavailable" }, 500);
  }

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const { data: config, error: configError } = await db.from("h_runtime_config")
    .select("secret_value")
    .eq("key", "poll_secret")
    .maybeSingle();
  if (configError) return reply({ ok: false, error: "Runtime secret lookup failed" }, 500);

  const runtimeSecret = String(config?.secret_value || "");
  if (!runtimeSecret || req.headers.get("x-h-runtime-secret") !== runtimeSecret) {
    return reply({ ok: false, error: "Unauthorized" }, 401);
  }

  let payload: unknown;
  try {
    payload = await req.json();
  } catch (_) {
    return reply({ ok: false, error: "Invalid JSON" }, 400);
  }

  const input = parseMediaMessagePayload(payload);
  if (!input) return reply({ ok: false, error: "invalid_or_unsupported_media_payload" }, 400);

  try {
    const analysis = await completeFreeOpenRouterMediaAnalysis(db, input);
    if (!analysis?.content) {
      await recordState(db, {
        ok: false,
        kind: input.kind,
        mime_type: input.mimeType,
        size_bytes: input.sizeBytes,
        raw_media_persisted: false,
        error: "no_strictly_free_media_analysis_available",
      });
      return reply({
        ok: false,
        error: "no_strictly_free_media_analysis_available",
        message: "No paid fallback was used.",
      }, 503);
    }

    const contextText = buildMediaConversationText(input, analysis.content);
    const inboxEndpoint = `${supabaseUrl.replace(/\/$/, "")}/functions/v1/h-whatsapp-inbox`;
    const bridgeResponse = await fetch(inboxEndpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-h-runtime-secret": runtimeSecret,
      },
      body: JSON.stringify({
        mode: "channel_message",
        wa_id: input.waId,
        message_id: input.messageId,
        text: contextText,
        source_type: input.kind,
        sender_role: "friend",
        can_send_external: false,
        received_at: input.receivedAt || new Date().toISOString(),
      }),
    });
    const bridgeText = await bridgeResponse.text();
    let bridge: any = {};
    try { bridge = bridgeText ? JSON.parse(bridgeText) : {}; } catch (_) {}
    if (!bridgeResponse.ok || bridge?.ok === false) {
      throw new Error(`Unified H media bridge failed (${bridgeResponse.status}): ${String(bridge?.error || bridgeText).slice(0, 300)}`);
    }

    await recordState(db, {
      ok: true,
      ...mediaStorageMetadata(input, analysis.model),
      unified_h_status: bridge?.status || "processed",
      duplicate: Boolean(bridge?.duplicate),
    });

    return reply({
      ok: true,
      duplicate: Boolean(bridge?.duplicate),
      status: bridge?.status || "processed",
      reply: bridge?.reply || null,
      media_kind: input.kind,
      media_model: analysis.model,
      raw_media_persisted: false,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error("H WhatsApp media bridge failed", message);
    await recordState(db, {
      ok: false,
      kind: input.kind,
      mime_type: input.mimeType,
      size_bytes: input.sizeBytes,
      raw_media_persisted: false,
      error: message.slice(0, 300),
    }).catch(() => undefined);
    return reply({ ok: false, error: message }, 500);
  }
});

async function recordState(db: any, value: Record<string, unknown>) {
  await db.from("h_runtime_state").upsert({
    key: "whatsapp_media_bridge",
    value: {
      ...value,
      updated_at: new Date().toISOString(),
    },
    updated_at: new Date().toISOString(),
  }, { onConflict: "key" });
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
