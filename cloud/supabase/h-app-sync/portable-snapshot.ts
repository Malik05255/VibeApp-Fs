const FORMAT = "h-portable-snapshot";
const CURRENT_SCHEMA_VERSION = 2;
const SUPPORTED_SCHEMA_VERSIONS = new Set([1, 2]);
export const PORTABLE_SNAPSHOT_MAX_ROWS = 500;

export type PortableSnapshotSchemaVersion = 1 | 2;
type DbClient = any;

type PortableSnapshotInput = {
  memories: any[];
  tasks: any[];
  reminders: any[];
  contacts?: any[];
  learningState: any | null;
};

export class PortableSnapshotLimitError extends Error {
  readonly section: string;
  readonly limit: number;

  constructor(section: string, limit = PORTABLE_SNAPSHOT_MAX_ROWS) {
    super(`portable_snapshot_limit_exceeded:${section}`);
    this.name = "PortableSnapshotLimitError";
    this.section = section;
    this.limit = limit;
  }
}

export function isPortableSnapshotLimitError(error: unknown): error is PortableSnapshotLimitError {
  return error instanceof PortableSnapshotLimitError;
}

/**
 * Read-only owner portability foundation. This exports only durable H-owned core state.
 * Provider credentials, Google/WhatsApp routing identities, conversation transcripts,
 * raw attachments, provider health and execution internals are intentionally excluded.
 *
 * Schema v2 adds owner-saved named contacts. A contact destination is user-owned durable
 * data, not H's WhatsApp owner/friend routing identity. Raw routing identities remain
 * excluded. Schema v1 remains verifiable/restorable for backward compatibility.
 *
 * Every collection is deliberately bounded. If a section exceeds the limit, the endpoint
 * fails instead of returning a silently incomplete backup.
 */
export async function createPortableSnapshot(
  db: DbClient,
  userKey: string,
  generatedAt = new Date(),
) {
  const [memories, tasks, reminders, contacts, learningState] = await Promise.all([
    db.from("h_runtime_memories")
      .select("id,category,body,original_text,created_at,updated_at")
      .eq("user_key", userKey)
      .order("created_at", { ascending: true })
      .order("id", { ascending: true })
      .limit(PORTABLE_SNAPSHOT_MAX_ROWS + 1),
    db.from("h_runtime_tasks")
      .select("id,title,body,task_type,priority,status,due_at,paused_at,completed_at,cancelled_at,created_at,updated_at")
      .eq("user_key", userKey)
      .order("created_at", { ascending: true })
      .order("id", { ascending: true })
      .limit(PORTABLE_SNAPSHOT_MAX_ROWS + 1),
    db.from("h_runtime_reminders")
      .select("id,title,body,original_text,interpreted_text,due_at,status,priority_class,task_id,reminder_type,lifecycle_status,domain,recurrence_rule,person_name,location,cooldown_until,completed_at,delivery_channel,created_at,updated_at")
      .eq("user_key", userKey)
      .order("created_at", { ascending: true })
      .order("id", { ascending: true })
      .limit(PORTABLE_SNAPSHOT_MAX_ROWS + 1),
    db.from("h_runtime_contacts")
      .select("id,name_key,display_name,target_wa_id,created_at,updated_at")
      .eq("user_key", userKey)
      .order("created_at", { ascending: true })
      .order("id", { ascending: true })
      .limit(PORTABLE_SNAPSHOT_MAX_ROWS + 1),
    db.from("h_runtime_learning_state")
      .select("first_met_at,last_interaction_at,turn_count,directness_score,technical_depth_score,programming_interest_score,solution_breadth_score,arabic_preference_score,concise_preference_score,code_replacement_preference_score,interaction_samples,interest_tags,updated_at")
      .eq("user_key", userKey)
      .maybeSingle(),
  ]);

  if (memories.error || tasks.error || reminders.error || contacts.error || learningState.error) {
    throw memories.error || tasks.error || reminders.error || contacts.error || learningState.error;
  }

  return buildPortableSnapshot({
    memories: memories.data ?? [],
    tasks: tasks.data ?? [],
    reminders: reminders.data ?? [],
    contacts: contacts.data ?? [],
    learningState: learningState.data ?? null,
  }, generatedAt, CURRENT_SCHEMA_VERSION);
}

