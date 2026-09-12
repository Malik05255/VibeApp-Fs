import test from "node:test";
import assert from "node:assert/strict";
import { evaluateProductionCertification } from "./h-production-certification.mjs";

const sha = "a".repeat(40);

function code(overrides = {}) {
  return {
    schemaVersion: 1,
    codeReady: true,
    tenGatesPassed: true,
    status: "CODE_READY",
    gitSha: sha,
    secretsIncluded: false,
    ...overrides,
  };
}

function doctor(overrides = {}) {
  return {
    schemaVersion: 1,
    configurationReady: true,
    zeroCostSafe: true,
    createsExternalResources: false,
    performsPurchases: false,
    secretsIncluded: false,
    invalid: [],
    ...overrides,
  };
}

function ledger(overrides = {}) {
  return {
    schemaVersion: 1,
    status: "LIVE_READY",
    productionDeclarationAllowed: true,
    maxEvidenceAgeDays: 14,
    blockers: [],
    secretsIncluded: false,
    gates: {
      backup: { ready: true },
      "standby-preflight": { ready: true },
      "failover-active": { ready: true },
      "whatsapp-voice": { ready: true },
    },
    ...overrides,
  };
}

test("certifies only when code, configuration, and live evidence are all ready", () => {
  const result = evaluateProductionCertification({ code: code(), doctor: doctor(), ledger: ledger(), now: Date.parse("2026-09-12T12:00:00Z") });
  assert.equal(result.productionCertified, true);
  assert.equal(result.status, "PRODUCTION_CERTIFIED");
  assert.equal(result.gitSha, sha);
  assert.equal(result.blockers.length, 0);
  assert.equal(result.secretsIncluded, false);
});

test("blocks when ten code gates are not proven", () => {
  const result = evaluateProductionCertification({ code: code({ tenGatesPassed: false }), doctor: doctor(), ledger: ledger() });
  assert.equal(result.productionCertified, false);
  assert.deepEqual(result.blockers, [{ layer: "code", reason: "code_readiness_invalid" }]);
});

test("blocks when external configuration is incomplete", () => {
  const result = evaluateProductionCertification({ code: code(), doctor: doctor({ configurationReady: false, invalid: ["H_STANDBY_HEALTH_URL"] }), ledger: ledger() });
  assert.equal(result.productionCertified, false);
  assert.equal(result.layers.configuration.ready, false);
  assert.deepEqual(result.layers.configuration.invalid, ["H_STANDBY_HEALTH_URL"]);
});

test("blocks when any live gate is not fresh and ready", () => {
  const broken = ledger({
    status: "LIVE_EXTERNAL_REQUIRED",
    productionDeclarationAllowed: false,
    blockers: ["whatsapp-voice"],
    gates: {
      backup: { ready: true },
      "standby-preflight": { ready: true },
      "failover-active": { ready: true },
      "whatsapp-voice": { ready: false, reason: "evidence_stale" },
    },
  });
  const result = evaluateProductionCertification({ code: code(), doctor: doctor(), ledger: broken });
  assert.equal(result.productionCertified, false);
  assert.equal(result.layers.live.ready, false);
  assert.deepEqual(result.layers.live.blockers, ["whatsapp-voice"]);
});

test("fails closed on malformed or missing documents", () => {
  const result = evaluateProductionCertification({ code: null, doctor: {}, ledger: null });
  assert.equal(result.productionCertified, false);
  assert.deepEqual(result.blockers.map((x) => x.layer), ["code", "configuration", "live"]);
  assert.equal(result.secretsIncluded, false);
});
