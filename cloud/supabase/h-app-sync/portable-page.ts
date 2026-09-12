export const PORTABLE_PAGE_FORMAT = "h-portable-page";
export const PORTABLE_MANIFEST_FORMAT = "h-portable-manifest";
export const PORTABLE_PAGE_SCHEMA_VERSION = 3;
export const PORTABLE_PAGE_DEFAULT_ROWS = 200;
export const PORTABLE_PAGE_MAX_ROWS = 500;

export const PORTABLE_V3_SECTIONS = [
  "memories",
  "tasks",
  "reminders",
  "contacts",
  "learning",
] as const;

export type PortableV3Section = typeof PORTABLE_V3_SECTIONS[number];

export type PortableV3PageRequest = {
  sessionId: string;
  section: PortableV3Section;
  pageIndex: number;
};

const SECTION_ORDER = new Map(PORTABLE_V3_SECTIONS.map((value, index) => [value, index]));
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SHA256 = /^[0-9a-f]{64}$/;

export function parsePortableV3PageSize(body: unknown): number {
  const root = objectOrEmpty(body);
  const requested = root.page_size ?? root.pageSize ?? PORTABLE_PAGE_DEFAULT_ROWS;
  const pageSize = Number(requested);
  if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > PORTABLE_PAGE_MAX_ROWS) {
    throw new Error("portable_v3_page_size_invalid");
  }
  return pageSize;
}

export function parsePortableV3PageRequest(body: unknown): PortableV3PageRequest {
  const root = objectOrEmpty(body);
  const sessionId = validateSessionId(root.session_id ?? root.sessionId);
  const section = String(root.section || "").trim().toLowerCase() as PortableV3Section;
  if (!SECTION_ORDER.has(section)) throw new Error("portable_v3_section_invalid");
  const pageIndex = Number(root.page_index ?? root.pageIndex);
  if (!Number.isInteger(pageIndex) || pageIndex < 0 || pageIndex > 1_000_000) {
    throw new Error("portable_v3_page_index_invalid");
  }
  return { sessionId, section, pageIndex };
}

export function validatePortableV3Counts(value: unknown): Record<string, number> {
  const root = objectOrEmpty(value);
  const result: Record<string, number> = {};
  for (const section of PORTABLE_V3_SECTIONS) {
    const key = section === "learning" ? "learningState" : section;
    const numeric = Number(root[key] ?? 0);
    if (!Number.isSafeInteger(numeric) || numeric < 0 || numeric > 10_000_000) {
      throw new Error("portable_v3_counts_invalid");
    }
    result[key] = numeric;
  }
  if (result.learningState > 1) throw new Error("portable_v3_learning_count_invalid");
  return result;
}

export async function buildPortableV3PageEnvelope(input: {
  sessionId: string;
  section: PortableV3Section;
  pageIndex: number;
  items: unknown[];
  counts: unknown;
  expiresAt: string;
  generatedAt?: Date;
}) {
  const sessionId = validateSessionId(input.sessionId);
  if (!SECTION_ORDER.has(input.section)) throw new Error("portable_v3_section_invalid");
  if (!Number.isInteger(input.pageIndex) || input.pageIndex < 0) throw new Error("portable_v3_page_index_invalid");
  if (!Array.isArray(input.items) || input.items.length > PORTABLE_PAGE_MAX_ROWS) {
    throw new Error("portable_v3_page_items_invalid");
  }
  const counts = validatePortableV3Counts(input.counts);
  const expiresAt = validTimestamp(input.expiresAt, "portable_v3_expiry_invalid");
  const core = {
    format: PORTABLE_PAGE_FORMAT,
    schemaVersion: PORTABLE_PAGE_SCHEMA_VERSION,
    scope: "portable_core_v3",
    sessionId,
    section: input.section,
    pageIndex: input.pageIndex,
    itemCount: input.items.length,
    counts,
    expiresAt,
    items: portableJson(input.items),
  };
  const digest = await sha256Hex(canonicalJson(core));
  return {
    ...core,
    generatedAt: (input.generatedAt ?? new Date()).toISOString(),
    integrity: {
      algorithm: "SHA-256",
      digest,
      authenticityGuaranteed: false,
    },
    completePage: true,
  };
}

export async function verifyPortableV3PageIntegrity(page: unknown): Promise<boolean> {
  try {
    const root = objectOrEmpty(page);
    if (root.format !== PORTABLE_PAGE_FORMAT || Number(root.schemaVersion) !== PORTABLE_PAGE_SCHEMA_VERSION) return false;
    if (root.scope !== "portable_core_v3") return false;
    const sessionId = validateSessionId(root.sessionId);
    const section = String(root.section || "") as PortableV3Section;
    if (!SECTION_ORDER.has(section)) return false;
    const pageIndex = Number(root.pageIndex);
    if (!Number.isInteger(pageIndex) || pageIndex < 0) return false;
    if (!Array.isArray(root.items) || root.items.length > PORTABLE_PAGE_MAX_ROWS) return false;
    if (Number(root.itemCount) !== root.items.length) return false;
    const counts = validatePortableV3Counts(root.counts);
    const expiresAt = validTimestamp(root.expiresAt, "portable_v3_expiry_invalid");
    const expected = String(objectOrEmpty(root.integrity).digest || "").trim().toLowerCase();
    if (!SHA256.test(expected)) return false;
    const core = {
      format: root.format,
      schemaVersion: Number(root.schemaVersion),
      scope: root.scope,
      sessionId,
      section,
      pageIndex,
      itemCount: Number(root.itemCount),
      counts,
      expiresAt,
      items: portableJson(root.items),
    };
    return constantTimeEqual(expected, await sha256Hex(canonicalJson(core)));
  } catch (_) {
    return false;
  }
}

