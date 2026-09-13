import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

async function loadChannelBridge() {
  let source = await readFile(new URL("./index.js", import.meta.url), "utf8");
  source = source.replace("async function bridgeChannelMessage(", "export async function bridgeChannelMessage(");
  return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

const env = {
  H_SUPABASE_VOICE_URL: "https://primary.supabase.co/functions/v1/h-whatsapp-inbox",
  H_RUNTIME_SECRET: "runtime-secret",
};

test("text channel bridge sends content and identity only; H Cloud owns authorization", async () => {
  const { bridgeChannelMessage } = await loadChannelBridge();
  const originalFetch = globalThis.fetch;
  let seen = null;

  globalThis.fetch = async (input, init = {}) => {
    assert.equal(String(input), env.H_SUPABASE_VOICE_URL);
    const body = JSON.parse(String(init.body || "{}"));
    seen = body;
    assert.equal(init.headers["x-h-runtime-secret"], env.H_RUNTIME_SECRET);
    return new Response(JSON.stringify({ ok: true, duplicate: false, reply: "رد H" }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  };

  try {
    const result = await bridgeChannelMessage(
      env,
      "+966 55 123 4567",
      "wamid.text.e2e",
      "احفظ هذه الفكرة",
      "text",
      "1789218000",
    );
    assert.equal(result.ok, true);
    assert.equal(result.reply, "رد H");
    assert.equal(seen.mode, "channel_message");
    assert.equal(seen.wa_id, "966551234567");
    assert.equal(seen.message_id, "wamid.text.e2e");
    assert.equal(seen.text, "احفظ هذه الفكرة");
    assert.equal(seen.source_type, "text");
    assert.match(seen.received_at, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    assert.deepEqual(
      Object.keys(seen).sort(),
      ["message_id", "mode", "received_at", "source_type", "text", "wa_id"].sort(),
      "transport payload must not grow authorization or provider authority fields",
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
