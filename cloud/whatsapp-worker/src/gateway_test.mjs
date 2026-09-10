import test from "node:test";
import assert from "node:assert/strict";
import { deriveSupabaseBaseUrl, handleAppFailoverRoute } from "./gateway.js";

const env = {
  H_SUPABASE_VOICE_URL: "https://primary.supabase.co/functions/v1/h-whatsapp-inbox",
  H_RUNTIME_SECRET: "primary-secret",
  H_STANDBY_FAILOVER_ENABLED: "true",
  H_STANDBY_SUPABASE_VOICE_URL: "https://standby.supabase.co/functions/v1/h-whatsapp-inbox",
  H_STANDBY_RUNTIME_SECRET: "standby-secret",
};

function request(token = "owner-google-token") {
  return new Request("https://h.example.workers.dev/h-app-failover-route", {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: "{}",
  });
}

function response(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function routeStatus(overrides = {}) {
  return {
    ok: true,
    linked: true,
    activeReady: false,
    mode: "inactive",
    promotionAttested: false,
    replicaWritesEnabled: true,
    schedulerActive: false,
    autonomousOutboundActive: false,
    replicationProtocol: "exact_mirror_v2",
    restoreVerified: true,
    ...overrides,
  };
}

test("rejects callers without Google bearer before runtime selection", async () => {
  let selected = false;
  const res = await handleAppFailoverRoute(
    new Request("https://h.example.workers.dev/h-app-failover-route", { method: "POST" }),
    env,
    async () => { throw new Error("must not fetch"); },
    async () => { selected = true; return { role: "standby" }; },
  );
  assert.equal(res.status, 401);
  assert.equal(selected, false);
});

test("rejects unlinked Google owner before any promotion selector runs", async () => {
  let selected = false;
  const res = await handleAppFailoverRoute(
    request(),
    env,
    async () => response({ ok: false, error: "app_not_linked" }, 403),
    async () => { selected = true; return { role: "standby" }; },
  );
  assert.equal(res.status, 403);
  assert.equal(selected, false);
});

test("healthy-primary decision returns no standby route or secret", async () => {
  const res = await handleAppFailoverRoute(
    request(),
    env,
    async () => response(routeStatus()),
    async () => ({ role: "primary", endpoint: env.H_SUPABASE_VOICE_URL, secret: env.H_RUNTIME_SECRET }),
  );
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.route, "primary");
  assert.equal(body.standbyActive, false);
  assert.equal(Object.hasOwn(body, "standbyBaseUrl"), false);
  assert.equal(JSON.stringify(body).includes("primary-secret"), false);
  assert.equal(JSON.stringify(body).includes("standby-secret"), false);
});

test("standby route is returned only after owner-facing active attestation", async () => {
  let statusCalls = 0;
  const res = await handleAppFailoverRoute(
    request(),
    env,
    async (url) => {
      assert.equal(String(url), "https://standby.supabase.co/functions/v1/h-standby-route-status");
      statusCalls += 1;
      return response(statusCalls === 1 ? routeStatus() : routeStatus({
        activeReady: true,
        mode: "request_only",
        promotionAttested: true,
        replicaWritesEnabled: false,
      }));
    },
    async () => ({ role: "standby", endpoint: env.H_STANDBY_SUPABASE_VOICE_URL, secret: env.H_STANDBY_RUNTIME_SECRET }),
  );
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(statusCalls, 2);
  assert.equal(body.route, "standby");
  assert.equal(body.standbyBaseUrl, "https://standby.supabase.co");
  assert.equal(body.mode, "request_only");
  assert.equal(body.promotionAttested, true);
  assert.equal(body.replicaWritesEnabled, false);
  assert.equal(body.schedulerActive, false);
  assert.equal(body.autonomousOutboundActive, false);
  assert.equal(body.replicationProtocol, "exact_mirror_v2");
  assert.equal(body.restoreVerified, true);
  assert.equal(JSON.stringify(body).includes("standby-secret"), false);
});

test("promotion selector cannot bypass a failed post-promotion owner attestation", async () => {
  let calls = 0;
  const res = await handleAppFailoverRoute(
    request(),
    env,
    async () => {
      calls += 1;
      return response(routeStatus());
    },
    async () => ({ role: "standby", endpoint: env.H_STANDBY_SUPABASE_VOICE_URL, secret: env.H_STANDBY_RUNTIME_SECRET }),
  );
  assert.equal(calls, 2);
  assert.equal(res.status, 503);
  const body = await res.json();
  assert.equal(body.error, "standby_active_attestation_failed");
});

test("standby must be explicitly configured before Android witness can run", async () => {
  const res = await handleAppFailoverRoute(request(), { ...env, H_STANDBY_FAILOVER_ENABLED: "false" });
  assert.equal(res.status, 503);
});

test("Supabase public base derivation rejects non-Supabase and credential-bearing URLs", () => {
  assert.equal(
    deriveSupabaseBaseUrl("https://standby.supabase.co/functions/v1/h-whatsapp-inbox"),
    "https://standby.supabase.co",
  );
  assert.equal(deriveSupabaseBaseUrl("https://user@standby.supabase.co/functions/v1/h-whatsapp-inbox"), null);
  assert.equal(deriveSupabaseBaseUrl("https://example.com/functions/v1/h-whatsapp-inbox"), null);
  assert.equal(deriveSupabaseBaseUrl("http://standby.supabase.co/functions/v1/h-whatsapp-inbox"), null);
});
