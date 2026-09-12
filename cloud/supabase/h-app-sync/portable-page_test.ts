import {
  buildPortableV3Manifest,
  buildPortableV3PageEnvelope,
  parsePortableV3PageRequest,
  parsePortableV3PageSize,
  verifyPortableV3Manifest,
  verifyPortableV3PageIntegrity,
} from "./portable-page.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

const sessionId = "33333333-3333-4333-8333-333333333333";
const createdAt = "2026-09-12T10:00:00.000Z";
const expiresAt = "2026-09-12T10:15:00.000Z";
const counts = { memories: 501, tasks: 1, reminders: 0, contacts: 0, learningState: 1 };

async function page(
  section: "memories" | "tasks" | "reminders" | "contacts" | "learning",
  pageIndex: number,
  items: unknown[],
  pageCounts: unknown = counts,
  pageSessionId = sessionId,
) {
  return await buildPortableV3PageEnvelope({
    sessionId: pageSessionId,
    section,
    pageIndex,
    items,
    counts: pageCounts,
    expiresAt,
    generatedAt: new Date(createdAt),
  });
}

Deno.test("portable v3 request parsing is bounded and opaque-session based", () => {
  assert(parsePortableV3PageSize({}) === 200);
  assert(parsePortableV3PageSize({ page_size: 500 }) === 500);
  const parsed = parsePortableV3PageRequest({
    session_id: sessionId,
    section: "memories",
    page_index: 2,
  });
  assert(parsed.sessionId === sessionId);
  assert(parsed.section === "memories");
  assert(parsed.pageIndex === 2);

  for (const invalid of [0, -1, 501, 1.5, "abc"]) {
    let failed = false;
    try {
      parsePortableV3PageSize({ page_size: invalid });
    } catch (_) {
      failed = true;
    }
    assert(failed, `invalid page size must fail: ${invalid}`);
  }
});

Deno.test("portable v3 page digest detects tampering", async () => {
  const value = await page("tasks", 0, [{ id: "1", body: "original" }]);
  assert(await verifyPortableV3PageIntegrity(value));
  (value.items[0] as any).body = "tampered";
  assert(!(await verifyPortableV3PageIntegrity(value)));
});

Deno.test("portable v3 manifest proves complete ordered multi-page export above 500 rows", async () => {
  const pages = [
    await page("memories", 0, Array.from({ length: 500 }, (_, index) => ({ id: `m-${index}` }))),
    await page("memories", 1, [{ id: "m-500" }]),
    await page("tasks", 0, [{ id: "1", body: "task" }]),
    await page("learning", 0, [{ firstMetAt: createdAt, turnCount: 9 }]),
  ];
  const manifest = await buildPortableV3Manifest({
    sessionId,
    pages,
    counts,
    createdAt,
    expiresAt,
    restoreSupported: true,
  });
  assert(manifest.completeForSchemaVersion === true);
  assert(manifest.pages.length === 4);
  assert(manifest.counts.memories === 501);
  assert(manifest.counts.learningState === 1);
  assert(await verifyPortableV3Manifest(manifest, pages));
});

Deno.test("portable v3 manifest rejects a missing middle page", async () => {
  const pages = [
    await page("memories", 0, Array.from({ length: 500 }, (_, index) => ({ id: `m-${index}` }))),
    await page("tasks", 0, [{ id: "1" }]),
    await page("learning", 0, [{ turnCount: 1 }]),
  ];
  let failed = false;
  try {
    await buildPortableV3Manifest({ sessionId, pages, counts, createdAt, expiresAt });
  } catch (error) {
    failed = String(error).includes("portable_v3_counts_mismatch");
  }
  assert(failed, "manifest must reject an incomplete section");
});

Deno.test("portable v3 manifest rejects duplicate and mixed-session pages", async () => {
  const memory0 = await page("memories", 0, Array.from({ length: 500 }, (_, index) => ({ id: `m-${index}` })));
  const memory1 = await page("memories", 1, [{ id: "m-500" }]);
  const task = await page("tasks", 0, [{ id: "1" }]);
  const learning = await page("learning", 0, [{ turnCount: 1 }]);

  let duplicateFailed = false;
  try {
    await buildPortableV3Manifest({
      sessionId,
      pages: [memory0, memory0, memory1, task, learning],
      counts,
      createdAt,
      expiresAt,
    });
  } catch (error) {
    duplicateFailed = String(error).includes("portable_v3_duplicate_page");
  }
  assert(duplicateFailed);

  const alien = await page(
    "memories",
    1,
    [{ id: "m-500" }],
    counts,
    "44444444-4444-4444-8444-444444444444",
  );
  let mixedFailed = false;
  try {
    await buildPortableV3Manifest({
      sessionId,
      pages: [memory0, alien, task, learning],
      counts,
      createdAt,
      expiresAt,
    });
  } catch (error) {
    mixedFailed = String(error).includes("portable_v3_mixed_session");
  }
  assert(mixedFailed);
});

Deno.test("portable v3 manifest rejects mixed count metadata", async () => {
  const alteredCounts = { ...counts, memories: 502 };
  const pages = [
    await page("memories", 0, Array.from({ length: 500 }, (_, index) => ({ id: `m-${index}` }))),
    await page("memories", 1, [{ id: "m-500" }], alteredCounts),
    await page("tasks", 0, [{ id: "1" }]),
    await page("learning", 0, [{ turnCount: 1 }]),
  ];
  let failed = false;
  try {
    await buildPortableV3Manifest({ sessionId, pages, counts, createdAt, expiresAt });
  } catch (error) {
    failed = String(error).includes("portable_v3_mixed_counts");
  }
  assert(failed);
});
