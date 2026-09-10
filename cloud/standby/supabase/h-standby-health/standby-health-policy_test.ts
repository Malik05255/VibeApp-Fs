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
      execution_runtime_ready: true,
      promoted: false,
    },
    execution: {
      contract: "h_standby_execution_v1",
      mode: "passive_preflight",
      core_schema_ready: true,
      function_inventory_ready: true,
      runtime_secret_ready: true,
      app_identity_rekey_ready: true,
      whatsapp_identity_rekey_ready: true,
      ai_credentials_rekey_ready: true,
      free_ai_route_ready: true,
      promotion_controls_ready: true,
      scheduler_active: false,
      autonomous_outbound_active: false,
      validated_at: "2026-09-09T23:59:20.000Z",
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

Deno.test("validated current exact mirror plus complete execution contract is standby-ready", () => {
  const result = evaluateStandbyHealth(healthyInput(), NOW);
  assertEquals(result.standbyReady, true);
  assertEquals(result.executionContractReady, true);
  assertEquals(result.executionRuntimeReady, true);
  assertEquals(result.restoreVerified, true);
  assertEquals(result.replicationFresh, true);
  assert(result.replicationLagSeconds != null && result.replicationLagSeconds <= 120);
});

Deno.test("runtime execution flag alone cannot advertise standby ready", () => {
  const input = healthyInput();
  input.execution.function_inventory_ready = false;
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.runtimeExecutionFlag, true);
  assertEquals(result.executionContractReady, false);
  assertEquals(result.executionRuntimeReady, false);
  assertEquals(result.standbyReady, false);
});

Deno.test("complete execution attestation cannot bypass disabled runtime execution flag", () => {
  const input = healthyInput();
  input.runtime.execution_runtime_ready = false;
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.executionContractReady, true);
  assertEquals(result.runtimeExecutionFlag, false);
  assertEquals(result.executionRuntimeReady, false);
  assertEquals(result.standbyReady, false);
});

Deno.test("missing rekey readiness fails closed", () => {
  const appIdentityMissing = healthyInput();
  appIdentityMissing.execution.app_identity_rekey_ready = false;
  assertEquals(evaluateStandbyHealth(appIdentityMissing, NOW).standbyReady, false);

  const whatsappIdentityMissing = healthyInput();
  whatsappIdentityMissing.execution.whatsapp_identity_rekey_ready = false;
  assertEquals(evaluateStandbyHealth(whatsappIdentityMissing, NOW).standbyReady, false);

  const aiCredentialsMissing = healthyInput();
  aiCredentialsMissing.execution.ai_credentials_rekey_ready = false;
  assertEquals(evaluateStandbyHealth(aiCredentialsMissing, NOW).standbyReady, false);
});

Deno.test("active autonomous scheduler or outbound path cannot be passive standby-ready", () => {
  const schedulerActive = healthyInput();
  schedulerActive.execution.scheduler_active = true;
  assertEquals(evaluateStandbyHealth(schedulerActive, NOW).executionContractReady, false);

  const outboundActive = healthyInput();
  outboundActive.execution.autonomous_outbound_active = true;
  assertEquals(evaluateStandbyHealth(outboundActive, NOW).executionContractReady, false);
});

Deno.test("wrong execution contract or mode fails closed", () => {
  const wrongContract = healthyInput();
  wrongContract.execution.contract = "unknown";
  assertEquals(evaluateStandbyHealth(wrongContract, NOW).standbyReady, false);

  const wrongMode = healthyInput();
  wrongMode.execution.mode = "active_primary";
  assertEquals(evaluateStandbyHealth(wrongMode, NOW).standbyReady, false);
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
