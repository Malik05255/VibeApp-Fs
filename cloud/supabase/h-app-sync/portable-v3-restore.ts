import { H_LEARNING_ALLOWED_TAGS } from "./learning-policy.ts";
import {
  PORTABLE_MANIFEST_FORMAT,
  PORTABLE_PAGE_FORMAT,
  PORTABLE_PAGE_MAX_ROWS,
  PORTABLE_PAGE_SCHEMA_VERSION,
  PORTABLE_V3_SECTIONS,
  type PortableV3Section,
  verifyPortableV3PageIntegrity,
} from "./portable-page.ts";

export const PORTABLE_V3_MAX_ROWS_PER_SECTION = 20_000;
export const PORTABLE_V3_MAX_TOTAL_ROWS = 50_000;
export const PORTABLE_V3_MAX_PAGE_BYTES = 4 * 1024 * 1024;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SHA256 = /^[0-9a-f]{64}$/;
const SECTION_ORDER = new Map(PORTABLE_V3_SECTIONS.map((value, index) => [value, index]));
const MEMORY_CATEGORIES = new Set(["identity", "preference", "relationship", "idea", "note", "general"]);
const TASK_PRIORITIES = new Set(["simple", "medium", "important"]);
const TASK_STATUSES = new Set(["active", "paused", "completed", "cancelled"]);
const REMINDER_STATUSES = new Set(["pending", "paused", "sent", "waiting_template", "cancelled", "failed"]);
const REMINDER_PRIORITIES = new Set(["simple", "medium", "important"]);
const REMINDER_TYPES = new Set(["TIME", "LOCATION", "PERSON", "RECURRING", "CONTEXTUAL"]);
const LIFECYCLE_STATUSES = new Set(["ACTIVE", "DEFERRED", "COMPLETED", "DISABLED", "CANCELLED"]);
const DOMAINS = new Set(["PERSONAL", "PROGRAMMING"]);
const DELIVERY_CHANNELS = new Set(["app", "whatsapp"]);

type ManifestDescriptor = {
  section: PortableV3Section;
  pageIndex: number;
  itemCount: number;
  digest: string;
};

export type PortableV3RestoreManifest = {
  sessionId: string;
  createdAt: string;
  expiresAt: string;
  counts: Record<string, number>;
  pages: ManifestDescriptor[];
  digest: string;
};

export async function validatePortableV3RestoreManifest(value: unknown): Promise<PortableV3RestoreManifest> {
  const root = record(value, "portable_v3_manifest_invalid");
  if (root.format !== PORTABLE_MANIFEST_FORMAT || Number(root.schemaVersion) !== PORTABLE_PAGE_SCHEMA_VERSION) {
    fail("portable_v3_manifest_schema_invalid");
  }
  if (root.scope !== "portable_core_v3" || root.completeForSchemaVersion !== true) {
    fail("portable_v3_manifest_incomplete");
  }
  const sessionId = uuidValue(root.sessionId, "portable_v3_manifest_session_invalid");
  const createdAt = requiredTimestamp(root.createdAt, "portable_v3_manifest_created_at_invalid");
  const expiresAt = requiredTimestamp(root.expiresAt, "portable_v3_manifest_expiry_invalid");
  if (Date.parse(expiresAt) <= Date.parse(createdAt)) fail("portable_v3_manifest_expiry_invalid");

  const counts = validateCounts(root.counts);
  const total = counts.memories + counts.tasks + counts.reminders + counts.contacts + counts.learningState;
  if (total > PORTABLE_V3_MAX_TOTAL_ROWS) fail("portable_v3_total_too_large");

  const descriptors = arrayValue(root.pages, "portable_v3_manifest_pages_invalid").map((raw) => {
    const row = record(raw, "portable_v3_manifest_page_invalid");
    const section = String(row.section || "") as PortableV3Section;
    if (!SECTION_ORDER.has(section)) fail("portable_v3_manifest_page_invalid");
    const pageIndex = boundedInteger(row.pageIndex, 0, 1_000_000, "portable_v3_manifest_page_invalid");
    const itemCount = boundedInteger(row.itemCount, 1, PORTABLE_PAGE_MAX_ROWS, "portable_v3_manifest_page_invalid");
    const digest = String(row.digest || "").trim().toLowerCase();
    if (!SHA256.test(digest)) fail("portable_v3_manifest_page_invalid");
    return { section, pageIndex, itemCount, digest };
  });
  descriptors.sort((a, b) => {
    const section = (SECTION_ORDER.get(a.section) ?? 99) - (SECTION_ORDER.get(b.section) ?? 99);
    return section !== 0 ? section : a.pageIndex - b.pageIndex;
  });
  assertDescriptorCompleteness(descriptors, counts);

  const integrity = record(root.integrity, "portable_v3_manifest_integrity_invalid");
  if (integrity.algorithm !== "SHA-256") fail("portable_v3_manifest_integrity_invalid");
  const expected = String(integrity.digest || "").trim().toLowerCase();
  if (!SHA256.test(expected)) fail("portable_v3_manifest_integrity_invalid");
  const core = {
    format: PORTABLE_MANIFEST_FORMAT,
    schemaVersion: PORTABLE_PAGE_SCHEMA_VERSION,
    scope: "portable_core_v3",
    sessionId,
    createdAt,
    expiresAt,
    counts,
    pages: descriptors,
  };
  const actual = await sha256Hex(canonicalJson(core));
  if (!constantTimeEqual(expected, actual)) fail("portable_v3_manifest_integrity_failed");

  return { sessionId, createdAt, expiresAt, counts, pages: descriptors, digest: expected };
}

