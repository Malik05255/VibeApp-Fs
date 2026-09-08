import { normalizeWaIdCandidate } from "./contact-manager.ts";

const OWNER_KEY_LABEL = "h-owner-wa-fingerprint-v1";

type DbClient = any;

export type HPeachDeliveryContext = {
  channel: "peach";
  senderRole: "owner" | "friend";
  canSendExternal: boolean;
};

export async function ownerFingerprint(waId: unknown, runtimeSecret: string): Promise<string | null> {
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
    new TextEncoder().encode(`${OWNER_KEY_LABEL}:${normalized}`),
  );
  return [...new Uint8Array(signed)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
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

export async function isOwnerWaId(db: DbClient, waId: unknown): Promise<boolean> {
  const fingerprint = await ownerFingerprint(waId, await loadRuntimeSecret(db));
  if (!fingerprint) return false;
  const { data, error } = await db.from("h_runtime_owner_identities")
    .select("wa_fingerprint")
    .eq("wa_fingerprint", fingerprint)
    .eq("active", true)
    .maybeSingle();
  if (error) throw error;
  return Boolean(data?.wa_fingerprint);
}

export async function resolvePeachDeliveryContext(
  db: DbClient,
  waId: unknown,
): Promise<HPeachDeliveryContext> {
  const owner = await isOwnerWaId(db, waId);
  return {
    channel: "peach",
    senderRole: owner ? "owner" : "friend",
    canSendExternal: owner,
  };
}
