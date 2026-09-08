import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const FUNCTION_NAME = "h-tavily-config";
const CREDENTIAL_ID = "tavily_default";
const PROVIDER = "tavily";
const PURPOSE = "tavily";
const FREE_PLAN = "researcher";
const USAGE_URL = "https://api.tavily.com/usage";

type Quota = {
  plan: string;
  accountUsed: number;
  accountLimit: number;
  keyUsed: number;
  keyLimit: number | null;
  remaining: number;
};

Deno.serve(async (req: Request) => {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRole) return json({ ok: false, error: "runtime credentials unavailable" }, 500);

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const url = new URL(req.url);
  const path = routePath(url.pathname);
  const publicBase = `${supabaseUrl}/functions/v1/${FUNCTION_NAME}`;

  try {
    if (req.method === "GET" && ["/", "/status", "/health"].includes(path)) {
      return json(await getStatus(db));
    }

    if (req.method === "GET" && path === "/connect") {
      const setup = url.searchParams.get("setup")?.trim() || "";
      const valid = await validateToken(db, setup);
      if (!valid.ok) return html(errorPage(valid.error || "Link invalid or expired"), 400);
      return html(connectPage(publicBase, setup));
    }

    if (req.method === "POST" && path === "/save") {
      const setup = url.searchParams.get("setup")?.trim() || "";
      const valid = await validateToken(db, setup);
      if (!valid.ok) return html(errorPage(valid.error || "Link invalid or expired"), 400);

      const form = await req.formData();
      const apiKey = String(form.get("api_key") || "").trim();
      if (apiKey.length < 12 || apiKey.length > 500) return html(errorPage("Tavily API key is invalid."), 400);

      const usage = await loadUsage(apiKey);
      const quota = parseQuota(usage);
      if (quota.plan.toLowerCase() !== FREE_PLAN) {
        return html(errorPage(`H accepts the free Researcher plan only. Current plan: ${escapeHtml(quota.plan || "unknown")}`), 400);
      }
      if (quota.accountLimit <= 0 || quota.remaining <= 0) {
        return html(errorPage("Your free Tavily quota is exhausted or unavailable."), 400);
      }

      const encrypted = await encryptSecret(apiKey);
      const now = new Date().toISOString();
      const { error: credError } = await db.from("h_runtime_ai_credentials").upsert({
        id: CREDENTIAL_ID,
        provider: PROVIDER,
        secret_ciphertext: encrypted.ciphertext,
        secret_iv: encrypted.iv,
        secret_version: 1,
        selected_model: null,
        model_verified_at: null,
        oauth_metadata: {
          free_only: true,
          plan: quota.plan,
          account_usage_at_connect: quota.accountUsed,
          account_limit_at_connect: quota.accountLimit,
          key_usage_at_connect: quota.keyUsed,
          key_limit_at_connect: quota.keyLimit,
          remaining_at_connect: quota.remaining,
          encryption_source: "supabase_service_role_derived_v1",
        },
        connected_at: now,
        updated_at: now,
      }, { onConflict: "id" });
      if (credError) throw credError;

      const { error: useError } = await db.from("h_runtime_ai_setup_links")
        .update({ used_at: now })
        .eq("token_hash", valid.hash)
        .eq("purpose", PURPOSE)
        .is("used_at", null);
      if (useError) throw useError;

      await db.from("h_runtime_state").upsert({
        key: "web_search",
        value: {
          provider: PROVIDER,
          connected: true,
          ready: true,
          free_only: true,
          plan: quota.plan,
          account_usage: quota.accountUsed,
          account_limit: quota.accountLimit,
          key_usage: quota.keyUsed,
          key_limit: quota.keyLimit,
          remaining: quota.remaining,
          connected_at: now,
          encryption_source: "supabase_service_role_derived_v1",
        },
        updated_at: now,
      }, { onConflict: "key" });

      return html(successPage(quota));
    }

    return json({ ok: false, error: "Not found", path }, 404);
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    console.error("h-tavily-config failed", message);
    return path === "/connect" || path === "/save" ? html(errorPage(message), 500) : json({ ok: false, error: message }, 500);
  }
});

