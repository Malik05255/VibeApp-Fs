import { buildPortableSnapshot } from "./portable-snapshot.ts";

export const PORTABLE_PAGE_FORMAT = "h-portable-page";
export const PORTABLE_PAGE_SCHEMA_VERSION = 3;
export const PORTABLE_PAGE_DEFAULT_ROWS = 200;
export const PORTABLE_PAGE_MAX_ROWS = 500;

export type PortablePageSection = "memories" | "tasks" | "reminders" | "contacts";

export type PortablePageCursor = {
  createdAt: string;
  id: string;
};

export type PortablePageRequest = {
  section: PortablePageSection;
  limit: number;
  cursor: PortablePageCursor | null;
};

const SECTIONS = new Set<PortablePageSection>(["memories", "tasks", "reminders", "contacts"]);
const SAFE_ID = /^[A-Za-z0-9_-]{1,128}$/;
const SAFE_TIMESTAMP = /^[0-9T:+.\-Z]{16,48}$/;

export function parsePortablePageRequest(body: unknown): PortablePageRequest {
  const root = objectOrEmpty(body);
  const section = String(root.section || "").trim().toLowerCase() as PortablePageSection;
  if (!SECTIONS.has(section)) throw new Error("portable_page_section_invalid");

  const requested = root.limit == null ? PORTABLE_PAGE_DEFAULT_ROWS : Number(root.limit);
  if (!Number.isInteger(requested) || requested < 1 || requested > PORTABLE_PAGE_MAX_ROWS) {
    throw new Error("portable_page_limit_invalid");
  }

  const rawCursor = String(root.cursor || "").trim();
  return {
    section,
    limit: requested,
    cursor: rawCursor ? decodePortablePageCursor(rawCursor) : null,
  };
}

export function encodePortablePageCursor(cursor: PortablePageCursor): string {
  const normalized = validateCursor(cursor);
  const json = JSON.stringify(normalized);
  return base64UrlEncode(new TextEncoder().encode(json));
}

export function decodePortablePageCursor(value: string): PortablePageCursor {
  const raw = String(value || "").trim();
  if (!raw || raw.length > 512 || !/^[A-Za-z0-9_-]+$/.test(raw)) {
    throw new Error("portable_page_cursor_invalid");
  }
  let decoded: unknown;
  try {
    decoded = JSON.parse(new TextDecoder().decode(base64UrlDecode(raw)));
  } catch (_) {
    throw new Error("portable_page_cursor_invalid");
  }
  return validateCursor(objectOrEmpty(decoded) as PortablePageCursor);
}

export async function sanitizePortablePageRows(
  section: PortablePageSection,
  rows: any[],
): Promise<Record<string, unknown>[]> {
  const input = {
    memories: section === "memories" ? rows : [],
    tasks: section === "tasks" ? rows : [],
    reminders: section === "reminders" ? rows : [],
    contacts: section === "contacts" ? rows : [],
    learningState: null,
  };
  const snapshot = await buildPortableSnapshot(input, new Date(0), 2);
  return [...((snapshot.payload as any)[section] || [])];
}

export async function buildPortablePageEnvelope(input: {
  section: PortablePageSection;
  items: Record<string, unknown>[];
  startCursor: string | null;
  nextCursor: string | null;
  hasMore: boolean;
  generatedAt?: Date;
}) {
  if (!SECTIONS.has(input.section)) throw new Error("portable_page_section_invalid");
  if (!Array.isArray(input.items) || input.items.length > PORTABLE_PAGE_MAX_ROWS) {
    throw new Error("portable_page_items_invalid");
  }
  if (input.hasMore && !input.nextCursor) throw new Error("portable_page_next_cursor_required");
  if (!input.hasMore && input.nextCursor) throw new Error("portable_page_next_cursor_unexpected");

  const pageCore = {
    format: PORTABLE_PAGE_FORMAT,
    schemaVersion: PORTABLE_PAGE_SCHEMA_VERSION,
    section: input.section,
    startCursor: input.startCursor,
    nextCursor: input.nextCursor,
    hasMore: input.hasMore,
    itemCount: input.items.length,
    items: input.items,
  };
  const digest = await sha256Hex(canonicalJson(pageCore));
  return {
    ...pageCore,
    generatedAt: (input.generatedAt ?? new Date()).toISOString(),
    integrity: {
      algorithm: "SHA-256",
      digest,
      authenticityGuaranteed: false,
    },
    completePage: true,
    restoreSupportedDirectly: false,
  };
}

export async function verifyPortablePageIntegrity(page: unknown): Promise<boolean> {
  const root = objectOrEmpty(page);
  if (root.format !== PORTABLE_PAGE_FORMAT || Number(root.schemaVersion) !== PORTABLE_PAGE_SCHEMA_VERSION) {
    return false;
  }
  const section = String(root.section || "") as PortablePageSection;
  if (!SECTIONS.has(section)) return false;
  if (!Array.isArray(root.items) || root.items.length > PORTABLE_PAGE_MAX_ROWS) return false;
  if (Number(root.itemCount) !== root.items.length) return false;
  const expected = String(objectOrEmpty(root.integrity).digest || "").trim().toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(expected)) return false;

  const pageCore = {
    format: root.format,
    schemaVersion: Number(root.schemaVersion),
    section,
    startCursor: root.startCursor == null ? null : String(root.startCursor),
    nextCursor: root.nextCursor == null ? null : String(root.nextCursor),
    hasMore: root.hasMore === true,
    itemCount: Number(root.itemCount),
    items: root.items,
  };
  const actual = await sha256Hex(canonicalJson(pageCore));
  return constantTimeEqual(expected, actual);
}

export function rawRowCursor(row: any): PortablePageCursor {
  return validateCursor({
    createdAt: String(row?.created_at || ""),
    id: String(row?.id || ""),
  });
}

function validateCursor(cursor: PortablePageCursor): PortablePageCursor {
  const createdAt = String(cursor?.createdAt || "").trim();
  const id = String(cursor?.id || "").trim();
  if (!SAFE_TIMESTAMP.test(createdAt) || !Number.isFinite(Date.parse(createdAt)) || !SAFE_ID.test(id)) {
    throw new Error("portable_page_cursor_invalid");
  }
  return { createdAt, id };
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

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlDecode(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}
