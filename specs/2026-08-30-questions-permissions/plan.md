# Item 9 — Questions & permissions: plan

1. Spec files (this directory).
2. Bridge protocol: generalize `PlanQuestion` (add `intentKind`), add
   `BridgeApproval`/approval wire helpers, add `POST /approval` to
   `BridgeServer` with an `onApproval` callback.
3. Runtime-side `answerer.mjs`: claim all question intents (fail-closed
   `next()`), add the `approval/request` handler forwarding to `/approval`.
4. IDE dialogs: `QuestionDialog` (generic question: options + custom text) and
   `PermissionDialog` (Allow once / Deny); `DshRuntimeService` routes plan-review
   → `PlanReviewDialog`, other intents → `QuestionDialog`, approvals →
   `PermissionDialog`, with EDT serialization.
5. Settings: `permissionMode` field + snapshot + combo (workspace-write /
   danger-full-access); `DshRuntimeConfig.fromSettings` passes it through.
6. Tests: BridgeServer/BridgeTypes approval round-trip unit tests; e2e
   permission flow (workspace-write, out-of-workspace write → approval/asked +
   bridge forward + rejected outcome + no file created).
7. Validation: `./gradlew test` + `buildPlugin`, artifact path reported;
   manual host checklist below.
