import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const FUNCTION_NAME = "h-failover-control-register";
const STATE_KEY = "app_failover_control_plane";
const EXPECTED_PATH = "/h-app-failover-route";

type DbClient = any;

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);

  const supabaseUrl = String(Deno.env.get("SUPABASE_URL") || "").trim();
  const serviceRole = String(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "").trim();
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const expectedSecret = await loadRuntimeSecret(db).catch(() => "");
  const providedSecret = String(req.headers.get("x-h-runtime-secret") || "").trim();
  if (!expectedSecret || !providedSecret || !constantTimeEqual(expectedSecret, providedSecret)) {
    return reply({ ok: false, error: "Unauthorized" }, 401);
  }

  try {
    const body = await req.json().catch(() => ({}));
    const controlUrl = normalizeControlUrl(String(body?.control_url || ""));
    if (!controlUrl) return reply({ ok: false, error: "invalid_control_url" }, 400);

    const now = new Date().toISOString();
    const { error } = await db.from("h_runtime_state").upsert({
      key: STATE_KEY,
      value: {
        provider: "cloudflare_worker",
        url: controlUrl,
        public_metadata_only: true,
        registered_at: now,
        credentials_exposed: false,
        runtime_secret_exposed: false,
      },
      updated_at: now,
    }, { onConflict: "key" });
    if (error) throw error;

    return reply({
      ok: true,
      service: FUNCTION_NAME,
      registered: true,
      controlUrl,
      publicMetadataOnly: true,
      credentialsExposed: false,
      runtimeSecretExposed: false,
    });
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return reply({ ok: false, error: "failover_control_register_failed" }, 500);
  }
});

async function loadRuntimeSecret(db: DbClient): Promise<string> {
  const { data, error } = await db.from("h_runtime_config")
    .select("secret_value")
    .eq("key", "poll_secret")
    .maybeSingle();
  if (error) throw error;
  const value = String(data?.secret_value || "").trim();
  if (!value) throw new Error("runtime_secret_missing");
  return value;
}

export function normalizeControlUrl(raw: string): string | null {
  try {
    const url = new URL(raw.trim());
    if (url.protocol !== "https:") return null;
    const host = url.hostname.toLowerCase();
    if (!host.endsWith(".workers.dev") || host.length <= ".workers.dev".length) return null;
    if (url.username || url.password || url.search || url.hash) return null;
    if (url.port && url.port !== "443") return null;
    if (url.pathname !== EXPECTED_PATH) return null;
    return `https://${host}${EXPECTED_PATH}`;
  } catch {
    return null;
  }
}

function constantTimeEqual(left: string, right: string): boolean {
  if (left.length !== right.length) return false;
  let diff = 0;
  for (let index = 0; index < left.length; index += 1) {
    diff |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return diff === 0;
}

function compactErrorCode(error: unknown): string {
  return (error instanceof Error ? error.message : String(error || "unknown_error"))
    .toLowerCase()
    .replace(/[^a-z0-9_:-]+/g, "_")
    .slice(0, 120) || "failover_control_register_failed";
}

function reply(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
