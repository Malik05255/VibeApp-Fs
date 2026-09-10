import {
  completeFreeOpenRouterMediaAnalysis,
  isStrictlyZeroPricedMediaModel,
  selectStrictlyFreeMediaModel,
} from "./openrouter-media.ts";
import type { HMediaMessageInput } from "../h-whatsapp-inbox/media-bridge.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("media pricing gate rejects any paid dimension", () => {
  assert(isStrictlyZeroPricedMediaModel({ prompt: "0", completion: "0" }), "zero pricing should pass");
  assert(!isStrictlyZeroPricedMediaModel({ prompt: "0", completion: "0", image: "0.001" }), "paid image fee must fail");
  assert(!isStrictlyZeroPricedMediaModel({ prompt: "0", completion: "0", audio: "0.001" }), "paid audio fee must fail");
  assert(!isStrictlyZeroPricedMediaModel({ prompt: "0.001", completion: "0" }), "paid prompt must fail");
});

Deno.test("image analysis requires explicit image input support", () => {
  const models = [
    {
      id: "free/text-only",
      context_length: 100000,
      pricing: { prompt: "0", completion: "0" },
      architecture: { input_modalities: ["text"] },
    },
    {
      id: "free/vision",
      context_length: 32000,
      pricing: { prompt: "0", completion: "0" },
      architecture: { input_modalities: ["text", "image"] },
    },
  ];
  assert(selectStrictlyFreeMediaModel(models, null, "image") === "free/vision", "vision model should be selected");
});

Deno.test("audio and video never fall back to a free text or image model", () => {
  const models = [
    {
      id: "openrouter/free",
      context_length: 200000,
      pricing: { prompt: "0", completion: "0" },
      architecture: { input_modalities: ["text", "image"] },
    },
    {
      id: "free/audio",
      context_length: 64000,
      pricing: { prompt: "0", completion: "0", audio: "0" },
      architecture: { input_modalities: ["text", "audio"] },
    },
    {
      id: "free/video",
      context_length: 32000,
      pricing: { prompt: "0", completion: "0", video: "0" },
      architecture: { input_modalities: ["text", "video"] },
    },
  ];

  assert(selectStrictlyFreeMediaModel(models, null, "audio") === "free/audio", "audio-capable free model required");
  assert(selectStrictlyFreeMediaModel(models, null, "video") === "free/video", "video-capable free model required");
});

Deno.test("paid preferred media model is never used by the strictly-free selector", () => {
  const models = [
    {
      id: "paid/preferred",
      context_length: 200000,
      pricing: { prompt: "0.01", completion: "0.01" },
      architecture: { input_modalities: ["text", "image", "audio", "video"] },
    },
    {
      id: "openrouter/free",
      context_length: 16000,
      pricing: { prompt: "0", completion: "0" },
      architecture: { input_modalities: ["text", "image"] },
    },
  ];
  assert(selectStrictlyFreeMediaModel(models, "paid/preferred", "image") === "openrouter/free", "paid preference must be ignored by free selector");
  assert(selectStrictlyFreeMediaModel(models, "paid/preferred", "audio") === null, "paid audio model must not leak into free selector");
  assert(selectStrictlyFreeMediaModel(models, "paid/preferred", "video") === null, "paid video model must not leak into free selector");
});

Deno.test("image analysis fails closed when no free vision model exists", () => {
  const models = [
    {
      id: "free/text-only",
      pricing: { prompt: "0", completion: "0" },
      architecture: { input_modalities: ["text"] },
    },
  ];
  assert(selectStrictlyFreeMediaModel(models, null, "image") === null, "must not fall back to text-only model");
});

const paidRoute = {
  id: "openrouter_owner_paid",
  provider: "openrouter",
  route_class: "owner_paid",
  credential_id: "openrouter_owner_paid",
  selected_model: "vendor/paid-multimodal",
  enabled: true,
  owner_enabled_at: "2026-09-11T00:00:00Z",
  hard_tasks_only: false,
  allow_free_fallback: false,
  daily_call_limit: 50,
  priority: 10,
  metadata: {
    pricing_ceiling: {
      prompt: 0.000001,
      completion: 0.000002,
      image: 0,
      audio: 0,
      video: 0,
    },
  },
};

function paidCatalogResponse() {
  return new Response(JSON.stringify({
    data: [{
      id: "vendor/paid-multimodal",
      pricing: {
        prompt: "0.000001",
        completion: "0.000002",
        image: "0",
        audio: "0",
        video: "0",
      },
      architecture: { input_modalities: ["text", "image", "file", "audio", "video"] },
    }],
  }), { status: 200, headers: { "content-type": "application/json" } });
}

async function buildEncryptedPaidCredential(root: string) {
  const encoder = new TextEncoder();
  const digest = await crypto.subtle.digest(
    "SHA-256",
    encoder.encode(`h-owner-paid-ai-aes-v1:openrouter:${root}`),
  );
  const key = await crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    key,
    encoder.encode("test-paid-key"),
  );
  return {
    provider: "openrouter",
    secret_ciphertext: encodeBase64Url(new Uint8Array(encrypted)),
    secret_iv: encodeBase64Url(iv),
    secret_version: 1,
    selected_model: "vendor/paid-multimodal",
    oauth_metadata: {
      owner_paid: true,
      byok: true,
      pricing_ceiling: paidRoute.metadata.pricing_ceiling,
    },
  };
}

function encodeBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function createPaidDb(credential: any) {
  const credentialLookups: string[] = [];
  const stateWrites: any[] = [];
  const queries: Record<string, any> = {};
  const db = {
    from(table: string) {
      if (table === "h_runtime_ai_provider_registry") {
        const query: any = {
          select: () => query,
          eq: () => query,
          update: () => query,
          then: (resolve: any, reject: any) => Promise.resolve({ data: [paidRoute], error: null }).then(resolve, reject),
        };
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
            if (id === "openrouter_owner_paid") return { data: credential, error: null };
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
    async rpc(name: string) {
      if (name === "h_claim_owner_paid_ai_call") {
        return {
          data: [{
            allowed: true,
            calls_used: 1,
            daily_limit: 50,
            provider: "openrouter",
            credential_id: "openrouter_owner_paid",
            selected_model: "vendor/paid-multimodal",
          }],
          error: null,
        };
      }
      if (name === "h_record_owner_paid_ai_usage") return { data: true, error: null };
      throw new Error(`unexpected rpc ${name}`);
    },
  };
  queries.db = db;
  return { db, credentialLookups, stateWrites };
}

async function withPaidEnvironment<T>(block: (credential: any) => Promise<T>): Promise<T> {
  const name = "SUPABASE_SERVICE_ROLE_KEY";
  const before = Deno.env.get(name);
  const root = "h-paid-media-routing-test-root";
  Deno.env.set(name, root);
  try {
    return await block(await buildEncryptedPaidCredential(root));
  } finally {
    if (before == null) Deno.env.delete(name);
    else Deno.env.set(name, before);
  }
}

async function withFetch<T>(impl: typeof fetch, block: () => Promise<T>): Promise<T> {
  const before = globalThis.fetch;
  (globalThis as any).fetch = impl;
  try {
    return await block();
  } finally {
    (globalThis as any).fetch = before;
  }
}

function mediaCases(): Array<{ name: string; input: HMediaMessageInput }> {
  const base = {
    waId: "966501234567",
    messageId: "wamid.paid-media",
    caption: "حلل الملف",
    sizeBytes: 5,
    durationMs: null,
    receivedAt: "2026-09-11T00:00:00Z",
  };
  return [
    {
      name: "image",
      input: { ...base, kind: "image", mimeType: "image/png", fileName: "image.png", base64: "aGVsbG8=" },
    },
    {
      name: "audio",
      input: { ...base, kind: "audio", mimeType: "audio/mpeg", fileName: "audio.mp3", base64: "aGVsbG8=", durationMs: 5_000 },
    },
    {
      name: "video",
      input: { ...base, kind: "video", mimeType: "video/mp4", fileName: "video.mp4", base64: "aGVsbG8=", durationMs: 5_000 },
    },
    {
      name: "pdf",
      input: { ...base, kind: "document", mimeType: "application/pdf", fileName: "doc.pdf", base64: "aGVsbG8=" },
    },
  ];
}

Deno.test("active paid route owns image audio video and PDF inference without free mixing", async () => {
  await withPaidEnvironment(async (credential) => {
    for (const testCase of mediaCases()) {
      const mock = createPaidDb(credential);
      const calls: Array<{ url: string; body: any }> = [];
      const result = await withFetch(async (input, init) => {
        const url = String(input);
        if (url.includes("/models")) {
          calls.push({ url, body: null });
          return paidCatalogResponse();
        }
        if (url.includes("/chat/completions")) {
          const body = JSON.parse(String(init?.body || "{}"));
          calls.push({ url, body });
          return new Response(JSON.stringify({
            choices: [{ message: { content: `paid-${testCase.name}` } }],
            usage: { prompt_tokens: 10, completion_tokens: 3, cost: 0.001 },
          }), { status: 200, headers: { "content-type": "application/json" } });
        }
        throw new Error(`unexpected network call ${url}`);
      }, () => completeFreeOpenRouterMediaAnalysis(mock.db, testCase.input));

      assert(result?.content === `paid-${testCase.name}`, `${testCase.name} did not use paid route`);
      assert(result?.model === "vendor/paid-multimodal", `${testCase.name} used wrong model`);
      assert(calls.length === 2, `${testCase.name} must make catalog + exactly one paid completion call`);
      assert(calls[1].body.model === "vendor/paid-multimodal", `${testCase.name} did not pin exact model`);
      assert(calls[1].body.max_tokens === 1200, `${testCase.name} paid output was not bounded`);
      assert(calls[1].body?.usage?.include === true, `${testCase.name} did not request cost telemetry`);
      assert(!mock.credentialLookups.includes("openrouter_default"), `${testCase.name} read free credential while paid route active`);
      if (testCase.name === "pdf") {
        assert(calls[1].body.plugins == null, "paid PDF must not mix in the free Cloudflare parser");
      }
    }
  });
});

Deno.test("paid media provider failure never falls back to openrouter_default", async () => {
  await withPaidEnvironment(async (credential) => {
    const mock = createPaidDb(credential);
    let networkCalls = 0;
    const image = mediaCases()[0].input;
    const result = await withFetch(async (input) => {
      networkCalls += 1;
      if (String(input).includes("/models")) return paidCatalogResponse();
      return new Response("rate limited", { status: 429 });
    }, () => completeFreeOpenRouterMediaAnalysis(mock.db, image));

    assert(result === null, "paid media failure must fail closed");
    assert(networkCalls === 2, "paid media failure must not retry or start a free request");
    assert(!mock.credentialLookups.includes("openrouter_default"), "free credential leaked after paid media failure");
  });
});
