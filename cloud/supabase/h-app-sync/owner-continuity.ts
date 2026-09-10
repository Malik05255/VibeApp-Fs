const OWNER_CONTINUITY_LABEL = "h-owner-continuity-v1";

/**
 * Stable, opaque proof that two authenticated app sessions resolve to the same H owner.
 *
 * The raw runtime user key may currently be a WhatsApp identifier, so it must never be
 * returned to Android. This HMAC is keyed by H's stable identity secret and therefore
 * cannot be used to recover the underlying routing identity by offline hashing.
 */
export async function ownerContinuityHandle(
  identitySecret: string,
  runtimeUserKey: string,
): Promise<string> {
  const secret = String(identitySecret || "").trim();
  const userKey = String(runtimeUserKey || "").trim();
  if (!secret || !userKey) throw new Error("owner_continuity_input_invalid");

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${OWNER_CONTINUITY_LABEL}:${userKey}`),
  );
  const digest = [...new Uint8Array(signature)]
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
  return `h1_${digest}`;
}

export function validOwnerContinuityHandle(value: unknown): boolean {
  return /^h1_[0-9a-f]{64}$/.test(String(value || ""));
}
