import { assertEquals, assert } from "jsr:@std/assert@1";
import { evaluateStandbyHealth, effectiveReplicationLagSeconds } from "./standby-health-policy.ts";

const NOW = Date.parse("2026-09-10T00:00:00.000Z");
const DIGEST = "a".repeat(64);
const REQUEST_ID = "promotion_request_123456789";
const PRIMARY_REF = "abavsspydbpkudhswmzp";
const STANDBY_REF = "bbbbbbbbbbbbbbbbbbbb";
const PUBLIC_JWK = JSON.stringify({
  kty: "EC",
  crv: "P-256",
  x: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  y: "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
  alg: "ES256",
  use: "sig",
});

function healthyInput(): any {
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
      paid_ai_budget_continuity_ready: true,
      ai_continuity_validated_at: "2026-09-09T23:59:40.000Z",
      promotion_controls_ready: true,
      scheduler_active: false,
      autonomous_outbound_active: false,
      validated_at: "2026-09-09T23:59:20.000Z",
    },
    replication: {
      mode: "continuous",
      protocol: "exact_mirror_v2",
      exact_mirror: true,
      last_digest: DIGEST,
      source_generated_at: "2026-09-09T23:59:30.000Z",
      last_replicated_at: "2026-09-09T23:59:40.000Z",
      lag_seconds: 30,
    },
    promotion: {},
    fencing: {},
    fencingConfig: {
      fencing_public_jwk: PUBLIC_JWK,
      fencing_issuer: "https://fence.example.test/h",
      fencing_primary_project_ref: PRIMARY_REF,
      fencing_standby_project_ref: STANDBY_REF,
    },
    replicationObservedAt: "2026-09-09T23:59:40.000Z",
  };
}

function promotedInput(): any {
  const input = healthyInput();
  input.runtime.promoted = true;
  input.runtime.allow_replica_writes = false;
  input.runtime.promotion_mode = "request_only";
  input.runtime.promotion_request_id = REQUEST_ID;
  input.runtime.promoted_at = "2026-09-09T23:59:50.000Z";
  input.execution.mode = "request_active";
  input.promotion = {
    protocol: "h_standby_promotion_v1",
    status: "active",
    mode: "request_only",
    request_id: REQUEST_ID,
    promoted_at: "2026-09-09T23:59:50.000Z",
    source_digest: DIGEST,
    source_generated_at: "2026-09-09T23:59:30.000Z",
  };
  input.fencing = {
    contract: "h_standby_fencing_v1",
    status: "active",
    authority_configured: true,
    request_id: REQUEST_ID,
    fence_epoch: 42,
    last_fence_epoch: 42,
    assertion_sha256: "b".repeat(64),
    primary_project_ref: PRIMARY_REF,
    standby_project_ref: STANDBY_REF,
    primary_write_fenced: true,
    fenced_at: "2026-09-09T23:59:45.000Z",
    promoted_at: "2026-09-09T23:59:50.000Z",
    automatic_self_promotion_enabled: false,
  };
  return input;
}

Deno.test("validated passive exact mirror plus fencing authority is preflight-ready but never active", () => {
  const result = evaluateStandbyHealth(healthyInput(), NOW);
  assertEquals(result.standbyReady, true);
  assertEquals(result.preflightReady, true);
  assertEquals(result.activeReady, false);
  assertEquals(result.fencingAuthorityReady, true);
  assertEquals(result.fencingAttested, false);
  assertEquals(result.passiveExecutionContractReady, true);
  assertEquals(result.executionContractReady, true);
  assertEquals(result.executionRuntimeReady, true);
  assertEquals(result.aiContinuityFresh, true);
  assertEquals(result.restoreVerified, true);
  assertEquals(result.replicationFresh, true);
  assert(result.replicationLagSeconds != null && result.replicationLagSeconds <= 120);
});

Deno.test("missing independent fencing authority blocks passive failover readiness", () => {
  const input = healthyInput();
  input.fencingConfig = {};
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.passiveExecutionContractReady, true);
  assertEquals(result.fencingAuthorityReady, false);
  assertEquals(result.standbyReady, false);
});

Deno.test("private EC key material is never accepted as fencing configuration", () => {
  const input = healthyInput();
  input.fencingConfig.fencing_public_jwk = JSON.stringify({
    kty: "EC", crv: "P-256", x: "A".repeat(43), y: "B".repeat(43), d: "C".repeat(43),
  });
  assertEquals(evaluateStandbyHealth(input, NOW).fencingAuthorityReady, false);
});

Deno.test("request-only promoted standby is active only with matching signed-fence attestation", () => {
  const result = evaluateStandbyHealth(promotedInput(), NOW);
  assertEquals(result.standbyReady, false);
  assertEquals(result.preflightReady, false);
  assertEquals(result.activeReady, true);
  assertEquals(result.promoted, true);
  assertEquals(result.replicaWritesEnabled, false);
  assertEquals(result.activeExecutionContractReady, true);
  assertEquals(result.promotionAttested, true);
  assertEquals(result.fencingAttested, true);
  assertEquals(result.primaryWriteFenced, true);
  assertEquals(result.fenceEpoch, 42);
});

Deno.test("promotion without fencing attestation fails active health closed", () => {
  const input = promotedInput();
  input.fencing = {};
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.promotionAttested, true);
  assertEquals(result.fencingAttested, false);
  assertEquals(result.activeReady, false);
});

