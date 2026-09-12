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

Deno.test("fresh reinstall and a brand-new device resolve the same H without a local handle", async () => {
  const stableSecret = "stable-identity-secret";
  const cloudOwnerKey = "opaque-runtime-owner-key";

  // Device A, reinstall, and device B deliberately start with no persisted continuity
  // handle. After Google owner authentication the cloud resolves the same runtime owner.
  const deviceA = await ownerContinuityHandle(stableSecret, cloudOwnerKey);
  const reinstallWithEmptyLocalState = await ownerContinuityHandle(stableSecret, cloudOwnerKey);
  const brandNewDeviceWithEmptyLocalState = await ownerContinuityHandle(stableSecret, cloudOwnerKey);

  assertEquals(reinstallWithEmptyLocalState, deviceA);
  assertEquals(brandNewDeviceWithEmptyLocalState, deviceA);
  assert(validOwnerContinuityHandle(deviceA));
});

Deno.test("Android and WhatsApp views of one runtime owner resolve one H continuity identity", async () => {
  const stableSecret = "stable-identity-secret";
  const sharedRuntimeOwnerKey = "opaque-runtime-owner-key";

  const appHandle = await ownerContinuityHandle(stableSecret, sharedRuntimeOwnerKey);
  const whatsappHandle = await ownerContinuityHandle(stableSecret, sharedRuntimeOwnerKey);
  assertEquals(appHandle, whatsappHandle);

  // A different runtime owner must never alias into the same H across channels.
  const unrelatedWhatsappOwner = await ownerContinuityHandle(stableSecret, "other-runtime-owner-key");
  assertNotEquals(appHandle, unrelatedWhatsappOwner);
});
