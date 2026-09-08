import {
  isStrictlyZeroPricedMediaModel,
  selectStrictlyFreeMediaModel,
} from "./openrouter-media.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("media pricing gate rejects any paid dimension", () => {
  assert(isStrictlyZeroPricedMediaModel({ prompt: "0", completion: "0" }), "zero pricing should pass");
  assert(!isStrictlyZeroPricedMediaModel({ prompt: "0", completion: "0", image: "0.001" }), "paid image fee must fail");
  assert(!isStrictlyZeroPricedMediaModel({ prompt: "0.001", completion: "0" }), "paid prompt must fail");
});

Deno.test("image analysis requires explicit image input support", () => {
  const models = [
    {
      id: "free/text-only",
      context_length: 100000,
      pricing: { prompt: "0", completion: "0" },
      architecture: { input_modalities: ["text"] },
    },
    {
      id: "free/vision",
      context_length: 32000,
      pricing: { prompt: "0", completion: "0" },
      architecture: { input_modalities: ["text", "image"] },
    },
  ];
  assert(selectStrictlyFreeMediaModel(models, null, "image") === "free/vision", "vision model should be selected");
});

Deno.test("paid preferred media model is never used", () => {
  const models = [
    {
      id: "paid/preferred",
      context_length: 200000,
      pricing: { prompt: "0.01", completion: "0.01" },
      architecture: { input_modalities: ["text", "image"] },
    },
    {
      id: "openrouter/free",
      context_length: 16000,
      pricing: { prompt: "0", completion: "0" },
      architecture: { input_modalities: ["text", "image"] },
    },
  ];
  assert(selectStrictlyFreeMediaModel(models, "paid/preferred", "image") === "openrouter/free", "paid preference must be ignored");
});

Deno.test("image analysis fails closed when no free vision model exists", () => {
  const models = [
    {
      id: "free/text-only",
      pricing: { prompt: "0", completion: "0" },
      architecture: { input_modalities: ["text"] },
    },
  ];
  assert(selectStrictlyFreeMediaModel(models, null, "image") === null, "must not fall back to text-only model");
});
