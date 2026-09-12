import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

async function loadVoiceInternals() {
  let source = await readFile(new URL("./index.js", import.meta.url), "utf8");
  for (const name of ["normalizeInboundMessage", "transcribeWhatsAppAudio", "bridgeVoiceTranscript"]) {
    source = source.replace(`async function ${name}(`, `export async function ${name}(`);
  }
  return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

const env = {
  META_GRAPH_VERSION: "v23.0",
  WHATSAPP_ACCESS_TOKEN: "meta-test-token",
  TRANSCRIPTION_API_KEY: "free-stt-test-key",
  TRANSCRIPTION_API_URL: "https://stt.example/v1/audio/transcriptions",
  TRANSCRIPTION_MODEL: "whisper-test",
  H_SUPABASE_VOICE_URL: "https://primary.supabase.co/functions/v1/h-whatsapp-inbox",
  H_RUNTIME_SECRET: "runtime-secret",
};

test("voice note flows Meta metadata -> media download -> STT -> unified H bridge", async () => {
  const { normalizeInboundMessage, bridgeVoiceTranscript } = await loadVoiceInternals();
  const calls = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (input, init = {}) => {
    const url = String(input);
    calls.push({ url, init });
    if (url === "https://graph.facebook.com/v23.0/media-voice-1") {
      return new Response(JSON.stringify({
        url: "https://media.example/voice-1.ogg",
        mime_type: "audio/ogg",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    if (url === "https://media.example/voice-1.ogg") {
      return new Response(new Blob([new Uint8Array([1, 2, 3, 4])], { type: "audio/ogg" }), { status: 200 });
    }
    if (url === env.TRANSCRIPTION_API_URL) {
      assert.match(String(init.headers?.Authorization || ""), /^Bearer /);
      return new Response(JSON.stringify({ text: "ذكرني بعد ساعة أشرب ماء" }), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    }
    if (url === env.H_SUPABASE_VOICE_URL) {
      const body = JSON.parse(String(init.body || "{}"));
      assert.equal(body.mode, "voice_transcript");
      assert.equal(body.wa_id, "966551234567");
      assert.equal(body.message_id, "wamid.voice.e2e");
      assert.equal(body.transcript, "ذكرني بعد ساعة أشرب ماء");
      assert.equal(body.sender_role, "owner");
      assert.equal(body.can_send_external, true);
      assert.equal(init.headers["x-h-runtime-secret"], env.H_RUNTIME_SECRET);
      return new Response(JSON.stringify({ ok: true, duplicate: false, reply: "تم إنشاء التذكير" }), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    }
    throw new Error(`unexpected request ${url}`);
  };

  try {
    const inbound = await normalizeInboundMessage({ type: "audio", audio: { id: "media-voice-1" } }, env);
    assert.equal(inbound.text, "ذكرني بعد ساعة أشرب ماء");
    assert.equal(inbound.error, undefined);

    const bridged = await bridgeVoiceTranscript(
      env,
      "+966 55 123 4567",
      "wamid.voice.e2e",
      inbound.text,
      "1789218000",
      { role: "owner", canSendExternal: true },
    );
    assert.equal(bridged.ok, true);
    assert.equal(bridged.reply, "تم إنشاء التذكير");
    assert.equal(calls.length, 4);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("voice note fails closed before Meta download when no STT credential exists", async () => {
  const { normalizeInboundMessage } = await loadVoiceInternals();
  const originalFetch = globalThis.fetch;
  let fetches = 0;
  globalThis.fetch = async () => {
    fetches += 1;
    throw new Error("network must not be touched");
  };
  try {
    const inbound = await normalizeInboundMessage(
      { type: "audio", audio: { id: "media-voice-2" } },
      { ...env, TRANSCRIPTION_API_KEY: "", GROQ_API_KEY: "" },
    );
    assert.equal(inbound.text, "");
    assert.match(inbound.error, /تحويل الصوت إلى نص غير مفعّل/);
    assert.equal(fetches, 0);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
