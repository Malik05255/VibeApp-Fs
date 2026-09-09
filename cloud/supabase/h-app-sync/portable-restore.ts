import { normalizeContactKey, normalizeWaIdCandidate } from "../h-whatsapp-inbox/contact-manager.ts";
import { H_LEARNING_ALLOWED_TAGS } from "./learning-policy.ts";
import {
  PORTABLE_SNAPSHOT_MAX_ROWS,
  verifyPortableSnapshotIntegrity,
} from "./portable-snapshot.ts";

const FORMAT = "h-portable-snapshot";
const SUPPORTED_SCHEMA_VERSIONS = new Set([1, 2]);
const BIGINT_MAX = 9_223_372_036_854_775_807n;
const MEMORY_CATEGORIES = new Set(["identity", "preference", "relationship", "idea", "note", "general"]);
const TASK_PRIORITIES = new Set(["simple", "medium", "important"]);
const TASK_STATUSES = new Set(["active", "paused", "completed", "cancelled"]);
const REMINDER_STATUSES = new Set(["pending", "paused", "sent", "waiting_template", "cancelled", "failed"]);
const REMINDER_PRIORITIES = new Set(["simple", "medium", "important"]);
const REMINDER_TYPES = new Set(["TIME", "LOCATION", "PERSON", "RECURRING", "CONTEXTUAL"]);
const LIFECYCLE_STATUSES = new Set(["ACTIVE", "DEFERRED", "COMPLETED", "DISABLED", "CANCELLED"]);
const DOMAINS = new Set(["PERSONAL", "PROGRAMMING"]);
const DELIVERY_CHANNELS = new Set(["app", "whatsapp"]);

export type PortableRestorePlan = {
  digest: string;
  schemaVersion: 1 | 2;
  counts: {
    memories: number;
    tasks: number;
    reminders: number;
    contacts: number;
    learningState: number;
  };
  payload: {
    assistantIdentity: "H";
    scope: "portable_core_v1" | "portable_core_v2";
    memories: Record<string, unknown>[];
    tasks: Record<string, unknown>[];
    reminders: Record<string, unknown>[];
    contacts: Record<string, unknown>[];
    learningState: Record<string, unknown> | null;
  };
};

export class PortableRestoreValidationError extends Error {
  readonly code: string;

  constructor(code: string) {
    super(code);
    this.name = "PortableRestoreValidationError";
    this.code = code;
  }
}

export function isPortableRestoreValidationError(error: unknown): error is PortableRestoreValidationError {
  return error instanceof PortableRestoreValidationError;
}

/**
 * Strictly validates supported portable snapshots before any database write.
 * Integrity is checked against the original payload first; only then do we construct
 * a bounded allow-listed payload for the atomic restore RPC. Unknown provider/routing
 * fields therefore cannot be smuggled into H-owned state through restore.
 *
 * Schema v1 is retained for backward-compatible restores. Schema v2 adds only the
 * owner's saved named contacts; H owner/friend routing identities and credentials remain
 * excluded from the portable payload.
 */
