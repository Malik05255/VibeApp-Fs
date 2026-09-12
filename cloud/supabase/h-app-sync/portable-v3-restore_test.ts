import {
  buildPortableV3Manifest,
  buildPortableV3PageEnvelope,
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
  (memoryPage.items[0] as any).body = "tampered";
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