Deno.test("mismatched fence target or request id fails active health", () => {
  const wrongTarget = promotedInput();
  wrongTarget.fencing.standby_project_ref = "cccccccccccccccccccc";
  assertEquals(evaluateStandbyHealth(wrongTarget, NOW).activeReady, false);

  const wrongRequest = promotedInput();
  wrongRequest.fencing.request_id = "other_request_12345678901";
  assertEquals(evaluateStandbyHealth(wrongRequest, NOW).activeReady, false);
});

Deno.test("automatic self-promotion flag is forbidden", () => {
  const input = promotedInput();
  input.fencing.automatic_self_promotion_enabled = true;
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.fencingAttested, false);
  assertEquals(result.activeReady, false);
});

Deno.test("active request-only runtime does not expire merely because primary replication becomes stale", () => {
  const input = promotedInput();
  input.replication.source_generated_at = "2026-09-09T23:40:00.000Z";
  input.replicationObservedAt = "2026-09-09T23:40:10.000Z";
  input.execution.ai_continuity_validated_at = "2026-09-09T23:40:00.000Z";
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.replicationFresh, false);
  assertEquals(result.aiContinuityFresh, false);
  assertEquals(result.activeReady, true);
});

Deno.test("promoted state without matching promotion attestation fails closed", () => {
  const input = promotedInput();
  input.promotion.request_id = "different_promotion_123456";
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.promotionAttested, false);
  assertEquals(result.activeReady, false);
});

Deno.test("promoted runtime must fence replica writes", () => {
  const input = promotedInput();
  input.runtime.allow_replica_writes = true;
  assertEquals(evaluateStandbyHealth(input, NOW).activeReady, false);
});

Deno.test("request-active runtime cannot enable scheduler or autonomous outbound", () => {
  const scheduler = promotedInput();
  scheduler.execution.scheduler_active = true;
  assertEquals(evaluateStandbyHealth(scheduler, NOW).activeReady, false);

  const outbound = promotedInput();
  outbound.execution.autonomous_outbound_active = true;
  assertEquals(evaluateStandbyHealth(outbound, NOW).activeReady, false);
});

Deno.test("legacy exact mirror v1 is not accepted for passive preflight", () => {
  const input = healthyInput();
  input.replication.protocol = "exact_mirror_v1";
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.replicationFresh, false);
  assertEquals(result.standbyReady, false);
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

Deno.test("complete passive execution attestation cannot bypass disabled runtime flag", () => {
  const input = healthyInput();
  input.runtime.execution_runtime_ready = false;
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.passiveExecutionContractReady, true);
  assertEquals(result.runtimeExecutionFlag, false);
  assertEquals(result.executionRuntimeReady, false);
  assertEquals(result.standbyReady, false);
});

Deno.test("missing identity or AI readiness fails passive preflight closed", () => {
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

Deno.test("paid AI budget continuity is mandatory before promotion", () => {
  const input = healthyInput();
  input.execution.paid_ai_budget_continuity_ready = false;
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.paidAiBudgetContinuityReady, false);
  assertEquals(result.passiveExecutionContractReady, false);
  assertEquals(result.standbyReady, false);
});

Deno.test("stale AI continuity attestation blocks passive promotion readiness", () => {
  const input = healthyInput();
  input.execution.ai_continuity_validated_at = "2026-09-09T23:55:00.000Z";
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.aiCredentialsRekeyReady, true);
  assertEquals(result.aiContinuityFresh, false);
  assertEquals(result.passiveExecutionContractReady, false);
  assertEquals(result.standbyReady, false);
});

Deno.test("wrong execution contract or passive mode fails closed", () => {
  const wrongContract = healthyInput();
  wrongContract.execution.contract = "unknown";
  assertEquals(evaluateStandbyHealth(wrongContract, NOW).standbyReady, false);

  const wrongMode = healthyInput();
  wrongMode.execution.mode = "active_primary";
  assertEquals(evaluateStandbyHealth(wrongMode, NOW).standbyReady, false);
});

Deno.test("old source snapshot cannot stay passive-ready because stored lag was once low", () => {
  const input = healthyInput();
  input.replication.source_generated_at = "2026-09-09T23:50:00.000Z";
  input.replication.lag_seconds = 5;
  input.replicationObservedAt = "2026-09-09T23:59:59.000Z";
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.replicationLagSeconds, 600);
  assertEquals(result.replicationFresh, false);
  assertEquals(result.standbyReady, false);
});

Deno.test("stale replication observation fails passive preflight closed", () => {
  const input = healthyInput();
  input.replicationObservedAt = "2026-09-09T23:55:00.000Z";
  const result = evaluateStandbyHealth(input, NOW);
  assertEquals(result.replicationFresh, false);
  assertEquals(result.standbyReady, false);
});

Deno.test("wrong H identity fails both passive and active health", () => {
  const passive = healthyInput();
  passive.runtime.h_identity = "Other";
  assertEquals(evaluateStandbyHealth(passive, NOW).standbyReady, false);

  const active = promotedInput();
  active.runtime.h_identity = "Other";
  assertEquals(evaluateStandbyHealth(active, NOW).activeReady, false);
});

Deno.test("effective lag never gets smaller than stored lag", () => {
  assertEquals(effectiveReplicationLagSeconds("2026-09-09T23:59:50.000Z", 25, NOW), 25);
  assertEquals(effectiveReplicationLagSeconds("2026-09-09T23:59:20.000Z", 5, NOW), 40);
});
