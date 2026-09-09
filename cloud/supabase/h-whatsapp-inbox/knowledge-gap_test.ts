import {
  assessKnowledgeGap,
  enqueueKnowledgeGap,
  isSensitiveKnowledgeGap,
  isVolatileKnowledgeGap,
} from "./knowledge-gap.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("queues explicit factual uncertainty", () => {
  const result = assessKnowledgeGap({
    query: "من أسس هذه الشركة؟",
    reply: "لا أعرف بشكل مؤكد.",
  });
  assert(result.shouldQueue);
  assert(result.reason === "explicit_uncertainty");
});

Deno.test("queues stable research with no evidence and unavailable free tools", () => {
  const result = assessKnowledgeGap({
    query: "من اخترع هذه التقنية؟",
    researchActive: true,
    evidenceCount: 0,
    providerTrace: ["tavily_not_connected", "exa_free_only_guard"],
    priority: "important",
  });
  assert(result.shouldQueue);
  assert(result.reason === "tool_unavailable");
  assert(result.priority === "important");
});

Deno.test("verifier rejection becomes a knowledge gap", () => {
  const result = assessKnowledgeGap({
    query: "هل هذا الادعاء التاريخي صحيح؟",
    verifierOk: false,
    researchActive: true,
    evidenceCount: 2,
  });
  assert(result.shouldQueue);
  assert(result.reason === "verifier_rejected");
});

Deno.test("ordinary actions and greetings never enter learning queue", () => {
  assert(!assessKnowledgeGap({ query: "ذكرني بكرة الساعة 8" }).shouldQueue);
  assert(!assessKnowledgeGap({ query: "اكتب لي رسالة اعتذار" }).shouldQueue);
  assert(!assessKnowledgeGap({ query: "السلام عليكم" }).shouldQueue);
});

Deno.test("volatile facts never enter durable learning queue", () => {
  for (const query of [
    "كم سعر البيتكوين الآن؟",
    "وش الطقس اليوم؟",
    "وش نتيجة المباراة الآن؟",
    "ما هو آخر تصريح رسمي؟",
    "هل المتجر مفتوح الآن؟",
    "what is the current stock price?",
  ]) {
    assert(isVolatileKnowledgeGap(query), `expected volatile query: ${query}`);
    const result = assessKnowledgeGap({ query, reply: "لا أعرف", verifierOk: false });
    assert(!result.shouldQueue, `volatile query must not be queued: ${query}`);
  }
});

Deno.test("sensitive questions fail closed", () => {
  assert(isSensitiveKnowledgeGap("وش كلمة المرور لحسابي password 123؟"));
  const result = assessKnowledgeGap({
    query: "ما هو API key الخاص بي؟",
    reply: "لا أعرف",
  });
  assert(!result.shouldQueue);
  assert(result.sensitive);
});

Deno.test("verified answer with evidence is not queued", () => {
  const result = assessKnowledgeGap({
    query: "متى افتتح المكان؟",
    reply: "افتتح في 2024 حسب المصدر.",
    researchActive: true,
    evidenceCount: 3,
    providerTrace: ["tavily:basic:3"],
    verifierOk: true,
  });
  assert(!result.shouldQueue);
});

Deno.test("queue writer sends only bounded gap metadata through server RPC", async () => {
  let captured: Record<string, unknown> | null = null;
  const db = {
    rpc(name: string, params: Record<string, unknown>) {
      assert(name === "h_enqueue_knowledge_gap");
      captured = params;
      return Promise.resolve({
        data: [{ id: "gap-1", status: "pending", occurrences: 2 }],
        error: null,
      });
    },
  };

  const assessment = assessKnowledgeGap({
    query: "  من   اخترع   التقنية؟  ",
    reply: "لا أعرف",
  });
  const result = await enqueueKnowledgeGap(db, "owner-key", assessment);

  assert(result.queued);
  assert(result.id === "gap-1");
  assert(result.occurrences === 2);
  if (captured === null) throw new Error("RPC params were not captured");
  const params: Record<string, unknown> = captured;
  assert(params.p_query_text === "من اخترع التقنية؟");
  assert(typeof params.p_query_key === "string" && String(params.p_query_key).length === 64);
  assert(params.p_reason === "explicit_uncertainty");
  assert(!JSON.stringify(params).includes("لا أعرف"), "reply text must not enter the durable queue");
});
