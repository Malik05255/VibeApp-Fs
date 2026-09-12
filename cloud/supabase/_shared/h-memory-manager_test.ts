import { assertEquals } from "jsr:@std/assert@1";
import { hasExplicitMemorySaveIntent, normalizeMemoryBody, parseExplicitMemoryMutation, memoryMutationReply } from "./h-memory-manager.ts";

Deno.test("parses explicit Arabic correction without broad semantic guessing", () => {
  assertEquals(parseExplicitMemoryMutation("صحح أحب القهوة سوداء إلى أحب القهوة بحليب"), {
    action: "correct",
    oldBody: "أحب القهوة سوداء",
    newBody: "أحب القهوة بحليب",
  });
  assertEquals(parseExplicitMemoryMutation("أنا غيرت رأيي بالقهوة"), null);
});

Deno.test("parses explicit Arabic and English forget commands", () => {
  assertEquals(parseExplicitMemoryMutation("انسَ أحب القهوة سوداء"), {
    action: "forget",
    body: "أحب القهوة سوداء",
  });
  assertEquals(parseExplicitMemoryMutation("forget I prefer window seats"), {
    action: "forget",
    body: "I prefer window seats",
  });
});

Deno.test("durable AI memory requires explicit owner save intent", () => {
  assertEquals(hasExplicitMemorySaveIntent("تذكر أني أفضل المقعد عند النافذة"), true);
  assertEquals(hasExplicitMemorySaveIntent("remember that I prefer window seats"), true);
  assertEquals(hasExplicitMemorySaveIntent("أنا أفضل المقعد عند النافذة"), false);
  assertEquals(hasExplicitMemorySaveIntent("ذكرني بعد ساعة أشرب ماء"), false);
});

Deno.test("normalization is bounded and deterministic", () => {
  assertEquals(normalizeMemoryBody("  أ   ب  "), "أ ب");
  assertEquals(normalizeMemoryBody("x".repeat(400)).length, 280);
});

Deno.test("not-found mutation reply stays fail closed", () => {
  const correction = memoryMutationReply({ ok: false, matched: false }, "correct");
  const forget = memoryMutationReply({ ok: false, matched: false }, "forget");
  assertEquals(correction.includes("ما غيّرت أي شيء"), true);
  assertEquals(forget.includes("ما حذفت أي شيء"), true);
});
