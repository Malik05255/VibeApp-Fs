export type LodgingResearchPlan = {
  active: true;
  query: string;
  searchQuery: string;
  missingContext: string[];
  hardConstraints: string[];
  context: string;
};

const ARABIC_MONTHS = "يناير|فبراير|مارس|ابريل|أبريل|مايو|يونيو|يوليو|اغسطس|أغسطس|سبتمبر|اكتوبر|أكتوبر|نوفمبر|ديسمبر";
const ENGLISH_MONTHS = "january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec";

export function buildLodgingResearchPlan(query: string): LodgingResearchPlan | null {
  const text = String(query || "").trim();
  if (!isLodgingPriceDiscovery(text)) return null;

  const missingContext: string[] = [];
  if (!hasStayWindow(text)) missingContext.push("stay_dates");
  if (!hasGuestCount(text)) missingContext.push("guest_count");

  const hardConstraints = [
    "lodging_price_claim_requires_explicit_source=true",
    "lodging_cheapest_claim_requires_comparable_stay=true",
    "lodging_price_must_include_tax_fee_basis=true",
    "lodging_room_and_occupancy_must_match=true",
    "lodging_availability_must_be_current=true",
    "lodging_distance_claim_requires_explicit_evidence=true",
    ...missingContext.map((item) => `missing_${item}=true`),
  ];

  const searchQuery = [
    text,
    "hotel room rate total price taxes fees availability",
    "Booking.com Agoda Expedia Hotels.com",
    "official hotel direct booking rate",
    "Saudi Arabia SAR",
  ].join(" ");

  const context = [
    "H_LODGING_PRICE_RESEARCH_CONTEXT",
    `Original request: ${text}`,
    `Missing stay context: ${missingContext.join(", ") || "none"}`,
    `Hard constraints: ${hardConstraints.join(" | ")}`,
    "",
    "LODGING RULES:",
    "- Treat hotel prices as time-sensitive inventory. Never reuse a remembered rate.",
    "- Do not call any hotel the cheapest unless at least two comparable bookable offers are evidenced for the same stay dates, guest count, room/occupancy basis and currency.",
    "- A price claim must identify whether taxes/fees are included or excluded. If the source does not say, label that basis unknown.",
    "- Distinguish nightly price from total-stay price. Never convert one into the other without explicit nights and arithmetic supported by the stay window.",
    "- Prefer the hotel's official booking page plus major booking platforms when evidence is available. Do not assume one platform is globally cheapest.",
    "- Do not invent availability, cancellation terms, breakfast, room category, distance, shuttle service, star rating, review score or review count.",
    "- If stay dates are missing, do not present a numeric price as the user's actual bookable price and do not declare a cheapest option. Ask for check-in and check-out dates.",
    "- If guest count is missing, state that occupancy can change the price and ask for guest count before a final cheapest-price conclusion.",
    "- When the user asks for a landmark such as الحرم, only claim proximity when an explicit source supports the distance/location relationship.",
    "- Preserve useful verified hotel candidates even when final price comparison cannot yet be completed.",
  ].join("\n");

  return { active: true, query: text, searchQuery, missingContext, hardConstraints, context };
}

export function isLodgingPriceDiscovery(query: string): boolean {
  const q = normalizeArabic(String(query || ""));
  const lodging = /(فندق|فنادق|سكن|اقامة|hotel|hotels|lodging|accommodation)/i.test(q);
  const commercial = /(ارخص|اقل\s*سعر|سعر|اسعار|حجز|احجز|عرض|عروض|خصم|ليلة|ليلتين|booking|book|cheapest|lowest\s*price|rate|rates|deal|deals|price)/i.test(q);
  return lodging && commercial;
}

export function hasStayWindow(query: string): boolean {
  const q = String(query || "");
  if (/(?:من|دخول|check[ -]?in)\s+.{1,40}(?:الى|إلى|حتى|خروج|check[ -]?out)/i.test(q)) return true;

  const numericDates = q.match(/\b(?:\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}(?:[-/]\d{2,4})?)\b/g) || [];
  if (numericDates.length >= 2) return true;

  const monthPattern = new RegExp(`\\b\\d{1,2}\\s+(?:${ARABIC_MONTHS}|${ENGLISH_MONTHS})(?:\\s+\\d{2,4})?`, "gi");
  const monthDates = q.match(monthPattern) || [];
  return monthDates.length >= 2;
}

export function hasGuestCount(query: string): boolean {
  const normalized = String(query || "").replace(/[٠-٩]/g, (digit) => String("٠١٢٣٤٥٦٧٨٩".indexOf(digit)));
  return /\b\d+\s*(?:شخص|اشخاص|أشخاص|بالغ|بالغين|ضيف|ضيوف|guests?|adults?|people|persons?)\b/i.test(normalized);
}

function normalizeArabic(value: string): string {
  return value
    .replace(/[إأآ]/g, "ا")
    .replace(/ى/g, "ي")
    .replace(/ؤ/g, "و")
    .replace(/ئ/g, "ي")
    .replace(/[ًٌٍَُِّْـ]/g, "");
}
