import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { normalizeWaIdCandidate } from "../h-whatsapp-inbox/contact-manager.ts";
import { friendFingerprint, ownerFingerprint } from "../h-whatsapp-inbox/owner-identity.ts";
import { createOwnerPairingChallenge } from "../h-whatsapp-inbox/owner-pairing.ts";

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
    const [owners, friends, pairing] = await Promise.all([
      db.from("h_runtime_owner_identities")
        .select("wa_fingerprint", { count: "exact", head: true })
        .eq("active", true),
      db.from("h_runtime_friend_identities")
        .select("wa_fingerprint", { count: "exact", head: true })
        .eq("active", true),
      db.from("h_runtime_owner_pairing")
        .select("expires_at")
        .is("consumed_at", null)
        .gt("expires_at", new Date().toISOString())
        .order("expires_at", { ascending: false })
        .limit(1)
        .maybeSingle(),
    ]);
    if (owners.error || friends.error || pairing.error) {
      throw owners.error || friends.error || pairing.error;
    }
    return reply({
      ok: true,
      activeOwnerIdentities: owners.count ?? 0,
      activeFriendIdentities: friends.count ?? 0,
      unknownSendersAllowed: false,
      activePairingChallenge: Boolean(pairing.data?.expires_at),
      pairingExpiresAt: pairing.data?.expires_at ?? null,
      rawWaIdsStored: false,
      rawPairingCodesStored: false,
      durableIdentityKey: "identity_secret",
    });
  }

  if (action === "create_pairing") {
    const challenge = await createOwnerPairingChallenge(db, runtimeSecret);
    return reply({
      ok: true,
      pairingCode: challenge.code,
      expiresAt: challenge.expiresAt,
      command: `اربطني كمالك ${challenge.code}`,
      oneTime: true,
      rawPairingCodeStored: false,
    });
  }

  const waId = normalizeWaIdCandidate(body?.wa_id);
  if (!waId) return reply({ ok: false, error: "invalid_wa_id" }, 400);
  const label = typeof body?.label === "string" ? body.label.trim().slice(0, 80) || null : null;
  const identitySecret = await loadIdentitySecret(db);

  if (action === "enroll" || action === "remove") {
    const fingerprint = await ownerFingerprint(waId, identitySecret);
    if (!fingerprint) return reply({ ok: false, error: "invalid_wa_id" }, 400);
    if (action === "enroll") {
      const { error } = await db.from("h_runtime_owner_identities").upsert({
        wa_fingerprint: fingerprint,
        label,
        active: true,
        updated_at: new Date().toISOString(),
      }, { onConflict: "wa_fingerprint" });
      if (error) throw error;
      return reply({ ok: true, enrolled: true, role: "owner", rawWaIdStored: false });
    }
    const { error } = await db.from("h_runtime_owner_identities")
      .update({ active: false, updated_at: new Date().toISOString() })
      .eq("wa_fingerprint", fingerprint);
    if (error) throw error;
    return reply({ ok: true, removed: true, role: "owner", rawWaIdStored: false });
  }

  if (action === "enroll_friend" || action === "remove_friend") {
    const fingerprint = await friendFingerprint(waId, identitySecret);
    if (!fingerprint) return reply({ ok: false, error: "invalid_wa_id" }, 400);
    if (action === "enroll_friend") {
      const { error } = await db.from("h_runtime_friend_identities").upsert({
        wa_fingerprint: fingerprint,
        label,
        active: true,
        updated_at: new Date().toISOString(),
      }, { onConflict: "wa_fingerprint" });
      if (error) throw error;
      return reply({ ok: true, enrolled: true, role: "friend", rawWaIdStored: false });
    }
    const { error } = await db.from("h_runtime_friend_identities")
      .update({ active: false, updated_at: new Date().toISOString() })
      .eq("wa_fingerprint", fingerprint);
    if (error) throw error;
    return reply({ ok: true, removed: true, role: "friend", rawWaIdStored: false });
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
