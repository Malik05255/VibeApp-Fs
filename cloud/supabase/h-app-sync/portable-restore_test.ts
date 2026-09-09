import { buildPortableSnapshot } from "./portable-snapshot.ts";
import {
  PortableRestoreValidationError,
  validatePortableRestoreSnapshot,
} from "./portable-restore.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

async function validSnapshot() {
  return await buildPortableSnapshot({
    memories: [{
      id: "11111111-1111-4111-8111-111111111111",
      category: "idea",
      body: "فكرة مشروع قابلة للنقل",
      original_text: "احفظ فكرة مشروع قابلة للنقل",
      created_at: "2026-09-01T00:00:00Z",
      updated_at: "2026-09-02T00:00:00Z",
    }],
    tasks: [{
      id: "7",
      title: "مهمة قابلة للنقل",
      body: "نفذ المهمة",
      task_type: "general",
      priority: "medium",
      status: "active",
      due_at: "2026-09-12T06:00:00Z",
      paused_at: null,
      completed_at: null,
      cancelled_at: null,
      created_at: "2026-09-03T00:00:00Z",
      updated_at: "2026-09-03T01:00:00Z",
    }],
    reminders: [{
      id: "22222222-2222-4222-8222-222222222222",
      title: "تذكير مرتبط",
      body: "اتصل",
      original_text: "ذكرني أتصل",
      interpreted_text: "اتصل",
      due_at: "2026-09-12T06:00:00Z",
      status: "pending",
      priority_class: "medium",
      task_id: "7",
      reminder_type: "TIME",
      lifecycle_status: "ACTIVE",
      domain: "PERSONAL",
      recurrence_rule: null,
      person_name: null,
      location: null,
      cooldown_until: null,
      completed_at: null,
      delivery_channel: "app",
      created_at: "2026-09-04T00:00:00Z",
      updated_at: "2026-09-04T01:00:00Z",
    }],
    contacts: [{
      id: "33333333-3333-4333-8333-333333333333",
      name_key: "محمد",
      display_name: "محمد",
      target_wa_id: "966551234567",
      created_at: "2026-09-05T00:00:00Z",
      updated_at: "2026-09-05T01:00:00Z",
    }],
    learningState: {
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
      interest_tags: { android: 3, cloud: 2 },
      updated_at: "2026-09-09T00:00:00Z",
    },
  }, new Date("2026-09-09T13:30:00Z"));
}

async function rejectionCode(promise: Promise<unknown>): Promise<string> {
  try {
    await promise;
  } catch (error) {
    assert(error instanceof PortableRestoreValidationError, `unexpected error: ${String(error)}`);
    return error.code;
  }
  throw new Error("expected restore validation to reject");
}

Deno.test("portable restore validates canonical H v2 state including named contacts", async () => {
  const snapshot = await validSnapshot();
  const plan = await validatePortableRestoreSnapshot(snapshot);

  assert(plan.schemaVersion === 2);
  assert(plan.digest === snapshot.payloadIntegrity.digest);
  assert(plan.counts.memories === 1);
  assert(plan.counts.tasks === 1);
  assert(plan.counts.reminders === 1);
  assert(plan.counts.contacts === 1);
  assert(plan.counts.learningState === 1);
  assert(plan.payload.assistantIdentity === "H");
  assert(plan.payload.scope === "portable_core_v2");
  assert(plan.payload.tasks[0].id === "7");
  assert(plan.payload.reminders[0].taskId === "7");
  assert(plan.payload.contacts[0].nameKey === "محمد");
  assert(plan.payload.contacts[0].targetWaId === "966551234567");

  const serialized = JSON.stringify(plan);
  for (const forbidden of [
    "provider_credentials",
    "runtime_secrets",
    "google_link_identity",
    "whatsapp_routing_identity",
    "execution_plan",
    "metadata",
    "conversation_id",
  ]) {
    assert(!serialized.includes(forbidden), `restore plan leaked excluded implementation field: ${forbidden}`);
  }
});

Deno.test("portable restore keeps schema v1 snapshots backward compatible", async () => {
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
  }, new Date("2026-09-09T13:30:00Z"), 1);

  const plan = await validatePortableRestoreSnapshot(snapshot);
  assert(plan.schemaVersion === 1);
  assert(plan.payload.scope === "portable_core_v1");
  assert(plan.counts.contacts === 0);
  assert(plan.payload.contacts.length === 0);
});

Deno.test("portable restore rejects payload tampering before sanitization", async () => {
  const snapshot = await validSnapshot();
  snapshot.payload.memories[0].body = "tampered";
  const code = await rejectionCode(validatePortableRestoreSnapshot(snapshot));
  assert(code === "portable_snapshot_integrity_failed");
});

