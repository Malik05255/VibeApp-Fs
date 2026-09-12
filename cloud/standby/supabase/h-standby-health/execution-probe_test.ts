import { runStandbyExecutionProbe, sha256Hex } from "./execution-probe.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

function mockDb(failTable = "") {
  const calls: string[] = [];
  return {
    calls,
    from(table: string) {
      return {
        select(_columns: string) {
          return {
            async limit(_count: number) {
              calls.push(table);
              return failTable === table ? { data: null, error: { message: "unreadable" } } : { data: [], error: null };
            },
          };
        },
      };
    },
  };
}

Deno.test("standby execution probe proves core reads and returns only nonce digest", async () => {
  const db = mockDb();
  const nonce = "live_probe_nonce_1234567890";
  const result = await runStandbyExecutionProbe(db, nonce);
  assert(result.requested === true);
  assert(result.coreSchemaReadable === true);
  assert(result.writesPerformed === false);
  assert(result.userContentReturned === false);
  assert(result.nonceSha256 === await sha256Hex(nonce));
  assert(result.nonceSha256 !== nonce);
  assert(db.calls.join(",") === "h_runtime_state,h_runtime_app_identities");
});

Deno.test("standby execution probe rejects malformed nonce", async () => {
  let rejected = false;
  try {
    await runStandbyExecutionProbe(mockDb(), "short");
  } catch {
    rejected = true;
  }
  assert(rejected, "malformed nonce was accepted");
});

Deno.test("standby execution probe fails closed when core schema cannot be read", async () => {
  let rejected = false;
  try {
    await runStandbyExecutionProbe(mockDb("h_runtime_app_identities"), "live_probe_nonce_1234567890");
  } catch {
    rejected = true;
  }
  assert(rejected, "unreadable core identity schema was accepted");
});
