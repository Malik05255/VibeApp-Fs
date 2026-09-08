import { ownerFingerprint } from "./owner-identity.ts";

const PAIRING_LABEL = "h-owner-pairing-code-v1";
const PAIRING_TTL_MS = 10 * 60_000;

type DbClient = any;

export type OwnerPairingCommand = {
  code: string;
};

export function parseOwnerPairingCommand(text: string): OwnerPairingCommand | null {
  const value = String(text || "").trim();
  const match = value.match(/^(?:يا\s*h[\s،,:-]*)?(?:اربطني\s+كمالك|اربطني\s+مالك|تفعيل\s+المالك|pair\s+owner)\s+(\d{8})$/iu);
  return match ? { code: match[1] } : null;
}

export async function pairingCodeFingerprint(code: string, runtimeSecret: string): Promise<string> {
  if (!/^\d{8}$/.test(code)) throw new Error("Invalid H owner pairing code");
  const secret = String(runtimeSecret || "").trim();
  if (!secret) throw new Error("H runtime secret is not configured");
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signed = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${PAIRING_LABEL}:${code}`),
  );
  return [...new Uint8Array(signed)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export async function createOwnerPairingChallenge(
  db: DbClient,
  runtimeSecret: string,
  now = new Date(),
): Promise<{ code: string; expiresAt: string }> {
  const code = randomEightDigitCode();
  const codeFingerprint = await pairingCodeFingerprint(code, runtimeSecret);
  const expiresAt = new Date(now.getTime() + PAIRING_TTL_MS).toISOString();

  const { error: expireError } = await db.from("h_runtime_owner_pairing")
    .update({ consumed_at: now.toISOString() })
    .is("consumed_at", null)
    .gt("expires_at", now.toISOString());
  if (expireError) throw expireError;

  const { error } = await db.from("h_runtime_owner_pairing").insert({
    code_fingerprint: codeFingerprint,
    expires_at: expiresAt,
  });
  if (error) throw error;
  return { code, expiresAt };
}

export async function consumeOwnerPairingCommand(
  db: DbClient,
  waId: unknown,
  text: string,
): Promise<"not_pairing" | "enrolled" | "invalid_or_expired"> {
  const command = parseOwnerPairingCommand(text);
  if (!command) return "not_pairing";
  const runtimeSecret = await loadRuntimeSecret(db);
  return consumeOwnerPairingChallenge(db, runtimeSecret, waId, command.code);
}

export async function consumeOwnerPairingChallenge(
  db: DbClient,
  runtimeSecret: string,
  waId: unknown,
  code: string,
  now = new Date(),
): Promise<"enrolled" | "invalid_or_expired"> {
  const waFingerprint = await ownerFingerprint(waId, runtimeSecret);
  if (!waFingerprint) return "invalid_or_expired";
  const codeFingerprint = await pairingCodeFingerprint(code, runtimeSecret);
  const consumedAt = now.toISOString();
  const { data, error } = await db.from("h_runtime_owner_pairing")
    .update({ consumed_at: consumedAt })
    .eq("code_fingerprint", codeFingerprint)
    .is("consumed_at", null)
    .gt("expires_at", consumedAt)
    .select("code_fingerprint")
    .maybeSingle();
  if (error) throw error;
  if (!data?.code_fingerprint) return "invalid_or_expired";

  const { error: ownerError } = await db.from("h_runtime_owner_identities").upsert({
    wa_fingerprint: waFingerprint,
    label: "paired_via_whatsapp",
    active: true,
    updated_at: consumedAt,
  }, { onConflict: "wa_fingerprint" });
  if (ownerError) throw ownerError;
  return "enrolled";
}

async function loadRuntimeSecret(db: DbClient): Promise<string> {
  const { data, error } = await db.from("h_runtime_config")
    .select("secret_value")
    .eq("key", "poll_secret")
    .maybeSingle();
  if (error) throw error;
  const secret = String(data?.secret_value || "").trim();
  if (!secret) throw new Error("H runtime secret is not configured");
  return secret;
}

function randomEightDigitCode(): string {
  const bytes = new Uint32Array(1);
  crypto.getRandomValues(bytes);
  return String(10_000_000 + (bytes[0] % 90_000_000));
}
