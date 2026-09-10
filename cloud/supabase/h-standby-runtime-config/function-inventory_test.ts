import {
  extractRelativeImports,
  REQUIRED_STANDBY_EXECUTION_FUNCTIONS,
  resolveRepoRelativeImport,
  STANDBY_CONTROL_PLANE_FUNCTIONS,
} from "./function-inventory.ts";

Deno.test("standby execution inventory is explicit and excludes control plane", () => {
  if (REQUIRED_STANDBY_EXECUTION_FUNCTIONS.length < 10) {
    throw new Error("execution inventory unexpectedly small");
  }
  const execution = new Set(REQUIRED_STANDBY_EXECUTION_FUNCTIONS);
  for (const slug of STANDBY_CONTROL_PLANE_FUNCTIONS) {
    if (execution.has(slug as never)) throw new Error(`control-plane function leaked into execution inventory: ${slug}`);
  }
  for (const required of ["h-app-sync", "h-whatsapp-inbox", "h-reminder-sync", "h-runtime-readiness"]) {
    if (!execution.has(required as never)) throw new Error(`required execution function missing: ${required}`);
  }
});

Deno.test("relative import extraction covers side effects, exports and dynamic imports", () => {
  const imports = extractRelativeImports(`
    import "./side-effect.ts";
    import { one } from "./one.ts";
    export { two } from "../shared/two.ts";
    const three = await import("../shared/three.ts");
    import { createClient } from "jsr:@supabase/supabase-js@2";
  `).sort();
  const expected = ["../shared/three.ts", "../shared/two.ts", "./one.ts", "./side-effect.ts"].sort();
  if (JSON.stringify(imports) !== JSON.stringify(expected)) {
    throw new Error(`unexpected relative imports: ${JSON.stringify(imports)}`);
  }
});

Deno.test("repo-relative imports preserve cross-function source paths", () => {
  const resolved = resolveRepoRelativeImport(
    "cloud/supabase/h-app-sync/index.ts",
    "../h-whatsapp-inbox/contact-manager.ts",
  );
  if (resolved !== "cloud/supabase/h-whatsapp-inbox/contact-manager.ts") {
    throw new Error(`cross-function import resolved incorrectly: ${resolved}`);
  }
});

Deno.test("repo-relative imports cannot escape cloud/supabase", () => {
  const escaped = resolveRepoRelativeImport("cloud/supabase/h-app-sync/index.ts", "../../../secret.ts");
  if (escaped !== null) throw new Error(`unsafe path escaped source root: ${escaped}`);
  const external = resolveRepoRelativeImport("cloud/supabase/h-app-sync/index.ts", "https://example.com/x.ts");
  if (external !== null) throw new Error(`non-relative import was accepted: ${external}`);
});
