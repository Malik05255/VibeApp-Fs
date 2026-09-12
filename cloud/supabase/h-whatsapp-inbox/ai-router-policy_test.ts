import {
  classifyRouteFailure,
  isStrictlyZeroPriced,
  rankStrictlyFreeModelCandidates,
} from "./ai-router-policy.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

const models = [
  {
    id: "openrouter/free",
    context_length: 32000,
    pricing: { prompt: "0", completion: "0" },
    architecture: { input_modalities: ["text"] },
  },
  {
    id: "free/large",
    context_length: 128000,
    pricing: { prompt: "0", completion: "0" },
    architecture: { input_modalities: ["text"] },
  },
  {
    id: "free/vision",
    context_length: 64000,
    pricing: { prompt: "0", completion: "0", image: "0" },
    architecture: { input_modalities: ["text", "image"] },
  },
  {
    id: "free-looking/unknown-image-price",
    context_length: 64000,
    pricing: { prompt: "0", completion: "0", image: null },
    architecture: { input_modalities: ["text", "image"] },
  },
  {
    id: "paid/huge",
    context_length: 1000000,
    pricing: { prompt: "0.001", completion: "0" },
    architecture: { input_modalities: ["text", "image"] },
  },
];

Deno.test("AI Router never includes a paid model", () => {
  const ranked = rankStrictlyFreeModelCandidates(models, "paid/huge", "text");
  assert(!ranked.includes("paid/huge"));
  assert(ranked.length >= 2);
  assert(isStrictlyZeroPriced(models[0].pricing));
});

Deno.test("unknown or blank pricing dimensions fail closed", () => {
  assert(!isStrictlyZeroPriced({ prompt: "0", completion: "0", image: null }));
  assert(!isStrictlyZeroPriced({ prompt: "0", completion: "0", image: "" }));
  assert(!isStrictlyZeroPriced({ prompt: "0", completion: "0", image: "   " }));
  assert(!isStrictlyZeroPriced({ prompt: "0", completion: "0", image: false }));
  assert(!isStrictlyZeroPriced({ prompt: "0" }));
  assert(!isStrictlyZeroPriced({ prompt: "0", completion: undefined }));
  assert(isStrictlyZeroPriced({ prompt: 0, completion: "0", image: "0.000" }));
});

Deno.test("preferred free route wins while healthy", () => {
  const ranked = rankStrictlyFreeModelCandidates(models, "free/large", "text");
  assert(ranked[0] === "free/large");
});

Deno.test("active cooldown removes an exhausted free route", () => {
  const now = Date.parse("2026-09-09T20:00:00Z");
  const ranked = rankStrictlyFreeModelCandidates(
    models,
    "free/large",
    "text",
    [{
      model: "free/large",
      capability: "text",
      attempts: 10,
      successes: 9,
      cooldown_until: "2026-09-09T20:10:00Z",
    }],
    null,
    now,
  );
  assert(!ranked.includes("free/large"));
  assert(ranked.includes("openrouter/free"));
});

Deno.test("verifier prefers a different healthy free model", () => {
  const ranked = rankStrictlyFreeModelCandidates(models, null, "text", [], "openrouter/free");
  assert(ranked[0] !== "openrouter/free");
  assert(ranked.includes("openrouter/free"));
});

Deno.test("vision routing excludes text-only, paid, and unknown-price vision routes", () => {
  const ranked = rankStrictlyFreeModelCandidates(models, null, "image");
  assert(ranked.length === 1);
  assert(ranked[0] === "free/vision");
  assert(!ranked.includes("free-looking/unknown-image-price"));
});

Deno.test("429 retries another free route with bounded cooldown", () => {
  const decision = classifyRouteFailure(429, "120", "rate limit", Date.parse("2026-09-09T20:00:00Z"));
  assert(decision.retryNext);
  assert(!decision.providerFatal);
  assert(decision.reason === "rate_limited");
  assert(decision.cooldownMs === 120000);
});

Deno.test("credential and billing failures never fan out across models", () => {
  for (const status of [401, 403, 402]) {
    const decision = classifyRouteFailure(status, null, "rejected");
    assert(decision.providerFatal);
    assert(!decision.retryNext);
  }
});

Deno.test("server and model failures allow strictly free failover", () => {
  assert(classifyRouteFailure(503, null).retryNext);
  assert(classifyRouteFailure(404, null).retryNext);
  assert(classifyRouteFailure(null, null, "network error").retryNext);
});
