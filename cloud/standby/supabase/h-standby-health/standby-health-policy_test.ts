import { assertEquals, assert } from "jsr:@std/assert@1";
import { evaluateStandbyHealth, effectiveReplicationLagSeconds } from "./standby-health-policy.ts";

const NOW = Date.parse("2026-09-10T00:00:00.000Z");
const DIGEST = "a".repeat(64);

function healthyInput() {
  return {
    runtime: {
      runtime_role: "standby",
      h_identity: "H",
      dedicated_h_standby: true,
      allow_replica_writes: true,
      promoted: false,
    },
    replication: {
      mode: "continuous",
      protocol: "exact_mirror_v1",
      exact_mirror: true,
      last_digest: DIGEST,
      source_generated_at: "2026-09-09T23:59:30.000Z",
      lag_seconds: 30,
    },
    replicationObservedAt: "2026-09-09T23:59:40.000Z",
  };
}

Deno.test("validated current exact mirror is standby-ready", () => {
  const result = evaluateStandbyHealth(healthyInput(), NOW);
  assertEquals(result.standbyReady, true);
  assertEquals(result.restoreVerified, true);
  assertEquals(result.replicationFresh, true);
  assert(result.replicationLagSeconds != null && result.replicationLagSeconds <= 120);
});

Deno.test("old source snapshot cannot stay ready because stored lag was once low", () => {
  const input = healthyInput();
  input.replication.source_generated_at = "2026-09-09T23:50:00.000Z";
  input.replication.lag_seconds = 5;
  input.replicationObservedAt = "2026-09-09T23:59:59.000Z";
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.replicationLagSeconds, 600);
  assertEquals(result.replicationFresh, false);
  assertEquals(result.standbyReady, false);
});

Deno.test("stale replication observation fails closed", () => {
  const input = healthyInput();
  input.replicationObservedAt = "2026-09-09T23:55:00.000Z";
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.replicationFresh, false);
  assertEquals(result.standbyReady, false);
});

Deno.test("promoted runtime is never advertised as standby-ready", () => {
  const input = healthyInput();
  input.runtime.promoted = true;
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.standbyReady, false);
});

Deno.test("wrong H identity or disabled replica writes fails closed", () => {
  const wrongIdentity = healthyInput();
  wrongIdentity.runtime.h_identity = "Other";
  assertEquals(evaluateStandbyHealth(wrongIdentity, NOW).standbyReady, false);

  const writesDisabled = healthyInput();
  writesDisabled.runtime.allow_replica_writes = false;
  assertEquals(evaluateStandbyHealth(writesDisabled, NOW).standbyReady, false);
});

Deno.test("effective lag never gets smaller than stored lag", () => {
  assertEquals(effectiveReplicationLagSeconds("2026-09-09T23:59:50.000Z", 25, NOW), 25);
  assertEquals(effectiveReplicationLagSeconds("2026-09-09T23:59:20.000Z", 5, NOW), 40);
});