Deno.test("portable restore rejects orphan reminder task references", async () => {
  const snapshot = await validSnapshot();
  const learningState = snapshot.payload.learningState;
  assert(learningState, "fixture must contain aggregate learning state");
  snapshot.payload.reminders[0].taskId = "99";
  const contacts = (snapshot.payload as any).contacts;
  const rebuilt = await buildPortableSnapshot({
    memories: snapshot.payload.memories.map((row: any) => ({
      id: row.id,
      category: row.category,
      body: row.body,
      original_text: row.originalText,
      created_at: row.createdAt,
      updated_at: row.updatedAt,
    })),
    tasks: snapshot.payload.tasks.map((row: any) => ({
      id: row.id,
      title: row.title,
      body: row.body,
      task_type: row.taskType,
      priority: row.priority,
      status: row.status,
      due_at: row.dueAt,
      paused_at: row.pausedAt,
      completed_at: row.completedAt,
      cancelled_at: row.cancelledAt,
      created_at: row.createdAt,
      updated_at: row.updatedAt,
    })),
    reminders: snapshot.payload.reminders.map((row: any) => ({
      id: row.id,
      title: row.title,
      body: row.body,
      original_text: row.originalText,
      interpreted_text: row.interpretedText,
      due_at: row.dueAt,
      status: row.status,
      priority_class: row.priorityClass,
      task_id: row.taskId,
      reminder_type: row.reminderType,
      lifecycle_status: row.lifecycleStatus,
      domain: row.domain,
      recurrence_rule: row.recurrenceRule,
      person_name: row.personName,
      location: row.location,
      cooldown_until: row.cooldownUntil,
      completed_at: row.completedAt,
      delivery_channel: row.deliveryChannel,
      created_at: row.createdAt,
      updated_at: row.updatedAt,
    })),
    contacts: contacts.map((row: any) => ({
      id: row.id,
      name_key: row.nameKey,
      display_name: row.displayName,
      target_wa_id: row.targetWaId,
      created_at: row.createdAt,
      updated_at: row.updatedAt,
    })),
    learningState: {
      first_met_at: learningState.firstMetAt,
      last_interaction_at: learningState.lastInteractionAt,
      turn_count: learningState.turnCount,
      directness_score: learningState.directnessScore,
      technical_depth_score: learningState.technicalDepthScore,
      programming_interest_score: learningState.programmingInterestScore,
      solution_breadth_score: learningState.solutionBreadthScore,
      arabic_preference_score: learningState.arabicPreferenceScore,
      concise_preference_score: learningState.concisePreferenceScore,
      code_replacement_preference_score: learningState.codeReplacementPreferenceScore,
      interaction_samples: learningState.interactionSamples,
      interest_tags: learningState.interestTags,
      updated_at: learningState.updatedAt,
    },
  }, new Date("2026-09-09T13:30:00Z"));

  const code = await rejectionCode(validatePortableRestoreSnapshot(rebuilt));
  assert(code === "portable_snapshot_orphan_reminder_task");
});

Deno.test("portable restore rejects duplicate source task ids", async () => {
  const snapshot = await validSnapshot();
  const first = snapshot.payload.tasks[0];
  const duplicateTask = {
    id: first.id,
    title: "مهمة ثانية",
    body: "مهمة مختلفة",
    task_type: "general",
    priority: "simple",
    status: "active",
    created_at: "2026-09-05T00:00:00Z",
    updated_at: "2026-09-05T00:00:00Z",
  };
  const rebuilt = await buildPortableSnapshot({
    memories: [],
    tasks: [
      {
        id: first.id,
        title: first.title,
        body: first.body,
        task_type: first.taskType,
        priority: first.priority,
        status: first.status,
        due_at: first.dueAt,
        created_at: first.createdAt,
        updated_at: first.updatedAt,
      },
      duplicateTask,
    ],
    reminders: [],
    contacts: [],
    learningState: null,
  }, new Date("2026-09-09T13:30:00Z"));

  const code = await rejectionCode(validatePortableRestoreSnapshot(rebuilt));
  assert(code === "portable_snapshot_duplicate_task_id");
});

Deno.test("portable restore rejects duplicate normalized contact names", async () => {
  const rebuilt = await buildPortableSnapshot({
    memories: [],
    tasks: [],
    reminders: [],
    contacts: [
      {
        id: "33333333-3333-4333-8333-333333333333",
        name_key: "محمد",
        display_name: "محمد",
        target_wa_id: "966551234567",
      },
      {
        id: "44444444-4444-4444-8444-444444444444",
        name_key: "محمد",
        display_name: "محمد",
        target_wa_id: "966551234568",
      },
    ],
    learningState: null,
  }, new Date("2026-09-09T13:30:00Z"));

  const code = await rejectionCode(validatePortableRestoreSnapshot(rebuilt));
  assert(code === "portable_snapshot_duplicate_contact_name");
});

Deno.test("portable restore rejects non-canonical contact destinations", async () => {
  const rebuilt = await buildPortableSnapshot({
    memories: [],
    tasks: [],
    reminders: [],
    contacts: [{
      id: "33333333-3333-4333-8333-333333333333",
      name_key: "محمد",
      display_name: "محمد",
      target_wa_id: "+966 55 123 4567",
    }],
    learningState: null,
  }, new Date("2026-09-09T13:30:00Z"));

  const code = await rejectionCode(validatePortableRestoreSnapshot(rebuilt));
  assert(code === "portable_snapshot_contact_invalid");
});

Deno.test("portable restore rejects envelope count mismatch even with valid payload digest", async () => {
  const snapshot = await validSnapshot();
  snapshot.counts.tasks = 999;
  const code = await rejectionCode(validatePortableRestoreSnapshot(snapshot));
  assert(code === "portable_snapshot_counts_mismatch");
});

Deno.test("portable restore rejects learning tags outside H aggregate schema", async () => {
  const snapshot = await buildPortableSnapshot({
    memories: [],
    tasks: [],
    reminders: [],
    contacts: [],
    learningState: {
      first_met_at: "2026-08-01T00:00:00Z",
      last_interaction_at: "2026-09-09T00:00:00Z",
      turn_count: 1,
      interest_tags: { "raw-secret-tag": 1 },
    },
  }, new Date("2026-09-09T13:30:00Z"));

  const code = await rejectionCode(validatePortableRestoreSnapshot(snapshot));
  assert(code === "portable_snapshot_learning_invalid");
});
