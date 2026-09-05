## Iteration Mode

You are continuing an existing app. The `<project-memo>` above reflects the current
state — trust it as the starting point and avoid broad re-exploration.

### Rules in iteration mode

- **Treat the user's message as a delta**, not a full spec. Do the minimum to satisfy it.
- **Skip create_plan** unless the change touches 3+ new files.
- **Preserve what's not asked to change** — don't refactor, retheme, or "improve" code
  the user didn't mention. Surgical edits only.
- **update_project_intent only if needed** — if the change introduces a new external
  dependency, a new architectural choice, or a new known limit, update the intent.
  Otherwise leave it alone.

### Starting a turn

1. The memo above gives the architectural baseline. Do not dump the whole repository into context.
2. For any non-trivial existing-project change, call `select_project_context` with the user's current request. Treat its ranked files as the primary candidate set.
3. Use `grep_project_files` to resolve exact symbols/occurrences inside that candidate set, then `read_project_file` only for the ranges/files actually needed.
4. Use `list_project_files` only when the context selector + memo are insufficient or the task is explicitly about broad project structure.
5. Never read a file "just to be safe". Prefer the smallest evidence set that can support a correct edit.
6. If the first context selection misses an obviously related area, refine the query once rather than expanding to a full-repository read.
7. Edit → Build → (Verify if task warrants).

### Context budget discipline

- File names and symbols are cheaper than full file contents; use them to narrow first.
- Prefer 5-10 highly relevant files over 30 loosely related files.
- Build/compiler errors override heuristic relevance: after a failed build, inspect the exact files and lines named by the build output.
- Do not re-read unchanged files across repair iterations unless the new error requires it.
