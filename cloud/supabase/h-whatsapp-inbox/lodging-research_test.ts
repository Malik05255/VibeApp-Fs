import {
  buildLodgingResearchPlan,
  hasGuestCount,
  hasStayWindow,
  isLodgingPriceDiscovery,
} from "./lodging-research.ts";

Deno.test("detects Arabic cheapest hotel request", () => {
  const query = "يا H شوف لي فندق قريب الحرم بأرخص سعر";
  if (!isLodgingPriceDiscovery(query)) throw new Error("expected lodging price intent");
  const plan = buildLodgingResearchPlan(query);
  if (!plan) throw new Error("expected lodging research plan");
  if (!plan.missingContext.includes("stay_dates")) throw new Error("stay dates must be required");
  if (!plan.missingContext.includes("guest_count")) throw new Error("guest count must be required");
  if (!plan.searchQuery.includes("Booking.com")) throw new Error("booking comparison sources missing");
  if (!plan.hardConstraints.includes("lodging_cheapest_claim_requires_comparable_stay=true")) {
    throw new Error("cheapest-price guard missing");
  }
});

Deno.test("does not specialize a generic hotel recommendation without price or booking intent", () => {
  if (isLodgingPriceDiscovery("أفضل فندق في جدة للعائلات")) {
    throw new Error("generic hotel discovery should stay on local-place research");
  }
});

Deno.test("recognizes explicit stay window and guests", () => {
  const query = "فندق في مكة من 12/10/2026 إلى 14/10/2026 لشخصين 2 أشخاص بأقل سعر";
  if (!hasStayWindow(query)) throw new Error("expected stay window");
  if (!hasGuestCount(query)) throw new Error("expected guest count");
  const plan = buildLodgingResearchPlan(query);
  if (!plan) throw new Error("expected lodging plan");
  if (plan.missingContext.length !== 0) throw new Error(`unexpected missing context: ${plan.missingContext.join(",")}`);
});

Deno.test("recognizes English lodging price request", () => {
  const query = "Find the cheapest hotel near the Haram for 2 guests, check-in 2026-10-12 check-out 2026-10-14";
  if (!isLodgingPriceDiscovery(query)) throw new Error("expected English lodging intent");
  if (!hasStayWindow(query)) throw new Error("expected English stay window");
  if (!hasGuestCount(query)) throw new Error("expected English guest count");
});