export async function buildPortableV3Manifest(input: {
  sessionId: string;
  pages: unknown[];
  counts: unknown;
  createdAt: string;
  expiresAt: string;
  restoreSupported?: boolean;
}) {
  const sessionId = validateSessionId(input.sessionId);
  const counts = validatePortableV3Counts(input.counts);
  const createdAt = validTimestamp(input.createdAt, "portable_v3_created_at_invalid");
  const expiresAt = validTimestamp(input.expiresAt, "portable_v3_expiry_invalid");
  const descriptors: Array<{ section: PortableV3Section; pageIndex: number; itemCount: number; digest: string }> = [];
  const seen = new Set<string>();

  for (const candidate of input.pages) {
    if (!await verifyPortableV3PageIntegrity(candidate)) throw new Error("portable_v3_page_integrity_failed");
    const page = objectOrEmpty(candidate);
    if (String(page.sessionId) !== sessionId) throw new Error("portable_v3_mixed_session");
    if (canonicalJson(page.counts) !== canonicalJson(counts)) throw new Error("portable_v3_mixed_counts");
    if (String(page.expiresAt) !== expiresAt) throw new Error("portable_v3_mixed_expiry");
    const section = String(page.section) as PortableV3Section;
    const pageIndex = Number(page.pageIndex);
    const key = `${section}:${pageIndex}`;
    if (seen.has(key)) throw new Error("portable_v3_duplicate_page");
    seen.add(key);
    descriptors.push({
      section,
      pageIndex,
      itemCount: Number(page.itemCount),
      digest: String(objectOrEmpty(page.integrity).digest).toLowerCase(),
    });
  }

  descriptors.sort((a, b) => {
    const section = (SECTION_ORDER.get(a.section) ?? 99) - (SECTION_ORDER.get(b.section) ?? 99);
    return section !== 0 ? section : a.pageIndex - b.pageIndex;
  });
  assertCompleteDescriptors(descriptors, counts);

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
  const digest = await sha256Hex(canonicalJson(core));
  return {
    ...core,
    completeForSchemaVersion: true,
    restoreSupported: input.restoreSupported === true,
    integrity: {
      algorithm: "SHA-256",
      digest,
      authenticityGuaranteed: false,
    },
    excludedByDesign: [
      "provider_credentials",
      "runtime_secrets",
      "google_link_identity",
      "whatsapp_routing_identity",
      "provider_health_and_quota_state",
      "conversation_transcripts",
      "task_execution_metadata",
      "raw_media_and_documents",
      "transient_media_derivatives",
      "cloud_destination_credentials",
    ],
  };
}

export async function verifyPortableV3Manifest(manifest: unknown, pages: unknown[]): Promise<boolean> {
  try {
    const root = objectOrEmpty(manifest);
    if (root.format !== PORTABLE_MANIFEST_FORMAT || Number(root.schemaVersion) !== PORTABLE_PAGE_SCHEMA_VERSION) return false;
    const rebuilt = await buildPortableV3Manifest({
      sessionId: String(root.sessionId || ""),
      pages,
      counts: root.counts,
      createdAt: String(root.createdAt || ""),
      expiresAt: String(root.expiresAt || ""),
      restoreSupported: root.restoreSupported === true,
    });
    const expected = String(objectOrEmpty(root.integrity).digest || "").trim().toLowerCase();
    return SHA256.test(expected) && constantTimeEqual(expected, rebuilt.integrity.digest);
  } catch (_) {
    return false;
  }
}

function assertCompleteDescriptors(
  descriptors: Array<{ section: PortableV3Section; pageIndex: number; itemCount: number }>,
  counts: Record<string, number>,
) {
  for (const section of PORTABLE_V3_SECTIONS) {
    const countKey = section === "learning" ? "learningState" : section;
    const expectedRows = counts[countKey] ?? 0;
    const pages = descriptors.filter((value) => value.section === section);
    if (expectedRows === 0) {
      if (pages.length !== 0) throw new Error("portable_v3_unexpected_empty_section_page");
      continue;
    }
    if (pages.length === 0) throw new Error("portable_v3_missing_page");
    let rows = 0;
    pages.forEach((page, index) => {
      if (page.pageIndex !== index) throw new Error("portable_v3_page_sequence_invalid");
      if (page.itemCount < 1 || page.itemCount > PORTABLE_PAGE_MAX_ROWS) throw new Error("portable_v3_page_items_invalid");
      rows += page.itemCount;
    });
    if (rows !== expectedRows) throw new Error("portable_v3_counts_mismatch");
  }
}

function validateSessionId(value: unknown): string {
  const id = String(value || "").trim().toLowerCase();
  if (!UUID.test(id)) throw new Error("portable_v3_session_invalid");
  return id;
}

function validTimestamp(value: unknown, code: string): string {
  const text = String(value || "").trim();
  if (!text || !Number.isFinite(Date.parse(text))) throw new Error(code);
  return text;
}

function objectOrEmpty(value: unknown): Record<string, any> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, any>
    : {};
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
