# Feature: Event model (roadmap item 4)

Branch: `feature/2026-08-26-event-model`
Spec date: 2026-08-26
Base: `main` after merging roadmap item 3 (merge commit cc169b2).

## Scope
- Typed Kotlin views over the raw `session.event` envelope for the 15 payloads the
  chat UI (item 5) will consume: `assistant/chunk` (all 7 `StreamChunk` variants,
  including `block-start`), `assistant/message`, `tool/call`, `tool/result`,
  `turn/start`, `turn/end`, `step/start`, `step/end`, `plan/mode`,
  `approval/asked`, `approval/decided`, `todo/write`, `user/message`,
  `command/run`, `command/done`.
- Envelope completion: `SessionEventEnvelope` gains the conditional surface fields
  DSH emits on surface events (`sourceEventSeqs`, `surfaceOp`).
- Unknown/plugin event types stay generic JSON (`UnknownEventData`); a known type
  whose payload fails to bind degrades to `MalformedEventData` (raw node + error) —
  the vocabulary is merge-extensible and the plugin must never crash on drift.
- No UI, no runtime-client behavior change (items 5+); `DshRuntimeClient` keeps
  delivering the raw envelope.

## Decisions (agreed with the user, 2026-08-26)
- Raw envelope + pure typed view layer: `SessionEventEnvelope.data` stays a
  `JsonNode`; `io.dsh.jb.events.EventMapper` produces `SessionEventView` over a
  sealed `EventData` hierarchy. Parsing is pure JVM and unit-testable headlessly.
- Jackson polymorphic sealed hierarchies for the discriminated unions
  (`StreamChunk.type`, `ContentBlock.type`, `FinishReason.kind`,
  `TurnEndReason.kind`, `TurnEndCancelCause.kind`, `MessageSource.kind`,
  `CommandSource.kind`): `@JsonTypeInfo(As.PROPERTY, visible = true)` +
  `@JsonSubTypes`; an unrecognized discriminator falls back to a per-union `Raw*`
  variant that captures the COMPLETE original JSON — discriminator included, via
  `@JsonAnySetter` — instead of failing (verified against the jackson-databind
  2.17.3 sources: with `defaultImpl` the unknown id takes the visible-merge path).
- `surfaceOp` is a tagged string-or-object union (`"append"` |
  `{"op":"replace","start","end"}`); `@JsonTypeInfo` cannot express a scalar type
  id, so it maps through a small custom `JsonDeserializer` with a `Raw` fallback.
- New package `io.dsh.jb.events`; `io.dsh.jb.protocol.WireTypes` only gains the two
  envelope fields.
- Closed vocabularies get helper enums (`TodoStatus`, `ApprovalOutcome`,
  `CommandOutcome`, `SessionEventType`) while the wire fields stay `String`
  (lossless: an unknown value stays readable instead of failing deserialization).
- Verification: JUnit-4 unit tests (one fixture per payload, plus fallback and
  malformed tolerance) AND the headless e2e extended to parse the real
  checkout-runtime stream.

## Context
- Authoritative payload shapes (harness checkout
  `/home/superguo/Projects/deepseek-harness`): `docs/persistence-catalog.md`
  (generated), `packages/core/session/src/types.ts`,
  `packages/llm/llm/src/{types,message}.ts`, `packages/sdk/protocol/src/types.ts`,
  `packages/interaction/user-approval/src/types.ts`,
  `packages/interaction/commands/src/types.ts`.
- The harness vocabulary is merge-extensible (plugins add event types, content-block
  types, finish kinds, message/command sources); raw fallbacks implement that rule.
- `ignorable` semantics: surfaced on the view for item 5's policy; the durable-log
  reconstruction refusal rule does not apply to a live streaming UI.
- Wire stability: `@JsonIgnoreProperties(ignoreUnknown = true)` throughout, so new
  fields inside known payloads stay harmless.
