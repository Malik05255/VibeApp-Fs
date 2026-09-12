import test from "node:test";
import assert from "node:assert/strict";
import { selectUnifiedRuntime } from "./router.js";

const env = {
  H_SUPABASE_VOICE_URL: "https://primary.supabase.co/functions/v1/h-whatsapp-inbox",
  H_RUNTIME_SECRET: "primary-secret",
  H_STANDBY_FAILOVER_ENABLED: "true",
  H_STANDBY_SUPABASE_VOICE_URL: "https://standby.supabase.co/functions/v1/h-whatsapp-inbox",
  H_STANDBY_RUNTIME_SECRET: "standby-secret",
  H_STANDBY_FAILOVER_CONFIRM_DELAY_MS: "0",
};

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function activeStandby(overrides = {}) {
  return {
    ok: true,
    service: "h-standby-health",
    runtimeRole: "standby",
    hIdentity: "H",
    activeReady: true,
    requestOnlyActive: true,
    promoted: true,
    promotionAttested: true,
    promotionMode: "request_only",
    replicaWritesEnabled: false,
    schedulerActive: false,
    autonomousOutboundActive: false,
    restoreVerified: true,
    replicationProtocol: "exact_mirror_v2",
    ...overrides,
  };
}

function preflightStandby(overrides = {}) {
  return {
    ok: true,
    service: "h-standby-health",
    runtimeRole: "standby",
    hIdentity: "H",
    preflightReady: true,
    standbyReady: true,
    activeReady: false,
    promoted: false,
    replicaWritesEnabled: true,
    promotionControlsReady: true,
    aiContinuityFresh: true,
    restoreVerified: true,
    replicationMode: "continuous",
    replicationProtocol: "exact_mirror_v2",
    replicationFresh: true,
    replicationLagSeconds: 3,
    ...overrides,
  };
}

test("healthy primary wins when an active standby claims the wrong replication protocol", async () => {
  const calls = [];
  const selected = await selectUnifiedRuntime(env, async (url) => {
    const value = String(url);
    calls.push(value);
    if (value.includes("primary.supabase.co")) {
      return json({ ok: true, service: "h-runtime-readiness" });
    }
    if (value.endsWith("/h-standby-health")) {
      return json(activeStandby({ replicationProtocol: "legacy_mirror_v1" }));
    }
    throw new Error(`unexpected URL ${value}`);
  });

  assert.equal(selected.role, "primary");
  assert.equal(calls.some((url) => url.endsWith("/h-standby-promote")), false);
});

test("failed primary does not execute on an active standby with the wrong replication protocol", async () => {
  let executionCalls = 0;
  await assert.rejects(
    () => selectUnifiedRuntime(env, async (url) => {
      const value = String(url);
      if (value.includes("primary.supabase.co")) {
        return json({ ok: false, service: "h-runtime-readiness" }, 503);
      }
      if (value.endsWith("/h-standby-health")) {
        return json(activeStandby({ replicationProtocol: "legacy_mirror_v1" }));
      }
      if (value === env.H_STANDBY_SUPABASE_VOICE_URL) {
        executionCalls += 1;
        return json({ ok: true });
      }
      throw new Error(`unexpected URL ${value}`);
    }),
    /No validated H runtime is available before execution/,
  );

  assert.equal(executionCalls, 0);
});

test("failed primary promotes only a fully attested fresh standby and selects it", async () => {
  let primaryChecks = 0;
  let healthChecks = 0;
  let promoteCalls = 0;
  const selected = await selectUnifiedRuntime(env, async (url, init) => {
    const value = String(url);
    if (value.endsWith("/h-runtime-readiness")) {
      primaryChecks += 1;
      return json({ ok: false, service: "h-runtime-readiness" }, 503);
    }
    if (value.endsWith("/h-standby-health")) {
      healthChecks += 1;
      return json(healthChecks === 1 ? preflightStandby() : activeStandby());
    }
    if (value.endsWith("/h-standby-promote")) {
      promoteCalls += 1;
      const payload = JSON.parse(String(init?.body || "{}"));
      return json({
        ok: true,
        service: "h-standby-promote",
        promoted: true,
        active: true,
        mode: "request_only",
        requestId: payload.request_id,
        schedulerActive: false,
        autonomousOutboundActive: false,
      });
    }
    throw new Error(`unexpected URL ${value}`);
  });

  assert.equal(selected.role, "standby");
  assert.equal(primaryChecks, 2, "primary must be confirmed failed twice before promotion");
  assert.equal(promoteCalls, 1, "standby promotion must happen exactly once for this request");
});

test("promoted standby is sticky across concurrent selectors even when primary later looks healthy", async () => {
  let promotionCalls = 0;
  const fetchImpl = async (url) => {
    const value = String(url);
    if (value.endsWith("/h-runtime-readiness")) {
      return json({ ok: true, service: "h-runtime-readiness" });
    }
    if (value.endsWith("/h-standby-health")) return json(activeStandby());
    if (value.endsWith("/h-standby-promote")) {
      promotionCalls += 1;
      return json({ ok: false }, 409);
    }
    throw new Error(`unexpected URL ${value}`);
  };

  const selections = await Promise.all(
    Array.from({ length: 20 }, () => selectUnifiedRuntime(env, fetchImpl)),
  );
  assert.equal(selections.every((value) => value.role === "standby"), true);
  assert.equal(promotionCalls, 0, "an already-promoted standby must not be promoted again or fail back automatically");
});