async function getStatus(db: any) {
  const { data: row } = await db.from("h_runtime_ai_credentials")
    .select("secret_ciphertext,secret_iv,secret_version,connected_at,updated_at")
    .eq("id", CREDENTIAL_ID).eq("provider", PROVIDER).maybeSingle();
  if (!row) return { ok: true, provider: PROVIDER, connected: false, ready: false, freeOnly: true };

  try {
    if (Number(row.secret_version || 1) !== 1) throw new Error("Unsupported credential version");
    const apiKey = await decryptSecret(String(row.secret_ciphertext), String(row.secret_iv));
    const quota = parseQuota(await loadUsage(apiKey));
    return {
      ok: true,
      provider: PROVIDER,
      connected: true,
      ready: quota.plan.toLowerCase() === FREE_PLAN && quota.remaining > 0,
      freeOnly: true,
      plan: quota.plan,
      accountUsage: quota.accountUsed,
      accountLimit: quota.accountLimit,
      keyUsage: quota.keyUsed,
      keyLimit: quota.keyLimit,
      remaining: quota.remaining,
      connectedAt: row.connected_at,
      updatedAt: row.updated_at,
    };
  } catch (e) {
    return { ok: true, provider: PROVIDER, connected: true, ready: false, freeOnly: true, error: e instanceof Error ? e.message : String(e) };
  }
}

async function validateToken(db: any, token: string): Promise<{ok:boolean;hash:string;error?:string}> {
  if (!token) return { ok: false, hash: "", error: "Link is incomplete." };
  const hash = await sha256b64url(token);
  const { data, error } = await db.from("h_runtime_ai_setup_links")
    .select("expires_at,used_at,purpose")
    .eq("token_hash", hash).eq("purpose", PURPOSE).maybeSingle();
  if (error) throw error;
  if (!data) return { ok: false, hash, error: "Link is invalid." };
  if (data.used_at) return { ok: false, hash, error: "This link was already used." };
  if (new Date(data.expires_at).getTime() <= Date.now()) return { ok: false, hash, error: "Link expired. Request a new link." };
  return { ok: true, hash };
}

async function loadUsage(apiKey: string) {
  const response = await fetch(USAGE_URL, { headers: { Authorization: `Bearer ${apiKey}`, Accept: "application/json" } });
  const text = await response.text();
  if (!response.ok) throw new Error(`Tavily rejected the key (${response.status}): ${text.slice(0, 180)}`);
  return JSON.parse(text);
}

function parseQuota(x: any): Quota {
  const plan = String(x?.account?.current_plan ?? x?.current_plan ?? x?.plan ?? x?.account?.plan ?? "").trim();
  const accountUsed = nonNegative(x?.account?.plan_usage, x?.usage, x?.total_usage, 0);
  const accountLimit = positive(x?.account?.plan_limit, x?.monthly_limit, x?.limit, plan.toLowerCase() === FREE_PLAN ? 1000 : 0);
  const keyUsed = nonNegative(x?.key?.usage, 0);
  const rawKeyLimit = Number(x?.key?.limit);
  const keyLimit = Number.isFinite(rawKeyLimit) && rawKeyLimit > 0 ? rawKeyLimit : null;
  const accountRemaining = Math.max(0, accountLimit - accountUsed);
  const keyRemaining = keyLimit == null ? Number.POSITIVE_INFINITY : Math.max(0, keyLimit - keyUsed);
  const remaining = Math.max(0, Math.min(accountRemaining, keyRemaining));
  return { plan, accountUsed, accountLimit, keyUsed, keyLimit, remaining };
}

function nonNegative(...values: unknown[]) {
  for (const value of values) {
    const n = Number(value);
    if (Number.isFinite(n) && n >= 0) return n;
  }
  return 0;
}
function positive(...values: unknown[]) {
  for (const value of values) {
    const n = Number(value);
    if (Number.isFinite(n) && n > 0) return n;
  }
  return 0;
}

