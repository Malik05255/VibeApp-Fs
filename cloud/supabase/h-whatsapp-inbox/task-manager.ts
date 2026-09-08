export type HTaskPriority = "simple" | "medium" | "important";
export type HTaskPrioritySource = "user" | "auto";
export type HTaskStatus = "active" | "paused" | "completed" | "cancelled";

export type HTaskRecord = {
  id: number;
  user_key: string;
  conversation_id: number | null;
  title: string | null;
  body: string;
  task_type: string;
  priority: HTaskPriority;
  priority_source: HTaskPrioritySource;
  status: HTaskStatus;
  due_at: string | null;
  execution_plan: Record<string, unknown>;
  metadata: Record<string, unknown>;
  result_text: string | null;
  created_at: string;
  updated_at: string;
};

type DbClient = any;

type CreateTaskOptions = {
  taskType?: string;
  dueAt?: Date | null;
  explicitPriority?: HTaskPriority | null;
  title?: string | null;
  metadata?: Record<string, unknown>;
};

export function normalizePriority(value: unknown): HTaskPriority | null {
  const text = String(value || "").trim().toLowerCase();
  if (/^(simple|بسيط(?:ة)?|خفيف(?:ة)?)$/.test(text)) return "simple";
  if (/^(medium|متوسط(?:ة)?)$/.test(text)) return "medium";
  if (/^(important|مهم(?:ة)?|عالي(?:ة)?|high)$/.test(text)) return "important";
  return null;
}

export function detectExplicitPriority(text: string): HTaskPriority | null {
  const patterns = [
    /(?:التصنيف|تصنيف(?:ها)?|الأولوية|الاولوية|درجة\s*المهمة|تعامل\s*معها\s*ك|صنّفها|صنفها)\s*[:=\-]?\s*(بسيطة|بسيط|متوسطة|متوسط|مهمة|مهم)/i,
    /(?:priority)\s*[:=\-]?\s*(simple|medium|important|high)/i,
    /\[(بسيطة|متوسطة|مهمة|simple|medium|important)\]/i,
  ];
  for (const pattern of patterns) {
    const match = text.match(pattern);
    const normalized = normalizePriority(match?.[1]);
    if (normalized) return normalized;
  }
  return null;
}

export function classifyTaskPriority(text: string, taskType = "general"): HTaskPriority {
  const normalized = text.trim();
  if (/(بحث\s*عميق|تحقق\s*دقيق|تدقيق\s*كامل|مصادر\s*متعددة|مقارنة\s*شاملة|لا\s*تخمن|بدون\s*تخمين|مهم\s*جدا|ضروري\s*جدا|deep\s*(research|search))/i.test(normalized)) {
    return "important";
  }
  if (/(قارن|مقارنة|أفضل|افضل|أسعار|اسعار|سعر|شراء|اشتري|مطعم|فندق|قريب|مسار|طريق|أخبار|اخبار|آخر|اخر|أحدث|احدث|تحقق|تأكد|مصادر|compare|price|shopping|restaurant|hotel|route|latest|news)/i.test(normalized)) {
    return "medium";
  }
  if (taskType === "reminder" || /(?:ذكرني|ذكّرني|remind me)/i.test(normalized)) return "simple";
  if (normalized.length > 220 || /(?:خطوات|حلل|حلّل|ابحث|إبحث|دراسة|تقرير|research|analyze)/i.test(normalized)) return "medium";
  return "simple";
}

export function executionPlanForPriority(priority: HTaskPriority): Record<string, unknown> {
  if (priority === "important") {
    return {
      effort: "important",
      source_target: 6,
      max_fallbacks: 3,
      cross_verify: true,
      require_specialized_source: true,
      retry_with_rephrase: true,
      allow_unverified_claims: false,
    };
  }
  if (priority === "medium") {
    return {
      effort: "medium",
      source_target: 4,
      max_fallbacks: 2,
      cross_verify: true,
      require_specialized_source: false,
      retry_with_rephrase: true,
      allow_unverified_claims: false,
    };
  }
  return {
    effort: "simple",
    source_target: 2,
    max_fallbacks: 1,
    cross_verify: false,
    require_specialized_source: false,
    retry_with_rephrase: false,
    allow_unverified_claims: false,
  };
}

