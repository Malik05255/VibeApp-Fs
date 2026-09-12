import {
  buildPortablePageEnvelope,
  decodePortablePageCursor,
  encodePortablePageCursor,
  parsePortablePageRequest,
  PORTABLE_PAGE_MAX_ROWS,
  sanitizePortablePageRows,
  verifyPortablePageIntegrity,
} from "./portable-page.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("portable v3 cursor round-trips and rejects filter injection", () => {
  const cursor = {
    createdAt: "2026-09-12T10:20:30.000Z",
    id: "33333333-3333-4333-8333-333333333333",
  };
  const encoded = encodePortablePageCursor(cursor);
  const decoded = decodePortablePageCursor(encoded);
  assert(decoded.createdAt === cursor.createdAt);
  assert(decoded.id === cursor.id);

  const malicious = btoa(JSON.stringify({
    createdAt: "2026-09-12T10:20:30.000Z),user_key.neq.safe",
    id: "x",
  })).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  let failed = false;
  try {
    decodePortablePageCursor(malicious);
  } catch (_) {
    failed = true;
  }
  assert(failed, "cursor filter injection must fail closed");
});

Deno.test("portable v3 request bounds every page", () => {
  assert(parsePortablePageRequest({ section: "memories" }).limit === 200);
  assert(parsePortablePageRequest({ section: "tasks", limit: PORTABLE_PAGE_MAX_ROWS }).limit === PORTABLE_PAGE_MAX_ROWS);

  for (const invalid of [0, -1, PORTABLE_PAGE_MAX_ROWS + 1, 1.5, "abc"]) {
    let failed = false;
    try {
      parsePortablePageRequest({ section: "memories", limit: invalid });
    } catch (_) {
      failed = true;
    }
    assert(failed, `invalid page limit must fail: ${invalid}`);
  }
});

Deno.test("portable v3 page reuses v2 privacy sanitization", async () => {
  const rows = [{
    id: "33333333-3333-4333-8333-333333333333",
    user_key: "must-not-export",
    category: "idea",
    body: "portable fact",
    original_text: "remember portable fact",
    created_at: "2026-09-12T10:00:00Z",
    updated_at: "2026-09-12T10:01:00Z",
    secret_ciphertext: "must-not-export-secret",
  }];
  const items = await sanitizePortablePageRows("memories", rows);
  const serialized = JSON.stringify(items);
  assert(items.length === 1);
  assert(serialized.includes("portable fact"));
  assert(!serialized.includes("must-not-export"));
  assert(!serialized.includes("secret_ciphertext"));
});

Deno.test("portable v3 page digest detects tampering", async () => {
  const page = await buildPortablePageEnvelope({
    section: "memories",
    items: [{ id: "m1", body: "original", createdAt: "2026-09-12T10:00:00Z" }],
    startCursor: null,
    nextCursor: null,
    hasMore: false,
    generatedAt: new Date("2026-09-12T10:00:00Z"),
  });
  assert(await verifyPortablePageIntegrity(page));
  (page.items[0] as any).body = "tampered";
  assert(!(await verifyPortablePageIntegrity(page)));
});

Deno.test("portable v3 represents more than legacy 500 rows as bounded pages", async () => {
  const total = PORTABLE_PAGE_MAX_ROWS * 2 + 1;
  const rows = Array.from({ length: total }, (_, index) => ({
    id: `m-${String(index).padStart(4, "0")}`,
    category: "general",
    body: `memory ${index}`,
    created_at: `2026-09-${String(1 + Math.floor(index / 100)).padStart(2, "0")}T00:00:00Z`,
  }));

  const chunks = [
    rows.slice(0, PORTABLE_PAGE_MAX_ROWS),
    rows.slice(PORTABLE_PAGE_MAX_ROWS, PORTABLE_PAGE_MAX_ROWS * 2),
    rows.slice(PORTABLE_PAGE_MAX_ROWS * 2),
  ];
  let exported = 0;
  for (let index = 0; index < chunks.length; index += 1) {
    const items = await sanitizePortablePageRows("memories", chunks[index]);
    const hasMore = index < chunks.length - 1;
    const nextCursor = hasMore
      ? encodePortablePageCursor({
        createdAt: `2026-09-${String(index + 2).padStart(2, "0")}T00:00:00Z`,
        id: `page-${index + 1}`,
      })
      : null;
    const page = await buildPortablePageEnvelope({
      section: "memories",
      items,
      startCursor: index === 0 ? null : "opaque-prior-cursor",
      nextCursor,
      hasMore,
    });
    assert(page.itemCount <= PORTABLE_PAGE_MAX_ROWS);
    assert(await verifyPortablePageIntegrity(page));
    exported += page.itemCount;
  }
  assert(exported === total);
});