function connectPage(base:string, setup:string){
  const action = `${base}/save?setup=${encodeURIComponent(setup)}`;
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Connect H Deep Search</title><style>${css()}</style></head><body><main><h1>Connect H Deep Search</h1><p>Paste your Tavily API key from the free <b>Researcher</b> plan.</p><form method="post" action="${action}"><label>Tavily API Key</label><input name="api_key" type="password" autocomplete="off" required placeholder="tvly-..."><button type="submit">Connect Internet Search</button></form><p class="small">The key is encrypted on the H server and is not stored in GitHub or the Android app.</p></main></body></html>`;
}
function successPage(q:Quota){ return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Connected</title><style>${css()}</style></head><body><main><h1>H Deep Search connected ✅</h1><p>Plan: <b>${escapeHtml(q.plan)}</b></p><p>Account usage: <b>${q.accountUsed}</b> / <b>${q.accountLimit}</b> credits.</p><p>Remaining usable credits: <b>${Number.isFinite(q.remaining) ? q.remaining : q.accountLimit - q.accountUsed}</b>.</p><p>H will stay on the free-only search path.</p></main></body></html>`; }
function errorPage(m:string){ return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Error</title><style>${css()}</style></head><body><main><h1>Could not connect H Deep Search</h1><p>${escapeHtml(m)}</p></main></body></html>`; }
function css(){ return `body{font-family:system-ui,sans-serif;background:#f6f7f9;color:#15171a;margin:0;padding:24px}main{max-width:560px;margin:8vh auto;background:#fff;padding:28px;border-radius:18px;box-shadow:0 8px 30px #00000012}h1{font-size:24px}p{line-height:1.65}label{display:block;margin:20px 0 8px;font-weight:700}input{box-sizing:border-box;width:100%;padding:14px;border:1px solid #cfd4da;border-radius:12px;font-size:16px}button{width:100%;margin-top:14px;padding:14px;border:0;border-radius:12px;background:#111;color:#fff;font-size:16px;font-weight:700}.small{font-size:13px;color:#626a73}`; }
function escapeHtml(v:string){ return String(v).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]||c)); }
function routePath(p:string){ const markers=[`/functions/v1/${FUNCTION_NAME}`,`/${FUNCTION_NAME}`]; for(const m of markers){ const i=p.indexOf(m); if(i>=0){ const r=p.slice(i+m.length); return r ? (r.startsWith("/")?r:`/${r}`) : "/"; } } for(const r of ["connect","save","status","health"]){ if(p===`/${r}`||p.endsWith(`/${r}`)) return `/${r}`; } return p||"/"; }

async function getKey(){ const root=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim(); if(!root) throw new Error("service role unavailable"); const d=await crypto.subtle.digest("SHA-256",ab(new TextEncoder().encode(`h-tavily-aes-v1:${root}`))); return crypto.subtle.importKey("raw",d,{name:"AES-GCM"},false,["encrypt","decrypt"]); }
async function encryptSecret(v:string){ const iv=crypto.getRandomValues(new Uint8Array(12)); const encrypted=await crypto.subtle.encrypt({name:"AES-GCM",iv:ab(iv)},await getKey(),ab(new TextEncoder().encode(v))); return {ciphertext:b64(new Uint8Array(encrypted)),iv:b64(iv)}; }
async function decryptSecret(c:string,i:string){ const d=await crypto.subtle.decrypt({name:"AES-GCM",iv:ab(unb64(i))},await getKey(),ab(unb64(c))); return new TextDecoder().decode(d); }
async function sha256b64url(v:string){ return b64(new Uint8Array(await crypto.subtle.digest("SHA-256",ab(new TextEncoder().encode(v))))); }
function b64(b:Uint8Array){ let s=""; for(const x of b)s+=String.fromCharCode(x); return btoa(s).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/g,""); }
function unb64(v:string){ const n=v.replace(/-/g,"+").replace(/_/g,"/"); const p=n+"=".repeat((4-n.length%4)%4); const s=atob(p); return Uint8Array.from(s,c=>c.charCodeAt(0)); }
function ab(b:Uint8Array):ArrayBuffer{ const c=new Uint8Array(b.byteLength); c.set(b); return c.buffer; }
function json(v:unknown,status=200){ return new Response(JSON.stringify(v),{status,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store"}}); }
function html(v:string,status=200){ return new Response(new TextEncoder().encode(v),{status,headers:{"content-type":"text/html; charset=utf-8","cache-control":"no-store","x-content-type-options":"nosniff"}}); }
