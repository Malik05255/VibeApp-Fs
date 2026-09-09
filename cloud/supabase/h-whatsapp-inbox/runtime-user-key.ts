import { normalizeWaIdCandidate } from "./contact-manager.ts";

const USER_KEY_ENCRYPTION_LABEL = "h-app-runtime-user-key-v1";

/**
 * Encrypts H's runtime user key (currently the normalized WhatsApp id) for server-only
 * handoff/storage. The raw identifier must never be persisted in app-link or pairing rows.
 */
export async function encryptRuntimeUserKey(userKey: unknown, secret: string): Promise<string> {
  const normalized = normalizeWaIdCandidate(userKey);
  if (!normalized) throw new Error("invalid_app_identity_user_key");
  const runtimeSecret = String(secret || "").trim();
  if (!runtimeSecret) throw new Error("runtime_secret_missing");

  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    await encryptionKey(runtimeSecret),
    new TextEncoder().encode(normalized),
  );
  return `${base64Url(iv)}.${base64Url(new Uint8Array(encrypted))}`;
}

export async function decryptRuntimeUserKey(ciphertext: unknown, secret: string): Promise<string> {
  const runtimeSecret = String(secret || "").trim();
  if (!runtimeSecret) throw new Error("runtime_secret_missing");
  const [ivText, dataText] = String(ciphertext || "").split(".");
  if (!ivText || !dataText) throw new Error("invalid_app_identity_ciphertext");

  let decrypted: ArrayBuffer;
  try {
    decrypted = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: base64UrlDecode(ivText) },
      await encryptionKey(runtimeSecret),
      base64UrlDecode(dataText),
    );
  } catch {
    throw new Error("invalid_app_identity_ciphertext");
  }

  const value = new TextDecoder().decode(decrypted).trim();
  const normalized = normalizeWaIdCandidate(value);
  if (!normalized || normalized !== value) throw new Error("invalid_app_identity_user_key");
  return normalized;
}

async function encryptionKey(secret: string): Promise<CryptoKey> {
  const material = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(`${USER_KEY_ENCRYPTION_LABEL}:${secret}`),
  );
  return crypto.subtle.importKey("raw", material, "AES-GCM", false, ["encrypt", "decrypt"]);
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlDecode(value: string): Uint8Array<ArrayBuffer> {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error("invalid_app_identity_ciphertext");
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}