export async function validatePortableRestoreSnapshot(snapshot: unknown): Promise<PortableRestorePlan> {
  const root = record(snapshot, "portable_snapshot_invalid");
  const schemaVersion = Number(root.schemaVersion);
  if (root.format !== FORMAT || !Number.isInteger(schemaVersion) || !SUPPORTED_SCHEMA_VERSIONS.has(schemaVersion)) {
    fail("portable_snapshot_schema_unsupported");
  }
  if (root.completeForSchemaVersion !== true) fail("portable_snapshot_incomplete");
  if (!validTimestamp(root.generatedAt)) fail("portable_snapshot_generated_at_invalid");
  if (!await verifyPortableSnapshotIntegrity(root)) fail("portable_snapshot_integrity_failed");

  const digest = stringValue(record(root.payloadIntegrity, "portable_snapshot_integrity_invalid").digest, 64, 64)
    .toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(digest)) fail("portable_snapshot_integrity_invalid");

  const payload = record(root.payload, "portable_snapshot_payload_invalid");
  const expectedScope = schemaVersion === 1 ? "portable_core_v1" : "portable_core_v2";
  if (payload.assistantIdentity !== "H" || payload.scope !== expectedScope) {
    fail("portable_snapshot_identity_mismatch");
  }

  const rawMemories = arrayValue(payload.memories, "memories");
  const rawTasks = arrayValue(payload.tasks, "tasks");
  const rawReminders = arrayValue(payload.reminders, "reminders");
  const rawContacts = schemaVersion >= 2 ? arrayValue(payload.contacts, "contacts") : [];
  enforceBound("memories", rawMemories);
  enforceBound("tasks", rawTasks);
  enforceBound("reminders", rawReminders);
  enforceBound("contacts", rawContacts);

  const memories = rawMemories.map(validateMemory);
  const tasks = rawTasks.map(validateTask);
  const reminders = rawReminders.map(validateReminder);
  const contacts = rawContacts.map(validateContact);
  const learningState = payload.learningState == null ? null : validateLearningState(payload.learningState);

  assertUniqueIds("memory", memories);
  assertUniqueIds("task", tasks);
  assertUniqueIds("reminder", reminders);
  assertUniqueIds("contact", contacts);
  assertUniqueContactNames(contacts);

  const taskIds = new Set(tasks.map((task) => String(task.id)));
  for (const reminder of reminders) {
    const taskId = reminder.taskId;
    if (taskId != null && !taskIds.has(String(taskId))) {
      fail("portable_snapshot_orphan_reminder_task");
    }
  }

  const counts = record(root.counts, "portable_snapshot_counts_invalid");
  const expectedEnvelopeCounts: Record<string, number> = {
    memories: memories.length,
    tasks: tasks.length,
    reminders: reminders.length,
    learningState: learningState ? 1 : 0,
  };
  if (schemaVersion >= 2) expectedEnvelopeCounts.contacts = contacts.length;
  for (const [key, expected] of Object.entries(expectedEnvelopeCounts)) {
    if (Number(counts[key]) !== expected) fail("portable_snapshot_counts_mismatch");
  }

  const version = schemaVersion as 1 | 2;
  return {
    digest,
    schemaVersion: version,
    counts: {
      memories: memories.length,
      tasks: tasks.length,
      reminders: reminders.length,
      contacts: contacts.length,
      learningState: learningState ? 1 : 0,
    },
    payload: {
      assistantIdentity: "H",
      scope: version === 1 ? "portable_core_v1" : "portable_core_v2",
      memories,
      tasks,
      reminders,
      contacts,
      learningState,
    },
  };
}

function validateMemory(value: unknown) {
  const row = record(value, "portable_snapshot_memory_invalid");
  const category = enumValue(row.category, MEMORY_CATEGORIES, "portable_snapshot_memory_invalid");
  const body = requiredText(row.body, 280, "portable_snapshot_memory_invalid");
  return {
    id: uuidValue(row.id, "portable_snapshot_memory_invalid"),
    category,
    body,
    originalText: optionalText(row.originalText, 500, "portable_snapshot_memory_invalid"),
    createdAt: timestampOrNull(row.createdAt, "portable_snapshot_memory_invalid"),
    updatedAt: timestampOrNull(row.updatedAt, "portable_snapshot_memory_invalid"),
  };
}

function validateTask(value: unknown) {
  const row = record(value, "portable_snapshot_task_invalid");
  return {
    id: bigintId(row.id, "portable_snapshot_task_invalid"),
    title: optionalText(row.title, 160, "portable_snapshot_task_invalid"),
    body: requiredText(row.body, 12_000, "portable_snapshot_task_invalid"),
    taskType: requiredText(row.taskType, 80, "portable_snapshot_task_invalid"),
    priority: enumValue(row.priority, TASK_PRIORITIES, "portable_snapshot_task_invalid"),
    status: enumValue(row.status, TASK_STATUSES, "portable_snapshot_task_invalid"),
    dueAt: timestampOrNull(row.dueAt, "portable_snapshot_task_invalid"),
    pausedAt: timestampOrNull(row.pausedAt, "portable_snapshot_task_invalid"),
    completedAt: timestampOrNull(row.completedAt, "portable_snapshot_task_invalid"),
    cancelledAt: timestampOrNull(row.cancelledAt, "portable_snapshot_task_invalid"),
    createdAt: timestampOrNull(row.createdAt, "portable_snapshot_task_invalid"),
    updatedAt: timestampOrNull(row.updatedAt, "portable_snapshot_task_invalid"),
  };
}

