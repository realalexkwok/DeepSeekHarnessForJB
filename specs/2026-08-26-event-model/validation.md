# Validation: Event model (roadmap item 4)

How we know this feature succeeded and can merge.

1. `./gradlew test` passes: the event-model unit tests map every in-scope payload
   from a representative fixture, keep unknown event types and unknown union
   discriminators as complete raw JSON, and tolerate malformed payloads without
   throwing.
2. The headless e2e passes AND asserts the typed mapper parses the real
   checkout-runtime stream (assistant chunks, assistant message, turn/step
   boundaries, user message; zero malformed events).
3. `./gradlew buildPlugin` still succeeds with the new code included.
4. No new dependency: Jackson (jackson-module-kotlin) and JUnit 4 remain the only
   added libraries (both already in `specs/tech-stack.md`).
5. Every mapping cross-checked against the harness sources listed in
   `requirements.md#Context`; any drift from those sources recorded here.
6. The user reviews the code in the IDE and approves the merge.

## Result
2026-08-26: criteria 1–5 verified on the remote.

1. `./gradlew test` green — 42 tests total: 33 `EventModelTest` unit tests (every
   in-scope payload from a representative fixture; unknown event types and unknown
   union discriminators preserved as complete raw JSON, discriminator included;
   malformed payloads degrade to `MalformedEventData` instead of throwing), the 8
   `JsonRpcCodecTest` tests from item 3, and the headless e2e.
2. The headless e2e passes with the item-4 assertions: the real checkout-runtime
   stream parses into typed `assistant/chunk` (incl. deltas/usage/finish),
   `assistant/message` with content, `turn/start`/`turn/end`,
   `step/start`, and `user/message`; zero `MalformedEventData`; the
   `assistant/message` envelope carries surface metadata (`surfaceOp`).
3. `./gradlew buildPlugin` green with the new code included.
4. No new dependency: Jackson (jackson-module-kotlin 2.17.3) and JUnit 4 (test scope)
   remain the only added libraries — both already in `specs/tech-stack.md`.
5. Mappings cross-checked against `docs/persistence-catalog.md`,
   `packages/core/session/src/types.ts`, `packages/llm/llm/src/{types,message}.ts`,
   `packages/sdk/protocol/src/types.ts`,
   `packages/interaction/user-approval/src/types.ts` and
   `packages/interaction/commands/src/types.ts`. Two implementation notes recorded
   during verification: (a) `@JsonDeserialize` lives in
   `com.fasterxml.jackson.databind.annotation`, used for `SurfaceOp`; (b)
   field-less union members are marker classes dispatched with `is` — Jackson
   constructs fresh instances, so a Kotlin `object` singleton would be broken.
   Drift found against the sources: none.

6. The user installed the build in Android Studio and verified it on 2026-08-26
   (criteria 1-5 above were already green on the remote): criterion satisfied. The
   item is committed to `feature/2026-08-26-event-model`.
