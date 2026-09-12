const NONCE_PATTERN = /^[A-Za-z0-9_-]{16,128}$/;

export async function runStandbyExecutionProbe(db: any, rawNonce: unknown) {
  const nonce = String(rawNonce || "").trim();
  if (!NONCE_PATTERN.test(nonce)) throw new Error("standby_execution_probe_nonce_invalid");

  const stateResult = await db.from("h_runtime_state").select("key").limit(1);
  if (stateResult.error) throw new Error("standby_execution_probe_state_unreadable");

  const identityResult = await db.from("h_runtime_app_identities").select("active").limit(1);
  if (identityResult.error) throw new Error("standby_execution_probe_identity_unreadable");

  return {
    requested: true,
    nonceSha256: await sha256Hex(nonce),
    coreSchemaReadable: true,
    tablesChecked: ["h_runtime_state", "h_runtime_app_identities"],
    writesPerformed: false,
    userContentReturned: false,
  };
}

export async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
