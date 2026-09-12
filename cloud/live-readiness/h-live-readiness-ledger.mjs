import { readdir, readFile, writeFile } from "node:fs/promises";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const TARGETS = ["backup", "standby-preflight", "failover-active", "whatsapp-voice"];
const DEFAULT_MAX_AGE_DAYS = 14;
const OUTPUT = process.env.H_LIVE_LEDGER_OUTPUT || "h-live-readiness-ledger.json";

function isoMs(value) {
  const parsed = Date.parse(String(value || ""));
  return Number.isFinite(parsed) ? parsed : null;
}

function completedMs(doc) {
  return isoMs(doc?.completedAt) ?? isoMs(doc?.startedAt) ?? 0;
}

function validateTarget(target, doc) {
  const evidence = doc?.evidence || {};
  if (doc?.ok !== true || Number(doc?.schemaVersion || 0) < 2 || doc?.target !== target) return "evidence_document_invalid";

  if (target === "backup") {
    return evidence.live === true &&
      evidence.writeVerified === true &&
      evidence.readVerified === true &&
      evidence.deleteVerified === true &&
      evidence.encryptedBackupRunnerVerified === true &&
      evidence.encryptedEnvelopeVerified === true &&
      evidence.plaintextChecksumVerified === true &&
      evidence.registryHealthy === true &&
      evidence.registryLastBackupVerified === true &&
      evidence.rawMediaIncluded === false &&
      evidence.rawSecretExposed === false
      ? null : "backup_deep_proof_incomplete";
  }

  if (target === "standby-preflight") {
    return evidence.live === true &&
      evidence.mode === "preflight" &&
      evidence.replicationProtocol === "exact_mirror_v2" &&
      evidence.restoreVerified === true &&
      evidence.executionNonceVerified === true &&
      evidence.standbyCoreSchemaReadable === true &&
      evidence.executionWritesPerformed === false &&
      evidence.userContentReturnedByProbe === false &&
      evidence.rawProviderCredentialsReplicated === false &&
      evidence.rawMediaReplicated === false
      ? null : "standby_preflight_proof_incomplete";
  }

  if (target === "failover-active") {
    return evidence.live === true &&
      evidence.mode === "active" &&
      evidence.replicationProtocol === "exact_mirror_v2" &&
      evidence.restoreVerified === true &&
      evidence.promotionAttested === true &&
      evidence.promotionMode === "request_only" &&
      evidence.requestOnlyActive === true &&
      evidence.executionNonceVerified === true &&
      evidence.standbyCoreSchemaReadable === true &&
      evidence.executionWritesPerformed === false &&
      evidence.userContentReturnedByProbe === false &&
      evidence.noAutomaticFailbackEvidence === true
      ? null : "active_failover_proof_incomplete";
  }

  if (target === "whatsapp-voice") {
    return evidence.live === true &&
      evidence.metaMetadataVerified === true &&
      evidence.metaDownloadVerified === true &&
      evidence.transcriptionVerified === true &&
      evidence.expectedProbePhraseDetected === true &&
      evidence.bridgeProcessed === true &&
      evidence.isolatedSyntheticWaId === true &&
      evidence.externalMessagingDisabledForProbe === true &&
      evidence.rawMediaPersistedByHarness === false
      ? null : "whatsapp_voice_proof_incomplete";
  }

  return "unknown_target";
}

export function evaluateEvidenceDocuments(documents, { now = Date.now(), maxAgeDays = DEFAULT_MAX_AGE_DAYS } = {}) {
  const maxAgeMs = Math.max(1, Number(maxAgeDays) || DEFAULT_MAX_AGE_DAYS) * 24 * 60 * 60 * 1000;
  const gates = {};
  const blockers = [];

  for (const target of TARGETS) {
    const candidates = documents
      .filter((doc) => doc && typeof doc === "object" && doc.target === target)
      .sort((a, b) => completedMs(b) - completedMs(a));
    const latest = candidates[0] || null;
    if (!latest) {
      gates[target] = { ready: false, reason: "missing_evidence", completedAt: null, gitSha: null };
      blockers.push(target);
      continue;
    }

    const time = completedMs(latest);
    const future = time > now + 5 * 60_000;
    const stale = !time || now - time > maxAgeMs;
    let reason = validateTarget(target, latest);
    if (!reason && future) reason = "evidence_time_in_future";
    if (!reason && stale) reason = "evidence_stale";

    const ready = reason == null;
    gates[target] = {
      ready,
      reason: reason || null,
      completedAt: latest.completedAt || null,
      gitSha: String(latest.gitSha || "").slice(0, 40) || null,
      evidenceSchemaVersion: Number(latest.schemaVersion || 0),
    };
    if (!ready) blockers.push(target);
  }

  const ready = blockers.length === 0;
  return {
    schemaVersion: 1,
    status: ready ? "LIVE_READY" : "LIVE_EXTERNAL_REQUIRED",
    productionDeclarationAllowed: ready,
    checkedAt: new Date(now).toISOString(),
    maxEvidenceAgeDays: maxAgeMs / (24 * 60 * 60 * 1000),
    gates,
    blockers,
    secretsIncluded: false,
  };
}

async function evidenceFiles(root) {
  const output = [];
  async function walk(dir) {
    for (const item of await readdir(dir, { withFileTypes: true })) {
      const path = join(dir, item.name);
      if (item.isDirectory()) await walk(path);
      else if (item.isFile() && item.name.endsWith(".json")) output.push(path);
    }
  }
  await walk(root);
  return output;
}

export async function loadEvidenceDirectory(root) {
  const docs = [];
  for (const file of await evidenceFiles(resolve(root))) {
    try {
      const parsed = JSON.parse(await readFile(file, "utf8"));
      if (parsed && typeof parsed === "object" && typeof parsed.target === "string") docs.push(parsed);
    } catch {}
  }
  return docs;
}

function cliOptions(argv) {
  const options = { dir: "h-live-evidence", requireReady: false, maxAgeDays: DEFAULT_MAX_AGE_DAYS };
  for (const arg of argv) {
    if (arg === "--require-ready") options.requireReady = true;
    else if (arg.startsWith("--max-age-days=")) options.maxAgeDays = Number(arg.slice("--max-age-days=".length));
    else if (!arg.startsWith("--")) options.dir = arg;
  }
  return options;
}

async function main() {
  const options = cliOptions(process.argv.slice(2));
  let docs = [];
  try { docs = await loadEvidenceDirectory(options.dir); } catch {}
  const ledger = evaluateEvidenceDocuments(docs, { maxAgeDays: options.maxAgeDays });
  await writeFile(OUTPUT, `${JSON.stringify(ledger, null, 2)}\n`, "utf8");
  console.log(JSON.stringify(ledger, null, 2));
  if (options.requireReady && !ledger.productionDeclarationAllowed) process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await main();