export async function validatePortableV3RestorePage(
  value: unknown,
  manifest: PortableV3RestoreManifest,
) {
  if (!await verifyPortableV3PageIntegrity(value)) fail("portable_v3_page_integrity_failed");
  const root = record(value, "portable_v3_page_invalid");
  if (root.format !== PORTABLE_PAGE_FORMAT || Number(root.schemaVersion) !== PORTABLE_PAGE_SCHEMA_VERSION) {
    fail("portable_v3_page_schema_invalid");
  }
  const sessionId = uuidValue(root.sessionId, "portable_v3_page_session_invalid");
  if (sessionId !== manifest.sessionId) fail("portable_v3_page_session_mismatch");
  const section = String(root.section || "") as PortableV3Section;
  if (!SECTION_ORDER.has(section)) fail("portable_v3_page_section_invalid");
  const pageIndex = boundedInteger(root.pageIndex, 0, 1_000_000, "portable_v3_page_index_invalid");
  const descriptor = manifest.pages.find((page) => page.section === section && page.pageIndex === pageIndex);
  if (!descriptor) fail("portable_v3_page_not_in_manifest");
  const pageDigest = String(record(root.integrity, "portable_v3_page_integrity_invalid").digest || "")
    .trim().toLowerCase();
  if (!constantTimeEqual(pageDigest, descriptor.digest)) fail("portable_v3_page_manifest_digest_mismatch");
  const rawItems = arrayValue(root.items, "portable_v3_page_items_invalid");
  if (rawItems.length !== descriptor.itemCount || rawItems.length > PORTABLE_PAGE_MAX_ROWS) {
    fail("portable_v3_page_count_mismatch");
  }
  if (new TextEncoder().encode(JSON.stringify(root)).byteLength > PORTABLE_V3_MAX_PAGE_BYTES) {
    fail("portable_v3_page_too_large");
  }

  const items = rawItems.map((item) => validateSectionItem(section, item));
  return { section, pageIndex, digest: pageDigest, items, itemCount: items.length };
}

function validateCounts(value: unknown): Record<string, number> {
  const root = record(value, "portable_v3_counts_invalid");
  const result: Record<string, number> = {};
  for (const key of ["memories", "tasks", "reminders", "contacts"]) {
    result[key] = boundedInteger(root[key] ?? 0, 0, PORTABLE_V3_MAX_ROWS_PER_SECTION, "portable_v3_counts_invalid");
  }
  result.learningState = boundedInteger(root.learningState ?? 0, 0, 1, "portable_v3_counts_invalid");
  return result;
}

function assertDescriptorCompleteness(descriptors: ManifestDescriptor[], counts: Record<string, number>) {
  const seen = new Set<string>();
  for (const descriptor of descriptors) {
    const key = `${descriptor.section}:${descriptor.pageIndex}`;
    if (seen.has(key)) fail("portable_v3_duplicate_page");
    seen.add(key);
  }
  for (const section of PORTABLE_V3_SECTIONS) {
    const countKey = section === "learning" ? "learningState" : section;
    const expected = counts[countKey];
    const pages = descriptors.filter((descriptor) => descriptor.section === section);
    if (expected === 0) {
      if (pages.length) fail("portable_v3_unexpected_page");
      continue;
    }
    if (!pages.length) fail("portable_v3_missing_page");
    let actual = 0;
    pages.forEach((page, index) => {
      if (page.pageIndex !== index) fail("portable_v3_page_sequence_invalid");
      actual += page.itemCount;
    });
    if (actual !== expected) fail("portable_v3_counts_mismatch");
  }
}

