import { normalizeContactKey, normalizeWaIdCandidate, resolveRuntimeContact } from "./contact-manager.ts";
import { createFriendPairingChallenge } from "./friend-pairing.ts";
import { friendFingerprint } from "./owner-identity.ts";

type DbClient = any;

export type FriendAccessDelivery = {
  senderRole?: string;
  canSendExternal?: boolean;
};

export type FriendAccessCommand =
  | { action: "status" }
  | { action: "create_invite"; label: string | null }
  | { action: "enroll"; targetWaId: string; label: string | null }
  | { action: "remove"; targetWaId: string; label: string | null }
  | { action: "enroll_contact"; contactName: string }
  | { action: "remove_contact"; contactName: string };

export type StoredFriendAccessCommand = {
  action: "enroll" | "remove";
  targetFingerprint: string;
  label: string | null;
};

const REDACTED_BODY = "[friend_access_command]";
const ACCESS_PHRASE = "(?:باستخدام|لاستخدام|استخدام|من\\s+استخدام)\\s+h|كصديق|كمستخدم|مصرح|مسموح|إلى\\s+h|الى\\s+h|في\\s+h";
const ACCESS_MARKER = new RegExp(`(?:${ACCESS_PHRASE})`, "iu");
const PHONE_PATTERN = /\+?\d[\d\s().-]{6,}\d/u;

export function canManageFriendAccess(delivery: FriendAccessDelivery): boolean {
  return delivery.senderRole === "owner" && delivery.canSendExternal === true;
}

export function parseFriendAccessCommand(text: string): FriendAccessCommand | null {
  const value = String(text || "").trim();
  if (!value) return null;

  const inviteMatch = value.match(/^(?:اعطني|أعطني|انشئ|أنشئ|سو|سوي|جهز|جهّز)\s+(?:لي\s+)?(?:كود|رمز)\s+(?:دعوة\s+)?(?:صديق|للصديق)(?:\s+(?:باسم|ل)\s+(.+))?$/iu);
  if (inviteMatch) {
    const label = inviteMatch[1]?.trim().slice(0, 80) || null;
    return { action: "create_invite", label };
  }

  if (/^(?:من|مين)\s+(?:المسموح|المصرح)(?:\s+له|\s+لهم)?\s+(?:باستخدام\s+)?h\??$/iu.test(value) ||
      /^(?:اعرض|أعرض|عرض)\s+(?:الأصدقاء|الاصدقاء|المصرح\s+لهم|المسموح\s+لهم)$/iu.test(value)) {
    return { action: "status" };
  }

  const enroll = /^(?:اسمح|أضف|اضف|فعّل|فعل|صرّح|صرح)(?:\s|$)/iu.test(value);
  const remove = /^(?:امنع|احظر|احذف|أزل|ازل|شيل|أوقف|اوقف)(?:\s|$)/iu.test(value);
  if ((!enroll && !remove) || !ACCESS_MARKER.test(value)) return null;

  const phoneMatch = value.match(PHONE_PATTERN);
  if (phoneMatch) {
    const targetWaId = normalizeWaIdCandidate(phoneMatch[0]);
    if (!targetWaId) return null;
    const labelMatch = value.match(new RegExp(`(?:باسم|اسم)\\s+([^،,]+?)(?=\\s+(?:${ACCESS_PHRASE})|$)`, "iu"));
    const label = labelMatch?.[1]?.trim().slice(0, 80) || null;
    return enroll
      ? { action: "enroll", targetWaId, label }
      : { action: "remove", targetWaId, label };
  }

  let contactName = value
    .replace(/^(?:اسمح|أضف|اضف|فعّل|فعل|صرّح|صرح|امنع|احظر|احذف|أزل|ازل|شيل|أوقف|اوقف)\s*/iu, "")
    .replace(/^(?:ل|لـ)?(?:صديقي|صديق|جهة\s*الاتصال)?\s*/iu, "")
    .replace(new RegExp(`\\s+(?:${ACCESS_PHRASE}).*$`, "iu"), "")
    .trim();
  if (contactName.startsWith("ل") && contactName.length > 1) contactName = contactName.slice(1).trim();
  if (!contactName || contactName.length > 120 || !normalizeContactKey(contactName)) return null;
  return enroll
    ? { action: "enroll_contact", contactName }
    : { action: "remove_contact", contactName };
}

export async function redactFriendAccessForStorage(
  db: DbClient,
  text: string,
): Promise<{ body: string; raw: Record<string, unknown> } | null> {
  const command = parseFriendAccessCommand(text);
  if (!command || (command.action !== "enroll" && command.action !== "remove")) return null;
  const secret = await loadRuntimeSecret(db);
  const targetFingerprint = await friendFingerprint(command.targetWaId, secret);
  if (!targetFingerprint) return null;
  return {
    body: REDACTED_BODY,
    raw: {
      source: "h_friend_access",
      redacted: true,
      friend_access_action: command.action,
      target_friend_fingerprint: targetFingerprint,
      label: command.label,
    },
  };
}

