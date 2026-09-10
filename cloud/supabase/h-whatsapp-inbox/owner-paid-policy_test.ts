import {
  classifyOwnerPaidTask,
  isPotentiallyPaidPricing,
  modelSupportsOwnerPaidCapability,
  normalizePricingSnapshot,
  parseOwnerPaidSetup,
  pricingWithinAuthorizedCeiling,
} from "./owner-paid-policy.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("owner-paid setup is always no-fallback and all-turn", () => {
  const setup = parseOwnerPaidSetup({ provider: "openrouter", model: "vendor/model", dailyCallLimit: 5 });
  assert(setup?.selectedModel === "vendor/model");
  assert(setup?.dailyCallLimit === 5);
  assert(setup?.hardTasksOnly === false);
  assert(setup?.allowFreeFallback === false);
});

Deno.test("legacy setup flags cannot re-enable segmentation or free fallback", () => {
  const setup = parseOwnerPaidSetup({
    provider: "openrouter",
    model: "vendor/model",
    dailyCallLimit: 5,
    hardTasksOnly: true,
    allowFreeFallback: true,
  });
  assert(setup?.hardTasksOnly === false);
  assert(setup?.allowFreeFallback === false);
});

Deno.test("invalid provider/model/limit fails closed", () => {
  assert(parseOwnerPaidSetup({ provider: "other", model: "x/y", dailyCallLimit: 2 }) === null);
  assert(parseOwnerPaidSetup({ provider: "openrouter", model: "", dailyCallLimit: 2 }) === null);
  assert(parseOwnerPaidSetup({ provider: "openrouter", model: "x/y", dailyCallLimit: 0 }) === null);
  assert(parseOwnerPaidSetup({ provider: "openrouter", model: "x/y", dailyCallLimit: 101 }) === null);
});

Deno.test("pricing snapshot requires verifiable non-negative numeric pricing", () => {
  const snapshot = normalizePricingSnapshot({ prompt: "0.000001", completion: "0.000002", image: "0" });
  assert(snapshot?.prompt === 0.000001);
  assert(isPotentiallyPaidPricing(snapshot));
  assert(normalizePricingSnapshot({ prompt: "unknown", completion: "0" }) === null);
  assert(normalizePricingSnapshot({ prompt: "-1", completion: "0" }) === null);
});

Deno.test("live price may decrease but never exceed owner-authorized ceiling", () => {
  assert(pricingWithinAuthorizedCeiling(
    { prompt: "0.0000005", completion: "0.000002" },
    { prompt: 0.000001, completion: 0.000002 },
  ).ok);
  const increased = pricingWithinAuthorizedCeiling(
    { prompt: "0.000003", completion: "0.000002" },
    { prompt: 0.000001, completion: 0.000002 },
  );
  assert(!increased.ok && increased.reason === "price_increased:prompt");
});

Deno.test("new nonzero charge component blocks the call", () => {
  const result = pricingWithinAuthorizedCeiling(
    { prompt: "0.000001", completion: "0.000002", image: "0.01" },
    { prompt: 0.000001, completion: 0.000002 },
  );
  assert(!result.ok && result.reason === "new_charge_component:image");
});

Deno.test("capability and task classification stay deterministic", () => {
  const vision = { architecture: { input_modalities: ["text", "image"] } };
  assert(modelSupportsOwnerPaidCapability(vision, "image"));
  assert(!modelSupportsOwnerPaidCapability(vision, "file"));
  assert(classifyOwnerPaidTask("هلا") === "ordinary");
  assert(classifyOwnerPaidTask("ابحث بعمق وقارن بين الخيارات") === "hard");
  assert(classifyOwnerPaidTask("x", { researchActive: true }) === "hard");
  assert(classifyOwnerPaidTask("x", { media: true }) === "hard");
});
