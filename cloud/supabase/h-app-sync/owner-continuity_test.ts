import { assert, assertEquals, assertNotEquals, assertRejects } from "jsr:@std/assert@1";
import {
  ownerContinuityHandle,
  validOwnerContinuityHandle,
} from "./owner-continuity.ts";

Deno.test("owner continuity handle is stable for the same H identity", async () => {
  const first = await ownerContinuityHandle("stable-identity-secret", "966500000001");
  const second = await ownerContinuityHandle("stable-identity-secret", "966500000001");

  assertEquals(first, second);
  assert(validOwnerContinuityHandle(first));
  assertEquals(first.length, 67);
  assert(!first.includes("966500000001"));
});

Deno.test("owner continuity handle separates different H owners", async () => {
  const first = await ownerContinuityHandle("stable-identity-secret", "966500000001");
  const second = await ownerContinuityHandle("stable-identity-secret", "966500000002");

  assertNotEquals(first, second);
});

Deno.test("owner continuity handle is bound to H stable identity secret", async () => {
  const first = await ownerContinuityHandle("identity-secret-a", "966500000001");
  const second = await ownerContinuityHandle("identity-secret-b", "966500000001");

  assertNotEquals(first, second);
});

Deno.test("owner continuity handle rejects missing secret or owner key", async () => {
  await assertRejects(() => ownerContinuityHandle("", "966500000001"));
  await assertRejects(() => ownerContinuityHandle("stable-identity-secret", ""));
});
