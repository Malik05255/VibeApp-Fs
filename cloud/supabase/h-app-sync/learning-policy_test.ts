import { normalizeLearningBaseline } from "./learning-policy.ts";

Deno.test("learning baseline clamps counters and strips unknown fields and tags", () => {
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
    prompt: "must never be persisted",
    response: "must never be persisted",
  });

  if (!value) throw new Error("expected normalized baseline");
  if (value.directness_score !== 20) throw new Error("score was not clamped");
  if (value.interest_tags.android !== 7 || value.interest_tags.github !== 2) {
    throw new Error("allowed tag counts were lost");
  }
  if ("secret_topic" in value.interest_tags) throw new Error("unknown tag leaked");
  if ("raw_conversation" in value || "prompt" in value || "response" in value) {
    throw new Error("raw content leaked into learning baseline");
  }
});

Deno.test("learning baseline rejects missing or invalid timestamps", () => {
  const now = Date.now();
  const valid = {
    first_met_at_ms: now - 1000,
    last_interaction_at_ms: now,
  };

  if (normalizeLearningBaseline({ ...valid, first_met_at_ms: 0 }) !== null) {
    throw new Error("zero first timestamp accepted");
  }
  if (normalizeLearningBaseline({ ...valid, last_interaction_at_ms: "bad" }) !== null) {
    throw new Error("invalid last timestamp accepted");
  }
});
