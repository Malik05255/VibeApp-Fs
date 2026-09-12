import test from "node:test";
import assert from "node:assert/strict";
import { diagnoseExternalReadiness } from "./h-external-readiness-doctor.mjs";

function configuredEnv() {
  return {
    H_BACKUP_SUPABASE_URL: "https://backup.supabase.co",
    H_BACKUP_SUPABASE_SERVICE_ROLE_KEY: "b".repeat(64),
    H_PRIMARY_SUPABASE_URL: "https://primary.supabase.co",
    H_PRIMARY_SUPABASE_SERVICE_ROLE_KEY: "p".repeat(64),
    H_BACKUP_RUNNER_URL: "https://primary.supabase.co/functions/v1/h-backup-runner",
    H_RUNTIME_SECRET: "runtime-secret",
    H_STANDBY_HEALTH_URL: "https://standby.supabase.co/functions/v1/h-standby-health",
    H_PRIMARY_HEALTH_URL: "https://primary.supabase.co/functions/v1/h-health",
    WHATSAPP_ACCESS_TOKEN: "meta-token",
    META_GRAPH_VERSION: "v24.0",
    H_SUPABASE_VOICE_URL: "https://primary.supabase.co/functions/v1/h-whatsapp-inbox",
    TRANSCRIPTION_API_KEY: "stt-key",
  };
}

test("fully configured environment is configuration-ready but never self-declares live-ready", () => {
  const result = diagnoseExternalReadiness(configuredEnv());
  assert.equal(result.configurationReady, true);
  assert.equal(result.liveReady, false);
  assert.equal(result.liveEvidenceStillRequired, true);
  assert.equal(result.zeroCostSafe, true);
  assert.equal(result.createsExternalResources, false);
  assert.equal(result.performsPurchases, false);
  assert.equal(result.distinctBackupProject, true);
  assert.deepEqual(result.invalid, []);
  for (const gate of Object.values(result.gates)) assert.equal(gate.configurationReady, true);
});

test("empty environment reports exact missing configuration without secret values", () => {
  const result = diagnoseExternalReadiness({});
  assert.equal(result.configurationReady, false);
  assert.ok(result.gates.backup.missing.includes("H_BACKUP_SUPABASE_URL"));
  assert.ok(result.gates.whatsappVoice.missing.includes("TRANSCRIPTION_API_KEY_or_GROQ_API_KEY"));
  const text = JSON.stringify(result);
  assert.equal(text.includes("runtime-secret"), false);
  assert.equal(result.secretsIncluded, false);
});

test("Groq key satisfies the STT credential alternative", () => {
  const env = configuredEnv();
  delete env.TRANSCRIPTION_API_KEY;
  env.GROQ_API_KEY = "groq-key";
  const result = diagnoseExternalReadiness(env);
  assert.equal(result.gates.whatsappVoice.missing.includes("TRANSCRIPTION_API_KEY_or_GROQ_API_KEY"), false);
  assert.equal(result.gates.whatsappVoice.configurationReady, true);
});

test("backup project may not be the primary project", () => {
  const env = configuredEnv();
  env.H_BACKUP_SUPABASE_URL = env.H_PRIMARY_SUPABASE_URL;
  const result = diagnoseExternalReadiness(env);
  assert.equal(result.configurationReady, false);
  assert.equal(result.distinctBackupProject, false);
  assert.ok(result.invalid.includes("BACKUP_PROJECT_MUST_DIFFER_FROM_PRIMARY"));
  assert.equal(result.gates.backup.configurationReady, false);
});

test("invalid URL or short service-role configuration fails closed", () => {
  const env = configuredEnv();
  env.H_STANDBY_HEALTH_URL = "http://standby.example/health";
  env.H_BACKUP_SUPABASE_SERVICE_ROLE_KEY = "short";
  const result = diagnoseExternalReadiness(env);
  assert.equal(result.configurationReady, false);
  assert.ok(result.invalid.includes("H_STANDBY_HEALTH_URL"));
  assert.ok(result.invalid.includes("H_BACKUP_SUPABASE_SERVICE_ROLE_KEY"));
  assert.equal(result.gates.standbyPreflight.configurationReady, false);
  assert.equal(result.gates.backup.configurationReady, false);
});
