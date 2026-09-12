import { assessKnowledgeGap } from "../h-whatsapp-inbox/knowledge-gap.ts";
import { extractLearningCandidate } from "./learning-cycle-policy.ts";
import { parseVerificationDecision } from "../h-knowledge-verifier/verification-policy.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("unknown -> research candidate -> independent verification -> re-ask becomes answerable", () => {
  const query = "ما اسم أول قمر صناعي أطلقته البشرية؟";
  const initial = assessKnowledgeGap({ query, reply: "لا أعرف بشكل مؤكد." });
  assert(initial.shouldQueue, "the initial uncertain answer must enter the learning queue");

  const candidate = extractLearningCandidate(
    query,
    JSON.stringify({ action: "reply", reply: "سبوتنيك 1 هو أول قمر صناعي أطلقته البشرية عام 1957." }),
  );
  assert(candidate, "deep research must be able to produce a bounded candidate");

  const verified = parseVerificationDecision(
    query,
    JSON.stringify({ action: "reply", reply: "H_VERIFY_OK: سبوتنيك 1 هو أول قمر صناعي أطلقته البشرية عام 1957." }),
  );
  assert(verified?.verified === true && verified.answer, "independent verification must promote a canonical answer");

  const reasked = assessKnowledgeGap({ query, reply: verified.answer });
  assert(!reasked.shouldQueue, "the verified answer must no longer look like an unresolved knowledge gap");
});

Deno.test("rejected or still-uncertain verification never marks learning as complete", () => {
  const query = "ما حقيقة معلومة غير موثقة؟";
  const rejected = parseVerificationDecision(
    query,
    JSON.stringify({ action: "reply", reply: "H_VERIFY_REJECT: الأدلة غير كافية للتحقق." }),
  );
  assert(rejected?.verified === false && rejected.answer === null);

  const uncertainOk = parseVerificationDecision(
    query,
    JSON.stringify({ action: "reply", reply: "H_VERIFY_OK: لا أعرف بشكل مؤكد." }),
  );
  assert(uncertainOk?.verified === false, "an accidental OK marker must not promote an uncertain answer");
});
