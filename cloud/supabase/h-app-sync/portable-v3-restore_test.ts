import {
  buildPortableV3Manifest,
  buildPortableV3PageEnvelope,
  type PortableV3Section,
} from "./portable-page.ts";
import {
  validatePortableV3RestoreManifest,
  validatePortableV3RestorePage,
} from "./portable-v3-restore.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

const sessionId = "33333333-3333-4333-8333-333333333333";
const createdAt = "2026-09-12T10:00:00.000Z";
const expiresAt = "2026-09-12T10:15:00.000Z";
const counts = { memories: 1, tasks: 1, reminders: 0, contacts: 0, learningState: 0 };

async function buildCountPages(
  section: PortableV3Section,
  total: number,
  allCounts: Record<string, number>,
) {
  const pages = [];
  let remaining = total;
  let pageIndex = 0;
  while (remaining > 0) {
    const size = Math.min(500, remaining);
    pages.push(await buildPortableV3PageEnvelope({
      sessionId,
      section,
      pageIndex,
      items: Array.from({ length: size }, () => ({})),
      counts: allCounts,
      expiresAt,
      generatedAt: new Date(createdAt),
    }));
    remaining -= size;
    pageIndex += 1;
  }
  return pages;
}

Deno.test("portable v3 restore accepts allow-listed manifest page content", async () => {
  const memoryPage = await buildPortableV3PageEnvelope({
    sessionId,
    section: "memories",
    pageIndex: 0,
    items: [{
      id: "11111111-1111-4111-8111-111111111111",
      category: "idea",
      body: "portable memory",
      originalText: "remember portable memory",
      createdAt,
      updatedAt: createdAt,
      secret: "ignored-by-restore",
    }],
    counts,
    expiresAt,
  });
  const taskPage = await buildPortableV3PageEnvelope({
    sessionId,
    section: "tasks",
    pageIndex: 0,
    items: [{
      id: "77",
      title: "Task",
      body: "portable task",
      taskType: "personal",
      priority: "simple",
      status: "active",
      dueAt: null,
      pausedAt: null,
      completedAt: null,
      cancelledAt: null,
      createdAt,
      updatedAt: createdAt,
    }],
    counts,
    expiresAt,
  });
  const manifest = await buildPortableV3Manifest({
    sessionId,
    pages: [memoryPage, taskPage],
    counts,
    createdAt,
    expiresAt,
  });
  const plan = await validatePortableV3RestoreManifest(manifest);
  const memory = await validatePortableV3RestorePage(memoryPage, plan);
  assert(memory.items.length === 1);
  assert(memory.items[0].body === "portable memory");
  assert(!("secret" in memory.items[0]), "unknown fields must be stripped before staging");
  const task = await validatePortableV3RestorePage(taskPage, plan);
  assert(task.items[0].id === "77");
});

Deno.test("portable v3 restore rejects manifest/page digest disagreement", async () => {
  const memoryPage = await buildPortableV3PageEnvelope({
    sessionId,
    section: "memories",
    pageIndex: 0,
    items: [{
      id: "11111111-1111-4111-8111-111111111111",
      category: "general",
      body: "memory",
      originalText: null,
      createdAt,
      updatedAt: createdAt,
    }],
    counts: { memories: 1, tasks: 0, reminders: 0, contacts: 0, learningState: 0 },
    expiresAt,
  });
  const manifest = await buildPortableV3Manifest({
    sessionId,
    pages: [memoryPage],
    counts: { memories: 1, tasks: 0, reminders: 0, contacts: 0, learningState: 0 },
    createdAt,
    expiresAt,
  });
  const plan = await validatePortableV3RestoreManifest(manifest);
  const tamperedItems = memoryPage.items as any[];
  (tamperedItems[0] as any).body = "tampered";
  let failed = false;
  try {
    await validatePortableV3RestorePage(memoryPage, plan);
  } catch (error) {
    failed = String(error).includes("portable_v3_page_integrity_failed");
  }
  assert(failed);
});

