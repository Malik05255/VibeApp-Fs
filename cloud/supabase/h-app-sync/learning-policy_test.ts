import {
  normalizeLearningBaseline,
  normalizeLearningEvents,
} from "./learning-policy.ts";

Deno.test("learning baseline clamps counters and strips unknown tags", () => {
  const now = Date.now();
  const value = normalizeLearningBaseline({
    first_met_at_ms: now - 1000,
    last_interaction_at_ms: now,
    turn_count: 42,
    directness_score: 99,
    technical_depth_score: 4,
    programming_interest_score: 3,
    solution_breadth_score: 2,
    arabic_preference_score: 7,
    concise_preference_score: 1,
    code_replacement_preference_score: 5,
    interaction_samples: 42,
    interest_tags: { android: 7, github: 2, secret_topic: 100 },
    raw_conversation: "must never be persisted",
  });

  if (!value) throw new Error("expected normalized baseline");
  if (value.directness_score !== 20) throw new Error("score was not clamped");
  if (value.interest_tags.android !== 7 || value.interest_tags.github !== 2) {
    throw new Error("allowed tag counts were lost");
  }
  if ("secret_topic" in value.interest_tags) throw new Error("unknown tag leaked");
  if ("raw_conversation" in value) throw new Error("raw text leaked into baseline");
});

Deno.test("learning events keep only aggregate signals and approved tags", () => {
  const now = Date.now();
  const events = normalizeLearningEvents([{
    event_id: "1c6a43ce-dab3-4c31-9807-9bc9d17ef63d",
    occurred_at_ms: now,
    signal: {
      directness: true,
      technical_depth: false,
      programming_interest: true,
      solution_breadth: false,
      arabic_preference: true,
      concise_preference: false,
      code_replacement_preference: false,
      interest_tags: ["android", "android", "private-topic", "github"],
      prompt: "raw prompt must be ignored",
    },
    response: "raw response must be ignored",
  }], now);

  if (!events || events.length !== 1) throw new Error("expected one event");
  const event = events[0];
  if (!event.signal.directness || !event.signal.programming_interest) {
    throw new Error("boolean signals were lost");
  }
  if (event.signal.interest_tags.join(",") !== "android,github") {
    throw new Error("tags were not normalized");
  }
  if ("prompt" in event.signal || "response" in event) throw new Error("raw content leaked");
});

Deno.test("learning event validation rejects invalid ids and implausible timestamps", () => {
  const now = Date.now();
  const signal = { interest_tags: [] };

  if (normalizeLearningEvents([{ event_id: "bad", occurred_at_ms: now, signal }], now) !== null) {
    throw new Error("invalid UUID accepted");
  }
  if (normalizeLearningEvents([{
    event_id: "1c6a43ce-dab3-4c31-9807-9bc9d17ef63d",
    occurred_at_ms: now + 60 * 60_000,
    signal,
  }], now) !== null) {
    throw new Error("future event accepted");
  }
});
