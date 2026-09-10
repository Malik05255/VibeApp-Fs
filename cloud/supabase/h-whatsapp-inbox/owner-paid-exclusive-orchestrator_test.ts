import {
  completeFreeOpenRouterChat,
  completeFreeOpenRouterMediaAnalysis,
} from "./openrouter-ai.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

const paidRoute = {
  id: "openrouter_owner_paid",
  provider: "openrouter",
  route_class: "owner_paid",
  credential_id: "openrouter_owner_paid",
  selected_model: "vendor/paid-model",
  enabled: true,
  owner_enabled_at: "2026-09-09T20:40:00Z",
  hard_tasks_only: false,
  allow_free_fallback: false,
  daily_call_limit: 20,
  priority: 10,
  metadata: {
    pricing_ceiling: { prompt: 0.000001, completion: 0.000002, image: 0 },
  },
};

const paidCredential = {
  provider: "openrouter",
  secret_ciphertext: "cipher",
  secret_iv: "iv",
  secret_version: 1,
  selected_model: "vendor/paid-model",
  oauth_metadata: {
    owner_paid: true,
    byok: true,
    pricing_ceiling: { prompt: 0.000001, completion: 0.000002, image: 0 },
  },
};

function thenable(result: any) {
  const query: any = {
    select: () => query,
    eq: () => query,
    then: (resolve: any, reject: any) => Promise.resolve(result).then(resolve, reject),
  };
  return query;
}

function createDb(routes: any[]) {
  const credentialLookups: string[] = [];
  const stateWrites: any[] = [];
  const rpcCalls: any[] = [];

  return {
    db: {
      from(table: string) {
        if (table === "h_runtime_ai_provider_registry") {
          const query: any = thenable({ data: routes, error: null });
          query.update = () => query;
          return query;
        }
        if (table === "h_runtime_ai_credentials") {
          let id = "";
          const query: any = {
            select: () => query,
            eq(field: string, value: unknown) {
              if (field === "id") id = String(value || "");
              return query;
            },
            async maybeSingle() {
              credentialLookups.push(id);
              if (id === "openrouter_owner_paid") return { data: paidCredential, error: null };
              if (id === "openrouter_default") return { data: null, error: null };
              return { data: null, error: null };
            },
          };
          return query;
        }
        if (table === "h_runtime_state") {
          return {
            async upsert(row: any) {
              stateWrites.push(row);
              return { error: null };
            },
          };
        }
        throw new Error(`unexpected table ${table}`);
      },
      async rpc(name: string, args: any) {
        rpcCalls.push({ name, args });
        if (name === "h_claim_owner_paid_ai_call") {
          return {
            data: [{
              allowed: true,
              calls_used: 1,
              daily_limit: 20,
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
    credentialLookups,
    stateWrites,
    rpcCalls,
  };
}

function catalogResponse() {
  return new Response(JSON.stringify({
    data: [{
      id: "vendor/paid-model",
      pricing: { prompt: "0.000001", completion: "0.000002", image: "0" },
      architecture: { input_modalities: ["text", "image"] },
    }],
  }), { status: 200, headers: { "content-type": "application/json" } });
}

async function withMockFetch<T>(
  implementation: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>,
  block: () => Promise<T>,
): Promise<T> {
  const original = globalThis.fetch;
  (globalThis as any).fetch = implementation;
  try {
    return await block();
  } finally {
    (globalThis as any).fetch = original;
  }
}

Deno.test("active paid text route returning 429 never touches the free credential", async () => {
  const mock = createDb([paidRoute]);
  const networkCalls: string[] = [];

  const result = await withMockFetch(async (input) => {
    const url = String(input);
    networkCalls.push(url);
    if (url.includes("/models")) return catalogResponse();
    if (url.includes("/chat/completions")) return new Response("rate limited", { status: 429 });
    throw new Error(`unexpected network call ${url}`);
  }, () => completeFreeOpenRouterChat(
    mock.db,
    [{ role: "user", content: "hello" }],
  ));

  assert(result === null, "paid 429 must fail closed");
  assert(networkCalls.length === 2, `expected catalog + one paid call, got ${networkCalls.length}`);
  assert(
    !mock.credentialLookups.includes("openrouter_default"),
    "free credential must never be read while an active paid route is blocked",
  );
  assert(
    mock.credentialLookups.filter((id) => id === "openrouter_owner_paid").length === 1,
    "only the selected paid credential should be read",
  );
});

Deno.test("active paid media route returning 429 never touches the free credential", async () => {
  const mock = createDb([paidRoute]);
  const networkCalls: string[] = [];

  const result = await withMockFetch(async (input) => {
    const url = String(input);
    networkCalls.push(url);
    if (url.includes("/models")) return catalogResponse();
    if (url.includes("/chat/completions")) return new Response("rate limited", { status: 429 });
    throw new Error(`unexpected network call ${url}`);
  }, () => completeFreeOpenRouterMediaAnalysis(mock.db, {
    waId: "966500000000",
    messageId: "wamid.test",
    kind: "image",
    mimeType: "image/png",
    fileName: "test.png",
    caption: "حلل الصورة",
    base64: "aGVsbG8=",
    sizeBytes: 5,
    durationMs: null,
    receivedAt: "2026-09-11T00:00:00Z",
  }));

  assert(result === null, "paid media 429 must fail closed");
  assert(networkCalls.length === 2, `expected catalog + one paid media call, got ${networkCalls.length}`);
  assert(
    !mock.credentialLookups.includes("openrouter_default"),
    "media must not escape to the free credential while paid routing is active",
  );
});

Deno.test("free route becomes eligible only when no active paid route exists", async () => {
  const mock = createDb([]);
  let networkCalls = 0;

  const result = await withMockFetch(async () => {
    networkCalls += 1;
    throw new Error("free network should not be reached without a configured free credential");
  }, () => completeFreeOpenRouterChat(
    mock.db,
    [{ role: "user", content: "hello" }],
  ));

  assert(result === null);
  assert(
    mock.credentialLookups.filter((id) => id === "openrouter_default").length === 1,
    "free credential lookup should resume only after the paid route is absent/disabled",
  );
  assert(
    !mock.credentialLookups.includes("openrouter_owner_paid"),
    "no paid credential should be loaded when no active paid route exists",
  );
  assert(networkCalls === 0, "no free provider call is allowed without a configured free credential");
});
