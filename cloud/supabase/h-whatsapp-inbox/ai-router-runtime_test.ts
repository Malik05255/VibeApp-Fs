import { completeWithFreeModelFailover } from "./ai-router-runtime.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

const models = [
  {
    id: "free/a",
    context_length: 128000,
    pricing: { prompt: "0", completion: "0" },
    architecture: { input_modalities: ["text"] },
  },
  {
    id: "free/b",
    context_length: 64000,
    pricing: { prompt: "0", completion: "0" },
    architecture: { input_modalities: ["text"] },
  },
];

function createMockDb() {
  let guard: Record<string, unknown> | null = null;
  const rpcCalls: Array<{ name: string; args: Record<string, unknown> }> = [];

  return {
    db: {
      from(table: string) {
        if (table === "h_runtime_state") {
          const query: any = {
            select: () => query,
            eq: () => query,
            maybeSingle: async () => ({ data: guard ? { value: guard } : null, error: null }),
            upsert: async (row: any) => {
              guard = row?.value && typeof row.value === "object" ? row.value : null;
              return { error: null };
            },
          };
          return query;
        }
        if (table === "h_runtime_ai_route_stats") {
          const query: any = {
            select: () => query,
            eq: () => query,
            in: async () => ({ data: [], error: null }),
          };
          return query;
        }
        throw new Error(`unexpected table ${table}`);
      },
      rpc: async (name: string, args: Record<string, unknown>) => {
        rpcCalls.push({ name, args });
        return { data: null, error: null };
      },
    },
    rpcCalls,
    getGuard: () => guard,
  };
}

Deno.test("runtime retries a 429 only on another strictly free model", async () => {
  const mock = createMockDb();
  const originalFetch = globalThis.fetch;
  const calledModels: string[] = [];

  globalThis.fetch = async (_input: RequestInfo | URL, init?: RequestInit) => {
    const payload = JSON.parse(String(init?.body || "{}"));
    calledModels.push(String(payload.model));
    if (calledModels.length === 1) {
      return new Response("rate limit", { status: 429, headers: { "retry-after": "60" } });
    }
    return new Response(JSON.stringify({
      choices: [{ message: { content: "ok" } }],
      usage: { prompt_tokens: 10, completion_tokens: 4 },
    }), { status: 200, headers: { "content-type": "application/json" } });
  };

  try {
    const result = await completeWithFreeModelFailover({
      db: mock.db,
      apiKey: "test-key",
      models,
      preferredModel: "free/a",
      capability: "text",
      messages: [{ role: "user", content: "test" }],
      temperature: 0,
      stage: "candidate",
    });

    assert(result?.content === "ok");
    assert(result?.model === "free/b");
    assert(result?.attempts === 2);
    assert(calledModels.join(",") === "free/a,free/b");
    assert(mock.rpcCalls.length === 2);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

Deno.test("provider-fatal 402 opens a circuit breaker and prevents repeated requests", async () => {
  const mock = createMockDb();
  const originalFetch = globalThis.fetch;
  let fetchCount = 0;

  globalThis.fetch = async () => {
    fetchCount += 1;
    return new Response("payment required", { status: 402 });
  };

  try {
    const request = {
      db: mock.db,
      apiKey: "test-key",
      models,
      preferredModel: "free/a",
      capability: "text" as const,
      messages: [{ role: "user", content: "test" }],
      temperature: 0,
      stage: "candidate" as const,
    };

    assert(await completeWithFreeModelFailover(request) === null);
    assert(fetchCount === 1, "402 must stop model fan-out immediately");
    assert(typeof mock.getGuard()?.["blocked_until"] === "string", "provider guard must be persisted");

    assert(await completeWithFreeModelFailover(request) === null);
    assert(fetchCount === 1, "active provider guard must suppress repeated provider calls");
  } finally {
    globalThis.fetch = originalFetch;
  }
});
