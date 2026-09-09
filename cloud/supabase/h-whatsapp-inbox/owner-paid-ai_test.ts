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
  daily_call_limit: 2,
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
    update: () => query,
    then: (resolve: any, reject: any) => Promise.resolve(result).then(resolve, reject),
    maybeSingle: async () => result,
  };
  return query;
}

function createDb(options: { claimAllowed?: boolean; routes?: any[] } = {}) {
  const rpcCalls: any[] = [];
  const stateWrites: any[] = [];
  const routes = options.routes ?? [route];
  return {
    db: {
      from(table: string) {
        if (table === "h_runtime_ai_provider_registry") return thenable({ data: routes, error: null });
        if (table === "h_runtime_ai_credentials") return thenable({ data: credential, error: null });
        if (table === "h_runtime_state") {
          return { upsert: async (row: any) => { stateWrites.push(row); return { error: null }; } };
        }
        throw new Error(`unexpected table ${table}`);
      },
      async rpc(name: string, args: any) {
        rpcCalls.push({ name, args });
        if (name === "h_claim_owner_paid_ai_call") {
          return {
            data: [{
              allowed: options.claimAllowed !== false,
              calls_used: options.claimAllowed === false ? 2 : 1,
              daily_limit: 2,
              provider: "openrouter",
              credential_id: "openrouter_owner_paid",
              selected_model: "vendor/paid-model",
            }],
            error: null,
          };
        }
        if (name === "h_record_owner_paid_ai_usage") return { data: true, error: null };
        throw new Error(`unexpected rpc ${name}`);
      },
    },
    rpcCalls,
    stateWrites,
  };
}

function catalogResponse(pricing = { prompt: "0.000001", completion: "0.000002" }) {
  return new Response(JSON.stringify({
    data: [{
      id: "vendor/paid-model",
      pricing,
      architecture: { input_modalities: ["text", "image"] },
    }],
  }), { status: 200, headers: { "content-type": "application/json" } });
}

Deno.test("no paid route means no paid fetch or claim", async () => {
  const mock = createDb({ routes: [] });
  let fetches = 0;
  const result = await completeWithOwnerPaidHelper({
    db: mock.db,
    messages: [{ role: "user", content: "hello" }],
    temperature: 0,
    stage: "candidate",
    taskClass: "ordinary",
    capability: "text",
    fetchImpl: async () => { fetches += 1; return catalogResponse(); },
    decryptImpl: async () => "key",
  });
  assert(result.status === "not_configured");
  assert(fetches === 0);
  assert(mock.rpcCalls.length === 0);
});

Deno.test("authorized paid call is exact, bounded and records provider cost", async () => {
  const mock = createDb();
  const urls: string[] = [];
  const fetchImpl = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    urls.push(url);
    if (url.includes("/models")) return catalogResponse();
    const body = JSON.parse(String(init?.body || "{}"));
    assert(body.model === "vendor/paid-model");
    assert(body.max_tokens === 1200, "paid output must be hard-bounded");
    assert(body?.usage?.include === true, "provider cost telemetry must be requested");
    return new Response(JSON.stringify({
      choices: [{ message: { content: "{\"action\":\"reply\",\"reply\":\"ok\"}" } }],
      usage: { prompt_tokens: 12, completion_tokens: 5, cost: 0.0042 },
    }), { status: 200 });
  };

  const result = await completeWithOwnerPaidHelper({
    db: mock.db,
    messages: [{ role: "user", content: "hello" }],
    temperature: 0.1,
    stage: "candidate",
    taskClass: "ordinary",
    capability: "text",
    fetchImpl,
    decryptImpl: async () => "key",
  });
  assert(result.status === "success");
  if (result.status === "success") {
    assert(result.model === "vendor/paid-model");
    assert(result.callsUsed === 1);
    assert(result.promptTokens === 12);
    assert(result.completionTokens === 5);
    assert(result.costUsd === 0.0042);
  }
  assert(urls.length === 2, `expected catalog + one paid request, got ${urls.length}`);
  assert(mock.rpcCalls.filter((call) => call.name === "h_claim_owner_paid_ai_call").length === 1);
  const usage = mock.rpcCalls.find((call) => call.name === "h_record_owner_paid_ai_usage");
  assert(usage?.args?.p_cost_usd === 0.0042);
});

Deno.test("oversized paid text is blocked before catalog, claim or provider", async () => {
  const mock = createDb();
  let fetches = 0;
  const result = await completeWithOwnerPaidHelper({
    db: mock.db,
    messages: [{ role: "user", content: "x".repeat(60_001) }],
    temperature: 0,
    stage: "candidate",
    taskClass: "hard",
    capability: "text",
    fetchImpl: async () => { fetches += 1; return catalogResponse(); },
    decryptImpl: async () => "key",
  });
  assert(result.status === "blocked");
  if (result.status === "blocked") assert(result.reason === "owner_paid_text_input_too_large");
  assert(fetches === 0);
  assert(mock.rpcCalls.length === 0);
});

Deno.test("price increase blocks before claim and provider call", async () => {
  const mock = createDb();
  let fetches = 0;
  const result = await completeWithOwnerPaidHelper({
    db: mock.db,
    messages: [{ role: "user", content: "hello" }],
    temperature: 0,
    stage: "candidate",
    taskClass: "ordinary",
    capability: "text",
    fetchImpl: async () => { fetches += 1; return catalogResponse({ prompt: "0.00001", completion: "0.000002" }); },
    decryptImpl: async () => "key",
  });
  assert(result.status === "blocked");
  if (result.status === "blocked") assert(result.reason === "price_increased:prompt");
  assert(fetches === 1);
  assert(mock.rpcCalls.length === 0);
});

Deno.test("daily limit blocks before paid completion request", async () => {
  const mock = createDb({ claimAllowed: false });
  let fetches = 0;
  const result = await completeWithOwnerPaidHelper({
    db: mock.db,
    messages: [{ role: "user", content: "hello" }],
    temperature: 0,
    stage: "candidate",
    taskClass: "ordinary",
    capability: "text",
    fetchImpl: async () => { fetches += 1; return catalogResponse(); },
    decryptImpl: async () => "key",
  });
  assert(result.status === "blocked");
  if (result.status === "blocked") assert(result.reason === "daily_call_limit_reached");
  assert(fetches === 1, "only catalog fetch is allowed before a denied claim");
  assert(mock.rpcCalls.length === 1);
});

Deno.test("paid provider failure is never retried", async () => {
  const mock = createDb();
  let fetches = 0;
  const result = await completeWithOwnerPaidHelper({
    db: mock.db,
    messages: [{ role: "user", content: "hello" }],
    temperature: 0,
    stage: "candidate",
    taskClass: "ordinary",
    capability: "text",
    fetchImpl: async (input) => {
      fetches += 1;
      if (String(input).includes("/models")) return catalogResponse();
      return new Response("rate limited", { status: 429 });
    },
    decryptImpl: async () => "key",
  });
  assert(result.status === "blocked");
  if (result.status === "blocked") assert(result.reason === "owner_paid_provider_http_429");
  assert(fetches === 2, "catalog + one provider request only; paid retries are forbidden");
});