export async function createTask(
  db: DbClient,
  userKey: string,
  conversationId: number | null,
  body: string,
  options: CreateTaskOptions = {},
): Promise<HTaskRecord> {
  const priority = options.explicitPriority ?? classifyTaskPriority(body, options.taskType || "general");
  const source: HTaskPrioritySource = options.explicitPriority ? "user" : "auto";
  const now = new Date().toISOString();
  const { data, error } = await db.from("h_runtime_tasks").insert({
    user_key: userKey,
    conversation_id: conversationId,
    title: options.title?.trim().slice(0, 160) || deriveTitle(body),
    body: body.trim().slice(0, 12000),
    task_type: options.taskType || "general",
    priority,
    priority_source: source,
    status: "active",
    due_at: options.dueAt?.toISOString() ?? null,
    execution_plan: executionPlanForPriority(priority),
    metadata: options.metadata ?? {},
    updated_at: now,
  }).select("*").single();
  if (error || !data) throw error || new Error("Failed to create H task");
  return data as HTaskRecord;
}

export async function listTasks(db: DbClient, userKey: string, limit = 10): Promise<HTaskRecord[]> {
  const { data, error } = await db.from("h_runtime_tasks")
    .select("*")
    .eq("user_key", userKey)
    .in("status", ["active", "paused"])
    .order("created_at", { ascending: false })
    .limit(limit);
  if (error) throw error;
  return (data ?? []) as HTaskRecord[];
}

export async function updateTaskPriority(
  db: DbClient,
  userKey: string,
  taskId: number,
  priority: HTaskPriority,
): Promise<HTaskRecord | null> {
  const { data, error } = await db.from("h_runtime_tasks").update({
    priority,
    priority_source: "user",
    execution_plan: executionPlanForPriority(priority),
    updated_at: new Date().toISOString(),
  }).eq("id", taskId).eq("user_key", userKey).select("*").maybeSingle();
  if (error) throw error;
  return data as HTaskRecord | null;
}

export async function updateTaskStatus(
  db: DbClient,
  userKey: string,
  taskId: number,
  status: HTaskStatus,
): Promise<HTaskRecord | null> {
  const now = new Date().toISOString();
  const patch: Record<string, unknown> = { status, updated_at: now };
  if (status === "paused") patch.paused_at = now;
  if (status === "active") patch.paused_at = null;
  if (status === "completed") patch.completed_at = now;
  if (status === "cancelled") patch.cancelled_at = now;

  const { data, error } = await db.from("h_runtime_tasks").update(patch)
    .eq("id", taskId).eq("user_key", userKey).select("*").maybeSingle();
  if (error) throw error;
  if (data && status === "cancelled") {
    await db.from("h_runtime_reminders").update({ status: "cancelled", updated_at: now })
      .eq("task_id", taskId).in("status", ["pending", "waiting_template"]);
  }
  return data as HTaskRecord | null;
}

export async function getTaskById(db: DbClient, userKey: string, taskId: number): Promise<HTaskRecord | null> {
  const { data, error } = await db.from("h_runtime_tasks").select("*")
    .eq("id", taskId).eq("user_key", userKey).maybeSingle();
  if (error) throw error;
  return data as HTaskRecord | null;
}

export async function completeTask(db: DbClient, taskId: number, resultText?: string | null): Promise<void> {
  const now = new Date().toISOString();
  const patch: Record<string, unknown> = { status: "completed", completed_at: now, updated_at: now };
  if (resultText) patch.result_text = resultText.slice(0, 12000);
  await db.from("h_runtime_tasks").update(patch).eq("id", taskId).eq("status", "active");
}

export function formatTaskList(tasks: HTaskRecord[]): string {
  if (!tasks.length) return "ما عندك مهام نشطة أو موقوفة حاليًا.";
  const lines = tasks.map((task) => {
    const priority = priorityLabel(task.priority);
    const status = task.status === "paused" ? "موقوفة مؤقتًا" : "نشطة";
    const due = task.due_at ? ` — ${formatRiyadhDate(new Date(task.due_at))}` : "";
    return `#${task.id} [${priority}] [${status}] ${task.title || task.body.slice(0, 80)}${due}`;
  });
  return `مهامك الحالية:\n${lines.join("\n")}`;
}

export function priorityLabel(priority: HTaskPriority): string {
  if (priority === "important") return "مهمة";
  if (priority === "medium") return "متوسطة";
  return "بسيطة";
}

function deriveTitle(body: string): string {
  const clean = body.replace(/\s+/g, " ").trim();
  return clean.length <= 80 ? clean : `${clean.slice(0, 77)}...`;
}

function formatRiyadhDate(date: Date): string {
  try {
    return new Intl.DateTimeFormat("ar-SA", {
      timeZone: "Asia/Riyadh",
      dateStyle: "medium",
      timeStyle: "short",
    }).format(date);
  } catch (_) {
    return date.toISOString();
  }
}
