import {
  isStrictlyZeroPriced,
  selectStrictlyFreeModel,
  selectStrictlyFreeModelForInput,
} from "./openrouter-ai.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("strict pricing accepts only all-zero numeric pricing", () => {
  assert(isStrictlyZeroPriced({ prompt: "0", completion: "0", request: "0" }), "zero pricing should pass");
  assert(!isStrictlyZeroPriced({ prompt: "0.000001", completion: "0" }), "paid prompt must fail");
  assert(!isStrictlyZeroPriced({ prompt: "0", completion: "0", request: "0.01" }), "paid request fee must fail");
  assert(!isStrictlyZeroPriced({ prompt: "0" }), "missing completion pricing must fail");
  assert(!isStrictlyZeroPriced({ prompt: "0", completion: "unknown" }), "unparseable pricing must fail");
});

Deno.test("preferred model is used only when it is strictly free", () => {
  const models = [
    { id: "paid/preferred", context_length: 999999, pricing: { prompt: "0.1", completion: "0.1" } },
    { id: "free/verified", context_length: 32000, pricing: { prompt: "0", completion: "0" } },
  ];
  assert(selectStrictlyFreeModel(models, "paid/preferred") === "free/verified", "paid preference must be ignored");
});

Deno.test("openrouter free router wins when catalog proves it is zero-priced", () => {
  const models = [
    { id: "free/large", context_length: 100000, pricing: { prompt: "0", completion: "0" } },
    { id: "openrouter/free", context_length: 8000, pricing: { prompt: "0", completion: "0" } },
  ];
  assert(selectStrictlyFreeModel(models, null) === "openrouter/free", "free router should be preferred");
});

Deno.test("no zero-priced model returns null instead of paid fallback", () => {
  const models = [
    { id: "paid/a", pricing: { prompt: "0.1", completion: "0.2" } },
    { id: "paid/b", pricing: { prompt: "0", completion: "0.2" } },
  ];
  assert(selectStrictlyFreeModel(models, null) === null, "must fail closed without a free model");
});

Deno.test("image analysis selects only zero-priced vision-capable models", () => {
  const models = [
    {
      id: "free/text-only",
      context_length: 200000,
      pricing: { prompt: "0", completion: "0", image: "0" },
      architecture: { input_modalities: ["text"] },
    },
    {
      id: "paid/vision",
      context_length: 300000,
      pricing: { prompt: "0", completion: "0", image: "0.001" },
      architecture: { input_modalities: ["text", "image"] },
    },
    {
      id: "free/vision",
      context_length: 64000,
      pricing: { prompt: "0", completion: "0", image: "0" },
      architecture: { input_modalities: ["text", "image"] },
    },
  ];
  assert(
    selectStrictlyFreeModelForInput(models, "paid/vision", "image") === "free/vision",
    "vision path must ignore paid and text-only models",
  );
});

Deno.test("image analysis fails closed when only paid or non-vision models exist", () => {
  const models = [
    {
      id: "free/text-only",
      pricing: { prompt: "0", completion: "0" },
      architecture: { input_modalities: ["text"] },
    },
    {
      id: "paid/vision",
      pricing: { prompt: "0.01", completion: "0.01", image: "0" },
      architecture: { input_modalities: ["text", "image"] },
    },
  ];
  assert(
    selectStrictlyFreeModelForInput(models, null, "image") === null,
    "must not fall back to paid vision",
  );
});
