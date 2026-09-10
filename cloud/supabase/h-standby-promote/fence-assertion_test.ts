import { assertEquals, assertRejects } from "jsr:@std/assert@1";
import { verifyFenceAssertion } from "./fence-assertion.ts";

const NOW = Date.parse("2026-09-10T10:30:00.000Z");
const PRIMARY = "abavsspydbpkudhswmzp";
const STANDBY = "bbbbbbbbbbbbbbbbbbbb";
const ISSUER = "https://fence.example.test/h";
const REQUEST_ID = "fence_request_1234567890";

async function fixture(overrides: Record<string, unknown> = {}) {
  const pair = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" },
    true,
    ["sign", "verify"],
  );
  const publicJwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  const now = Math.floor(NOW / 1000);
  const claims = {
    iss: ISSUER,
    aud: `h-standby:${STANDBY}`,
    jti: REQUEST_ID,
    primary_project_ref: PRIMARY,
    standby_project_ref: STANDBY,
    primary_write_fenced: true,
    fence_epoch: 41,
    iat: now - 2,
    exp: now + 58,
    fenced_at: now - 1,
    ...overrides,
  };
  const assertion = await sign(pair.privateKey, claims);
  return {
    assertion,
    config: {
      publicJwk: JSON.stringify(publicJwk),
      issuer: ISSUER,
      primaryProjectRef: PRIMARY,
      standbyProjectRef: STANDBY,
    },
  };
}

Deno.test("valid ES256 fence assertion binds primary, standby and monotonic epoch input", async () => {
  const value = await fixture();
  const result = await verifyFenceAssertion(value.assertion, value.config, NOW);
  assertEquals(result.requestId, REQUEST_ID);
  assertEquals(result.fenceEpoch, 41);
  assertEquals(result.primaryProjectRef, PRIMARY);
  assertEquals(result.standbyProjectRef, STANDBY);
  assertEquals(result.assertionSha256.length, 64);
});

Deno.test("unfenced primary assertion is rejected", async () => {
  const value = await fixture({ primary_write_fenced: false });
  await assertRejects(() => verifyFenceAssertion(value.assertion, value.config, NOW), Error, "primary_not_fenced");
});

Deno.test("wrong standby target is rejected", async () => {
  const value = await fixture({ standby_project_ref: "cccccccccccccccccccc" });
  await assertRejects(() => verifyFenceAssertion(value.assertion, value.config, NOW), Error, "fence_standby_mismatch");
});

Deno.test("expired assertion is rejected", async () => {
  const now = Math.floor(NOW / 1000);
  const value = await fixture({ iat: now - 180, exp: now - 60, fenced_at: now - 170 });
  await assertRejects(() => verifyFenceAssertion(value.assertion, value.config, NOW), Error, "fence_assertion_expired");
});

Deno.test("tampered assertion is rejected cryptographically", async () => {
  const value = await fixture();
  const [header, payload, signature] = value.assertion.split(".");
  const decoded = JSON.parse(new TextDecoder().decode(base64UrlDecode(payload)));
  decoded.fence_epoch = 999;
  const tampered = `${header}.${base64Url(new TextEncoder().encode(JSON.stringify(decoded)))}.${signature}`;
  await assertRejects(() => verifyFenceAssertion(tampered, value.config, NOW), Error, "fence_signature_invalid");
});

Deno.test("public configuration rejects private EC key material", async () => {
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const privateJwk = await crypto.subtle.exportKey("jwk", pair.privateKey);
  const value = await fixture();
  await assertRejects(() => verifyFenceAssertion(value.assertion, {
    ...value.config,
    publicJwk: JSON.stringify(privateJwk),
  }, NOW), Error, "fencing_private_key_forbidden");
});

async function sign(privateKey: CryptoKey, claims: Record<string, unknown>) {
  const header = base64Url(new TextEncoder().encode(JSON.stringify({ alg: "ES256", typ: "h-fence+jwt" })));
  const payload = base64Url(new TextEncoder().encode(JSON.stringify(claims)));
  const signingInput = `${header}.${payload}`;
  const signature = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    privateKey,
    new TextEncoder().encode(signingInput),
  );
  return `${signingInput}.${base64Url(new Uint8Array(signature))}`;
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlDecode(value: string): Uint8Array<ArrayBuffer> {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}
