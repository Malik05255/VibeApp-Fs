import routedWorker, { selectUnifiedRuntime, standbyRuntimeConfigured } from "./router.js";

/**
 * External failover monitor for the already-configured H standby.
 *
 * The monitor never sends a WhatsApp message and never switches traffic itself. It only
 * reuses the same fail-closed runtime selector used by ingress. If the primary fails both
 * health probes and the standby is fresh/passive-preflight-ready, the selector may perform
 * request-only promotion. Scheduler and autonomous outbound remain disabled by the standby
 * promotion contract.
 */
export async function monitorStandbyPromotion(env, fetchImpl = fetch) {
  if (!standbyRuntimeConfigured(env)) {
    return { checked: false, standbyConfigured: false, selectedRole: null };
  }

  try {
    const selected = await selectUnifiedRuntime(env, fetchImpl);
    return {
      checked: true,
      standbyConfigured: true,
      selectedRole: selected.role === "standby" ? "standby" : "primary",
    };
  } catch {
    // Fail closed. A scheduled monitor must never relax readiness because neither runtime
    // could be positively attested during this observation.
    return {
      checked: true,
      standbyConfigured: true,
      selectedRole: null,
      error: "no_validated_runtime",
    };
  }
}

export default {
  fetch(request, env, ctx) {
    return routedWorker.fetch(request, env, ctx);
  },

  async scheduled(event, env, ctx) {
    if (standbyRuntimeConfigured(env)) {
      ctx.waitUntil(
        monitorStandbyPromotion(env).then((result) => {
          if (result.error) {
            console.warn("H standby promotion monitor failed closed");
          }
        }).catch(() => {
          console.warn("H standby promotion monitor failed closed");
        }),
      );
    }

    // Preserve every existing reminder/legacy scheduled task unchanged.
    return routedWorker.scheduled(event, env, ctx);
  },
};