Deno.test("portable v3 restore rejects invalid task ids before database staging", async () => {
  const taskPage = await buildPortableV3PageEnvelope({
    sessionId,
    section: "tasks",
    pageIndex: 0,
    items: [{
      id: "not-a-bigint",
      title: null,
      body: "bad task",
      taskType: "personal",
      priority: "simple",
      status: "active",
      dueAt: null,
      pausedAt: null,
      completedAt: null,
      cancelledAt: null,
      createdAt,
      updatedAt: createdAt,
    }],
    counts: { memories: 0, tasks: 1, reminders: 0, contacts: 0, learningState: 0 },
    expiresAt,
  });
  const manifest = await buildPortableV3Manifest({
    sessionId,
    pages: [taskPage],
    counts: { memories: 0, tasks: 1, reminders: 0, contacts: 0, learningState: 0 },
    createdAt,
    expiresAt,
  });
  const plan = await validatePortableV3RestoreManifest(manifest);
  let failed = false;
  try {
    await validatePortableV3RestorePage(taskPage, plan);
  } catch (error) {
    failed = String(error).includes("portable_v3_task_invalid");
  }
  assert(failed);
});

Deno.test("portable v3 restore accepts the 20,000-row per-section ceiling", async () => {
  const maxCounts = { memories: 20_000, tasks: 0, reminders: 0, contacts: 0, learningState: 0 };
  const pages = await buildCountPages("memories", 20_000, maxCounts);
  const manifest = await buildPortableV3Manifest({
    sessionId,
    pages,
    counts: maxCounts,
    createdAt,
    expiresAt,
  });
  const plan = await validatePortableV3RestoreManifest(manifest);
  assert(plan.counts.memories === 20_000);
  assert(plan.pages.length === 40);
});

Deno.test("portable v3 restore rejects more than 20,000 rows in one section", async () => {
  const oversizedCounts = { memories: 20_001, tasks: 0, reminders: 0, contacts: 0, learningState: 0 };
  const pages = await buildCountPages("memories", 20_001, oversizedCounts);
  const manifest = await buildPortableV3Manifest({
    sessionId,
    pages,
    counts: oversizedCounts,
    createdAt,
    expiresAt,
  });
  let failed = false;
  try {
    await validatePortableV3RestoreManifest(manifest);
  } catch (error) {
    failed = String(error).includes("portable_v3_counts_invalid");
  }
  assert(failed, "restore must reject a section above the 20,000-row ceiling");
});

Deno.test("portable v3 restore rejects more than 50,000 rows in total", async () => {
  const totalCounts = {
    memories: 16_667,
    tasks: 16_667,
    reminders: 16_667,
    contacts: 0,
    learningState: 0,
  };
  const pages = [
    ...await buildCountPages("memories", totalCounts.memories, totalCounts),
    ...await buildCountPages("tasks", totalCounts.tasks, totalCounts),
    ...await buildCountPages("reminders", totalCounts.reminders, totalCounts),
  ];
  const manifest = await buildPortableV3Manifest({
    sessionId,
    pages,
    counts: totalCounts,
    createdAt,
    expiresAt,
  });
  let failed = false;
  try {
    await validatePortableV3RestoreManifest(manifest);
  } catch (error) {
    failed = String(error).includes("portable_v3_total_too_large");
  }
  assert(failed, "restore must reject a portable set above 50,000 rows total");
});

Deno.test("portable v3 restore detects manifest metadata tampering", async () => {
  const baseCounts = { memories: 1, tasks: 0, reminders: 0, contacts: 0, learningState: 0 };
  const pages = await buildCountPages("memories", 1, baseCounts);
  const manifest = await buildPortableV3Manifest({
    sessionId,
    pages,
    counts: baseCounts,
    createdAt,
    expiresAt,
  });
  const tampered = {
    ...manifest,
    createdAt: "2026-09-12T10:00:01.000Z",
  };
  let failed = false;
  try {
    await validatePortableV3RestoreManifest(tampered);
  } catch (error) {
    failed = String(error).includes("portable_v3_manifest_integrity_failed");
  }
  assert(failed, "manifest metadata tampering must fail integrity validation");
});
