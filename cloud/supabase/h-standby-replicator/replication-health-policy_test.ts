import { assertEquals } from "jsr:@std/assert@1";
import { replicationHealthEligible, REPLICATION_PROTOCOL } from "./replication-health-policy.ts";

function healthyReplication() {
  return {
    ok: true,
    service: "h-standby-health",
    standbyReady: false,
    runtimeRole: "standby",
    hIdentity: "H",
    promoted: false,
    restoreVerified: true,
    replicationMode: "continuous",
    replicationProtocol: REPLICATION_PROTOCOL,
    replicationFresh: true,
    replicationLagSeconds: 20,
    appIdentityRekeyReady: true,
    whatsappIdentityRekeyReady: true,
    aiCredentialsRekeyReady: false,
    promotionControlsReady: false,
  };
}

Deno.test("replication may be healthy while failover remains closed", () => {
  const health = healthyReplication();
  assertEquals(health.standbyReady, false);
  assertEquals(replicationHealthEligible(health), true);
});

Deno.test("identity continuity is mandatory for replication readiness", () => {
  const appMissing = healthyReplication();
  appMissing.appIdentityRekeyReady = false;
  assertEquals(replicationHealthEligible(appMissing), false);

  const whatsappMissing = healthyReplication();
  whatsappMissing.whatsappIdentityRekeyReady = false;
  assertEquals(replicationHealthEligible(whatsappMissing), false);
});

Deno.test("legacy protocol and stale replication fail closed", () => {
  const legacy = healthyReplication();
  legacy.replicationProtocol = "exact_mirror_v1";
  assertEquals(replicationHealthEligible(legacy), false);

  const stale = healthyReplication();
  stale.replicationFresh = false;
  stale.replicationLagSeconds = 300;
  assertEquals(replicationHealthEligible(stale), false);
});

Deno.test("promoted or wrong runtime identity cannot be replication-ready", () => {
  const promoted = healthyReplication();
  promoted.promoted = true;
  assertEquals(replicationHealthEligible(promoted), false);

  const wrong = healthyReplication();
  wrong.hIdentity = "Other";
  assertEquals(replicationHealthEligible(wrong), false);
});
