import { decryptRuntimeUserKey, encryptRuntimeUserKey } from "./runtime-user-key.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("runtime user key round-trips without exposing raw WhatsApp id", async () => {
  const waId = "966551234567";
  const secret = "stable-test-runtime-secret";
  const ciphertext = await encryptRuntimeUserKey(waId, secret);
  assert(ciphertext.length > 20);
  assert(!ciphertext.includes(waId), "ciphertext must not expose the raw runtime user key");
  assert(await decryptRuntimeUserKey(ciphertext, secret) === waId);
});

Deno.test("runtime user key ciphertext is randomized and secret-bound", async () => {
  const waId = "966551234567";
  const first = await encryptRuntimeUserKey(waId, "secret-a");
  const second = await encryptRuntimeUserKey(waId, "secret-a");
  assert(first !== second, "AES-GCM IV must randomize ciphertext for the same user key");

  let rejected = false;
  try {
    await decryptRuntimeUserKey(first, "secret-b");
  } catch {
    rejected = true;
  }
  assert(rejected, "ciphertext must not decrypt under a different runtime secret");
});

Deno.test("runtime user key helper rejects malformed identifiers and ciphertext", async () => {
  let invalidUser = false;
  try {
    await encryptRuntimeUserKey("not-a-whatsapp-id", "secret");
  } catch {
    invalidUser = true;
  }
  assert(invalidUser);

  let invalidCipher = false;
  try {
    await decryptRuntimeUserKey("not.a.valid.cipher", "secret");
  } catch {
    invalidCipher = true;
  }
  assert(invalidCipher);
});
