import {
  buildMediaConversationText,
  decodeTextDocument,
  isSupportedMedia,
  maxAudioVideoDurationMs,
  maxMediaBytes,
  parseMediaMessagePayload,
} from "./media-bridge.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function b64(text: string): string {
  return btoa(text);
}

Deno.test("media bridge accepts supported image and sanitizes metadata", () => {
  const parsed = parseMediaMessagePayload({
    mode: "media_message",
    wa_id: "+966 50 123 4567",
    message_id: "wamid.image.1",
    kind: "image",
    mime_type: "image/jpeg; charset=binary",
    file_name: "../camera/photo.jpg",
    caption: "  وش موجود في الصورة؟  ",
    base64: b64("fake-jpeg-bytes"),
    received_at: "2026-09-08T20:00:00+03:00",
  });
  assert(parsed, "supported image should parse");
  assert(parsed.waId === "966501234567", "WA ID should be normalized");
  assert(parsed.mimeType === "image/jpeg", "MIME parameters should be stripped");
  assert(parsed.fileName === ".._camera_photo.jpg", "path separators should be removed");
  assert(parsed.caption === "وش موجود في الصورة؟", "caption should be normalized");
  assert(parsed.durationMs === null, "image must not acquire duration");
});

Deno.test("media bridge accepts PDF and small text documents but rejects unsafe types", () => {
  assert(isSupportedMedia("document", "application/pdf", 1024), "PDF should be supported");
  assert(isSupportedMedia("document", "text/plain", 1024), "plain text should be supported");
  assert(!isSupportedMedia("document", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 1024), "Office binary should fail closed until a parser exists");
  assert(!isSupportedMedia("image", "image/svg+xml", 1024), "SVG should not be sent to vision models");
  assert(!isSupportedMedia("document", "application/pdf", maxMediaBytes() + 1), "oversized media must be rejected");
});

Deno.test("audio and video require known duration at or below three minutes", () => {
  const audio = parseMediaMessagePayload({
    mode: "media_message",
    wa_id: "966501234567",
    message_id: "wamid.audio.1",
    kind: "audio",
    mime_type: "audio/mpeg",
    duration_ms: maxAudioVideoDurationMs(),
    base64: b64("fake-mp3"),
  });
  assert(audio?.kind === "audio", "three minute audio should parse");

  const video = parseMediaMessagePayload({
    mode: "media_message",
    wa_id: "966501234567",
    message_id: "wamid.video.1",
    kind: "video",
    mime_type: "video/mp4",
    duration_ms: 30_000,
    base64: b64("fake-mp4"),
  });
  assert(video?.kind === "video", "short video should parse");

  assert(parseMediaMessagePayload({
    mode: "media_message",
    wa_id: "966501234567",
    message_id: "missing-duration",
    kind: "audio",
    mime_type: "audio/mpeg",
    base64: b64("fake-mp3"),
  }) === null, "raw audio without verified duration must fail closed");

  assert(parseMediaMessagePayload({
    mode: "media_message",
    wa_id: "966501234567",
    message_id: "too-long-video",
    kind: "video",
    mime_type: "video/mp4",
    duration_ms: maxAudioVideoDurationMs() + 1,
    base64: b64("fake-mp4"),
  }) === null, "video over three minutes must not enter raw cloud processing");
});

Deno.test("text documents are decoded without persisting raw media into chat", () => {
  const parsed = parseMediaMessagePayload({
    mode: "media_message",
    wa_id: "966501234567",
    message_id: "wamid.text.1",
    kind: "document",
    mime_type: "text/plain",
    file_name: "notes.txt",
    caption: "لخصه",
    base64: b64("Meeting at 10:00. Bring the report."),
  });
  assert(parsed, "text document should parse");
  assert(decodeTextDocument(parsed) === "Meeting at 10:00. Bring the report.", "text should decode");
  const stored = buildMediaConversationText(parsed, "الملف يذكر اجتماعًا الساعة 10 وإحضار التقرير.");
  assert(stored.includes("تعليق المستخدم: لخصه"), "caption should be retained as context");
  assert(stored.includes("اجتماعًا الساعة 10"), "analysis should be retained");
  assert(stored.includes("الأصل الخام لم يُحفظ"), "retention boundary should be explicit");
  assert(!stored.includes(parsed.base64), "raw base64 must not be stored in chat text");
});

Deno.test("invalid base64 and unsupported MIME fail closed", () => {
  assert(parseMediaMessagePayload({
    mode: "media_message",
    wa_id: "966501234567",
    message_id: "bad",
    kind: "image",
    mime_type: "image/jpeg",
    base64: "%%%not-base64%%%",
  }) === null, "invalid base64 must be rejected");

  assert(parseMediaMessagePayload({
    mode: "media_message",
    wa_id: "966501234567",
    message_id: "zip",
    kind: "document",
    mime_type: "application/zip",
    base64: b64("zip"),
  }) === null, "unsupported file type must be rejected");
});
