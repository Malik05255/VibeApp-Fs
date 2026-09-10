import test from "node:test";
import assert from "node:assert/strict";
import { monitorStandbyPromotion } from "./router-scheduled.js";

const baseEnv = {
  H_SUPABASE_VOICE_URL: "https://primary.supabase.co/functions/v1/h-whatsapp-inbox",
  H_RUNTIME_SECRET: "primary-secret",
};

const standbyEnv = {
  ...baseEnv,
  H_STANDBY_FAILOVER_ENABLED: "true",
  H_STANDBY_SUPABASE_VOICE_URL: "https://standby.supabase.co/functions/v1/h-whatsapp-inbox",
  H_STANDBY_RUNTIME_SECRET: "standby-secret",
  H_STANDBY_FAILOVER_CONFIRM_DELAY_MS: "0",
};

function jsonResponse(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function passiveStandbyHealth(overrides = {}) {
  return {
    ok: true,
    service: "h-standby-health",
    standbyReady: true,
    preflightReady: true,
    activeReady: false,
    runtimeRole: "standby",
    hIdentity: "H",
    promoted: false,
    replicaWritesEnabled: true,
    promotionControlsReady: true,
    aiContinuityFresh: true,
    restoreVerified: true,
    replicationMode: "continuous",
    replicationProtocol: "exact_mirror_v2",
    replicationFresh: true,
    replicationLagSeconds: 15,
    ...overrides,
  };
}

function activeStandbyHealth(overrides = {}) {
  return {
    ok: true,
    service: "h-standby-health",
    standbyReady: false,
    preflightReady: false,
    activeReady: true,
    requestOnlyActive: true,
    runtimeRole: "standby",
    hIdentity: "H",
    promoted: true,
    promotionAttested: true,
    promotionMode: "request_only",
    replicaWritesEnabled: false,
    schedulerActive: false,
    autonomousOutboundActive: false,
    restoreVerified: true,
    replicationMode: "continuous",
    replicationProtocol: "exact_mirror_v2",
    replicationFresh: false,
    replicationLagSeconds: 500,
    ...overrides,
  };
}

test("disabled standby monitor performs no network probes", async () => {
  let calls = 0;
  const result = await monitorStandbyPromotion(baseEnv, async () => {
    calls += 1;
    throw new Error("unexpected network call");
  });

  assert.deepEqual(result, {
    checked: false,
    standbyConfigured: false,
    selectedRole: null,
  });
  assert.equal(calls, 0);
});

test("healthy primary remains selected and monitor never promotes passive standby", async () => {
  let promotionCalls = 0;
  const result = await monitorStandbyPromotion(standbyEnv, async (url) => {
    const value = String(url);
    if (value.includes("primary.supabase.co")) {
      return jsonResponse({ ok: true, service: "h-runtime-readiness" });
    }
    if (value.endsWith("/h-standby-health")) return jsonResponse(passiveStandbyHealth());
    if (value.endsWith("/h-standby-promote")) {
      promotionCalls += 1;
      return jsonResponse({ ok: false }, 409);
    }
    throw new Error(`unexpected URL ${value}`);
  });

  assert.equal(result.checked, true);
  assert.equal(result.selectedRole, "primary");
  assert.equal(promotionCalls, 0);
});

test("monitor promotes only after two failed primary probes and verifies active standby", async () => {
  let primaryCalls = 0;
  let standbyHealthCalls = 0;
  let promotionCalls = 0;

  const result = await monitorStandbyPromotion(standbyEnv, async (url, init = {}) => {
    const value = String(url);
    if (value.includes("primary.supabase.co")) {
      primaryCalls += 1;
      return jsonResponse({ ok: false }, 503);
    }
    if (value.endsWith("/h-standby-health")) {
      standbyHealthCalls += 1;
      return jsonResponse(standbyHealthCalls === 1 ? passiveStandbyHealth() : activeStandbyHealth());
    }
    if (value.endsWith("/h-standby-promote")) {
      promotionCalls += 1;
      const body = JSON.parse(String(init.body || "{}"));
      return jsonResponse({
        ok: true,
        service: "h-standby-promote",
        promoted: true,
        active: true,
        mode: "request_only",
        requestId: body.request_id,
        canonicalPromotionRequestId: body.request_id,
        schedulerActive: false,
        autonomousOutboundActive: false,
      });
    }
    throw new Error(`unexpected URL ${value}`);
  });

  assert.equal(result.selectedRole, "standby");
  assert.equal(primaryCalls, 2);
  assert.equal(standbyHealthCalls, 2);
  assert.equal(promotionCalls, 1);
});

test("stale standby fails closed and is never promoted", async () => {
  let promotionCalls = 0;
  const result = await monitorStandbyPromotion(standbyEnv, async (url) => {
    const value = String(url);
    if (value.includes("primary.supabase.co")) return jsonResponse({ ok: false }, 503);
    if (value.endsWith("/h-standby-health")) {
      return jsonResponse(passiveStandbyHealth({
        standbyReady: false,
        preflightReady: false,
        replicationFresh: false,
        replicationLagSeconds: 900,
      }));
    }
    if (value.endsWith("/h-standby-promote")) {
      promotionCalls += 1;
      return jsonResponse({ ok: false }, 409);
    }
    throw new Error(`unexpected URL ${value}`);
  });

  assert.equal(result.selectedRole, null);
  assert.equal(result.error, "no_validated_runtime");
  assert.equal(promotionCalls, 0);
});
