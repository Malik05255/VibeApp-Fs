import {
  pairingCodeFingerprint,
  parseOwnerPairingCommand,
  redactOwnerPairingForStorage,
  storedOwnerPairingFingerprint,
} from "./owner-pairing.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("owner pairing command accepts only explicit eight-digit code", () => {
  const arabic = parseOwnerPairingCommand("اربطني كمالك 12345678");
  const english = parseOwnerPairingCommand("pair owner 87654321");
  assert(arabic?.code === "12345678");
  assert(english?.code === "87654321");
  assert(parseOwnerPairingCommand("اربطني كمالك 1234") === null);
  assert(parseOwnerPairingCommand("12345678") === null);
  assert(parseOwnerPairingCommand("احفظ رقم محمد 966551234567") === null);
});

Deno.test("pairing code fingerprint is deterministic and hides raw code", async () => {
  const secret = "stable-test-runtime-secret";
  const a = await pairingCodeFingerprint("12345678", secret);
  const b = await pairingCodeFingerprint("12345678", secret);
  const c = await pairingCodeFingerprint("87654321", secret);
  assert(a === b, "same code and secret must produce same fingerprint");
  assert(a !== c, "different codes must not share fingerprint");
  assert(/^[0-9a-f]{64}$/.test(a));
  assert(!a.includes("12345678"), "fingerprint must not expose raw pairing code");
});

Deno.test("pairing command is redacted before inbox persistence", async () => {
  const envelope = await redactOwnerPairingForStorage(
    "اربطني كمالك 12345678",
    "stable-test-runtime-secret",
  );
  assert(envelope?.body === "[owner_pairing_command]");
  const serialized = JSON.stringify(envelope);
  assert(!serialized.includes("12345678"), "raw pairing code must never enter persisted envelope");
  assert(envelope?.raw.redacted === true);
  assert(storedOwnerPairingFingerprint(envelope?.raw) === envelope?.raw.pairing_code_fingerprint);
});

Deno.test("owner pairing reader rejects ordinary and friend envelopes", async () => {
  assert(storedOwnerPairingFingerprint({ pairing_code_fingerprint: "12345678" }) === null);
  const friendFingerprint = "a".repeat(64);
  assert(storedOwnerPairingFingerprint({
    source: "h_friend_pairing",
    redacted: true,
    pairing_code_fingerprint: friendFingerprint,
  }) === null, "friend pairing must never be consumed as owner pairing");

  const envelope = await redactOwnerPairingForStorage(
    "ذكرني بكرة أشتري قهوة",
    "stable-test-runtime-secret",
  );
  assert(envelope === null);
});
