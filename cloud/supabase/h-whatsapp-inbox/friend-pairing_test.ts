import {
  consumeFriendPairingFingerprint,
  createFriendPairingChallenge,
  friendPairingCodeFingerprint,
  parseFriendPairingCommand,
  redactFriendPairingForStorage,
  storedFriendPairingFingerprint,
} from "./friend-pairing.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("friend pairing accepts only explicit eight-digit code", () => {
  assert(parseFriendPairingCommand("اربطني كصديق 12345678")?.code === "12345678");
  assert(parseFriendPairingCommand("يا H اربطني كصديق 87654321")?.code === "87654321");
  assert(parseFriendPairingCommand("اربطني كصديق 1234") === null);
  assert(parseFriendPairingCommand("12345678") === null);
});

Deno.test("friend pairing code fingerprint hides raw code", async () => {
  const value = await friendPairingCodeFingerprint("12345678", "stable-runtime-secret");
  assert(/^[0-9a-f]{64}$/.test(value));
  assert(!value.includes("12345678"));
});

Deno.test("friend pairing command is redacted before storage", async () => {
  const envelope = await redactFriendPairingForStorage("اربطني كصديق 12345678", "stable-runtime-secret");
  assert(envelope?.body === "[friend_pairing_command]");
  assert(!JSON.stringify(envelope).includes("12345678"));
  assert(Boolean(storedFriendPairingFingerprint(envelope?.raw)));
});

Deno.test("friend challenge stores fingerprint and optional label only", async () => {
  const inserted: any[] = [];
  const db = {
    from(table: string) {
      return {
        async insert(payload: unknown) {
          assert(table === "h_runtime_friend_pairing");
          inserted.push(payload);
          return { error: null };
        },
      };
    },
  };
  const result = await createFriendPairingChallenge(db, "stable-runtime-secret", "محمد", new Date("2026-09-09T00:00:00Z"));
  assert(/^\d{8}$/.test(result.code));
  assert(result.expiresAt === "2026-09-09T00:10:00.000Z");
  assert(inserted.length === 1);
  const serialized = JSON.stringify(inserted[0]);
  assert(!serialized.includes(result.code), "raw invite code must not be persisted");
  assert(serialized.includes("محمد"));
});

Deno.test("consuming valid friend fingerprint enrolls HMAC identity once", async () => {
  const runtimeSecret = "stable-runtime-secret";
  // The migration initially copies poll_secret into identity_secret, preserving the
  // existing fingerprint key material while allowing poll_secret to rotate later.
  const identitySecret = runtimeSecret;
  const codeFingerprint = await friendPairingCodeFingerprint("12345678", runtimeSecret);
  const calls: Array<{ table: string; payload: any }> = [];
  let pairingConsumed = false;
  const db = {
    from(table: string) {
      const query: any = {
        table,
        payload: null,
        update(payload: unknown) { this.payload = payload; calls.push({ table, payload }); return this; },
        eq() { return this; },
        is() { return this; },
        gt() { return this; },
        select() { return this; },
        async maybeSingle() {
          if (table === "h_runtime_config") {
            return { data: { secret_value: identitySecret }, error: null };
          }
          if (table !== "h_runtime_friend_pairing" || pairingConsumed) return { data: null, error: null };
          pairingConsumed = true;
          return { data: { code_fingerprint: codeFingerprint, label: "محمد" }, error: null };
        },
        async upsert(payload: unknown) {
          calls.push({ table, payload });
          return { error: null };
        },
      };
      return query;
    },
  };
  const first = await consumeFriendPairingFingerprint(db, runtimeSecret, "966501234567", codeFingerprint, new Date("2026-09-09T00:05:00Z"));
  assert(first === "enrolled");
  const friendCall = calls.find((call) => call.table === "h_runtime_friend_identities");
  assert(Boolean(friendCall));
  const serialized = JSON.stringify(friendCall?.payload);
  assert(!serialized.includes("966501234567"), "raw friend number must not be persisted");
  assert(serialized.includes("محمد"));

  const second = await consumeFriendPairingFingerprint(db, runtimeSecret, "966501234567", codeFingerprint, new Date("2026-09-09T00:06:00Z"));
  assert(second === "invalid_or_expired", "one-time challenge must not be reusable");
});
