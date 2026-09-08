import {
  ownerFingerprint,
  resolvePeachDeliveryContext,
} from "./owner-identity.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("owner fingerprint is deterministic and does not contain raw wa id", async () => {
  const previous = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", "test-service-role-key");
  try {
    const a = await ownerFingerprint("+966 50 123 4567");
    const b = await ownerFingerprint("966501234567");
    assert(a === b, "normalized owner fingerprint must be deterministic");
    assert(Boolean(a && /^[0-9a-f]{64}$/.test(a)), "fingerprint must be sha256 hex");
    assert(!String(a).includes("966501234567"), "fingerprint must not expose raw wa id");
  } finally {
    if (previous == null) Deno.env.delete("SUPABASE_SERVICE_ROLE_KEY");
    else Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", previous);
  }
});

Deno.test("Peach owner context grants external messaging only on active fingerprint", async () => {
  const previous = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", "test-service-role-key");
  try {
    const expected = await ownerFingerprint("966501234567");
    const db = {
      from() {
        return {
          select() { return this; },
          eq(column: string, value: unknown) {
            if (column === "wa_fingerprint") this.matches = value === expected;
            if (column === "active") this.active = value === true;
            return this;
          },
          matches: false,
          active: false,
          async maybeSingle() {
            return { data: this.matches && this.active ? { wa_fingerprint: expected } : null, error: null };
          },
        };
      },
    };
    const owner = await resolvePeachDeliveryContext(db, "966501234567");
    const friend = await resolvePeachDeliveryContext(db, "966509999999");
    assert(owner.senderRole === "owner" && owner.canSendExternal === true);
    assert(friend.senderRole === "friend" && friend.canSendExternal === false);
  } finally {
    if (previous == null) Deno.env.delete("SUPABASE_SERVICE_ROLE_KEY");
    else Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", previous);
  }
});
