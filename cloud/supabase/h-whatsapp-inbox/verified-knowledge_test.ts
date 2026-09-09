import { recallVerifiedKnowledge } from "./verified-knowledge.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("verified recall skips volatile and sensitive questions before database access", async () => {
  const db = { rpc: () => { throw new Error("rpc_must_not_run"); } };
  assert(await recallVerifiedKnowledge(db, "owner:1", "كم سعر البيتكوين الآن؟") === null);
  assert(await recallVerifiedKnowledge(db, "owner:1", "وش API key حقي؟") === null);
});

Deno.test("verified recall remains owner scoped and uses canonical query key", async () => {
  const calls: Array<Record<string, unknown>> = [];
  const db = {
    rpc: async (name: string, args: Record<string, unknown>) => {
      calls.push({ name, ...args });
      return { data: [{ answer_text: "إجابة موثقة" }], error: null };
    },
  };

  const answer = await recallVerifiedKnowledge(db, "owner:abc", "من اخترع البلوتوث؟");
  const captured = calls[0];
  assert(answer === "إجابة موثقة");
  assert(captured?.["name"] === "h_recall_verified_knowledge");
  assert(captured?.["p_user_key"] === "owner:abc");
  assert(captured?.["p_query_key"] === "51ddde29e6eb3f5f995d104b6ca449479dd9511d77a0a20d9008164eb89e466b");
});

Deno.test("verified recall fails open to the normal AI path on database errors", async () => {
  const db = {
    rpc: async () => ({ data: null, error: new Error("temporary_db_error") }),
  };
  assert(await recallVerifiedKnowledge(db, "owner:1", "من اخترع البلوتوث؟") === null);
});
