# Plan: Event model (roadmap item 4)

Task groups, in execution order.

A. Feature spec files (this folder): `requirements.md`, `plan.md`, `validation.md`.
B. Envelope completion in `io.dsh.jb.protocol.WireTypes`: `SurfaceOp`
   (Append / Replace / Raw) and the `sourceEventSeqs` / `surfaceOp` fields on
   `SessionEventEnvelope`.
C. Shared value types in `io.dsh.jb.events.ValueTypes`: `TokenUsage`,
   `LlmFailure`, `ContentBlock`, `FinishReason`, `StreamChunk` (7 variants),
   `MessageSource`, `AssistantMessage` / `ToolResultMessage`, `TodoItem` +
   `TodoStatus`, `TurnEndReason` + `TurnEndCancelCause`.
D. Event payloads in `io.dsh.jb.events.EventTypes`: sealed `EventData` with the
   15 typed events, `UnknownEventData`, `MalformedEventData`, `CommandSource`,
   helper enums (`ApprovalOutcome`, `CommandOutcome`, `SessionEventType`).
E. View API in `io.dsh.jb.events.EventMapper`: `SessionEventView` +
   `EventMapper.parse/parseData`.
F. Unit tests: one fixture per payload, unknown-type / unknown-discriminator
   fallbacks (full raw fidelity), malformed-payload tolerance, surface-op parsing.
G. E2e extension + verification: the headless e2e parses the real runtime stream;
   `./gradlew test` + `./gradlew buildPlugin`; results recorded in
   `validation.md`.
