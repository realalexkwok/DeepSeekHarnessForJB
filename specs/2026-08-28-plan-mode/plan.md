# Plan: Plan mode review (roadmap item 8)

Task groups, in execution order.

A. Feature spec files (this folder): `requirements.md`, `plan.md`, `validation.md`.
B. Runtime-side answerer plugin (JS) + `--patch` wiring in `DshRuntimeClient`.
C. IDE-side bridge server: localhost HTTP endpoint, per-runtime token, request
   dispatch to the pending review panel.
D. Review panel UI + transcript wiring (`plan/mode` indicator, pending-review
   notice, exit-plan tool card with Approve / Keep planning).
E. Verification: unit tests (bridge protocol, answerer payloads, panel model) +
   an end-to-end test where the answerer round-trips through the IDE bridge;
   `./gradlew test` + `buildPlugin` green; report the built artifact's absolute
   path for the user's manual plan-mode smoke.
