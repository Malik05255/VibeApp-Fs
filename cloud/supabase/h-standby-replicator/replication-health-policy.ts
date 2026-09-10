export const MAX_REPLICATION_LAG_SECONDS = 120;
export const REPLICATION_PROTOCOL = "exact_mirror_v2";

export function replicationHealthEligible(health: any): boolean {
  const lag = finiteNumber(health?.replicationLagSeconds);
  return health?.ok === true &&
    health?.service === "h-standby-health" &&
    health?.runtimeRole === "standby" &&
    health?.hIdentity === "H" &&
    health?.promoted !== true &&
    health?.restoreVerified === true &&
    health?.replicationMode === "continuous" &&
    health?.replicationProtocol === REPLICATION_PROTOCOL &&
    health?.replicationFresh === true &&
    health?.appIdentityRekeyReady === true &&
    health?.whatsappIdentityRekeyReady === true &&
    lag != null &&
    lag <= MAX_REPLICATION_LAG_SECONDS;
}

export function compactReplicationHealthReason(health: any): string {
  if (!health || typeof health !== "object") return "invalid_response";
  if (health?.runtimeRole !== "standby" || health?.hIdentity !== "H" || health?.promoted === true) {
    return "runtime_identity_mismatch";
  }
  if (health?.appIdentityRekeyReady !== true || health?.whatsappIdentityRekeyReady !== true) {
    return "identity_replica_not_ready";
  }
  if (health?.replicationProtocol !== REPLICATION_PROTOCOL || health?.replicationFresh !== true) {
    return "replication_protocol_not_ready";
  }
  const lag = finiteNumber(health?.replicationLagSeconds);
  if (lag == null || lag > MAX_REPLICATION_LAG_SECONDS) return "replication_stale";
  return "health_contract_mismatch";
}

function finiteNumber(value: unknown): number | null {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}
