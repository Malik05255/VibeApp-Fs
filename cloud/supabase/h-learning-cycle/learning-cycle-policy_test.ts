import {
  buildLearningCycleMessages,
  extractLearningCandidate,
  learningRetryDelayMinutes,
} from "./learning-cycle-policy.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("Learning Cycle forces stable deep research without exposing internals", () => {
  const messages = buildLearningCycleMessages("من اخترع التقنية؟");
  assert(messages.length === 2);
  assert(messages[1].content.includes("من اخترع التقنية؟"));
  assert(messages[1].content.includes("بحث عميق"));
  const serialized = JSON.stringify(messages).toLowerCase();
  assert(!serialized.includes("api_key"));
  assert(!serialized.includes("service_role"));
});

Deno.test("Learning Cycle accepts only factual reply decisions", () => {
  const valid = extractLearningCandidate(
    "من اخترع التقنية؟",
    JSON.stringify({ action: "reply", reply: "طُورت التقنية على يد فريق بحثي وفق المصادر المتاحة." }),
  );
  assert(valid?.reply.includes("فريق بحثي"));

  assert(extractLearningCandidate(
    "من اخترع التقنية؟",
    JSON.stringify({ action: "create_task", body: "ابحث لاحقًا" }),
  ) === null);
});

Deno.test("Learning Cycle rejects unresolved candidate answers", () => {
  const candidate = extractLearningCandidate(
    "من اخترع التقنية؟",
    JSON.stringify({ action: "reply", reply: "لا أعرف بشكل مؤكد." }),
  );
  assert(candidate === null);
});

Deno.test("Learning Cycle retry backoff is bounded", () => {
  assert(learningRetryDelayMinutes(1) === 60);
  assert(learningRetryDelayMinutes(2) === 360);
  assert(learningRetryDelayMinutes(3) === 1440);
  assert(learningRetryDelayMinutes(8) === 4320);
});
