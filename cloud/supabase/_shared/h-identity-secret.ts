import { assertHRequestExecutionAllowed } from "./h-runtime-execution-guard.ts";

export const H_IDENTITY_SECRET_CONFIG_KEY = "identity_secret";

type DbClient = any;
type IdentitySecretOptions = {
  /** Narrow control-plane reads may inspect a passive standby without enabling execution. */
  allowPassiveStandby?: boolean;
};

/**
 * Stable H identity key. Unlike poll_secret, this key may be shared with a validated
 * standby because it protects durable identity fingerprints/ciphertext rather than
 * authenticating runtime polling calls.
 *
 * Ordinary identity-backed execution crosses the standby execution fence here. Primary
 * runtimes remain unchanged because they do not carry a standby_runtime role record.
 */
export async function loadIdentitySecret(
  db: DbClient,
  options: IdentitySecretOptions = {},
): Promise<string> {
  if (options.allowPassiveStandby !== true) {
    await assertHRequestExecutionAllowed(db);
  }

  const { data, error } = await db.from("h_runtime_config")
    .select("secret_value")
    .eq("key", H_IDENTITY_SECRET_CONFIG_KEY)
    .maybeSingle();
  if (error) throw error;
  const value = String(data?.secret_value || "").trim();
  if (!value) throw new Error("identity_secret_missing");
  return value;
}
