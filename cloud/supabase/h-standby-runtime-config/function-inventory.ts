export const REQUIRED_STANDBY_EXECUTION_FUNCTIONS = [
  "h-ai-provider-app",
  "h-ai-provider-config",
  "h-app-media",
  "h-app-runtime-route",
  "h-app-sync",
  "h-knowledge-verifier",
  "h-learning-cycle",
  "h-openrouter-oauth",
  "h-owner-config",
  "h-portable-restore",
  "h-portable-snapshot",
  "h-provider-config",
  "h-reminder-sync",
  "h-runtime-readiness",
  "h-standby-promote",
  "h-tavily-config",
  "h-whatsapp-action",
  "h-whatsapp-inbox",
  "h-whatsapp-media",
  "h-whatsapp-peach",
] as const;

export const STANDBY_CONTROL_PLANE_FUNCTIONS = [
  "h-backup-runner",
  "h-cloud-backup-config",
  "h-cloud-manager",
  "h-standby-replicator",
  "h-standby-runtime-app",
  "h-standby-runtime-config",
] as const;

// h-app-runtime-route was introduced after the base standby bundle was frozen. Keep its
// source immutable as well instead of making the provisioner depend on a moving branch.
// Cache entries are namespaced by bundle ref so dependencies from this supplemental bundle
// can never contaminate a function deployed from the base bundle.
export const APP_RUNTIME_ROUTE_BUNDLE_REF = "9bb49eb6b32cfc73b91732063ed10cf0ffa88f07";

const SOURCE_ROOT = "cloud/supabase/";
const MAX_SOURCE_FILE_BYTES = 512 * 1024;
const MAX_FUNCTION_FILES = 96;
const MAX_FUNCTION_BUNDLE_BYTES = 4 * 1024 * 1024;
const MAX_IMPORT_DEPTH = 64;

export type StandbyFunctionInventoryResult = {
  count: number;
  slugs: string[];
  bundleRef: string;
  supplementalBundleRefs: string[];
};

type SourceFile = {
  path: string;
  body: ArrayBuffer;
  text: string | null;
};

type InventoryOptions = {
  projectRef: string;
  managementToken: string;
  githubToken: string;
  bundleRef: string;
  managementApi: string;
  githubContentsBase: string;
};

export async function deployAndVerifyStandbyFunctionInventory(
  options: InventoryOptions,
): Promise<StandbyFunctionInventoryResult> {
  if (!/^[0-9a-f]{40}$/.test(options.bundleRef)) throw new Error("standby_function_bundle_ref_invalid");
  if (!/^[0-9a-f]{40}$/.test(APP_RUNTIME_ROUTE_BUNDLE_REF)) throw new Error("standby_app_route_bundle_ref_invalid");
  const cache = new Map<string, SourceFile | null>();

  for (const slug of REQUIRED_STANDBY_EXECUTION_FUNCTIONS) {
    const sourceOptions = slug === "h-app-runtime-route"
      ? { ...options, bundleRef: APP_RUNTIME_ROUTE_BUNDLE_REF }
      : options;
    const entrypoint = `${SOURCE_ROOT}${slug}/index.ts`;
    const files = await collectPinnedSourceClosure(entrypoint, sourceOptions, cache);
    await deployFunction(slug, entrypoint, files, sourceOptions);
  }

  const inventory = await listFunctions(options);
  const missing = REQUIRED_STANDBY_EXECUTION_FUNCTIONS.filter((slug) => inventory.get(slug) !== "ACTIVE");
  if (missing.length > 0) {
    throw new Error(`standby_function_inventory_unverified:${missing.join(",")}`);
  }

  return {
    count: REQUIRED_STANDBY_EXECUTION_FUNCTIONS.length,
    slugs: [...REQUIRED_STANDBY_EXECUTION_FUNCTIONS],
    bundleRef: options.bundleRef,
    supplementalBundleRefs: [APP_RUNTIME_ROUTE_BUNDLE_REF],
  };
}

async function collectPinnedSourceClosure(
  entrypoint: string,
  options: InventoryOptions,
  cache: Map<string, SourceFile | null>,
): Promise<SourceFile[]> {
  const first = await fetchCachedSourceFile(entrypoint, options, cache);
  if (!first) throw new Error(`standby_function_entrypoint_missing:${entrypoint}`);

  const files = new Map<string, SourceFile>();
  const queue: Array<{ file: SourceFile; depth: number }> = [{ file: first, depth: 0 }];
  let totalBytes = 0;

  while (queue.length > 0) {
    const current = queue.shift()!;
    if (files.has(current.file.path)) continue;
    if (current.depth > MAX_IMPORT_DEPTH) throw new Error("standby_function_import_depth_exceeded");

    files.set(current.file.path, current.file);
    totalBytes += current.file.body.byteLength;
    if (files.size > MAX_FUNCTION_FILES) throw new Error("standby_function_file_limit_exceeded");
    if (totalBytes > MAX_FUNCTION_BUNDLE_BYTES) throw new Error("standby_function_bundle_too_large");

    if (!current.file.text) continue;
    for (const specifier of extractRelativeImports(current.file.text)) {
      const dependency = await resolveDependency(current.file.path, specifier, options, cache);
      if (!files.has(dependency.path)) queue.push({ file: dependency, depth: current.depth + 1 });
    }
  }

  return [...files.values()].sort((a, b) => a.path.localeCompare(b.path));
}