function validateSectionItem(section: PortableV3Section, value: unknown): Record<string, unknown> {
  const row = record(value, `portable_v3_${section}_invalid`);
  if (section === "memories") {
    return {
      id: uuidValue(row.id, "portable_v3_memory_invalid"),
      category: enumValue(row.category, MEMORY_CATEGORIES, "portable_v3_memory_invalid"),
      body: requiredText(row.body, 280, "portable_v3_memory_invalid"),
      originalText: optionalText(row.originalText, 500, "portable_v3_memory_invalid"),
      createdAt: timestampOrNull(row.createdAt, "portable_v3_memory_invalid"),
      updatedAt: timestampOrNull(row.updatedAt, "portable_v3_memory_invalid"),
    };
  }
  if (section === "tasks") {
    return {
      id: bigintId(row.id, "portable_v3_task_invalid"),
      title: optionalText(row.title, 160, "portable_v3_task_invalid"),
      body: requiredText(row.body, 12_000, "portable_v3_task_invalid"),
      taskType: requiredText(row.taskType, 80, "portable_v3_task_invalid"),
      priority: enumValue(row.priority, TASK_PRIORITIES, "portable_v3_task_invalid"),
      status: enumValue(row.status, TASK_STATUSES, "portable_v3_task_invalid"),
      dueAt: timestampOrNull(row.dueAt, "portable_v3_task_invalid"),
      pausedAt: timestampOrNull(row.pausedAt, "portable_v3_task_invalid"),
      completedAt: timestampOrNull(row.completedAt, "portable_v3_task_invalid"),
      cancelledAt: timestampOrNull(row.cancelledAt, "portable_v3_task_invalid"),
      createdAt: timestampOrNull(row.createdAt, "portable_v3_task_invalid"),
      updatedAt: timestampOrNull(row.updatedAt, "portable_v3_task_invalid"),
    };
  }
  if (section === "reminders") {
    return {
      id: uuidValue(row.id, "portable_v3_reminder_invalid"),
      title: optionalText(row.title, 160, "portable_v3_reminder_invalid"),
      body: requiredText(row.body, 12_000, "portable_v3_reminder_invalid"),
      originalText: optionalText(row.originalText, 12_000, "portable_v3_reminder_invalid"),
      interpretedText: optionalText(row.interpretedText, 12_000, "portable_v3_reminder_invalid"),
      dueAt: timestampOrNull(row.dueAt, "portable_v3_reminder_invalid"),
      status: enumValue(row.status, REMINDER_STATUSES, "portable_v3_reminder_invalid"),
      priorityClass: enumValue(row.priorityClass, REMINDER_PRIORITIES, "portable_v3_reminder_invalid"),
      taskId: row.taskId == null ? null : bigintId(row.taskId, "portable_v3_reminder_invalid"),
      reminderType: enumValue(row.reminderType, REMINDER_TYPES, "portable_v3_reminder_invalid"),
      lifecycleStatus: enumValue(row.lifecycleStatus, LIFECYCLE_STATUSES, "portable_v3_reminder_invalid"),
      domain: enumValue(row.domain, DOMAINS, "portable_v3_reminder_invalid"),
      recurrenceRule: optionalText(row.recurrenceRule, 2_000, "portable_v3_reminder_invalid"),
      personName: optionalText(row.personName, 240, "portable_v3_reminder_invalid"),
      location: safeJson(row.location, "portable_v3_reminder_invalid"),
      cooldownUntil: timestampOrNull(row.cooldownUntil, "portable_v3_reminder_invalid"),
      completedAt: timestampOrNull(row.completedAt, "portable_v3_reminder_invalid"),
      deliveryChannel: enumValue(row.deliveryChannel, DELIVERY_CHANNELS, "portable_v3_reminder_invalid"),
      createdAt: timestampOrNull(row.createdAt, "portable_v3_reminder_invalid"),
      updatedAt: timestampOrNull(row.updatedAt, "portable_v3_reminder_invalid"),
    };
  }
  if (section === "contacts") {
    return {
      id: uuidValue(row.id, "portable_v3_contact_invalid"),
      nameKey: requiredText(row.nameKey, 120, "portable_v3_contact_invalid"),
      displayName: requiredText(row.displayName, 120, "portable_v3_contact_invalid"),
      targetWaId: phoneDigits(row.targetWaId, "portable_v3_contact_invalid"),
      createdAt: timestampOrNull(row.createdAt, "portable_v3_contact_invalid"),
      updatedAt: timestampOrNull(row.updatedAt, "portable_v3_contact_invalid"),
    };
  }

  const tags = record(row.interestTags ?? {}, "portable_v3_learning_invalid");
  const interestTags: Record<string, number> = {};
  for (const [rawKey, rawValue] of Object.entries(tags)) {
    const key = rawKey.trim().toLowerCase();
    if (!H_LEARNING_ALLOWED_TAGS.has(key)) fail("portable_v3_learning_invalid");
    const count = boundedInteger(rawValue, 0, 1_000_000, "portable_v3_learning_invalid");
    if (count > 0) interestTags[key] = count;
  }
  return {
    firstMetAt: requiredTimestamp(row.firstMetAt, "portable_v3_learning_invalid"),
    lastInteractionAt: requiredTimestamp(row.lastInteractionAt, "portable_v3_learning_invalid"),
    turnCount: boundedInteger(row.turnCount, 0, 1_000_000_000, "portable_v3_learning_invalid"),
    directnessScore: boundedInteger(row.directnessScore, 0, 20, "portable_v3_learning_invalid"),
    technicalDepthScore: boundedInteger(row.technicalDepthScore, 0, 20, "portable_v3_learning_invalid"),
    programmingInterestScore: boundedInteger(row.programmingInterestScore, 0, 20, "portable_v3_learning_invalid"),
    solutionBreadthScore: boundedInteger(row.solutionBreadthScore, 0, 20, "portable_v3_learning_invalid"),
    arabicPreferenceScore: boundedInteger(row.arabicPreferenceScore, 0, 20, "portable_v3_learning_invalid"),
    concisePreferenceScore: boundedInteger(row.concisePreferenceScore, 0, 20, "portable_v3_learning_invalid"),
    codeReplacementPreferenceScore: boundedInteger(row.codeReplacementPreferenceScore, 0, 20, "portable_v3_learning_invalid"),
    interactionSamples: boundedInteger(row.interactionSamples, 0, 1_000_000_000, "portable_v3_learning_invalid"),
    interestTags,
    updatedAt: timestampOrNull(row.updatedAt, "portable_v3_learning_invalid"),
  };
}

