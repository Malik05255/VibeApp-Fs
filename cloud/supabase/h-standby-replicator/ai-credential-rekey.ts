export type AiCredentialRow = {
  id: string;
  provider: string;
  secret_ciphertext: string;
  secret_iv: string;
  secret_version: number;
  selected_model: string | null;
  model_verified_at: string | null;
  oauth_metadata: Record<string, unknown> | null;
  connected_at: string;
  updated_at: string;
};

const SUPPORTED_CREDENTIALS = new Map<string, string>([
  ["openrouter_default", "openrouter"],
  ["openrouter_owner_paid", "openrouter"],
  ["tavily_default", "tavily"],
]);

export async function rekeyAiCredentialRows(
  rows: AiCredentialRow[],
  primaryServiceRole: string,
  standbyServiceRole: string,
): Promise<AiCredentialRow[]> {
  if (!primaryServiceRole.trim() || !standbyServiceRole.trim()) {
    throw new Error("ai_rekey_root_missing");
  }
  const output: AiCredentialRow[] = [];
  for (const row of rows) {
    validateCredentialRow(row);
    const plain = await decryptAiCredentialForRuntime(row, primaryServiceRole);
    const encrypted = await encryptAiCredentialForRuntime(row.id, row.provider, plain, standbyServiceRole);
    output.push({
      ...row,
      secret_ciphertext: encrypted.ciphertext,
      secret_iv: encrypted.iv,
      oauth_metadata: {
        ...(row.oauth_metadata && typeof row.oauth_metadata === "object" && !Array.isArray(row.oauth_metadata)
          ? row.oauth_metadata
          : {}),
        standby_rekeyed: true,
        standby_rekey_scheme: "service_role_derived_v1",
      },
    });
  }
  return output;
}

export async function encryptAiCredentialForRuntime(
  id: string,
  provider: string,
  plaintext: string,
  serviceRole: string,
): Promise<{ ciphertext: string; iv: string }> {
  const value = String(plaintext || "").trim();
  if (value.length < 8 || value.length > 4096) throw new Error("ai_credential_plaintext_invalid");
  const key = await deriveKey(id, provider, serviceRole, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv: toArrayBuffer(iv) },
    key,
    toArrayBuffer(new TextEncoder().encode(value)),
  );
  return { ciphertext: base64Url(new Uint8Array(encrypted)), iv: base64Url(iv) };
}

export async function decryptAiCredentialForRuntime(
  row: Pick<AiCredentialRow, "id" | "provider" | "secret_ciphertext" | "secret_iv">,
  serviceRole: string,
): Promise<string> {
  const key = await deriveKey(row.id, row.provider, serviceRole, ["decrypt"]);
  let decrypted: ArrayBuffer;
  try {
    decrypted = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: toArrayBuffer(base64UrlDecode(row.secret_iv)) },
      key,
      toArrayBuffer(base64UrlDecode(row.secret_ciphertext)),
    );
  } catch {
    throw new Error("ai_credential_decrypt_failed");
  }
  const value = new TextDecoder().decode(decrypted).trim();
  if (value.length < 8 || value.length > 4096) throw new Error("ai_credential_plaintext_invalid");
  return value;
}

function validateCredentialRow(row: AiCredentialRow): void {
  const expectedProvider = SUPPORTED_CREDENTIALS.get(String(row?.id || ""));
  if (!expectedProvider || expectedProvider !== String(row?.provider || "")) {
    throw new Error("unsupported_ai_credential");
  }
  if (Number(row.secret_version) !== 1) throw new Error("unsupported_ai_credential_version");
  if (!/^[A-Za-z0-9_-]{16,8192}$/.test(String(row.secret_ciphertext || ""))) {
    throw new Error("ai_credential_ciphertext_invalid");
  }
  if (!/^[A-Za-z0-9_-]{16,128}$/.test(String(row.secret_iv || ""))) {
    throw new Error("ai_credential_iv_invalid");
  }
}

async function deriveKey(
  id: string,
  provider: string,
  serviceRole: string,
  usages: KeyUsage[],
): Promise<CryptoKey> {
  const root = String(serviceRole || "").trim();
  if (!root) throw new Error("ai_rekey_root_missing");
  const label = encryptionLabel(id, provider, root);
  const digest = await crypto.subtle.digest("SHA-256", toArrayBuffer(new TextEncoder().encode(label)));
  return crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, usages);
}

function encryptionLabel(id: string, provider: string, root: string): string {
  if (id === "openrouter_default" && provider === "openrouter") return `h-openrouter-aes-v1:${root}`;
  if (id === "tavily_default" && provider === "tavily") return `h-tavily-aes-v1:${root}`;
  if (id === "openrouter_owner_paid" && provider === "openrouter") {
    return `h-owner-paid-ai-aes-v1:${provider}:${root}`;
  }
  throw new Error("unsupported_ai_credential");
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlDecode(value: string): Uint8Array {
  if (!/^[A-Za-z0-9_-]+$/.test(String(value || ""))) throw new Error("ai_credential_encoding_invalid");
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}
