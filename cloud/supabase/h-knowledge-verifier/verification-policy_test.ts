import {
  buildVerificationMessages,
  parseVerificationDecision,
} from "./verification-policy.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("verification research query excludes the candidate answer", () => {
  const messages = buildVerificationMessages(
    "من اخترع تقنية البلوتوث؟",
    "اخترعها شخص افتراضي لا ينبغي أن يلوث البحث",
  );
  const latestUser = messages[messages.length - 1]?.content || "";
  assert(latestUser.includes("من اخترع تقنية البلوتوث؟"));
  assert(latestUser.includes("بحث عميق مستقل"));
  assert(!latestUser.includes("شخص افتراضي"));
  assert(messages[0]?.content.includes("UNTRUSTED_CANDIDATE_START"));
});

Deno.test("accepts a bounded verified canonical answer", () => {
  const result = parseVerificationDecision(
    "من اخترع تقنية البلوتوث؟",
    JSON.stringify({ action: "reply", reply: "H_VERIFY_OK: الإجابة الموثقة هنا." }),
  );
  assert(result?.verified === true);
  assert(result?.answer === "الإجابة الموثقة هنا.");
  assert(result?.error === null);
});

Deno.test("reject marker never promotes an answer", () => {
  const result = parseVerificationDecision(
    "من اخترع التقنية؟",
    JSON.stringify({ action: "reply", reply: "H_VERIFY_REJECT: الأدلة غير كافية." }),
  );
  assert(result?.verified === false);
  assert(result?.answer === null);
  assert(result?.error === "الأدلة غير كافية.");
});

Deno.test("OK marker still fails closed when answer remains uncertain", () => {
  const result = parseVerificationDecision(
    "من اخترع التقنية؟",
    JSON.stringify({ action: "reply", reply: "H_VERIFY_OK: لا أعرف بشكل مؤكد." }),
  );
  assert(result?.verified === false);
  assert(result?.answer === null);
  assert(result?.error === "verification_answer_still_uncertain");
});

Deno.test("non reply or unmarked output is invalid", () => {
  assert(parseVerificationDecision(
    "سؤال؟",
    JSON.stringify({ action: "save_memory", reply: "H_VERIFY_OK: شيء" }),
  ) === null);
  assert(parseVerificationDecision(
    "سؤال؟",
    JSON.stringify({ action: "reply", reply: "شيء بلا علامة تحقق" }),
  ) === null);
});
