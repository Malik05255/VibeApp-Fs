import {
  friendFingerprint,
  ownerFingerprint,
  resolvePeachDeliveryContext,
} from "./owner-identity.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("owner and friend fingerprints are deterministic private and role-separated", async () => {
  const secret = "stable-test-runtime-secret";
  const ownerA = await ownerFingerprint("+966 50 123 4567", secret);
  const ownerB = await ownerFingerprint("966501234567", secret);
  const friend = await friendFingerprint("966501234567", secret);
  assert(ownerA === ownerB, "normalized owner fingerprint must be deterministic");
  assert(Boolean(ownerA && /^[0-9a-f]{64}$/.test(ownerA)), "owner fingerprint must be sha256 hex");
  assert(Boolean(friend && /^[0-9a-f]{64}$/.test(friend)), "friend fingerprint must be sha256 hex");
  assert(ownerA !== friend, "owner and friend roles must use separate HMAC domains");
  assert(!String(ownerA).includes("966501234567"), "owner fingerprint must not expose raw wa id");
  assert(!String(friend).includes("966501234567"), "friend fingerprint must not expose raw wa id");
});

Deno.test("Peach access is owner friend or default-deny blocked", async () => {
  const secret = "stable-test-runtime-secret";
  const ownerWa = "966501234567";
  const friendWa = "966509876543";
  const ownerExpected = await ownerFingerprint(ownerWa, secret);
  const friendExpected = await friendFingerprint(friendWa, secret);

  const db = {
    from(table: string) {
      const query: any = {
        table,
        fingerprint: null,
        active: null,
        select() { return this; },
        eq(column: string, value: unknown) {
          if (column === "wa_fingerprint") this.fingerprint = value;
          if (column === "active") this.active = value;
          return this;
        },
        async maybeSingle() {
          if (this.table === "h_runtime_config") {
            return { data: { secret_value: secret }, error: null };
          }
          const ownerMatch = this.table === "h_runtime_owner_identities" &&
            this.fingerprint === ownerExpected && this.active === true;
          const friendMatch = this.table === "h_runtime_friend_identities" &&
            this.fingerprint === friendExpected && this.active === true;
          const match = ownerMatch || friendMatch;
          return { data: match ? { wa_fingerprint: this.fingerprint } : null, error: null };
        },
      };
      return query;
    },
  };

  const owner = await resolvePeachDeliveryContext(db, ownerWa);
  const friend = await resolvePeachDeliveryContext(db, friendWa);
  const stranger = await resolvePeachDeliveryContext(db, "966500000099");

  assert(owner.allowed === true && owner.senderRole === "owner" && owner.canSendExternal === true);
  assert(friend.allowed === true && friend.senderRole === "friend" && friend.canSendExternal === false);
  assert(stranger.allowed === false && stranger.senderRole === "friend" && stranger.canSendExternal === false);
});
