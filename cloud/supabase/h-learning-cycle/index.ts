import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { completeFreeOpenRouterChat } from "../h-whatsapp-inbox/openrouter-ai.ts";
import {
  buildLearningCycleMessages,
  extractLearningCandidate,
} from "./learning-cycle-policy.ts";

const DEFAULT_BATCH = 2;
const MAX_BATCH = 3;

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ ok: false, error: "Method not allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRole) {
    return json({ ok: false, error: "Supabase runtime credentials unavailable" }, 500);
  }

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const { data: config, error: configError } = await db.from("h_runtime_config")
    .select("secret_value")
    .eq("key", "poll_secret")
    .maybeSingle();
  if (configError) return json({ ok: false, error: "Runtime authorization unavailable" }, 500);
  if (!config?.secret_value || req.headers.get("x-h-runtime-secret") !== config.secret_value) {
    return json({ ok: false, error: "Unauthorized" }, 401);
  }

  let requestedLimit = DEFAULT_BATCH;
  try {
    const payload = await req.json();
    const value = Number((payload as any)?.limit);
    if (Number.isFinite(value)) requestedLimit = Math.floor(value);
  } catch (_) {}
  const limit = Math.max(1, Math.min(MAX_BATCH, requestedLimit || DEFAULT_BATCH));

  const { data: claimed, error: claimError } = await db.rpc("h_claim_knowledge_gaps", { p_limit: limit });
  if (claimError) return json({ ok: false, error: "Learning Queue claim failed" }, 500);

  const gaps = Array.isArray(claimed) ? claimed : [];
  let candidates = 0;
  let failed = 0;

  for (const gap of gaps) {
    const id = String(gap?.id || "");
    const userKey = String(gap?.user_key || "");
    const queryText = String(gap?.query_text || "").trim();
    if (!id || !userKey || !queryText) {
      failed += 1;
      continue;
    }

    try {
      const ai = await completeFreeOpenRouterChat(db, buildLearningCycleMessages(queryText));
      const candidate = ai ? extractLearningCandidate(queryText, ai.content) : null;
      const finish = await db.rpc("h_finish_knowledge_gap_research", {
        p_id: id,
        p_user_key: userKey,
        p_candidate_answer: candidate?.reply ?? null,
        p_candidate_model: candidate ? ai?.model ?? null : null,
        p_error: candidate ? null : ai ? "candidate_unverified_or_invalid" : "free_research_unavailable",
      });
      if (finish.error) throw finish.error;
      if (candidate) candidates += 1;
      else failed += 1;
    } catch (error) {
      failed += 1;
      try {
        await db.rpc("h_finish_knowledge_gap_research", {
          p_id: id,
          p_user_key: userKey,
          p_candidate_answer: null,
          p_candidate_model: null,
          p_error: errorMessage(error).slice(0, 500),
        });
      } catch (_) {
        // A stale researching row is reclaimable after the server-side timeout.
      }
    }
  }

  // Deliberately return counts only. Gap queries and researched candidate answers are
  // owner-scoped durable state and never need to leave this internal endpoint response.
  return json({ ok: true, claimed: gaps.length, candidates, failed }, 200);
});

function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message || error.name;
  return String(error || "unknown_error");
}

function json(value: unknown, status: number): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}
