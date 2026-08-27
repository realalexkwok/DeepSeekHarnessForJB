# Plan: Chat tool window (roadmap item 5)

Task groups, in execution order.

A. Feature spec files (this folder): `requirements.md`, `plan.md`, `validation.md`.
B. `io.dsh.jb.chat`: `TranscriptTypes` (rows/state) and `ChatTranscriptModel`
   (event fold: streaming assistant rows, tool-card pairing, FIFO optimistic-echo
   reconciliation, turn/step/plan/approval notices, todo snapshot, plan-mode flag,
   running/idle status, unknown/malformed tolerance) + content/reason rendering
   helpers.
C. Unit tests for the model (fixture-driven folds, tolerance paths).
D. `io.dsh.jb.ui.DshChatPanel`: the Swing transcript (rows, streaming text, tool
   cards, thinking toggle, todo panel, composer, status line) replacing the
   placeholder; `DshToolWindowFactory` mounts it.
E. Wiring: `DshRuntimeService` buffers pre-start listeners; the panel starts the
   runtime on open, sends prompts, and renders `session.status`.
F. E2e extension: fold the real checkout-runtime stream through
   `ChatTranscriptModel`.
G. Verification: `./gradlew test` + `./gradlew buildPlugin`; results in
   `validation.md`.
