import { friendFingerprint } from "./owner-identity.ts";

const FRIEND_PAIRING_LABEL = "h-friend-pairing-code-v1";
const FRIEND_PAIRING_TTL_MS = 10 * 60_000;
const REDACTED_FRIEND_PAIRING_BODY = "[friend_pairing_command]";

type DbClient = any;

export type FriendPairingCommand = { code: string };

export type RedactedFriendPairingEnvelope = {
  body: string;
  raw: {
    source: "peach_friend_pairing";
    redacted: true;
    pairing_code_fingerprint: string;
  };
};

export function parseFriendPairingCommand(text: string): FriendPairingCommand | null {
  const value = String(text || "").trim();
  const match = value.match(/^(?:يا\s*h[\s،,:-]*)?(?:اربطني\s+كصديق|اربطني\s+صديق|تفعيل\s+الصديق|pair\s+friend)\s+(\d{8})$/iu);
  return match ? { code: match[1] } : null;
}

export async function friendPairingCodeFingerprint(code: string, runtimeSecret: string): Promise<string> {
  if (!/^\d{8}$/.test(code)) throw new Error("Invalid H friend pairing code");
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
    new TextEncoder().encode(`${FRIEND_PAIRING_LABEL}:${code}`),
  );
  return [...new Uint8Array(signed)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export async function redactFriendPairingForStorage(
  text: string | null,
  runtimeSecret: string,
): Promise<RedactedFriendPairingEnvelope | null> {
  const command = parseFriendPairingCommand(String(text || ""));
  if (!command) return null;
  return {
    body: REDACTED_FRIEND_PAIRING_BODY,
    raw: {
      source: "peach_friend_pairing",
      redacted: true,
      pairing_code_fingerprint: await friendPairingCodeFingerprint(command.code, runtimeSecret),
    },
  };
}

export function storedFriendPairingFingerprint(raw: unknown): string | null {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  const record = raw as Record<string, unknown>;
  if (record.source !== "peach_friend_pairing" || record.redacted !== true) return null;
  const value = String(record.pairing_code_fingerprint || "").trim();
  return /^[0-9a-f]{64}$/.test(value) ? value : null;
}

export async function createFriendPairingChallenge(
  db: DbClient,
  runtimeSecret: string,
  label: string | null = null,
  now = new Date(),
): Promise<{ code: string; expiresAt: string }> {
  const code = randomEightDigitCode();
  const codeFingerprint = await friendPairingCodeFingerprint(code, runtimeSecret);
  const expiresAt = new Date(now.getTime() + FRIEND_PAIRING_TTL_MS).toISOString();
  const cleanLabel = typeof label === "string" ? label.trim().slice(0, 80) || null : null;
  const { error } = await db.from("h_runtime_friend_pairing").insert({
    code_fingerprint: codeFingerprint,
    label: cleanLabel,
    expires_at: expiresAt,
  });
  if (error) throw error;
  return { code, expiresAt };
}

export async function consumeFriendPairingFingerprint(
  db: DbClient,
  runtimeSecret: string,
  waId: unknown,
  codeFingerprint: string,
  now = new Date(),
): Promise<"enrolled" | "invalid_or_expired"> {
  if (!/^[0-9a-f]{64}$/.test(String(codeFingerprint || ""))) return "invalid_or_expired";
  const waFingerprint = await friendFingerprint(waId, runtimeSecret);
  if (!waFingerprint) return "invalid_or_expired";

  const consumedAt = now.toISOString();
  const { data, error } = await db.from("h_runtime_friend_pairing")
    .update({ consumed_at: consumedAt })
    .eq("code_fingerprint", codeFingerprint)
    .is("consumed_at", null)
    .gt("expires_at", consumedAt)
    .select("code_fingerprint,label")
    .maybeSingle();
  if (error) throw error;
  if (!data?.code_fingerprint) return "invalid_or_expired";

  const label = typeof data.label === "string" ? data.label.trim().slice(0, 80) || null : null;
  const { error: friendError } = await db.from("h_runtime_friend_identities").upsert({
    wa_fingerprint: waFingerprint,
    label: label ?? "paired_via_whatsapp",
    active: true,
    updated_at: consumedAt,
  }, { onConflict: "wa_fingerprint" });
  if (friendError) throw friendError;
  return "enrolled";
}

function randomEightDigitCode(): string {
  const bytes = new Uint32Array(1);
  crypto.getRandomValues(bytes);
  return String(10_000_000 + (bytes[0] % 90_000_000));
}
