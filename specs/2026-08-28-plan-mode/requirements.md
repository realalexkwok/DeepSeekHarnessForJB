# Feature: Plan mode review (roadmap item 8)

Branch: `feature/2026-08-28-plan-mode`
Spec date: 2026-08-28

## Scope
- Render the harness plan mode as a real review gate in the IDE: a review panel
  for `exit_plan_mode` (Approve / Keep planning), with the answer delivered back
  into the runtime so the plan lands in the tool result and the model proceeds.
- Deliver the mechanism end-to-end:
  1. a runtime-side answerer plugin (Node, shipped with the plugin) installed via
     a composition `--patch` over the built-in `sdk` profile;
  2. a localhost HTTP bridge owned by the IDE plugin (random port, per-runtime
     token) that the answerer calls and awaits the user's decision on;
  3. the plan review panel UI plus transcript wiring (`plan/mode` state rows and
     the exit-plan tool card).
- The same bridge is reused by roadmap item 9 (permissions/ask-user).

## Decisions (agreed with the user, 2026-08-28)
- Research-first outcome (verified against dsh-v0.1.2-alpha.1):
  `exit_plan_mode` ALWAYS asks the `user-questions` waterfall (`plan-review`,
  options Approve / Keep planning); the answer returns in the tool result;
  `PlanModeConfig` has no review toggle; the SDK wire has no answering channel
  (no server→client requests), so today the ask fails closed with
  "no user-questions answerer accepted the request". A runtime-side answerer
  installed via `--patch` + a localhost side channel is the only gating path.
- The answerer plugin is a plain JS file referenced by relative path from the
  patch file (harness patch rows support file-path plugin names); the IDE plugin
  stages the patch + JS next to each spawned runtime and passes `--patch <file>`.
- Bridge protocol: JSON over HTTP on 127.0.0.1:<random-port>, per-runtime bearer
  token; requests carry the question payload; the response carries
  `{ answers: [{ id, answer }] }`. The answerer awaits the IDE decision with a
  timeout (fail-closed to the harness's unavailable outcome).
- Panel behavior: Approve answers `plan-review` with the approve option; Keep
  planning answers with the keep-planning option; while a review is pending the
  transcript shows a waiting notice; `plan/mode` events toggle a mode indicator.
- No new Gradle dependencies (JDK HttpServer + platform UI only, per
  `specs/tech-stack.md`).

## Context
- Harness facts: `packages/plan/plan-mode/src/index.ts` (REVIEW_ID
  `plan-review`, APPROVE_LABEL, KEEP_PLANNING_LABEL, `userQuestions.ask`),
  `packages/interaction/user-questions/src/index.ts` (`user-questions/request`
  waterfall, noAnswerer rejection), base bundle rows `user-questions` +
  `plan-mode` (both composed by the `sdk` profile).
- The plugin already drives the runtime via `DshRuntimeClient`
  (`--profile sdk`, `DSH_HOME`, `initialize.cwd`).
