import { writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const OUTPUT = process.env.H_EXTERNAL_DOCTOR_OUTPUT || "h-external-readiness-doctor.json";

const GROUPS = {
  backup: [
    "H_BACKUP_SUPABASE_URL",
    "H_BACKUP_SUPABASE_SERVICE_ROLE_KEY",
    "H_PRIMARY_SUPABASE_URL",
    "H_PRIMARY_SUPABASE_SERVICE_ROLE_KEY",
    "H_BACKUP_RUNNER_URL",
    "H_RUNTIME_SECRET",
  ],
  standbyPreflight: ["H_STANDBY_HEALTH_URL", "H_RUNTIME_SECRET"],
  failoverActive: ["H_STANDBY_HEALTH_URL", "H_RUNTIME_SECRET", "H_PRIMARY_HEALTH_URL"],
  whatsappVoice: ["WHATSAPP_ACCESS_TOKEN", "META_GRAPH_VERSION", "H_SUPABASE_VOICE_URL", "H_RUNTIME_SECRET"],
};

function present(env, name) {
  return String(env[name] || "").trim().length > 0;
}

function safeHttps(value) {
  try {
    const url = new URL(String(value || "").trim());
    return url.protocol === "https:" && !url.username && !url.password;
  } catch {
    return false;
  }
}

function supabaseHttps(value) {
  try {
    const url = new URL(String(value || "").trim());
    return url.protocol === "https:" && url.hostname.endsWith(".supabase.co") && !url.username && !url.password;
  } catch {
    return false;
  }
}

function missingFor(env, names) {
  return names.filter((name) => !present(env, name));
}

export function diagnoseExternalReadiness(env = process.env) {
  const missing = {
    backup: missingFor(env, GROUPS.backup),
    standbyPreflight: missingFor(env, GROUPS.standbyPreflight),
    failoverActive: missingFor(env, GROUPS.failoverActive),
    whatsappVoice: missingFor(env, GROUPS.whatsappVoice),
  };

  const sttReady = present(env, "TRANSCRIPTION_API_KEY") || present(env, "GROQ_API_KEY");
  if (!sttReady) missing.whatsappVoice.push("TRANSCRIPTION_API_KEY_or_GROQ_API_KEY");

  const invalid = [];
  if (present(env, "H_BACKUP_SUPABASE_URL") && !supabaseHttps(env.H_BACKUP_SUPABASE_URL)) invalid.push("H_BACKUP_SUPABASE_URL");
  if (present(env, "H_PRIMARY_SUPABASE_URL") && !supabaseHttps(env.H_PRIMARY_SUPABASE_URL)) invalid.push("H_PRIMARY_SUPABASE_URL");
  for (const name of ["H_BACKUP_RUNNER_URL", "H_STANDBY_HEALTH_URL", "H_PRIMARY_HEALTH_URL", "H_SUPABASE_VOICE_URL"]) {
    if (present(env, name) && !safeHttps(env[name])) invalid.push(name);
  }
  for (const name of ["H_BACKUP_SUPABASE_SERVICE_ROLE_KEY", "H_PRIMARY_SUPABASE_SERVICE_ROLE_KEY"]) {
    if (present(env, name) && String(env[name]).trim().length < 40) invalid.push(name);
  }

  let distinctBackupProject = null;
  if (present(env, "H_BACKUP_SUPABASE_URL") && present(env, "H_PRIMARY_SUPABASE_URL") &&
      supabaseHttps(env.H_BACKUP_SUPABASE_URL) && supabaseHttps(env.H_PRIMARY_SUPABASE_URL)) {
    distinctBackupProject = String(env.H_BACKUP_SUPABASE_URL).replace(/\/$/, "") !== String(env.H_PRIMARY_SUPABASE_URL).replace(/\/$/, "");
    if (!distinctBackupProject) invalid.push("BACKUP_PROJECT_MUST_DIFFER_FROM_PRIMARY");
  }

  const uniqueInvalid = [...new Set(invalid)];
  const gates = {
    backup: { configurationReady: missing.backup.length === 0 && !uniqueInvalid.some((x) => x.includes("BACKUP") || x.includes("PRIMARY")) && distinctBackupProject === true, missing: missing.backup },
    standbyPreflight: { configurationReady: missing.standbyPreflight.length === 0 && !uniqueInvalid.includes("H_STANDBY_HEALTH_URL"), missing: missing.standbyPreflight },
    failoverActive: { configurationReady: missing.failoverActive.length === 0 && !uniqueInvalid.includes("H_STANDBY_HEALTH_URL") && !uniqueInvalid.includes("H_PRIMARY_HEALTH_URL"), missing: missing.failoverActive },
    whatsappVoice: { configurationReady: missing.whatsappVoice.length === 0 && !uniqueInvalid.includes("H_SUPABASE_VOICE_URL"), missing: missing.whatsappVoice },
  };

  const configurationReady = Object.values(gates).every((gate) => gate.configurationReady) && uniqueInvalid.length === 0;
  return {
    schemaVersion: 1,
    configurationReady,
    liveReady: false,
    liveEvidenceStillRequired: true,
    zeroCostSafe: true,
    createsExternalResources: false,
    performsPurchases: false,
    gates,
    invalid,
    distinctBackupProject,
    voiceMediaIdIsRuntimeInput: true,
    nextWorkflow: "H Live External Evidence",
    secretsIncluded: false,
  };
}

async function main() {
  const result = diagnoseExternalReadiness();
  await writeFile(OUTPUT, `${JSON.stringify(result, null, 2)}\n`, "utf8");
  console.log(JSON.stringify(result, null, 2));
  if (process.argv.includes("--require-configured") && !result.configurationReady) process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await main();
