# Validation: Chat tool window (roadmap item 5)

How we know this feature succeeded and can merge.

1. `./gradlew test` passes: `ChatTranscriptModelTest` covers the streaming fold,
   tool-card pairing, FIFO optimistic-echo reconciliation, notices, todo snapshot,
   plan-mode flag, running/idle status, and tolerance of unknown/malformed events.
2. The headless e2e passes and additionally folds the real checkout-runtime stream
   through the model (canonical user row, completed assistant row with the mock
   output, turn notices, idle at the end, no pending echoes).
3. `./gradlew buildPlugin` still succeeds with the new code included.
4. No new dependency: Swing + platform components, Jackson, JUnit 4 (test scope) —
   all already in `specs/tech-stack.md`.
5. Manual smoke in Android Studio: the tool window opens and starts the runtime
   (`DSH_RUNTIME_MODE=node` + `DSH_CHECKOUT` env), sending a prompt streams text
   and thinking, tool cards render, the todo panel updates, and idea.log stays free
   of plugin exceptions.
6. The user reviews the code in the IDE and approves the merge.

## Result
2026-08-26: criteria 1-4 verified on the remote (criterion 5 is your Android Studio
smoke, criterion 6 your review).

1. `./gradlew test` green — 58 tests total: 16 `ChatTranscriptModelTest` (streaming
   fold, finalization without clobbering streamed text, FIFO optimistic-echo
   reconciliation, tool-card pairing + error/meta, settled-card creation for missed
   calls, turn/step/plan/approval notices, todo snapshot, running/idle status,
   failure notice on error finish, unknown/malformed tolerance, listener contract),
   33 `EventModelTest` + 8 `JsonRpcCodecTest` from items 3-4, and the e2e.
2. The headless e2e passes with the item-5 assertions: the real checkout-runtime
   stream folds into a canonical user row, completed assistant rows carrying the
   mock output, turn notices, and an idle end state.
3. `./gradlew buildPlugin` green with the chat UI included.
4. No new dependency: Swing/platform components, Jackson, JUnit 4 (test scope) only.
   Two fixes found by the tests during verification: a missing `AbortedEnd` import
   in the model, and an assistant-message fallback that duplicated reasoning text in
   the visible row (`renderAssistantText` now renders text blocks only; reasoning
   is disclosed separately).
5. Pending your manual smoke in Android Studio (tool window, send against the
   checkout runtime, streaming/thinking/tool cards/todos, clean idea.log). First smoke
   attempt confirmed the fail-safe path: with no env config the runtime start fails
   gracefully (status line + notice row + idea.log warning). The failure notice now
   includes actionable env-var guidance (verified again: 58 tests + buildPlugin green).
6. User review: the user smoke-tested on 2026-08-27; the run hit the item-3
   env-var staging barrier (no inherited env → graceful start failure). Per user
   approval, the manual smoke is re-run after the settings item (pulled forward from
   item 10) replaces the env-var config; criteria 1-4 and the fail-safe path remain
   verified here.