export async function buildPortableSnapshot(
  input: PortableSnapshotInput,
  generatedAt = new Date(),
  schemaVersion: PortableSnapshotSchemaVersion = CURRENT_SCHEMA_VERSION,
) {
  if (!SUPPORTED_SCHEMA_VERSIONS.has(schemaVersion)) {
    throw new Error(`portable_snapshot_schema_unsupported:${schemaVersion}`);
  }

  enforceLimit("memories", input.memories);
  enforceLimit("tasks", input.tasks);
  enforceLimit("reminders", input.reminders);
  const contacts = input.contacts ?? [];
  if (schemaVersion >= 2) enforceLimit("contacts", contacts);

  const payload = schemaVersion === 1
    ? {
      assistantIdentity: "H",
      scope: "portable_core_v1",
      memories: stableRows(input.memories.map(portableMemory)),
      tasks: stableRows(input.tasks.map(portableTask)),
      reminders: stableRows(input.reminders.map(portableReminder)),
      learningState: portableLearningState(input.learningState),
    }
    : {
      assistantIdentity: "H",
      scope: "portable_core_v2",
      memories: stableRows(input.memories.map(portableMemory)),
      tasks: stableRows(input.tasks.map(portableTask)),
      reminders: stableRows(input.reminders.map(portableReminder)),
      contacts: stableRows(contacts.map(portableContact)),
      learningState: portableLearningState(input.learningState),
    };
  const payloadDigest = await sha256Hex(canonicalJson(payload));

  const baseCounts = {
    memories: payload.memories.length,
    tasks: payload.tasks.length,
    reminders: payload.reminders.length,
    learningState: payload.learningState ? 1 : 0,
  };
  const counts = schemaVersion === 1
    ? baseCounts
    : { ...baseCounts, contacts: (payload as any).contacts.length };

  return {
    format: FORMAT,
    schemaVersion,
    generatedAt: generatedAt.toISOString(),
    completeForSchemaVersion: true,
    restoreSupported: true,
    payloadIntegrity: {
      algorithm: "SHA-256",
      digest: payloadDigest,
      authenticityGuaranteed: false,
    },
    counts,
    excludedByDesign: schemaVersion === 1
      ? [
        "provider_credentials",
        "runtime_secrets",
        "google_link_identity",
        "whatsapp_routing_identity",
        "provider_health_and_quota_state",
        "conversation_transcripts",
        "task_execution_metadata",
        "raw_media_and_documents",
        "transient_media_derivatives",
        "contacts_v1_pending",
        "files_v1_pending",
        "cloud_destination_credentials",
      ]
      : [
        "provider_credentials",
        "runtime_secrets",
        "google_link_identity",
        "whatsapp_routing_identity",
        "provider_health_and_quota_state",
        "conversation_transcripts",
        "task_execution_metadata",
        "raw_media_and_documents",
        "transient_media_derivatives",
        "files_v2_pending",
        "cloud_destination_credentials",
      ],
    payload,
  };
}

export async function verifyPortableSnapshotIntegrity(snapshot: any): Promise<boolean> {
  const schemaVersion = Number(snapshot?.schemaVersion);
  if (!snapshot || snapshot.format !== FORMAT || !SUPPORTED_SCHEMA_VERSIONS.has(schemaVersion)) return false;
  const expected = String(snapshot?.payloadIntegrity?.digest || "").trim().toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(expected)) return false;
  const actual = await sha256Hex(canonicalJson(snapshot.payload));
  return constantTimeEqual(expected, actual);
}

function enforceLimit(section: string, rows: unknown[]) {
  if (!Array.isArray(rows) || rows.length > PORTABLE_SNAPSHOT_MAX_ROWS) {
    throw new PortableSnapshotLimitError(section);
  }
}

function portableMemory(row: any) {
  return {
    id: text(row?.id),
    category: text(row?.category),
    body: text(row?.body),
    originalText: nullableText(row?.original_text),
    createdAt: nullableText(row?.created_at),
    updatedAt: nullableText(row?.updated_at),
  };
}

