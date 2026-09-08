import { normalizeWaIdCandidate } from "./contact-manager.ts";

const OWNER_KEY_LABEL = "h-owner-wa-fingerprint-v1";

type DbClient = any;

export type HPeachDeliveryContext = {
  channel: "peach";
  senderRole: "owner" | "friend";
  canSendExternal: boolean;
};

export async function ownerFingerprint(waId: unknown): Promise<string | null> {
  const normalized = normalizeWaIdCandidate(waId);
  if (!normalized) return null;
  const root = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!root) throw new Error("SUPABASE_SERVICE_ROLE_KEY is not configured");
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

export async function isOwnerWaId(db: DbClient, waId: unknown): Promise<boolean> {
  const fingerprint = await ownerFingerprint(waId);
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