function record(value: unknown, code: string): Record<string, any> {
  if (!value || typeof value !== "object" || Array.isArray(value)) fail(code);
  return value as Record<string, any>;
}

function arrayValue(value: unknown, code: string): unknown[] {
  if (!Array.isArray(value)) fail(code);
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

function uuidValue(value: unknown, code: string): string {
  const text = String(value ?? "").trim().toLowerCase();
  if (!UUID.test(text)) fail(code);
  return text;
}

function bigintId(value: unknown, code: string): string {
  const text = String(value ?? "").trim();
  if (!/^[1-9][0-9]{0,18}$/.test(text)) fail(code);
  const number = BigInt(text);
  if (number > 9_223_372_036_854_775_807n) fail(code);
  return text;
}

function phoneDigits(value: unknown, code: string): string {
  const text = String(value ?? "").trim();
  if (!/^[1-9][0-9]{7,19}$/.test(text)) fail(code);
  return text;
}

function requiredTimestamp(value: unknown, code: string): string {
  const text = String(value ?? "").trim();
  if (!text || !Number.isFinite(Date.parse(text))) fail(code);
  return text;
}

function timestampOrNull(value: unknown, code: string): string | null {
  return value == null ? null : requiredTimestamp(value, code);
}

function boundedInteger(value: unknown, min: number, max: number, code: string): number {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number < min || number > max) fail(code);
  return number;
}

function safeJson(value: unknown, code: string): unknown {
  if (value == null) return null;
  let serialized = "";
  try {
    serialized = JSON.stringify(value);
  } catch (_) {
    fail(code);
  }
  if (serialized.length > 12_000) fail(code);
  return JSON.parse(serialized);
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

function fail(code: string): never {
  throw new Error(code);
}
