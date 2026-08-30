# Validation: Plan mode review (roadmap item 8)

Both halves are required before merge (constitution: two-sided verification).

## Automatic (agent)
1. `./gradlew test` green: bridge-protocol tests (request/response/token/round-trip),
   answerer payload tests, panel-model tests, plus all existing suites including
   the headless e2e.
2. An end-to-end test exercises the runtime-side answerer through the IDE bridge:
   a mock runtime answerer forwards a `plan-review` question to the localhost
   bridge and receives the approve answer (proves the full gating loop).
3. `./gradlew buildPlugin` green.

## Manual (user, from the host machine)
4. Install the built artifact (absolute path reported with the results) and run a
   plan-mode prompt: the transcript shows the plan-mode indicator, `exit_plan_mode`
   opens the review panel, Approve returns the answer to the runtime and the model
   leaves plan mode (Keep planning keeps it); idea.log clean.
5. The user approves the merge.

## Result
To be filled after verification.