function validateReminder(value: unknown) {
  const row = record(value, "portable_snapshot_reminder_invalid");
  return {
    id: uuidValue(row.id, "portable_snapshot_reminder_invalid"),
    title: optionalText(row.title, 160, "portable_snapshot_reminder_invalid"),
    body: requiredText(row.body, 12_000, "portable_snapshot_reminder_invalid"),
    originalText: optionalText(row.originalText, 12_000, "portable_snapshot_reminder_invalid"),
    interpretedText: optionalText(row.interpretedText, 12_000, "portable_snapshot_reminder_invalid"),
    dueAt: timestampOrNull(row.dueAt, "portable_snapshot_reminder_invalid"),
    status: enumValue(row.status, REMINDER_STATUSES, "portable_snapshot_reminder_invalid"),
    priorityClass: enumValue(row.priorityClass, REMINDER_PRIORITIES, "portable_snapshot_reminder_invalid"),
    taskId: row.taskId == null ? null : bigintId(row.taskId, "portable_snapshot_reminder_invalid"),
    reminderType: enumValue(row.reminderType, REMINDER_TYPES, "portable_snapshot_reminder_invalid"),
    lifecycleStatus: enumValue(row.lifecycleStatus, LIFECYCLE_STATUSES, "portable_snapshot_reminder_invalid"),
    domain: enumValue(row.domain, DOMAINS, "portable_snapshot_reminder_invalid"),
    recurrenceRule: optionalText(row.recurrenceRule, 2_000, "portable_snapshot_reminder_invalid"),
    personName: optionalText(row.personName, 240, "portable_snapshot_reminder_invalid"),
    location: safeJson(row.location, "portable_snapshot_reminder_invalid"),
    cooldownUntil: timestampOrNull(row.cooldownUntil, "portable_snapshot_reminder_invalid"),
    completedAt: timestampOrNull(row.completedAt, "portable_snapshot_reminder_invalid"),
    deliveryChannel: enumValue(row.deliveryChannel, DELIVERY_CHANNELS, "portable_snapshot_reminder_invalid"),
    createdAt: timestampOrNull(row.createdAt, "portable_snapshot_reminder_invalid"),
    updatedAt: timestampOrNull(row.updatedAt, "portable_snapshot_reminder_invalid"),
  };
}

function validateContact(value: unknown) {
  const code = "portable_snapshot_contact_invalid";
  const row = record(value, code);
  const displayName = requiredText(row.displayName, 120, code);
  const nameKey = requiredText(row.nameKey, 120, code);
  if (normalizeContactKey(displayName) !== nameKey || normalizeContactKey(nameKey) !== nameKey) fail(code);
  const targetText = requiredText(row.targetWaId, 20, code);
  const targetWaId = normalizeWaIdCandidate(targetText);
  if (!targetWaId || targetWaId !== targetText) fail(code);
  return {
    id: uuidValue(row.id, code),
    nameKey,
    displayName,
    targetWaId,
    createdAt: timestampOrNull(row.createdAt, code),
    updatedAt: timestampOrNull(row.updatedAt, code),
  };
}

function validateLearningState(value: unknown) {
  const row = record(value, "portable_snapshot_learning_invalid");
  const firstMetAt = requiredTimestamp(row.firstMetAt, "portable_snapshot_learning_invalid");
  const lastInteractionAt = requiredTimestamp(row.lastInteractionAt, "portable_snapshot_learning_invalid");
  const tagsRecord = record(row.interestTags ?? {}, "portable_snapshot_learning_invalid");
  const interestTags: Record<string, number> = {};
  for (const [rawTag, rawCount] of Object.entries(tagsRecord)) {
    const tag = rawTag.trim().toLowerCase();
    if (!H_LEARNING_ALLOWED_TAGS.has(tag)) fail("portable_snapshot_learning_invalid");
    const count = boundedInteger(rawCount, 0, 1_000_000, "portable_snapshot_learning_invalid");
    if (count > 0) interestTags[tag] = count;
  }
  return {
    firstMetAt,
    lastInteractionAt,
    turnCount: boundedInteger(row.turnCount, 0, 1_000_000_000, "portable_snapshot_learning_invalid"),
    directnessScore: boundedInteger(row.directnessScore, 0, 20, "portable_snapshot_learning_invalid"),
    technicalDepthScore: boundedInteger(row.technicalDepthScore, 0, 20, "portable_snapshot_learning_invalid"),
    programmingInterestScore: boundedInteger(row.programmingInterestScore, 0, 20, "portable_snapshot_learning_invalid"),
    solutionBreadthScore: boundedInteger(row.solutionBreadthScore, 0, 20, "portable_snapshot_learning_invalid"),
    arabicPreferenceScore: boundedInteger(row.arabicPreferenceScore, 0, 20, "portable_snapshot_learning_invalid"),
    concisePreferenceScore: boundedInteger(row.concisePreferenceScore, 0, 20, "portable_snapshot_learning_invalid"),
    codeReplacementPreferenceScore: boundedInteger(row.codeReplacementPreferenceScore, 0, 20, "portable_snapshot_learning_invalid"),
    interactionSamples: boundedInteger(row.interactionSamples, 0, 1_000_000_000, "portable_snapshot_learning_invalid"),
    interestTags,
    updatedAt: timestampOrNull(row.updatedAt, "portable_snapshot_learning_invalid"),
  };
}

