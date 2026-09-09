import {
  peachUnsupportedMediaEnvelope,
  peachUnsupportedMediaFallback,
} from "./peach-media-fallback.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("audio without body gets deterministic fallback", () => {
  const fallback = peachUnsupportedMediaFallback("audio", null);
  assert(fallback?.kind === "audio");
  assert(fallback.reply.includes("التسجيل الصوتي"));
  assert(fallback.reply.includes("أرسل محتواه كنص"));
});

Deno.test("known media aliases normalize safely", () => {
  assert(peachUnsupportedMediaFallback("voice-note", null)?.kind === "audio");
  assert(peachUnsupportedMediaFallback("photo", null)?.kind === "image");
  assert(peachUnsupportedMediaFallback("pdf", null)?.kind === "document");
  assert(peachUnsupportedMediaFallback("video", null)?.kind === "video");
});

Deno.test("text body and unknown message types do not trigger media fallback", () => {
  assert(peachUnsupportedMediaFallback("audio", "transcribed text") === null);
  assert(peachUnsupportedMediaFallback("text", null) === null);
  assert(peachUnsupportedMediaFallback(null, null) === null);
});

Deno.test("unsupported media envelope contains no media bytes or URL", () => {
  const envelope = peachUnsupportedMediaEnvelope("audio", 20385268);
  assert(envelope.body === "[unsupported_media:audio]");
  const serialized = JSON.stringify(envelope);
  assert(serialized.includes("peach_unsupported_media"));
  assert(serialized.includes("20385268"));
  assert(!serialized.includes("http"));
  assert(envelope.raw.media_reference_available === false);
});
