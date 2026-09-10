export type HProviderRouteClass = "internal_free" | "owner_paid";

export type HProviderRoute = {
  id: string;
  provider: string;
  routeClass: HProviderRouteClass;
  credentialId: string | null;
  selectedModel: string | null;
  enabled: boolean;
  ownerEnabledAt: string | null;
  hardTasksOnly: boolean;
  allowFreeFallback: boolean;
  dailyCallLimit: number | null;
  priority: number;
};

/**
 * Converts an untrusted database row into a bounded H provider route. Invalid rows are
 * ignored rather than becoming an accidental helper path.
 *
 * Legacy owner-paid policy columns are retained for storage compatibility only. An active
 * owner-paid/BYOK route is always normalized to all-turn routing with no automatic free
 * fallback, so old rows cannot silently re-enable provider mixing.
 */
export function parseProviderRoute(row: any): HProviderRoute | null {
  const id = String(row?.id || "").trim();
  const provider = String(row?.provider || "").trim().toLowerCase();
  const routeClass = String(row?.route_class || "").trim();
  if (!/^[a-z0-9][a-z0-9._:-]{1,95}$/.test(id)) return null;
  if (!/^[a-z0-9][a-z0-9._-]{1,63}$/.test(provider)) return null;
  if (routeClass !== "internal_free" && routeClass !== "owner_paid") return null;

  const credentialId = nullableText(row?.credential_id, 160);
  const selectedModel = nullableText(row?.selected_model, 200);
  const dailyCallLimit = nullablePositiveInt(row?.daily_call_limit, 10_000);
  const priority = boundedInt(row?.priority, 0, 10_000, 100);
  const ownerEnabledAt = nullableIsoDate(row?.owner_enabled_at);
  const ownerPaid = routeClass === "owner_paid";

  return {
    id,
    provider,
    routeClass,
    credentialId,
    selectedModel,
    enabled: row?.enabled === true,
    ownerEnabledAt,
    hardTasksOnly: ownerPaid ? false : row?.hard_tasks_only !== false,
    allowFreeFallback: ownerPaid ? false : row?.allow_free_fallback !== false,
    dailyCallLimit,
    priority,
  };
}

export function activeHiddenFreeRoutes(rows: any[]): HProviderRoute[] {
  return rows
    .map(parseProviderRoute)
    .filter((route): route is HProviderRoute => Boolean(
      route && route.routeClass === "internal_free" && route.enabled,
    ))
    .sort((a, b) => a.priority - b.priority || a.id.localeCompare(b.id));
}

/**
 * Returns the single explicitly owner-authorized paid/BYOK helper. Any ambiguity fails
 * closed so H never silently mixes paid helpers.
 */
export function activeOwnerPaidHelper(rows: any[]): HProviderRoute | null {
  const active = rows
    .map(parseProviderRoute)
    .filter((route): route is HProviderRoute => Boolean(
      route &&
      route.routeClass === "owner_paid" &&
      route.enabled &&
      route.ownerEnabledAt &&
      route.credentialId &&
      route.selectedModel &&
      route.dailyCallLimit,
    ));

  return active.length === 1 ? active[0] : null;
}

/**
 * Once the owner enables a valid paid/BYOK route it is eligible for every AI turn.
 * taskClass remains in the signature temporarily for source compatibility with existing
 * callers, but it no longer segments paid routing.
 */
export function paidHelperEligibleForTurn(
  route: HProviderRoute | null,
  _taskClass: "ordinary" | "hard",
): boolean {
  if (!route || route.routeClass !== "owner_paid" || !route.enabled) return false;
  if (!route.ownerEnabledAt || !route.credentialId || !route.selectedModel || !route.dailyCallLimit) return false;
  return true;
}

function nullableText(value: unknown, max: number): string | null {
  const text = String(value ?? "").trim();
  return text && text.length <= max ? text : null;
}

function nullablePositiveInt(value: unknown, max: number): number | null {
  if (value == null || value === "") return null;
  const number = Math.floor(Number(value));
  return Number.isFinite(number) && number >= 1 && number <= max ? number : null;
}

function boundedInt(value: unknown, min: number, max: number, fallback: number): number {
  const number = Math.floor(Number(value));
  return Number.isFinite(number) && number >= min && number <= max ? number : fallback;
}

function nullableIsoDate(value: unknown): string | null {
  const raw = String(value ?? "").trim();
  if (!raw) return null;
  const date = new Date(raw);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
