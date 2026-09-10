const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{16,128}$/;
const PROJECT_REF_PATTERN = /^[a-z0-9-]{8,64}$/;
const MAX_ASSERTION_BYTES = 8192;
const MAX_ASSERTION_LIFETIME_SECONDS = 120;
const MAX_CLOCK_SKEW_SECONDS = 10;

type FenceConfig = {
  publicJwk: string;
  issuer: string;
  primaryProjectRef: string;
  standbyProjectRef: string;
};

export type VerifiedFenceAssertion = {
  requestId: string;
  fenceEpoch: number;
  primaryProjectRef: string;
  standbyProjectRef: string;
  fencedAt: string;
  assertionSha256: string;
};

export async function verifyFenceAssertion(
  compactJws: string,
  config: FenceConfig,
  nowMs = Date.now(),
): Promise<VerifiedFenceAssertion> {
  const assertion = String(compactJws || "").trim();
  if (!assertion || new TextEncoder().encode(assertion).byteLength > MAX_ASSERTION_BYTES) {
    throw new Error("fence_assertion_invalid");
  }

  const parts = assertion.split(".");
  if (parts.length !== 3 || parts.some((part) => !/^[A-Za-z0-9_-]+$/.test(part))) {
    throw new Error("fence_assertion_format_invalid");
  }

  const header = decodeJson(parts[0], "fence_header_invalid");
  if (header?.alg !== "ES256" || header?.typ !== "h-fence+jwt") {
    throw new Error("fence_algorithm_invalid");
  }

  const normalized = normalizeConfig(config);
  const jwk = parseVerificationJwk(normalized.publicJwk);
  const key = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["verify"],
  );
  const verified = await crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    base64UrlDecode(parts[2]),
    new TextEncoder().encode(`${parts[0]}.${parts[1]}`),
  );
  if (!verified) throw new Error("fence_signature_invalid");

  const claims = decodeJson(parts[1], "fence_claims_invalid");
  const requestId = String(claims?.jti || "").trim();
  const primaryProjectRef = String(claims?.primary_project_ref || "").trim();
  const standbyProjectRef = String(claims?.standby_project_ref || "").trim();
  const issuer = normalizeHttpsIssuer(String(claims?.iss || ""));
  const audience = String(claims?.aud || "").trim();
  const fenceEpoch = Number(claims?.fence_epoch);
  const issuedAt = Number(claims?.iat);
  const expiresAt = Number(claims?.exp);
  const fencedAtSeconds = Number(claims?.fenced_at);

  if (!REQUEST_ID_PATTERN.test(requestId)) throw new Error("fence_request_id_invalid");
  if (!issuer || issuer !== normalized.issuer) throw new Error("fence_issuer_mismatch");
  if (audience !== `h-standby:${normalized.standbyProjectRef}`) throw new Error("fence_audience_mismatch");
  if (primaryProjectRef !== normalized.primaryProjectRef) throw new Error("fence_primary_mismatch");
  if (standbyProjectRef !== normalized.standbyProjectRef) throw new Error("fence_standby_mismatch");
  if (claims?.primary_write_fenced !== true) throw new Error("primary_not_fenced");
  if (!Number.isSafeInteger(fenceEpoch) || fenceEpoch <= 0) throw new Error("fence_epoch_invalid");
  if (![issuedAt, expiresAt, fencedAtSeconds].every(Number.isSafeInteger)) throw new Error("fence_time_invalid");
  if (expiresAt <= issuedAt || expiresAt - issuedAt > MAX_ASSERTION_LIFETIME_SECONDS) {
    throw new Error("fence_lifetime_invalid");
  }

  const nowSeconds = Math.floor(nowMs / 1000);
  if (issuedAt > nowSeconds + MAX_CLOCK_SKEW_SECONDS) throw new Error("fence_issued_in_future");
  if (expiresAt < nowSeconds - MAX_CLOCK_SKEW_SECONDS) throw new Error("fence_assertion_expired");
  if (fencedAtSeconds < issuedAt - MAX_CLOCK_SKEW_SECONDS || fencedAtSeconds > nowSeconds + MAX_CLOCK_SKEW_SECONDS) {
    throw new Error("fenced_at_invalid");
  }

  return {
    requestId,
    fenceEpoch,
    primaryProjectRef,
    standbyProjectRef,
    fencedAt: new Date(fencedAtSeconds * 1000).toISOString(),
    assertionSha256: await sha256Hex(assertion),
  };
}

export function parseVerificationJwk(raw: string): JsonWebKey {
  let jwk: any;
  try { jwk = JSON.parse(String(raw || "")); } catch { throw new Error("fencing_public_jwk_invalid"); }
  if (!jwk || typeof jwk !== "object" || Array.isArray(jwk)) throw new Error("fencing_public_jwk_invalid");
  if (jwk.kty !== "EC" || jwk.crv !== "P-256" || !isBase64UrlCoordinate(jwk.x) || !isBase64UrlCoordinate(jwk.y)) {
    throw new Error("fencing_public_jwk_invalid");
  }
  if (jwk.d != null) throw new Error("fencing_private_key_forbidden");
  if (jwk.alg != null && jwk.alg !== "ES256") throw new Error("fencing_public_jwk_invalid");
  if (jwk.use != null && jwk.use !== "sig") throw new Error("fencing_public_jwk_invalid");
  return { kty: "EC", crv: "P-256", x: jwk.x, y: jwk.y, alg: "ES256", use: "sig", ext: true };
}

function normalizeConfig(config: FenceConfig): FenceConfig {
  const issuer = normalizeHttpsIssuer(String(config?.issuer || ""));
  const primaryProjectRef = String(config?.primaryProjectRef || "").trim();
  const standbyProjectRef = String(config?.standbyProjectRef || "").trim();
  const publicJwk = String(config?.publicJwk || "").trim();
  if (!issuer) throw new Error("fencing_issuer_invalid");
  if (!PROJECT_REF_PATTERN.test(primaryProjectRef) || !PROJECT_REF_PATTERN.test(standbyProjectRef) || primaryProjectRef === standbyProjectRef) {
    throw new Error("fencing_project_refs_invalid");
  }
  parseVerificationJwk(publicJwk);
  return { publicJwk, issuer, primaryProjectRef, standbyProjectRef };
}

function normalizeHttpsIssuer(raw: string): string | null {
  try {
    const url = new URL(raw.trim());
    if (url.protocol !== "https:" || url.username || url.password || url.hash || url.search) return null;
    const normalized = url.toString().replace(/\/$/, "");
    return normalized.length >= 8 && normalized.length <= 200 ? normalized : null;
  } catch { return null; }
}

function isBase64UrlCoordinate(value: unknown): boolean {
  return typeof value === "string" && /^[A-Za-z0-9_-]{40,64}$/.test(value);
}

function decodeJson(value: string, code: string): any {
  try { return JSON.parse(new TextDecoder().decode(base64UrlDecode(value))); } catch { throw new Error(code); }
}

function base64UrlDecode(value: string): Uint8Array<ArrayBuffer> {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error("invalid_base64url");
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
