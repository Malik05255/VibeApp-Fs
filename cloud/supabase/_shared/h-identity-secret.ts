import { assertHRequestExecutionAllowed } from "./h-runtime-execution-guard.ts";

export const H_IDENTITY_SECRET_CONFIG_KEY = "identity_secret";

type DbClient = any;

type IdentitySecretOptions = {
  /** Control-plane code may inspect/promote a passive standby without enabling user execution. */
  allowPassiveStandby?: boolean;
};

/**
 * Stable H identity key. Unlike poll_secret, this key is allowed to be shared with a
 * validated standby because it protects durable identity fingerprints/ciphertext rather
 * than authenticating runtime polling calls.
 *
 * Every ordinary identity consumer also crosses the standby execution boundary here.
 * This makes a passive standby fail closed across app/WhatsApp/provider endpoints without
 * relying on every individual Edge Function to remember its own guard. Only narrowly
 * scoped standby control-plane code may opt out while it validates or promotes the replica.
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
