import { assertEquals, assertRejects } from "jsr:@std/assert@1";
import {
  decryptAiCredentialForRuntime,
  encryptAiCredentialForRuntime,
  rekeyAiCredentialRows,
  type AiCredentialRow,
} from "./ai-credential-rekey.ts";

const PRIMARY = "primary-service-role-secret-abcdefghijklmnopqrstuvwxyz";
const STANDBY = "standby-service-role-secret-abcdefghijklmnopqrstuvwxyz";

function baseRow(id: string, provider: string, ciphertext: string, iv: string): AiCredentialRow {
  return {
    id,
    provider,
    secret_ciphertext: ciphertext,
    secret_iv: iv,
    secret_version: 1,
    selected_model: id.startsWith("openrouter") ? "openrouter/free" : null,
    model_verified_at: null,
    oauth_metadata: { free_only: id !== "openrouter_owner_paid" },
    connected_at: "2026-09-10T00:00:00.000Z",
    updated_at: "2026-09-10T00:00:00.000Z",
  };
}

for (const [id, provider] of [
  ["openrouter_default", "openrouter"],
  ["tavily_default", "tavily"],
  ["openrouter_owner_paid", "openrouter"],
] as const) {
  Deno.test(`rekeys ${id} for the standby service role without changing policy metadata`, async () => {
    const plain = `${id}-secret-value-1234567890`;
    const source = await encryptAiCredentialForRuntime(id, provider, plain, PRIMARY);
    const rows = await rekeyAiCredentialRows([baseRow(id, provider, source.ciphertext, source.iv)], PRIMARY, STANDBY);
    assertEquals(rows.length, 1);
    assertEquals(rows[0].id, id);
    assertEquals(rows[0].provider, provider);
    assertEquals(rows[0].oauth_metadata?.standby_rekeyed, true);
    assertEquals(await decryptAiCredentialForRuntime(rows[0], STANDBY), plain);
    await assertRejects(() => decryptAiCredentialForRuntime(rows[0], PRIMARY), Error, "ai_credential_decrypt_failed");
  });
}

Deno.test("rejects unknown credential schemes instead of copying opaque ciphertext", async () => {
  const source = await encryptAiCredentialForRuntime(
    "openrouter_default",
    "openrouter",
    "known-secret-value-1234567890",
    PRIMARY,
  );
  const unknown = baseRow("future_provider_default", "future_provider", source.ciphertext, source.iv);
  await assertRejects(
    () => rekeyAiCredentialRows([unknown], PRIMARY, STANDBY),
    Error,
    "unsupported_ai_credential",
  );
});
