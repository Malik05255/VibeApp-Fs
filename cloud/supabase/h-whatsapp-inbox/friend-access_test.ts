import {
  canManageFriendAccess,
  executeStoredFriendAccess,
  parseFriendAccessCommand,
  redactFriendAccessForStorage,
  storedFriendAccessCommand,
} from "./friend-access.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("Arabic friend access commands parse without ASCII word boundaries", () => {
  const invite = parseFriendAccessCommand("اعطني كود صديق باسم محمد");
  assert(invite?.action === "create_invite");
  if (invite?.action === "create_invite") assert(invite.label === "محمد");

  const enroll = parseFriendAccessCommand("اسمح للرقم +966 50 123 4567 باستخدام H");
  assert(enroll?.action === "enroll");
  if (enroll?.action === "enroll") assert(enroll.targetWaId === "966501234567");

  const labeled = parseFriendAccessCommand("اسمح للرقم 966501234567 باسم محمد باستخدام H");
  assert(labeled?.action === "enroll");
  if (labeled?.action === "enroll") assert(labeled.label === "محمد", "access phrase must not leak into label");

  const remove = parseFriendAccessCommand("امنع 966501234567 من استخدام H");
  assert(remove?.action === "remove");

  const byContact = parseFriendAccessCommand("أضف محمد كصديق");
  assert(byContact?.action === "enroll_contact");
  if (byContact?.action === "enroll_contact") assert(byContact.contactName === "محمد");

  const byContactPhrase = parseFriendAccessCommand("أضف محمد باستخدام H");
  assert(byContactPhrase?.action === "enroll_contact");
  if (byContactPhrase?.action === "enroll_contact") assert(byContactPhrase.contactName === "محمد", "access phrase must not leak into contact name");

  assert(parseFriendAccessCommand("احفظ رقم محمد 966501234567") === null, "contact save must not become access management");
});

Deno.test("friend access is owner-only", () => {
  assert(canManageFriendAccess({ senderRole: "owner", canSendExternal: true }) === true);
  assert(canManageFriendAccess({ senderRole: "friend", canSendExternal: false }) === false);
  assert(canManageFriendAccess({ senderRole: "owner", canSendExternal: false }) === false);
});

Deno.test("direct-number access command is redacted to HMAC before storage", async () => {
  const secret = "stable-test-runtime-secret";
  const db = {
    from(table: string) {
      return {
        select() { return this; },
        eq() { return this; },
        async maybeSingle() {
          if (table === "h_runtime_config") return { data: { secret_value: secret }, error: null };
          return { data: null, error: null };
        },
      };
    },
  };
  const envelope = await redactFriendAccessForStorage(db, "اسمح للرقم 966501234567 باستخدام H");
  assert(Boolean(envelope));
  assert(envelope?.body === "[friend_access_command]");
  const serialized = JSON.stringify(envelope);
  assert(!serialized.includes("966501234567"), "raw friend number must not be stored");
  const stored = storedFriendAccessCommand(envelope?.raw);
  assert(stored?.action === "enroll");
  assert(Boolean(stored && /^[0-9a-f]{64}$/.test(stored.targetFingerprint)));
});

Deno.test("stored friend access execution grants only owner capability", async () => {
  const calls: Array<{ table: string; operation: string; payload?: unknown }> = [];
  const db = {
    from(table: string) {
      const query: any = {
        upsert(payload: unknown) { calls.push({ table, operation: "upsert", payload }); return Promise.resolve({ error: null }); },
        update(payload: unknown) { calls.push({ table, operation: "update", payload }); return this; },
        eq() { return Promise.resolve({ error: null }); },
      };
      return query;
    },
  };
  const command = { action: "enroll" as const, targetFingerprint: "a".repeat(64), label: "محمد" };
  const denied = await executeStoredFriendAccess(db, command, { senderRole: "friend", canSendExternal: false });
  assert(denied.includes("صاحب H"));
  assert(calls.length === 0, "friend must not mutate allowlist");

  const allowed = await executeStoredFriendAccess(db, command, { senderRole: "owner", canSendExternal: true });
  assert(allowed.includes("محمد"));
  const firstCall = calls.at(0);
  assert(firstCall?.table === "h_runtime_friend_identities" && firstCall.operation === "upsert");
});