function portableTask(row: any) {
  return {
    id: text(row?.id),
    title: nullableText(row?.title),
    body: nullableText(row?.body),
    taskType: nullableText(row?.task_type),
    priority: nullableText(row?.priority),
    status: nullableText(row?.status),
    dueAt: nullableText(row?.due_at),
    pausedAt: nullableText(row?.paused_at),
    completedAt: nullableText(row?.completed_at),
    cancelledAt: nullableText(row?.cancelled_at),
    createdAt: nullableText(row?.created_at),
    updatedAt: nullableText(row?.updated_at),
  };
}

function portableReminder(row: any) {
  return {
    id: text(row?.id),
    title: nullableText(row?.title),
    body: nullableText(row?.body),
    originalText: nullableText(row?.original_text),
    interpretedText: nullableText(row?.interpreted_text),
    dueAt: nullableText(row?.due_at),
    status: nullableText(row?.status),
    priorityClass: nullableText(row?.priority_class),
    taskId: nullableText(row?.task_id),
    reminderType: nullableText(row?.reminder_type),
    lifecycleStatus: nullableText(row?.lifecycle_status),
    domain: nullableText(row?.domain),
    recurrenceRule: nullableText(row?.recurrence_rule),
    personName: nullableText(row?.person_name),
    location: portableJson(row?.location),
    cooldownUntil: nullableText(row?.cooldown_until),
    completedAt: nullableText(row?.completed_at),
    deliveryChannel: nullableText(row?.delivery_channel),
    createdAt: nullableText(row?.created_at),
    updatedAt: nullableText(row?.updated_at),
  };
}

function portableContact(row: any) {
  return {
    id: text(row?.id),
    nameKey: text(row?.name_key),
    displayName: text(row?.display_name),
    targetWaId: text(row?.target_wa_id),
    createdAt: nullableText(row?.created_at),
    updatedAt: nullableText(row?.updated_at),
  };
}

function portableLearningState(row: any) {
  if (!row || typeof row !== "object") return null;
  return {
    firstMetAt: nullableText(row.first_met_at),
    lastInteractionAt: nullableText(row.last_interaction_at),
    turnCount: nonNegativeInt(row.turn_count),
    directnessScore: boundedScore(row.directness_score),
    technicalDepthScore: boundedScore(row.technical_depth_score),
    programmingInterestScore: boundedScore(row.programming_interest_score),
    solutionBreadthScore: boundedScore(row.solution_breadth_score),
    arabicPreferenceScore: boundedScore(row.arabic_preference_score),
    concisePreferenceScore: boundedScore(row.concise_preference_score),
    codeReplacementPreferenceScore: boundedScore(row.code_replacement_preference_score),
    interactionSamples: nonNegativeInt(row.interaction_samples),
    interestTags: portableJson(row.interest_tags) ?? {},
    updatedAt: nullableText(row.updated_at),
  };
}

function stableRows(rows: any[]) {
  return [...rows].sort((a, b) => {
    const created = String(a?.createdAt || "").localeCompare(String(b?.createdAt || ""));
    if (created !== 0) return created;
    return String(a?.id || "").localeCompare(String(b?.id || ""));
  });
}

function portableJson(value: unknown): unknown {
  if (value == null) return null;
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return value;
  if (Array.isArray(value)) return value.map(portableJson);
  if (typeof value === "object") {
    const result: Record<string, unknown> = {};
    for (const key of Object.keys(value as Record<string, unknown>).sort()) {
      result[key] = portableJson((value as Record<string, unknown>)[key]);
    }
    return result;
  }
  return null;
}

function canonicalJson(value: unknown): string {
  return JSON.stringify(portableJson(value));
}

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let mismatch = 0;
  for (let index = 0; index < a.length; index += 1) mismatch |= a.charCodeAt(index) ^ b.charCodeAt(index);
  return mismatch === 0;
}

function text(value: unknown): string {
  return String(value ?? "");
}

function nullableText(value: unknown): string | null {
  if (value == null) return null;
  return String(value);
}

function nonNegativeInt(value: unknown): number {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? Math.max(0, Math.trunc(numeric)) : 0;
}

function boundedScore(value: unknown): number {
  return Math.min(20, nonNegativeInt(value));
}
