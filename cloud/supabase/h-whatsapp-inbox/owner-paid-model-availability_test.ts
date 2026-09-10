import { completeWithOwnerPaidHelper } from "./owner-paid-ai.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

const route = {
  id: "openrouter_owner_paid",
  provider: "openrouter",
  route_class: "owner_paid",
  credential_id: "openrouter_owner_paid",
  selected_model: "vendor/paid-model",
  enabled: true,
  owner_enabled_at: "2026-09-09T20:40:00Z",
  hard_tasks_only: false,
  allow_free_fallback: false,
  daily_call_limit: 10,
  priority: 10,
  metadata: { pricing_ceiling: { prompt: 0.000001, completion: 0.000002 } },
};

const credential = {
  provider: "openrouter",
  secret_ciphertext: "cipher",
  secret_iv: "iv",
  secret_version: 1,
  selected_model: "vendor/paid-model",
  oauth_metadata: {
    owner_paid: true,
    byok: true,
    pricing_ceiling: { prompt: 0.000001, completion: 0.000002 },
  },
};

function thenable(result: any) {
  const query: any = {
    select: () => query,
    eq: () => query,
    then: (resolve: any, reject: any) => Promise.resolve(result).then(resolve, reject),
    maybeSingle: async () => result,
  };
  return query;
}

Deno.test("selected paid model missing from live catalog fails closed before claim", async () => {
  const rpcCalls: string[] = [];
  let fetches = 0;
  const db = {
    from(table: string) {
      if (table === "h_runtime_ai_provider_registry") return thenable({ data: [route], error: null });
      if (table === "h_runtime_ai_credentials") return thenable({ data: credential, error: null });
      if (table === "h_runtime_state") return { upsert: async () => ({ error: null }) };
      throw new Error(`unexpected table ${table}`);
    },
    async rpc(name: string) {
      rpcCalls.push(name);
      throw new Error(`RPC must not be reached when model is unavailable: ${name}`);
    },
  };

  const result = await completeWithOwnerPaidHelper({
    db,
    messages: [{ role: "user", content: "hello" }],
    temperature: 0,
    stage: "candidate",
    taskClass: "ordinary",
    capability: "text",
    decryptImpl: async () => "key",
    fetchImpl: async () => {
      fetches += 1;
      return new Response(JSON.stringify({
        data: [{
          id: "vendor/other-model",
          pricing: { prompt: "0.000001", completion: "0.000002" },
          architecture: { input_modalities: ["text"] },
        }],
      }), { status: 200, headers: { "content-type": "application/json" } });
    },
  });

  assert(result.status === "blocked");
  if (result.status === "blocked") {
    assert(result.reason === "authorized_model_missing_from_live_catalog");
    assert(result.allowFreeFallback === false);
  }
  assert(fetches === 1, "only the live catalog should be queried");
  assert(rpcCalls.length === 0, "daily paid-call claim must not happen for a missing model");
});
