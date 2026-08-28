# Plan: Diff preview/apply (roadmap item 7)

Task groups, in execution order.

A. Feature spec files (this folder): `requirements.md`, `plan.md`, `validation.md`.
B. Pure `io.dsh.jb.diff.FsDiffParser` (meta JsonNode → List<FileChange>; absent /
   malformed / empty → null) + unit tests.
C. `io.dsh.jb.ui.DshDiffDialog`: file selector, platform DiffRequestPanel views,
   Apply / Apply All / Reject All, workspace-confined WriteCommandAction applies.
D. `DshChatPanel` tool cards: "Diff (N files)" button when the result meta parses.
E. E2e extension: stateful mock LLM (read → write → text) asserting the real
   `tool/result` meta parses to the expected diff and the file was rewritten.
F. Verification: `./gradlew test` + `./gradlew buildPlugin` (stamped version);
   results in `validation.md`.
