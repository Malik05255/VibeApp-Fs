import { readFile, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const OUTPUT = process.env.H_PRODUCTION_CERTIFICATION_OUTPUT || "h-production-certification.json";
const CODE_PATH = process.env.H_CODE_READINESS_INPUT || "h-code-readiness.json";
const DOCTOR_PATH = process.env.H_EXTERNAL_DOCTOR_INPUT || "h-external-readiness-doctor.json";
const LEDGER_PATH = process.env.H_LIVE_LEDGER_INPUT || "h-live-readiness-ledger.json";
const LIVE_TARGETS = ["backup", "standby-preflight", "failover-active", "whatsapp-voice"];

function validSha(value) {
  return /^[0-9a-f]{40}$/i.test(String(value || ""));
}

function codeLayer(doc) {
  const ready = doc?.schemaVersion >= 1 &&
    doc?.codeReady === true &&
    doc?.tenGatesPassed === true &&
    doc?.status === "CODE_READY" &&
    doc?.secretsIncluded === false &&
    validSha(doc?.gitSha);
  return {
    ready,
    reason: ready ? null : "code_readiness_invalid",
    gitSha: validSha(doc?.gitSha) ? String(doc.gitSha).toLowerCase() : null,
  };
}

function configurationLayer(doc) {
  const ready = doc?.schemaVersion >= 1 &&
    doc?.configurationReady === true &&
    doc?.zeroCostSafe === true &&
    doc?.createsExternalResources === false &&
    doc?.performsPurchases === false &&
    doc?.secretsIncluded === false;
  return {
    ready,
    reason: ready ? null : "external_configuration_incomplete",
    invalid: Array.isArray(doc?.invalid) ? doc.invalid : [],
  };
}

function liveLayer(doc) {
  const gates = doc?.gates || {};
  const allTargetsReady = LIVE_TARGETS.every((target) => gates?.[target]?.ready === true);
  const ready = doc?.schemaVersion >= 1 &&
    doc?.status === "LIVE_READY" &&
    doc?.productionDeclarationAllowed === true &&
    Array.isArray(doc?.blockers) && doc.blockers.length === 0 &&
    doc?.secretsIncluded === false &&
    allTargetsReady;
  return {
    ready,
    reason: ready ? null : "fresh_live_evidence_incomplete",
    blockers: Array.isArray(doc?.blockers) ? doc.blockers : LIVE_TARGETS,
    maxEvidenceAgeDays: Number(doc?.maxEvidenceAgeDays || 0) || null,
  };
}

export function evaluateProductionCertification({ code, doctor, ledger, now = Date.now() }) {
  const layers = {
    code: codeLayer(code),
    configuration: configurationLayer(doctor),
    live: liveLayer(ledger),
  };
  const blockers = Object.entries(layers)
    .filter(([, layer]) => !layer.ready)
    .map(([layer, value]) => ({ layer, reason: value.reason }));
  const productionCertified = blockers.length === 0;

  return {
    schemaVersion: 1,
    status: productionCertified ? "PRODUCTION_CERTIFIED" : "CERTIFICATION_BLOCKED",
    productionCertified,
    certifiedAt: productionCertified ? new Date(now).toISOString() : null,
    checkedAt: new Date(now).toISOString(),
    gitSha: layers.code.gitSha,
    layers,
    blockers,
    zeroCostSafe: doctor?.zeroCostSafe === true,
    liveEvidenceRequired: !layers.live.ready,
    secretsIncluded: false,
  };
}

async function readJson(path) {
  try {
    return JSON.parse(await readFile(path, "utf8"));
  } catch {
    return null;
  }
}

async function main() {
  const [code, doctor, ledger] = await Promise.all([
    readJson(CODE_PATH),
    readJson(DOCTOR_PATH),
    readJson(LEDGER_PATH),
  ]);
  const result = evaluateProductionCertification({ code, doctor, ledger });
  await writeFile(OUTPUT, `${JSON.stringify(result, null, 2)}\n`, "utf8");
  console.log(JSON.stringify(result, null, 2));
  if (process.argv.includes("--require-certified") && !result.productionCertified) process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await main();
