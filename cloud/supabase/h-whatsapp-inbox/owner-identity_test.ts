import {
  ownerFingerprint,
  resolvePeachDeliveryContext,
} from "./owner-identity.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("owner fingerprint is deterministic and does not contain raw wa id", async () => {
  const secret = "stable-test-runtime-secret";
  const a = await ownerFingerprint("+966 50 123 4567", secret);
  const b = await ownerFingerprint("966501234567", secret);
  assert(a === b, "normalized owner fingerprint must be deterministic");
  assert(Boolean(a && /^[0-9a-f]{64}$/.test(a)), "fingerprint must be sha256 hex");
  assert(!String(a).includes("966501234567"), "fingerprint must not expose raw wa id");
});

Deno.test("Peach owner context grants external messaging only on active fingerprint", async () => {
  const secret = "stable-test-runtime-secret";
  const expected = await ownerFingerprint("966501234567", secret);
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
          const matches = this.fingerprint === expected && this.active === true;
          return { data: matches ? { wa_fingerprint: expected } : null, error: null };
        },
      };
      return query;
    },
  };
  const owner = await resolvePeachDeliveryContext(db, "966501234567");
  const friend = await resolvePeachDeliveryContext(db, "966509999999");
  assert(owner.senderRole === "owner" && owner.canSendExternal === true);
  assert(friend.senderRole === "friend" && friend.canSendExternal === false);
});
