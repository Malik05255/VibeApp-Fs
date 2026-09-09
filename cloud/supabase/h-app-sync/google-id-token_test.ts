import { validateGoogleClaims } from "./google-id-token.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

const NOW = Date.UTC(2026, 8, 9, 7, 0, 0);
const NOW_SECONDS = Math.floor(NOW / 1000);

function validClaims(overrides: Record<string, unknown> = {}) {
  return {
    iss: "https://accounts.google.com",
    sub: "google-subject-123",
    aud: "trusted-web-client.apps.googleusercontent.com",
    exp: NOW_SECONDS + 3600,
    iat: NOW_SECONDS - 30,
    email: "owner@example.com",
    email_verified: true,
    ...overrides,
  };
}

Deno.test("Google claims expose stable subject and confirmed OAuth audience", () => {
  const identity = validateGoogleClaims(validClaims(), NOW);
  assert(identity.subject === "google-subject-123");
  assert(identity.audience === "trusted-web-client.apps.googleusercontent.com");
  assert(identity.email === "owner@example.com");
});

Deno.test("authorized party is bound when Google supplies multiple audiences", () => {
  const identity = validateGoogleClaims(validClaims({
    aud: ["trusted-web-client.apps.googleusercontent.com", "secondary-client.apps.googleusercontent.com"],
    azp: "trusted-web-client.apps.googleusercontent.com",
  }), NOW);
  assert(identity.audience === "trusted-web-client.apps.googleusercontent.com");
});

Deno.test("expired, future, unverified, and mismatched Google claims fail closed", () => {
  for (const claims of [
    validClaims({ exp: NOW_SECONDS - 1000 }),
    validClaims({ iat: NOW_SECONDS + 1000 }),
    validClaims({ email_verified: false }),
    validClaims({ iss: "https://example.com" }),
    validClaims({ aud: ["a", "b"], azp: "c" }),
  ]) {
    let rejected = false;
    try {
      validateGoogleClaims(claims, NOW);
    } catch (_) {
      rejected = true;
    }
    assert(rejected, "invalid Google claims must be rejected");
  }
});
