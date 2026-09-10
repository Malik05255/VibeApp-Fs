export const H_IDENTITY_SECRET_CONFIG_KEY = "identity_secret";

type DbClient = any;

/**
 * Stable H identity key. Unlike poll_secret, this key is allowed to be shared with a
 * validated standby because it protects durable identity fingerprints/ciphertext rather
 * than authenticating runtime polling calls.
 *
 * The primary migration initializes it from the current poll_secret once so all existing
 * HMAC fingerprints and encrypted runtime user keys remain valid during the transition.
 * After every identity consumer has moved to this key, poll_secret can be rotated without
 * invalidating Google/WhatsApp identity state.
 */
export async function loadIdentitySecret(db: DbClient): Promise<string> {
  const { data, error } = await db.from("h_runtime_config")
    .select("secret_value")
    .eq("key", H_IDENTITY_SECRET_CONFIG_KEY)
    .maybeSingle();
  if (error) throw error;
  const value = String(data?.secret_value || "").trim();
  if (!value) throw new Error("identity_secret_missing");
  return value;
}
