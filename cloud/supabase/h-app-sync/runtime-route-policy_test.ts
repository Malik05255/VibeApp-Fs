import { assertEquals } from "jsr:@std/assert@1";
import { evaluateAppRuntimeRoute } from "./runtime-route-policy.ts";

Deno.test("primary runtime is request ready without standby promotion state", () => {
  assertEquals(evaluateAppRuntimeRoute({}), {
    runtimeRole: "primary",
    requestReady: true,
    requestOnlyActive: false,
    promoted: false,
    replicaWritesFenced: false,
    promotionAttested: false,
  });
});

function activeInput() {
  const requestId = "route_request_1234567890";
  const digest = "a".repeat(64);
  return {
    runtime: {
      runtime_role: "standby",
      h_identity: "H",
      dedicated_h_standby: true,
      allow_replica_writes: false,
      execution_runtime_ready: true,
      promoted: true,
      promotion_mode: "request_only",
      promotion_request_id: requestId,
    },
    execution: {
      contract: "h_standby_execution_v1",
      mode: "request_active",
      core_schema_ready: true,
      function_inventory_ready: true,
      runtime_secret_ready: true,
      app_identity_rekey_ready: true,
      whatsapp_identity_rekey_ready: true,
      ai_credentials_rekey_ready: true,
      free_ai_route_ready: true,
      paid_ai_budget_continuity_ready: true,
      promotion_controls_ready: true,
      execution_runtime_ready: true,
      scheduler_active: false,
      autonomous_outbound_active: false,
    },
    replication: { last_digest: digest },
    promotion: {
      protocol: "h_standby_promotion_v1",
      status: "active",
      mode: "request_only",
      request_id: requestId,
      source_digest: digest,
    },
  };
}

Deno.test("request-only promoted standby is request ready", () => {
  const result = evaluateAppRuntimeRoute(activeInput());
  assertEquals(result.runtimeRole, "standby");
  assertEquals(result.requestReady, true);
  assertEquals(result.requestOnlyActive, true);
  assertEquals(result.replicaWritesFenced, true);
  assertEquals(result.promotionAttested, true);
});

Deno.test("passive standby fails closed", () => {
  const input = activeInput();
  input.runtime.promoted = false;
  input.runtime.allow_replica_writes = true;
  input.execution.mode = "passive_preflight";
  input.execution.execution_runtime_ready = false;
  const result = evaluateAppRuntimeRoute(input);
  assertEquals(result.requestReady, false);
  assertEquals(result.requestOnlyActive, false);
});

Deno.test("promotion digest mismatch fails closed", () => {
  const input = activeInput();
  input.promotion.source_digest = "b".repeat(64);
  const result = evaluateAppRuntimeRoute(input);
  assertEquals(result.requestReady, false);
  assertEquals(result.promotionAttested, false);
});

Deno.test("standby cannot serve app requests with scheduler active", () => {
  const input = activeInput();
  input.execution.scheduler_active = true;
  assertEquals(evaluateAppRuntimeRoute(input).requestReady, false);
});
