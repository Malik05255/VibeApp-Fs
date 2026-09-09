const GOOGLE_ISSUERS = new Set(["accounts.google.com", "https://accounts.google.com"]);
const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const CLOCK_SKEW_SECONDS = 300;
const KEY_CACHE_MS = 60 * 60_000;

type GoogleClaims = Record<string, unknown>;
type GoogleJwk = JsonWebKey & { kid?: string };

export type VerifiedGoogleIdentity = {
  subject: string;
  audience: string;
  email: string | null;
};

let cachedKeys: { expiresAt: number; keys: GoogleJwk[] } | null = null;

export async function verifyGoogleIdToken(token: string, now = Date.now()): Promise<VerifiedGoogleIdentity> {
  const parts = String(token || "").split(".");
  if (parts.length !== 3) throw new Error("invalid_google_token");

  const header = decodeJson(parts[0]);
  if (header.alg !== "RS256" || typeof header.kid !== "string" || !header.kid) {
    throw new Error("unsupported_google_token");
  }

  const key = (await googleKeys(now)).find((candidate) => candidate.kid === header.kid);
  if (!key) {
    cachedKeys = null;
    const refreshed = (await googleKeys(now, true)).find((candidate) => candidate.kid === header.kid);
    if (!refreshed) throw new Error("unknown_google_signing_key");
    await verifySignature(refreshed, parts);
  } else {
    await verifySignature(key, parts);
  }

  const claims = decodeJson(parts[1]);
  return validateGoogleClaims(claims, now);
}

export function validateGoogleClaims(claims: GoogleClaims, now = Date.now()): VerifiedGoogleIdentity {
  const issuer = typeof claims.iss === "string" ? claims.iss : "";
  if (!GOOGLE_ISSUERS.has(issuer)) throw new Error("invalid_google_issuer");

  const subject = typeof claims.sub === "string" ? claims.sub.trim() : "";
  if (!subject || subject.length > 255) throw new Error("invalid_google_subject");

  const exp = numericClaim(claims.exp);
  const iat = numericClaim(claims.iat);
  const nowSeconds = Math.floor(now / 1000);
  if (!exp || exp < nowSeconds - CLOCK_SKEW_SECONDS) throw new Error("expired_google_token");
  if (iat && iat > nowSeconds + CLOCK_SKEW_SECONDS) throw new Error("future_google_token");

  const audiences = typeof claims.aud === "string"
    ? [claims.aud]
    : Array.isArray(claims.aud)
      ? claims.aud.filter((value): value is string => typeof value === "string" && value.length > 0)
      : [];
  const authorizedParty = typeof claims.azp === "string" ? claims.azp.trim() : "";
  const audience = authorizedParty || (audiences.length === 1 ? audiences[0] : "");
  if (!audience || audience.length > 255) throw new Error("invalid_google_audience");
  if (authorizedParty && !audiences.includes(authorizedParty)) throw new Error("invalid_google_authorized_party");

  const emailVerified = claims.email_verified;
  if (emailVerified !== true && emailVerified !== "true") throw new Error("unverified_google_email");
  const email = typeof claims.email === "string" && claims.email.trim() ? claims.email.trim() : null;

  return { subject, audience, email };
}

async function googleKeys(now: number, force = false): Promise<GoogleJwk[]> {
  if (!force && cachedKeys && cachedKeys.expiresAt > now) return cachedKeys.keys;
  const response = await fetch(GOOGLE_JWKS_URL, { headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error("google_keys_unavailable");
  const body = await response.json().catch(() => ({}));
  const keys = Array.isArray(body?.keys) ? body.keys.filter((value: unknown) => value && typeof value === "object") as GoogleJwk[] : [];
  if (!keys.length) throw new Error("google_keys_unavailable");
  cachedKeys = { expiresAt: now + KEY_CACHE_MS, keys };
  return keys;
}

async function verifySignature(jwk: GoogleJwk, parts: string[]) {
  const publicKey = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );
  const valid = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    publicKey,
    base64UrlBytes(parts[2]),
    new TextEncoder().encode(`${parts[0]}.${parts[1]}`),
  );
  if (!valid) throw new Error("invalid_google_signature");
}

function decodeJson(value: string): GoogleClaims {
  try {
    return JSON.parse(new TextDecoder().decode(base64UrlBytes(value)));
  } catch (_) {
    throw new Error("invalid_google_token");
  }
}

function base64UrlBytes(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function numericClaim(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}
