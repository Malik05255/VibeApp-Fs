import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { normalizeWaIdCandidate } from "../h-whatsapp-inbox/contact-manager.ts";
import { ownerFingerprint } from "../h-whatsapp-inbox/owner-identity.ts";

Deno.serve(async (req: Request) => {
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "Supabase runtime credentials unavailable" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });

  const { data: config } = await db.from("h_runtime_config")
    .select("secret_value")
    .eq("key", "poll_secret")
    .maybeSingle();
  const runtimeSecret = String(config?.secret_value || "").trim();
  if (!runtimeSecret || req.headers.get("x-h-runtime-secret") !== runtimeSecret) {
    return reply({ ok: false, error: "Unauthorized" }, 401);
  }
  if (req.method !== "POST") return reply({ ok: false, error: "Method not allowed" }, 405);

  const body = await req.json().catch(() => ({}));
  const action = String(body?.action || "status");

  if (action === "status") {
    const { count, error } = await db.from("h_runtime_owner_identities")
      .select("wa_fingerprint", { count: "exact", head: true })
      .eq("active", true);
    if (error) throw error;
    return reply({ ok: true, activeOwnerIdentities: count ?? 0, rawWaIdsStored: false });
  }

  const waId = normalizeWaIdCandidate(body?.wa_id);
  if (!waId) return reply({ ok: false, error: "invalid_wa_id" }, 400);
  const fingerprint = await ownerFingerprint(waId, runtimeSecret);
  if (!fingerprint) return reply({ ok: false, error: "invalid_wa_id" }, 400);

  if (action === "enroll") {
    const label = typeof body?.label === "string" ? body.label.trim().slice(0, 80) || null : null;
    const { error } = await db.from("h_runtime_owner_identities").upsert({
      wa_fingerprint: fingerprint,
      label,
      active: true,
      updated_at: new Date().toISOString(),
    }, { onConflict: "wa_fingerprint" });
    if (error) throw error;
    return reply({ ok: true, enrolled: true, rawWaIdStored: false });
  }

  if (action === "remove") {
    const { error } = await db.from("h_runtime_owner_identities")
      .update({ active: false, updated_at: new Date().toISOString() })
      .eq("wa_fingerprint", fingerprint);
    if (error) throw error;
    return reply({ ok: true, removed: true, rawWaIdStored: false });
  }

  return reply({ ok: false, error: "unsupported_action" }, 400);
});

function reply(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
