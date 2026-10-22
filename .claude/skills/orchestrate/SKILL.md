---
name: orchestrate
description: Run the PodOuvir multi-agent workflow — take a plan.md item (or the next unchecked one), split it into small tasks, and drive test-planner → coder → tester for each, looping on failures, then review and commit. Use when asked to "orchestrate", "work on the next item", or implement a milestone.
---

# Orchestrate

You (the main session) are the orchestrator. You plan, delegate, review and commit; you do not write
production code or tests yourself unless a subagent is stuck twice on the same thing.

## Team (in `.claude/agents/`)
| Agent | Model | Job |
|---|---|---|
| `test-planner` | sonnet | Writes `## Test plan` into the task file |
| `coder` | sonnet (cheaper) | Implements the task, appends `## Implementation notes` |
| `tester` | sonnet | Writes + runs tests, appends `## Test results` |

## 1. Pick work
Take the item the user named, or the first unchecked item of the current milestone in `plan.md`.

## 2. Split into tasks
Each task: one cohesive unit (≈ one file or one function group), finishable in one coder run,
testable on its own. Create `docs/tasks/<NNN>-<slug>.md` from this template:

```markdown
# <NNN> <title>
Plan item: <milestone + checkbox text>
Status: planned
Depends on: <task ids or none>

## Goal
## Files
## Public API          <!-- exact Kotlin signatures -->
## Acceptance criteria <!-- bullet list, testable -->
```
Number tasks sequentially across the repo (look at existing files in `docs/tasks/`).

## 3. Run each task (respect `Depends on`)
1. **test-planner** with the task path → check the plan covers every acceptance criterion.
2. **coder** with the task path. Independent tasks touching different files may run in parallel
   (multiple Agent calls in one message); tasks sharing files run one after another.
3. **tester** with the task path.
4. If any case FAILS: decide whether the code or the test is wrong (read both).
   - Code wrong → send the failing cases back to **coder** (SendMessage to the same agent if possible).
   - Test wrong → send back to **tester** with the reason.
   - Max 3 rounds per task; then stop and report to the user with the evidence.
5. Set `Status: done` in the task file.

## 4. Review and commit
- Read the full diff (`git diff`). Check it against `AGENTS.md` rules (layers, strings in EN + PT,
  no proprietary deps, no unrelated changes).
- Run the full check (`android-build-verify` skill). If you can't (no SDK), say so — CI will run it.
- Tick the checkbox in `plan.md`; add a Decisions-log row for any notable choice.
- Commit with a conventional message referencing the task ids, e.g. `feat(rss): parse feeds (#004)`.
  Push only if the user asked for pushes in this session.

## 5. Report
Short summary to the user: tasks done, test counts (pass / fail / not run), what's next, blockers.

## Cost discipline
Give subagents the task path, not the whole conversation. Keep tasks small so the cheap coder
model succeeds on the first try; if it fails twice on the same task, split the task further or
escalate that one task to a stronger model (`model: opus` override on the Agent call).
