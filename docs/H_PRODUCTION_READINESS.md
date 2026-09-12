# H Production Readiness

H has two evidence classes:

- `CODE_READY`: deterministic CI or production schema/runtime evidence exists for the contract.
- `LIVE_EXTERNAL_REQUIRED`: the contract depends on an external resource or owner credential and must not be reported as live until that real resource is connected and exercised.

| Gate | State | Required evidence |
|---|---|---|
| 1. Backup Cloud | LIVE_EXTERNAL_REQUIRED | A second real cloud must pass storage write/read/delete validation, encrypted backup, standby runtime provisioning, and registry health. |
| 2. Primary -> Standby live failover | LIVE_EXTERNAL_REQUIRED | Kill/unreach Primary, attest exact_mirror_v2 standby, promote request-only, execute on standby, prove no split-brain and no automatic failback. |
| 3. Uninstall/reinstall same H | CODE_READY | Fresh local state + authenticated Google owner resolves the same opaque H continuity handle and cloud owner scope. |
| 4. New device same H | CODE_READY | A different device with no local H handle resolves the same H from the linked Google owner identity. |
| 5. App <-> WhatsApp continuity | CODE_READY | Both channels map to the same runtime H owner; raw Google/WhatsApp identifiers are not the portable identity. |
| 6. WhatsApp Voice | LIVE_EXTERNAL_REQUIRED | CI proves Meta-media -> STT -> H-bridge behavior; a real Meta voice note and real configured STT credential are still required for live evidence. |
| 7. Free provider exhaustion | CODE_READY | Three bounded strictly-zero-cost attempts may exhaust; no non-zero/unknown-priced model is called and no paid fallback opens. |
| 8. Learning long-run behavior | CODE_READY | Unknown -> Learning Cycle candidate -> independent verifier -> canonical answer -> re-ask no longer unresolved. Production scheduler must remain active. |
| 9. Stress/outage safety | CODE_READY | Concurrent routing, sticky promotion, webhook idempotency, restore advisory lock/idempotency, and provider-outage fail-closed tests pass. |
| 10. Production final review | LIVE_EXTERNAL_REQUIRED | Every CODE_READY gate passes and gates 1, 2 and 6 have real external live evidence. |

## Production declaration rule

Do not declare H 100/100 while any `LIVE_EXTERNAL_REQUIRED` gate lacks real evidence. Simulated CI is necessary but cannot substitute for a real Backup Cloud, a real failover event, or a real WhatsApp Voice event.

Current repository CI must therefore distinguish engineering completion from live production completion and remain fail-closed about external readiness.