async function resolveDependency(
  fromPath: string,
  specifier: string,
  options: InventoryOptions,
  cache: Map<string, SourceFile | null>,
): Promise<SourceFile> {
  const resolved = resolveRepoRelativeImport(fromPath, specifier);
  if (!resolved) throw new Error(`standby_function_import_unsafe:${fromPath}`);

  for (const candidate of dependencyCandidates(resolved)) {
    const file = await fetchCachedSourceFile(candidate, options, cache);
    if (file) return file;
  }
  throw new Error(`standby_function_dependency_missing:${resolved}`);
}

async function fetchCachedSourceFile(
  path: string,
  options: InventoryOptions,
  cache: Map<string, SourceFile | null>,
): Promise<SourceFile | null> {
  const cacheKey = `${options.bundleRef}:${path}`;
  if (cache.has(cacheKey)) return cache.get(cacheKey) ?? null;
  const encodedPath = path.split("/").map((segment) => encodeURIComponent(segment)).join("/");
  const response = await fetch(`${options.githubContentsBase}/${encodedPath}?ref=${options.bundleRef}`, {
    headers: {
      Authorization: `Bearer ${options.githubToken}`,
      Accept: "application/vnd.github.raw+json",
      "X-GitHub-Api-Version": "2022-11-28",
      "Cache-Control": "no-store",
    },
  });
  if (response.status === 404) {
    cache.set(cacheKey, null);
    return null;
  }
  if (!response.ok) throw new Error(`standby_function_source_fetch_${response.status}`);

  const body = await response.arrayBuffer();
  if (body.byteLength === 0) throw new Error(`standby_function_source_empty:${path}`);
  if (body.byteLength > MAX_SOURCE_FILE_BYTES) throw new Error(`standby_function_source_too_large:${path}`);
  const text = isTextModule(path) ? new TextDecoder().decode(body) : null;
  const result = { path, body, text };
  cache.set(cacheKey, result);
  return result;
}

async function deployFunction(
  slug: string,
  entrypoint: string,
  files: SourceFile[],
  options: InventoryOptions,
): Promise<void> {
  const form = new FormData();
  form.append("metadata", JSON.stringify({ name: slug, entrypoint_path: entrypoint, verify_jwt: false }));
  for (const file of files) {
    form.append("file", new Blob([file.body], { type: contentType(file.path) }), file.path);
  }

  const response = await fetch(
    `${options.managementApi}/projects/${encodeURIComponent(options.projectRef)}/functions/deploy?slug=${encodeURIComponent(slug)}`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${options.managementToken}`, "Cache-Control": "no-store" },
      body: form,
    },
  );
  if (!response.ok) throw new Error(`standby_function_deploy_${slug}_${response.status}`);
}

async function listFunctions(options: InventoryOptions): Promise<Map<string, string>> {
  const response = await fetch(
    `${options.managementApi}/projects/${encodeURIComponent(options.projectRef)}/functions`,
    {
      headers: {
        Authorization: `Bearer ${options.managementToken}`,
        Accept: "application/json",
        "Cache-Control": "no-store",
      },
    },
  );
  if (!response.ok) throw new Error(`standby_function_inventory_list_${response.status}`);
  const body = await response.json().catch(() => null);
  if (!Array.isArray(body)) throw new Error("standby_function_inventory_list_invalid");

  const result = new Map<string, string>();
  for (const item of body) {
    const slug = String(item?.slug || "").trim();
    const status = String(item?.status || "").trim().toUpperCase();
    if (slug) result.set(slug, status);
  }
  return result;
}

export function extractRelativeImports(source: string): string[] {
  const found = new Set<string>();
  const pattern = /(?:from\s*|import\s*\(\s*|import\s*)["'](\.{1,2}\/[^"']+)["']/g;
  for (const match of source.matchAll(pattern)) {
    const value = String(match[1] || "").trim();
    if (value) found.add(value);
  }
  return [...found];
}

export function resolveRepoRelativeImport(fromPath: string, specifier: string): string | null {
  if (!specifier.startsWith("./") && !specifier.startsWith("../")) return null;
  const cleanSpecifier = specifier.split(/[?#]/, 1)[0];
  const parts = fromPath.split("/");
  parts.pop();
  for (const segment of cleanSpecifier.split("/")) {
    if (!segment || segment === ".") continue;
    if (segment === "..") {
      if (parts.length === 0) return null;
      parts.pop();
    } else {
      parts.push(segment);
    }
  }
  const resolved = parts.join("/");
  if (!resolved.startsWith(SOURCE_ROOT) || resolved.includes("/../") || resolved.endsWith("/..")) return null;
  return resolved;
}

function dependencyCandidates(path: string): string[] {
  if (/[.][a-z0-9]+$/i.test(path)) return [path];
  return [
    `${path}.ts`,
    `${path}.tsx`,
    `${path}.js`,
    `${path}.mjs`,
    `${path}.json`,
    `${path}/index.ts`,
    `${path}/index.tsx`,
    `${path}/index.js`,
  ];
}

function isTextModule(path: string): boolean {
  return /[.](?:ts|tsx|js|jsx|mjs|cjs|json|jsonc|css|html|txt)$/i.test(path);
}

function contentType(path: string): string {
  if (/[.](?:ts|tsx)$/i.test(path)) return "application/typescript";
  if (/[.](?:js|jsx|mjs|cjs)$/i.test(path)) return "application/javascript";
  if (/[.]jsonc?$/i.test(path)) return "application/json";
  return "application/octet-stream";
}
