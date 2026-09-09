import {
  activeHiddenFreeRoutes,
  activeOwnerPaidHelper,
  paidHelperEligibleForTurn,
  parseProviderRoute,
} from "./provider-registry.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

const paid = {
  id: "openrouter_owner_paid",
  provider: "openrouter",
  route_class: "owner_paid",
  credential_id: "openrouter_default",
  selected_model: "vendor/strong-model",
  enabled: true,
  owner_enabled_at: "2026-09-09T20:40:00Z",
  hard_tasks_only: true,
  allow_free_fallback: true,
  daily_call_limit: 10,
  priority: 10,
};

Deno.test("internal free route remains separate and hidden from paid selection", () => {
  const rows = [{
    id: "openrouter_free_hidden",
    provider: "openrouter",
    route_class: "internal_free",
    credential_id: "openrouter_default",
    enabled: true,
    hard_tasks_only: false,
    allow_free_fallback: true,
    priority: 100,
  }, paid];

  const free = activeHiddenFreeRoutes(rows);
  assert(free.length === 1);
  assert(free[0].id === "openrouter_free_hidden");
  assert(activeOwnerPaidHelper(rows)?.id === "openrouter_owner_paid");
});

Deno.test("paid helper requires explicit consent, exact model, credential and daily limit", () => {
  for (const patch of [
    { owner_enabled_at: null },
    { selected_model: null },
    { credential_id: null },
    { daily_call_limit: null },
    { enabled: false },
  ]) {
    assert(activeOwnerPaidHelper([{ ...paid, ...patch }]) === null);
  }
});

Deno.test("multiple active paid helpers fail closed instead of mixing spend", () => {
  const second = { ...paid, id: "other_owner_paid", provider: "other" };
  assert(activeOwnerPaidHelper([paid, second]) === null);
});

Deno.test("hard-tasks-only paid helper is ineligible for ordinary turns", () => {
  const route = activeOwnerPaidHelper([paid]);
  assert(!paidHelperEligibleForTurn(route, "ordinary"));
  assert(paidHelperEligibleForTurn(route, "hard"));
});

Deno.test("owner may explicitly allow a paid helper for ordinary turns", () => {
  const route = activeOwnerPaidHelper([{ ...paid, hard_tasks_only: false }]);
  assert(paidHelperEligibleForTurn(route, "ordinary"));
});

Deno.test("invalid registry rows never become provider routes", () => {
  assert(parseProviderRoute({ ...paid, id: "BAD ID" }) === null);
  assert(parseProviderRoute({ ...paid, provider: "Bad Provider" }) === null);
  assert(parseProviderRoute({ ...paid, route_class: "automatic_paid" }) === null);
});
