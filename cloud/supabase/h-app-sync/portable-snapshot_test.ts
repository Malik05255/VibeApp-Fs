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

Deno.test("portable snapshot v2 exports selected H-owned core state and named contacts", async () => {
  const snapshot = await buildPortableSnapshot({
    memories: [{
      id: "memory-1",
      user_key: "internal-user-key",
      category: "idea",
      body: "فكرة مشروع",
      original_text: "احفظ فكرة مشروع",
      created_at: "2026-09-01T00:00:00Z",
      updated_at: "2026-09-02T00:00:00Z",
      secret_ciphertext: "must-not-export",
    }],
    tasks: [{
      id: "task-1",
      user_key: "internal-user-key",
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
      user_key: "internal-user-key",
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
    contacts: [{
      id: "33333333-3333-4333-8333-333333333333",
      user_key: "internal-user-key",
      name_key: "محمد",
      display_name: "محمد",
      target_wa_id: "966551234567",
      created_at: "2026-09-05T00:00:00Z",
      updated_at: "2026-09-05T01:00:00Z",
    }],
    learningState: {
      user_key: "internal-user-key",
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
  assert(snapshot.schemaVersion === 2);
  assert(snapshot.completeForSchemaVersion === true);
  assert(snapshot.restoreSupported === true);
  assert(snapshot.counts.memories === 1);
  assert(snapshot.counts.tasks === 1);
  assert(snapshot.counts.reminders === 1);
  assert("contacts" in snapshot.counts && snapshot.counts.contacts === 1);
  assert(snapshot.counts.learningState === 1);
  assert((snapshot.payload as any).scope === "portable_core_v2");
  assert((snapshot.payload as any).contacts[0].displayName === "محمد");
  assert((snapshot.payload as any).contacts[0].targetWaId === "966551234567");
  assert(await verifyPortableSnapshotIntegrity(snapshot));

  const serialized = JSON.stringify(snapshot);
  for (const forbidden of [
    "internal-user-key",
    "must-not-export",
    "internal-conversation",
    "hidden-provider",
    "raw-provider-prompt",
    "provider-internal-error",
    "secret_ciphertext",
    "execution_plan",
    "whatsapp_routing_identity\":{",
  ]) {
    assert(!serialized.includes(forbidden), `snapshot leaked excluded field/value: ${forbidden}`);
  }
  assert(serialized.includes("فكرة مشروع"));
  assert(serialized.includes("Riyadh"));
  assert(serialized.includes("966551234567"), "saved contact destination must remain portable owner data");
  assert(!snapshot.excludedByDesign.includes("contacts_v1_pending"));
  assert(snapshot.excludedByDesign.includes("files_v2_pending"));
});

Deno.test("portable snapshot digest is deterministic for equivalent v2 payloads", async () => {
  const first = await buildPortableSnapshot({
    memories: [
      { id: "b", body: "B", created_at: "2026-01-02T00:00:00Z" },
      { id: "a", body: "A", created_at: "2026-01-01T00:00:00Z" },
    ],
    tasks: [],
    reminders: [],
    contacts: [
      { id: "c2", name_key: "ب", display_name: "ب", target_wa_id: "966500000002", created_at: "2026-01-02T00:00:00Z" },
      { id: "c1", name_key: "ا", display_name: "ا", target_wa_id: "966500000001", created_at: "2026-01-01T00:00:00Z" },
    ],
    learningState: { interest_tags: { z: 1, a: 2 } },
  }, new Date("2026-01-01T00:00:00Z"));

  const second = await buildPortableSnapshot({
    memories: [
      { id: "a", body: "A", created_at: "2026-01-01T00:00:00Z" },
      { id: "b", body: "B", created_at: "2026-01-02T00:00:00Z" },
    ],
    tasks: [],
    reminders: [],
    contacts: [
      { id: "c1", name_key: "ا", display_name: "ا", target_wa_id: "966500000001", created_at: "2026-01-01T00:00:00Z" },
      { id: "c2", name_key: "ب", display_name: "ب", target_wa_id: "966500000002", created_at: "2026-01-02T00:00:00Z" },
    ],
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
    contacts: [],
    learningState: null,
  }, generatedAt);
  assert(await verifyPortableSnapshotIntegrity(snapshot));

  snapshot.payload.memories[0].body = "tampered";
  assert(!(await verifyPortableSnapshotIntegrity(snapshot)));
});

Deno.test("portable snapshot refuses silently truncated contact sections", async () => {
  const contacts = Array.from({ length: PORTABLE_SNAPSHOT_MAX_ROWS + 1 }, (_, index) => ({
    id: `c-${index}`,
    name_key: `contact-${index}`,
    display_name: `Contact ${index}`,
    target_wa_id: `96655${String(index).padStart(7, "0")}`,
    created_at: "2026-01-01T00:00:00Z",
  }));

  let error: unknown = null;
  try {
    await buildPortableSnapshot({ memories: [], tasks: [], reminders: [], contacts, learningState: null }, generatedAt);
  } catch (caught) {
    error = caught;
  }
  assert(error instanceof PortableSnapshotLimitError);
  assert(error.section === "contacts");
  assert(error.limit === PORTABLE_SNAPSHOT_MAX_ROWS);
});

Deno.test("portable schema v1 snapshots remain verifiable for backward restore", async () => {
  const snapshot = await buildPortableSnapshot({
    memories: [],
    tasks: [],
    reminders: [],
    contacts: [{
      id: "33333333-3333-4333-8333-333333333333",
      name_key: "محمد",
      display_name: "محمد",
      target_wa_id: "966551234567",
    }],
    learningState: null,
  }, generatedAt, 1);

  assert(snapshot.schemaVersion === 1);
  assert((snapshot.payload as any).scope === "portable_core_v1");
  assert(!("contacts" in snapshot.payload), "v1 payload contract must remain unchanged");
  assert(!("contacts" in snapshot.counts), "v1 envelope counts must remain unchanged");
  assert(snapshot.excludedByDesign.includes("contacts_v1_pending"));
  assert(await verifyPortableSnapshotIntegrity(snapshot));
});
