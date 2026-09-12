import test from "node:test";
import assert from "node:assert/strict";
import { evaluateEvidenceDocuments } from "./h-live-readiness-ledger.mjs";

const NOW = Date.parse("2026-09-12T15:30:00Z");

function doc(target, evidence, options = {}) {
  return {
    schemaVersion: 2,
    ok: options.ok ?? true,
    target,
    startedAt: options.startedAt || "2026-09-12T15:00:00Z",
    completedAt: options.completedAt || "2026-09-12T15:01:00Z",
    gitSha: options.gitSha || "a".repeat(40),
    evidence,
    secretsIncluded: false,
  };
}

function backupEvidence(overrides = {}) {
  return {
    live: true,
    writeVerified: true,
    readVerified: true,
    deleteVerified: true,
    encryptedBackupRunnerVerified: true,
    encryptedEnvelopeVerified: true,
    plaintextChecksumVerified: true,
    registryHealthy: true,
    registryLastBackupVerified: true,
    rawMediaIncluded: false,
    rawSecretExposed: false,
    ...overrides,
  };
}

function preflightEvidence(overrides = {}) {
  return {
    live: true,
    mode: "preflight",
    replicationProtocol: "exact_mirror_v2",
    restoreVerified: true,
    executionNonceVerified: true,
    standbyCoreSchemaReadable: true,
    executionWritesPerformed: false,
    userContentReturnedByProbe: false,
    rawProviderCredentialsReplicated: false,
    rawMediaReplicated: false,
    ...overrides,
  };
}

function activeEvidence(overrides = {}) {
  return {
    live: true,
    mode: "active",
    replicationProtocol: "exact_mirror_v2",
    restoreVerified: true,
    promotionAttested: true,
    promotionMode: "request_only",
    requestOnlyActive: true,
    executionNonceVerified: true,
    standbyCoreSchemaReadable: true,
    executionWritesPerformed: false,
    userContentReturnedByProbe: false,
    noAutomaticFailbackEvidence: true,
    ...overrides,
  };
}

function voiceEvidence(overrides = {}) {
  return {
    live: true,
    metaMetadataVerified: true,
    metaDownloadVerified: true,
    transcriptionVerified: true,
    expectedProbePhraseDetected: true,
    bridgeProcessed: true,
    isolatedSyntheticWaId: true,
    externalMessagingDisabledForProbe: true,
    rawMediaPersistedByHarness: false,
    ...overrides,
  };
}

function validDocs() {
  return [
    doc("backup", backupEvidence()),
    doc("standby-preflight", preflightEvidence()),
    doc("failover-active", activeEvidence()),
    doc("whatsapp-voice", voiceEvidence()),
  ];
}

test("all four fresh deep proofs allow LIVE_READY", () => {
  const ledger = evaluateEvidenceDocuments(validDocs(), { now: NOW, maxAgeDays: 14 });
  assert.equal(ledger.status, "LIVE_READY");
  assert.equal(ledger.productionDeclarationAllowed, true);
  assert.deepEqual(ledger.blockers, []);
  for (const gate of Object.values(ledger.gates)) assert.equal(gate.ready, true);
});

test("missing evidence keeps production declaration fail closed", () => {
  const docs = validDocs().filter((item) => item.target !== "whatsapp-voice");
  const ledger = evaluateEvidenceDocuments(docs, { now: NOW });
  assert.equal(ledger.status, "LIVE_EXTERNAL_REQUIRED");
  assert.equal(ledger.productionDeclarationAllowed, false);
  assert.deepEqual(ledger.blockers, ["whatsapp-voice"]);
  assert.equal(ledger.gates["whatsapp-voice"].reason, "missing_evidence");
});

test("newer failed evidence overrides an older successful artifact", () => {
  const docs = validDocs();
  docs.push(doc("backup", {}, {
    ok: false,
    completedAt: "2026-09-12T15:20:00Z",
    gitSha: "b".repeat(40),
  }));
  const ledger = evaluateEvidenceDocuments(docs, { now: NOW });
  assert.equal(ledger.productionDeclarationAllowed, false);
  assert.equal(ledger.gates.backup.ready, false);
  assert.equal(ledger.gates.backup.reason, "evidence_document_invalid");
  assert.equal(ledger.gates.backup.gitSha, "b".repeat(40));
});

test("stale evidence expires even if it once passed", () => {
  const docs = validDocs().map((item) => ({
    ...item,
    startedAt: "2026-08-20T10:00:00Z",
    completedAt: "2026-08-20T10:01:00Z",
  }));
  const ledger = evaluateEvidenceDocuments(docs, { now: NOW, maxAgeDays: 14 });
  assert.equal(ledger.productionDeclarationAllowed, false);
  assert.equal(ledger.blockers.length, 4);
  assert.equal(ledger.gates.backup.reason, "evidence_stale");
});

test("future-dated evidence is rejected", () => {
  const docs = validDocs();
  docs[0] = doc("backup", backupEvidence(), {
    startedAt: "2026-09-12T16:00:00Z",
    completedAt: "2026-09-12T16:01:00Z",
  });
  const ledger = evaluateEvidenceDocuments(docs, { now: NOW });
  assert.equal(ledger.gates.backup.ready, false);
  assert.equal(ledger.gates.backup.reason, "evidence_time_in_future");
});

test("deep backup proof cannot omit checksum or registry validation", () => {
  const docs = validDocs();
  docs[0] = doc("backup", backupEvidence({ plaintextChecksumVerified: false }));
  const ledger = evaluateEvidenceDocuments(docs, { now: NOW });
  assert.equal(ledger.gates.backup.ready, false);
  assert.equal(ledger.gates.backup.reason, "backup_deep_proof_incomplete");
});

test("standby proof cannot claim readiness if execution writes occurred", () => {
  const docs = validDocs();
  docs[1] = doc("standby-preflight", preflightEvidence({ executionWritesPerformed: true }));
  const ledger = evaluateEvidenceDocuments(docs, { now: NOW });
  assert.equal(ledger.gates["standby-preflight"].ready, false);
  assert.equal(ledger.gates["standby-preflight"].reason, "standby_preflight_proof_incomplete");
});

test("voice proof remains fail closed if external messaging isolation is lost", () => {
  const docs = validDocs();
  docs[3] = doc("whatsapp-voice", voiceEvidence({ externalMessagingDisabledForProbe: false }));
  const ledger = evaluateEvidenceDocuments(docs, { now: NOW });
  assert.equal(ledger.gates["whatsapp-voice"].ready, false);
  assert.equal(ledger.gates["whatsapp-voice"].reason, "whatsapp_voice_proof_incomplete");
});
