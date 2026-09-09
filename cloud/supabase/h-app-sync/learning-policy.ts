export const H_LEARNING_ALLOWED_TAGS = new Set([
  "programming",
  "android",
  "github",
  "authentication",
  "ai",
  "ui-ux",
  "cloud",
]);

const MAX_SCORE = 20;
const MAX_COUNT = 1_000_000_000;
const MAX_TAG_COUNT = 1_000_000;
const MAX_EVENTS_PER_SYNC = 20;
const MAX_EVENT_AGE_MS = 365 * 24 * 60 * 60_000;
const MAX_FUTURE_SKEW_MS = 10 * 60_000;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export type LearningBaseline = {
  first_met_at_ms: number;
  last_interaction_at_ms: number;
  turn_count: number;
  directness_score: number;
  technical_depth_score: number;
  programming_interest_score: number;
  solution_breadth_score: number;
  arabic_preference_score: number;
  concise_preference_score: number;
  code_replacement_preference_score: number;
  interaction_samples: number;
  interest_tags: Record<string, number>;
};

export type LearningSignal = {
  directness: boolean;
  technical_depth: boolean;
  programming_interest: boolean;
  solution_breadth: boolean;
  arabic_preference: boolean;
  concise_preference: boolean;
  code_replacement_preference: boolean;
  interest_tags: string[];
};

export type LearningEvent = {
  event_id: string;
  occurred_at_ms: number;
  signal: LearningSignal;
};

export function normalizeLearningBaseline(value: unknown): LearningBaseline | null {
  if (!isRecord(value)) return null;
  const now = Date.now();
  const first = boundedTimestamp(value.first_met_at_ms, now);
  const last = boundedTimestamp(value.last_interaction_at_ms, now);
  if (first == null || last == null) return null;

  return {
    first_met_at_ms: Math.min(first, last),
    last_interaction_at_ms: Math.max(first, last),
    turn_count: boundedInt(value.turn_count, 0, MAX_COUNT),
    directness_score: boundedInt(value.directness_score, 0, MAX_SCORE),
    technical_depth_score: boundedInt(value.technical_depth_score, 0, MAX_SCORE),
    programming_interest_score: boundedInt(value.programming_interest_score, 0, MAX_SCORE),
    solution_breadth_score: boundedInt(value.solution_breadth_score, 0, MAX_SCORE),
    arabic_preference_score: boundedInt(value.arabic_preference_score, 0, MAX_SCORE),
    concise_preference_score: boundedInt(value.concise_preference_score, 0, MAX_SCORE),
    code_replacement_preference_score: boundedInt(value.code_replacement_preference_score, 0, MAX_SCORE),
    interaction_samples: boundedInt(value.interaction_samples, 0, MAX_COUNT),
    interest_tags: normalizeTagCounts(value.interest_tags),
  };
}

export function normalizeLearningEvents(value: unknown, nowMs = Date.now()): LearningEvent[] | null {
  if (!Array.isArray(value) || value.length === 0 || value.length > MAX_EVENTS_PER_SYNC) return null;
  const normalized: LearningEvent[] = [];
  const seen = new Set<string>();

  for (const raw of value) {
    if (!isRecord(raw)) return null;
    const eventId = String(raw.event_id || "").trim().toLowerCase();
    if (!UUID_RE.test(eventId)) return null;
    if (seen.has(eventId)) continue;
    seen.add(eventId);

    const occurredAtMs = Number(raw.occurred_at_ms);
    if (!Number.isSafeInteger(occurredAtMs)) return null;
    if (occurredAtMs < nowMs - MAX_EVENT_AGE_MS || occurredAtMs > nowMs + MAX_FUTURE_SKEW_MS) return null;

    const signal = normalizeLearningSignal(raw.signal);
    if (!signal) return null;
    normalized.push({ event_id: eventId, occurred_at_ms: occurredAtMs, signal });
  }

  return normalized.length > 0 ? normalized : null;
}

function normalizeLearningSignal(value: unknown): LearningSignal | null {
  if (!isRecord(value)) return null;
  return {
    directness: value.directness === true,
    technical_depth: value.technical_depth === true,
    programming_interest: value.programming_interest === true,
    solution_breadth: value.solution_breadth === true,
    arabic_preference: value.arabic_preference === true,
    concise_preference: value.concise_preference === true,
    code_replacement_preference: value.code_replacement_preference === true,
    interest_tags: Array.isArray(value.interest_tags)
      ? [...new Set(value.interest_tags
        .map((tag) => String(tag || "").trim().toLowerCase())
        .filter((tag) => H_LEARNING_ALLOWED_TAGS.has(tag)))]
      : [],
  };
}

function normalizeTagCounts(value: unknown): Record<string, number> {
  if (!isRecord(value)) return {};
  const result: Record<string, number> = {};
  for (const [rawTag, rawCount] of Object.entries(value)) {
    const tag = rawTag.trim().toLowerCase();
    if (!H_LEARNING_ALLOWED_TAGS.has(tag)) continue;
    const count = boundedInt(rawCount, 0, MAX_TAG_COUNT);
    if (count > 0) result[tag] = count;
  }
  return result;
}

function boundedInt(value: unknown, min: number, max: number): number {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return min;
  return Math.min(max, Math.max(min, Math.trunc(numeric)));
}

function boundedTimestamp(value: unknown, nowMs: number): number | null {
  const numeric = Number(value);
  if (!Number.isSafeInteger(numeric) || numeric <= 0) return null;
  const earliest = nowMs - 20 * 365 * 24 * 60 * 60_000;
  const latest = nowMs + MAX_FUTURE_SKEW_MS;
  return Math.min(latest, Math.max(earliest, numeric));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
