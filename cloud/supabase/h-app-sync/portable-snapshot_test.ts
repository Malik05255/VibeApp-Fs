import {
  buildPortableSnapshot,
  PortableSnapshotLimitError,
  PORTABLE_SNAPSHOT_MAX_ROWS,
  verifyPortableSnapshotIntegrity,
} from "./portable-snapshot.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

const generatedAt = new Date("2026-09-09T13:30:00Z");

Deno.test("portable snapshot exports only selected H-owned core state", async () => {
  const snapshot = await buildPortableSnapshot({
    memories: [{
      id: "memory-1",
      user_key: "966500000000",
      category: "idea",
      body: "فكرة مشروع",
      original_text: "احفظ فكرة مشروع",
      created_at: "2026-09-01T00:00:00Z",
      updated_at: "2026-09-02T00:00:00Z",
      secret_ciphertext: "must-not-export",
    }],
    tasks: [{
      id: "task-1",
      user_key: "966500000000",
      conversation_id: "internal-conversation",
      title: "مهمة",
      body: "نفذ المهمة",
      task_type: "personal",
      priority: "normal",
      status: "active",
      execution_plan: { provider: "hidden-provider" },
      metadata: { prompt: "raw-provider-prompt" },
      created_at: "2026-09-03T00:00:00Z",
      updated_at: "2026-09-03T01:00:00Z",
    }],
    reminders: [{
      id: "reminder-1",
      user_key: "966500000000",
      conversation_id: "internal-conversation",
      title: "تذكير",
      body: "اتصل",
      original_text: "ذكرني أتصل",
      interpreted_text: "اتصل",
      due_at: "2026-09-10T06:00:00Z",
      status: "pending",
      priority_class: "normal",
      reminder_type: "time",
      lifecycle_status: "active",
      domain: "personal",
      location: { city: "Riyadh", lat: 24.7 },
      delivery_channel: "app",
      last_error: "provider-internal-error",
      created_at: "2026-09-04T00:00:00Z",
      updated_at: "2026-09-04T01:00:00Z",
    }],
    learningState: {
      user_key: "966500000000",
      first_met_at: "2026-08-01T00:00:00Z",
      last_interaction_at: "2026-09-09T00:00:00Z",
      turn_count: 20,
      directness_score: 12,
      technical_depth_score: 15,
      programming_interest_score: 10,
      solution_breadth_score: 8,
      arabic_preference_score: 18,
      concise_preference_score: 11,
      code_replacement_preference_score: 9,
      interaction_samples: 20,
      interest_tags: { android: 3, h: 5 },
      updated_at: "2026-09-09T00:00:00Z",
    },
  }, generatedAt);

  assert(snapshot.format === "h-portable-snapshot");
  assert(snapshot.schemaVersion === 1);
  assert(snapshot.completeForSchemaVersion === true);
  assert(snapshot.restoreSupported === true);
  assert(snapshot.counts.memories === 1);
  assert(snapshot.counts.tasks === 1);
  assert(snapshot.counts.reminders === 1);
  assert(snapshot.counts.learningState === 1);
  assert(await verifyPortableSnapshotIntegrity(snapshot));

  const serialized = JSON.stringify(snapshot);
  for (const forbidden of [
    "966500000000",
    "must-not-export",
    "internal-conversation",
    "hidden-provider",
    "raw-provider-prompt",
    "provider-internal-error",
    "secret_ciphertext",
    "execution_plan",
  ]) {
    assert(!serialized.includes(forbidden), `snapshot leaked excluded field/value: ${forbidden}`);
  }
  assert(serialized.includes("فكرة مشروع"));
  assert(serialized.includes("Riyadh"));
});

Deno.test("portable snapshot digest is deterministic for equivalent payloads", async () => {
  const first = await buildPortableSnapshot({
    memories: [
      { id: "b", body: "B", created_at: "2026-01-02T00:00:00Z" },
      { id: "a", body: "A", created_at: "2026-01-01T00:00:00Z" },
    ],
    tasks: [],
    reminders: [],
    learningState: { interest_tags: { z: 1, a: 2 } },
  }, new Date("2026-01-01T00:00:00Z"));

  const second = await buildPortableSnapshot({
    memories: [
      { id: "a", body: "A", created_at: "2026-01-01T00:00:00Z" },
      { id: "b", body: "B", created_at: "2026-01-02T00:00:00Z" },
    ],
    tasks: [],
    reminders: [],
    learningState: { interest_tags: { a: 2, z: 1 } },
  }, new Date("2030-01-01T00:00:00Z"));

  assert(first.payloadIntegrity.digest === second.payloadIntegrity.digest);
  assert(first.generatedAt !== second.generatedAt, "generation time is envelope metadata, not payload integrity");
});

Deno.test("portable snapshot integrity detects payload changes", async () => {
  const snapshot = await buildPortableSnapshot({
    memories: [{ id: "m1", body: "original", created_at: "2026-01-01T00:00:00Z" }],
    tasks: [],
    reminders: [],
    learningState: null,
  }, generatedAt);
  assert(await verifyPortableSnapshotIntegrity(snapshot));

  snapshot.payload.memories[0].body = "tampered";
  assert(!(await verifyPortableSnapshotIntegrity(snapshot)));
});

Deno.test("portable snapshot refuses silently truncated sections", async () => {
  const memories = Array.from({ length: PORTABLE_SNAPSHOT_MAX_ROWS + 1 }, (_, index) => ({
    id: `m-${index}`,
    body: `memory-${index}`,
    created_at: "2026-01-01T00:00:00Z",
  }));

  let error: unknown = null;
  try {
    await buildPortableSnapshot({ memories, tasks: [], reminders: [], learningState: null }, generatedAt);
  } catch (caught) {
    error = caught;
  }
  assert(error instanceof PortableSnapshotLimitError);
  assert(error.section === "memories");
  assert(error.limit === PORTABLE_SNAPSHOT_MAX_ROWS);
});