function enforceBound(section: string, rows: unknown[]) {
  if (rows.length > PORTABLE_SNAPSHOT_MAX_ROWS) fail(`portable_snapshot_${section}_too_large`);
}

function assertUniqueIds(section: string, rows: Record<string, unknown>[]) {
  const ids = new Set<string>();
  for (const row of rows) {
    const id = String(row.id || "");
    if (!id || ids.has(id)) fail(`portable_snapshot_duplicate_${section}_id`);
    ids.add(id);
  }
}

function assertUniqueContactNames(rows: Record<string, unknown>[]) {
  const names = new Set<string>();
  for (const row of rows) {
    const nameKey = String(row.nameKey || "");
    if (!nameKey || names.has(nameKey)) fail("portable_snapshot_duplicate_contact_name");
    names.add(nameKey);
  }
}

function record(value: unknown, code: string): Record<string, any> {
  if (!value || typeof value !== "object" || Array.isArray(value)) fail(code);
  return value as Record<string, any>;
}

function arrayValue(value: unknown, section: string): unknown[] {
  if (!Array.isArray(value)) fail(`portable_snapshot_${section}_invalid`);
  return value;
}

function enumValue(value: unknown, allowed: Set<string>, code: string): string {
  const text = String(value ?? "");
  if (!allowed.has(text)) fail(code);
  return text;
}

function requiredText(value: unknown, max: number, code: string): string {
  if (typeof value !== "string") fail(code);
  const text = value.trim();
  if (!text || text.length > max) fail(code);
  return text;
}

function optionalText(value: unknown, max: number, code: string): string | null {
  if (value == null) return null;
  if (typeof value !== "string" || value.length > max) fail(code);
  const text = value.trim();
  return text || null;
}

function stringValue(value: unknown, min: number, max: number): string {
  if (typeof value !== "string" || value.length < min || value.length > max) fail("portable_snapshot_integrity_invalid");
  return value;
}

function uuidValue(value: unknown, code: string): string {
  const text = String(value ?? "").toLowerCase();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(text)) fail(code);
  return text;
}

function bigintId(value: unknown, code: string): string {
  const text = String(value ?? "");
  if (!/^[1-9][0-9]{0,18}$/.test(text)) fail(code);
  try {
    if (BigInt(text) > BIGINT_MAX) fail(code);
  } catch {
    fail(code);
  }
  return text;
}

function timestampOrNull(value: unknown, code: string): string | null {
  if (value == null) return null;
  if (!validTimestamp(value)) fail(code);
  return new Date(String(value)).toISOString();
}

function requiredTimestamp(value: unknown, code: string): string {
  const normalized = timestampOrNull(value, code);
  if (!normalized) fail(code);
  return normalized;
}

function validTimestamp(value: unknown): boolean {
  return typeof value === "string" && value.length <= 80 && Number.isFinite(Date.parse(value));
}

function boundedInteger(value: unknown, min: number, max: number, code: string): number {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number < min || number > max) fail(code);
  return number;
}

function safeJson(value: unknown, code: string): unknown {
  const normalized = normalizeJson(value, 0, code);
  if (JSON.stringify(normalized).length > 16_000) fail(code);
  return normalized;
}

function normalizeJson(value: unknown, depth: number, code: string): unknown {
  if (depth > 8) fail(code);
  if (value == null || typeof value === "string" || typeof value === "boolean") return value;
  if (typeof value === "number") {
    if (!Number.isFinite(value)) fail(code);
    return value;
  }
  if (Array.isArray(value)) return value.map((item) => normalizeJson(item, depth + 1, code));
  if (typeof value === "object") {
    const result: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
      if (key.length > 120) fail(code);
      result[key] = normalizeJson(item, depth + 1, code);
    }
    return result;
  }
  fail(code);
}

function fail(code: string): never {
  throw new PortableRestoreValidationError(code);
}