export function storedFriendAccessCommand(raw: unknown): StoredFriendAccessCommand | null {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  const record = raw as Record<string, unknown>;
  if (record.source !== "h_friend_access" || record.redacted !== true) return null;
  const action = String(record.friend_access_action || "");
  const targetFingerprint = String(record.target_friend_fingerprint || "");
  if ((action !== "enroll" && action !== "remove") || !/^[0-9a-f]{64}$/.test(targetFingerprint)) return null;
  const label = typeof record.label === "string" ? record.label.trim().slice(0, 80) || null : null;
  return { action, targetFingerprint, label };
}

export async function executeStoredFriendAccess(
  db: DbClient,
  command: StoredFriendAccessCommand,
  delivery: FriendAccessDelivery,
): Promise<string> {
  if (!canManageFriendAccess(delivery)) return "إدارة المصرح لهم باستخدام H متاحة لصاحب H فقط.";
  return applyFingerprintCommand(db, command.action, command.targetFingerprint, command.label);
}

export async function maybeExecuteFriendAccessCommand(
  db: DbClient,
  userKey: string,
  text: string,
  delivery: FriendAccessDelivery,
): Promise<string | null> {
  const command = parseFriendAccessCommand(text);
  if (!command) return null;
  if (!canManageFriendAccess(delivery)) return "إدارة المصرح لهم باستخدام H متاحة لصاحب H فقط.";

  if (command.action === "create_invite") {
    const secret = await loadRuntimeSecret(db);
    const challenge = await createFriendPairingChallenge(db, secret, command.label);
    const labelText = command.label ? ` لـ${command.label}` : "";
    return `كود دعوة H${labelText}: ${challenge.code}\nصالح 10 دقائق ويستخدم مرة واحدة. أرسل له: اربطني كصديق ${challenge.code}`;
  }

  if (command.action === "status") {
    const { data, error, count } = await db.from("h_runtime_friend_identities")
      .select("label", { count: "exact" })
      .eq("active", true)
      .order("created_at", { ascending: true });
    if (error) throw error;
    const labels = (data ?? []).map((row: any) => String(row?.label || "").trim()).filter(Boolean);
    const total = count ?? labels.length;
    if (!total) return "لا يوجد أصدقاء مصرح لهم باستخدام H حاليًا.";
    if (!labels.length) return `يوجد ${total} صديق مصرح له باستخدام H. الأرقام نفسها غير مخزنة كنص خام.`;
    return `المصرح لهم باستخدام H: ${labels.join("، ")}${labels.length < total ? `، و${total - labels.length} بدون اسم محفوظ` : ""}.`;
  }

  let fingerprint: string | null = null;
  let label: string | null = null;
  let action: "enroll" | "remove";
  if (command.action === "enroll" || command.action === "remove") {
    const secret = await loadRuntimeSecret(db);
    fingerprint = await friendFingerprint(command.targetWaId, secret);
    label = command.label;
    action = command.action;
  } else {
    const contact = await resolveRuntimeContact(db, userKey, command.contactName);
    if (!contact) return `ما لقيت ${command.contactName} ضمن جهات اتصال H المحفوظة.`;
    const secret = await loadRuntimeSecret(db);
    fingerprint = await friendFingerprint(contact.target_wa_id, secret);
    label = contact.display_name;
    action = command.action === "enroll_contact" ? "enroll" : "remove";
  }
  if (!fingerprint) return "رقم واتساب غير صالح.";
  return applyFingerprintCommand(db, action, fingerprint, label);
}

async function applyFingerprintCommand(
  db: DbClient,
  action: "enroll" | "remove",
  targetFingerprint: string,
  label: string | null,
): Promise<string> {
  const now = new Date().toISOString();
  if (action === "enroll") {
    const { error } = await db.from("h_runtime_friend_identities").upsert({
      wa_fingerprint: targetFingerprint,
      label,
      active: true,
      updated_at: now,
    }, { onConflict: "wa_fingerprint" });
    if (error) throw error;
    return label ? `تم السماح لـ${label} باستخدام H.` : "تم السماح لهذا الرقم باستخدام H.";
  }
  const { error } = await db.from("h_runtime_friend_identities")
    .update({ active: false, updated_at: now })
    .eq("wa_fingerprint", targetFingerprint);
  if (error) throw error;
  return label ? `تم منع ${label} من استخدام H.` : "تم منع هذا الرقم من استخدام H.";
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
