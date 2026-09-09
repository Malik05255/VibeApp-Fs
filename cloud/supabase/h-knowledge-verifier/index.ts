import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { completeFreeOpenRouterChat } from "../h-whatsapp-inbox/openrouter-ai.ts";
import {
  buildVerificationMessages,
  parseVerificationDecision,
} from "./verification-policy.ts";

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

  const { data: claimed, error: claimError } = await db.rpc("h_claim_knowledge_candidates", { p_limit: limit });
  if (claimError) return json({ ok: false, error: "Knowledge candidate claim failed" }, 500);

  const candidates = Array.isArray(claimed) ? claimed : [];
  let verified = 0;
  let rejected = 0;
  let failed = 0;

  for (const row of candidates) {
    const id = String(row?.id || "");
    const userKey = String(row?.user_key || "");
    const queryText = String(row?.query_text || "").trim();
    const candidateAnswer = String(row?.candidate_answer || "").trim();
    if (!id || !userKey || !queryText || !candidateAnswer) {
      failed += 1;
      continue;
    }

    try {
      const ai = await completeFreeOpenRouterChat(
        db,
        buildVerificationMessages(queryText, candidateAnswer),
      );
      const decision = ai ? parseVerificationDecision(queryText, ai.content) : null;
      const accepted = decision?.verified === true && Boolean(decision.answer);
      const finish = await db.rpc("h_finish_knowledge_verification", {
        p_id: id,
        p_user_key: userKey,
        p_verified: accepted,
        p_canonical_answer: accepted ? decision?.answer ?? null : null,
        p_verification_model: accepted ? ai?.model ?? null : null,
        p_error: accepted
          ? null
          : decision?.error ?? (ai ? "verification_output_invalid" : "free_verification_unavailable"),
      });
      if (finish.error) throw finish.error;
      if (accepted) verified += 1;
      else rejected += 1;
    } catch (error) {
      failed += 1;
      try {
        await db.rpc("h_finish_knowledge_verification", {
          p_id: id,
          p_user_key: userKey,
          p_verified: false,
          p_canonical_answer: null,
          p_verification_model: null,
          p_error: errorMessage(error).slice(0, 500),
        });
      } catch (_) {
        // A stale verifying row is reclaimable by the server-side lease timeout.
      }
    }
  }

  // Do not return queries, candidate answers or verified knowledge from this internal worker.
  return json({ ok: true, claimed: candidates.length, verified, rejected, failed }, 200);
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
