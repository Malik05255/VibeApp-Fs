import { parseChannelMessagePayload } from "../h-whatsapp-inbox/channel-message.ts";
import { normalizeWaIdCandidate } from "../h-whatsapp-inbox/contact-manager.ts";
import {
  decryptRuntimeUserKey,
  encryptRuntimeUserKey,
} from "../h-whatsapp-inbox/runtime-user-key.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("Meta WhatsApp owner key round-trips through app pairing unchanged", async () => {
  const parsed = parseChannelMessagePayload({
    mode: "channel_message",
    wa_id: "+966 50 123 4567",
    message_id: "wamid.cross-channel-owner",
    text: "احفظ هذه الفكرة",
    source_type: "text",
    sender_role: "owner",
    can_send_external: true,
  });
  assert(parsed != null);
  assert(parsed.waId === "966501234567");

  const identitySecret = "test-h-stable-identity-secret";
  const encrypted = await encryptRuntimeUserKey(parsed.waId, identitySecret);
  const appUserKey = await decryptRuntimeUserKey(encrypted, identitySecret);

  assert(appUserKey === parsed.waId);
  assert(appUserKey === normalizeWaIdCandidate("+966 50 123 4567"));
});

Deno.test("all accepted WhatsApp phone formatting variants collapse to one H owner key", () => {
  const variants = [
    "+966501234567",
    "966 50 123 4567",
    "966-50-123-4567",
    "(966) 50 123 4567",
  ];
  const normalized = variants.map(normalizeWaIdCandidate);
  assert(normalized.every((value) => value === "966501234567"));
});

Deno.test("different WhatsApp owners cannot collapse into one H owner key", async () => {
  const identitySecret = "test-h-stable-identity-secret";
  const first = normalizeWaIdCandidate("+966501234567");
  const second = normalizeWaIdCandidate("+966509876543");
  assert(first != null && second != null && first !== second);

  const firstRoundTrip = await decryptRuntimeUserKey(
    await encryptRuntimeUserKey(first, identitySecret),
    identitySecret,
  );
  const secondRoundTrip = await decryptRuntimeUserKey(
    await encryptRuntimeUserKey(second, identitySecret),
    identitySecret,
  );
  assert(firstRoundTrip !== secondRoundTrip);
});
