export type HOwnerPaidTaskClass = "ordinary" | "hard";
export type HOwnerPaidCapability = "text" | "image" | "file";

export type HOwnerPaidSetup = {
  provider: "openrouter";
  selectedModel: string;
  dailyCallLimit: number;
  hardTasksOnly: boolean;
  allowFreeFallback: boolean;
};

export type PricingSnapshot = Record<string, number>;

export function parseOwnerPaidSetup(input: any): HOwnerPaidSetup | null {
  const provider = String(input?.provider || "openrouter").trim().toLowerCase();
  const selectedModel = String(input?.selectedModel ?? input?.selected_model ?? input?.model ?? "").trim();
  const dailyCallLimit = boundedPositiveInt(input?.dailyCallLimit ?? input?.daily_call_limit, 100);
  if (provider !== "openrouter") return null;
  if (!selectedModel || selectedModel.length > 200 || /[\u0000-\u001f]/.test(selectedModel)) return null;
  if (!dailyCallLimit) return null;
  return {
    provider: "openrouter",
    selectedModel,
    dailyCallLimit,
    hardTasksOnly: input?.hardTasksOnly === true || input?.hard_tasks_only === true,
    // No free/paid mixing by default. The owner must explicitly opt in to fallback.
    allowFreeFallback: input?.allowFreeFallback === true || input?.allow_free_fallback === true,
  };
}

/**
 * Converts OpenRouter pricing into a strict numeric ceiling. Unknown non-empty pricing
 * fields fail closed because H cannot prove what the provider may charge for them.
 */
export function normalizePricingSnapshot(pricing: unknown): PricingSnapshot | null {
  if (!pricing || typeof pricing !== "object" || Array.isArray(pricing)) return null;
  const output: PricingSnapshot = {};
  for (const [key, raw] of Object.entries(pricing as Record<string, unknown>)) {
    if (raw == null || raw === "") continue;
    const value = Number(raw);
    if (!Number.isFinite(value) || value < 0) return null;
    output[key] = value;
  }
  if (!Number.isFinite(output.prompt) || !Number.isFinite(output.completion)) return null;
  return output;
}

export function isPotentiallyPaidPricing(snapshot: PricingSnapshot | null): boolean {
  if (!snapshot) return false;
  return Object.values(snapshot).some((value) => value > 0);
}

/**
 * The current catalog is allowed only when every chargeable live component is at or
 * below the exact ceiling the owner accepted when connecting the helper.
 */
export function pricingWithinAuthorizedCeiling(
  livePricing: unknown,
  authorizedCeiling: unknown,
): { ok: true; live: PricingSnapshot; ceiling: PricingSnapshot } | { ok: false; reason: string } {
  const live = normalizePricingSnapshot(livePricing);
  const ceiling = normalizePricingSnapshot(authorizedCeiling);
  if (!live) return { ok: false, reason: "live_pricing_unverifiable" };
  if (!ceiling) return { ok: false, reason: "authorized_pricing_ceiling_missing" };

  for (const [key, liveValue] of Object.entries(live)) {
    if (liveValue <= 0) continue;
    const accepted = ceiling[key];
    if (!Number.isFinite(accepted)) return { ok: false, reason: `new_charge_component:${key}` };
    if (liveValue > accepted + Number.EPSILON) return { ok: false, reason: `price_increased:${key}` };
  }
  return { ok: true, live, ceiling };
}

export function modelSupportsOwnerPaidCapability(model: any, capability: HOwnerPaidCapability): boolean {
  const modalities = Array.isArray(model?.architecture?.input_modalities)
    ? model.architecture.input_modalities.map((value: unknown) => String(value).toLowerCase())
    : [];
  if (!modalities.length) return capability === "text";
  return modalities.includes(capability);
}

export function classifyOwnerPaidTask(
  text: string,
  options: { researchActive?: boolean; media?: boolean } = {},
): HOwnerPaidTaskClass {
  if (options.researchActive || options.media) return "hard";
  const normalized = String(text || "").trim();
  if (normalized.length >= 700) return "hard";
  if (/(?:ابحث\s+(?:بعمق|بشكل\s+عميق)|بحث\s+عميق|حلل|حلّل|قارن|مقارنة\s+شاملة|دراسة|تقرير|برمج|اكتب\s+كود|صحح\s+الكود|debug|deep\s+research|research|analy[sz]e|compare|architecture|refactor)/i.test(normalized)) {
    return "hard";
  }
  return "ordinary";
}

export function lastTextUserMessage(messages: any[]): string {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    if (String(messages[i]?.role || "") !== "user") continue;
    const content = messages[i]?.content;
    if (typeof content === "string") return content;
    if (Array.isArray(content)) {
      return content
        .filter((part: any) => part?.type === "text" && typeof part?.text === "string")
        .map((part: any) => part.text)
        .join("\n")
        .trim();
    }
  }
  return "";
}

function boundedPositiveInt(value: unknown, max: number): number | null {
  const number = Math.floor(Number(value));
  return Number.isFinite(number) && number >= 1 && number <= max ? number : null;
}
