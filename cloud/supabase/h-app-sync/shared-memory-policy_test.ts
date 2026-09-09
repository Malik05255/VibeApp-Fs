import {
  isSensitiveSharedMemory,
  normalizeSharedMemoryInput,
} from "./shared-memory-policy.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("shared memory normalizes explicit ordinary facts", () => {
  const memory = normalizeSharedMemoryInput({
    text: "  أفضل القهوة العربية   بدون سكر  ",
    original_text: "احفظ أني أفضل القهوة العربية بدون سكر",
    category: "preference",
  });
  assert(memory?.text === "أفضل القهوة العربية بدون سكر");
  assert(memory?.category === "preference");
});

Deno.test("shared memory rejects secrets and verification codes", () => {
  for (const text of [
    "كلمة المرور عندي هي hunter2",
    "رمز التحقق 123456",
    "my API key is abcdef",
    "card 4111 1111 1111 1111",
  ]) {
    assert(isSensitiveSharedMemory(text));
    assert(normalizeSharedMemoryInput({ text }) === null);
  }
});

Deno.test("unknown categories fail to general without expanding privilege", () => {
  const memory = normalizeSharedMemoryInput({ text: "مشروعي القادم متجر عسل", category: "system" });
  assert(memory?.category === "general");
});
