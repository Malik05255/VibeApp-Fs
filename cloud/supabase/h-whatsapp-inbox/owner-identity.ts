import { normalizeWaIdCandidate } from "./contact-manager.ts";

const OWNER_KEY_LABEL = "h-owner-wa-fingerprint-v1";
const FRIEND_KEY_LABEL = "h-friend-wa-fingerprint-v1";

type DbClient = any;

export type HPeachDeliveryContext = {
  channel: "peach";
  allowed: boolean;
  senderRole: "owner" | "friend";
  canSendExternal: boolean;
};

async function identityFingerprint(
  waId: unknown,
  runtimeSecret: string,
  label: string,
): Promise<string | null> {
  const normalized = normalizeWaIdCandidate(waId);
  const root = String(runtimeSecret || "").trim();
  if (!normalized) return null;
  if (!root) throw new Error("H runtime secret is not configured");
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(root),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signed = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${label}:${normalized}`),
  );
  return [...new Uint8Array(signed)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function ownerFingerprint(waId: unknown, runtimeSecret: string): Promise<string | null> {
  return identityFingerprint(waId, runtimeSecret, OWNER_KEY_LABEL);
}

export function friendFingerprint(waId: unknown, runtimeSecret: string): Promise<string | null> {
  return identityFingerprint(waId, runtimeSecret, FRIEND_KEY_LABEL);
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

async function hasActiveFingerprint(db: DbClient, table: string, fingerprint: string | null): Promise<boolean> {
  if (!fingerprint) return false;
  const { data, error } = await db.from(table)
    .select("wa_fingerprint")
    .eq("wa_fingerprint", fingerprint)
    .eq("active", true)
    .maybeSingle();
  if (error) throw error;
  return Boolean(data?.wa_fingerprint);
}

export async function isOwnerWaId(db: DbClient, waId: unknown): Promise<boolean> {
  const secret = await loadRuntimeSecret(db);
  return hasActiveFingerprint(db, "h_runtime_owner_identities", await ownerFingerprint(waId, secret));
}

export async function isFriendWaId(db: DbClient, waId: unknown): Promise<boolean> {
  const secret = await loadRuntimeSecret(db);
  return hasActiveFingerprint(db, "h_runtime_friend_identities", await friendFingerprint(waId, secret));
}

export async function resolvePeachDeliveryContext(
  db: DbClient,
  waId: unknown,
): Promise<HPeachDeliveryContext> {
  const secret = await loadRuntimeSecret(db);
  const owner = await hasActiveFingerprint(
    db,
    "h_runtime_owner_identities",
    await ownerFingerprint(waId, secret),
  );
  if (owner) {
    return {
      channel: "peach",
      allowed: true,
      senderRole: "owner",
      canSendExternal: true,
    };
  }

  const friend = await hasActiveFingerprint(
    db,
    "h_runtime_friend_identities",
    await friendFingerprint(waId, secret),
  );
  return {
    channel: "peach",
    allowed: friend,
    senderRole: "friend",
    canSendExternal: false,
  };
}
