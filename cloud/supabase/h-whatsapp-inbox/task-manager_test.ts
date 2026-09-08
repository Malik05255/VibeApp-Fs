import {
  classifyTaskPriority,
  detectExplicitPriority,
  executionPlanForPriority,
  normalizePriority,
} from "./task-manager.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("explicit Arabic task priority is detected", () => {
  assert(detectExplicitPriority("ذكرني بعد ساعة أراجع الملف، التصنيف: مهمة") === "important", "important priority must be detected");
  assert(detectExplicitPriority("تعامل معها ك متوسطة") === "medium", "medium priority must be detected");
  assert(detectExplicitPriority("[بسيطة] ذكرني بعد 5 دقائق") === "simple", "simple priority must be detected");
});

Deno.test("priority aliases normalize safely", () => {
  assert(normalizePriority("مهم") === "important", "important alias");
  assert(normalizePriority("متوسطة") === "medium", "medium alias");
  assert(normalizePriority("بسيط") === "simple", "simple alias");
  assert(normalizePriority("unknown") === null, "unknown priority must fail closed");
});

Deno.test("auto classifier increases effort for research and comparison", () => {
  assert(classifyTaskPriority("ذكرني بعد عشر دقائق أشرب ماء", "reminder") === "simple", "basic reminder should be simple");
  assert(classifyTaskPriority("قارن لي لابتوبات تحت 1500 ريال") === "medium", "shopping comparison should be medium");
  assert(classifyTaskPriority("سو لي بحث عميق وتحقق من عدة مصادر بدون تخمين") === "important", "deep verified research should be important");
});

Deno.test("important execution plan is stricter than simple", () => {
  const simple = executionPlanForPriority("simple") as any;
  const important = executionPlanForPriority("important") as any;
  assert(important.source_target > simple.source_target, "important tasks need more sources");
  assert(important.max_fallbacks > simple.max_fallbacks, "important tasks need more fallbacks");
  assert(important.cross_verify === true, "important tasks must cross verify");
  assert(important.allow_unverified_claims === false, "unverified claims must always be blocked");
});
