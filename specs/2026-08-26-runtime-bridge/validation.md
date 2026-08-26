# Validation: Runtime bridge (roadmap item 3)

How we know this feature succeeded and can merge.

1. `./gradlew test` passes: codec unit tests cover framing, request/response correlation,
   error responses, notification delivery, malformed-line tolerance, and EOF failure.
2. The headless e2e test passes: a Kotlin driver spawns the real DSH runtime
   (`node <checkout>/packages/examples/jsonrpc-demo/lib/bin.js`) against a mock LLM,
   `initialize` returns `serverInfo.name == "deepseek-harness-sdk-runtime"`,
   `session/prompt` returns a `messageId`, a committed assistant message is observed via
   `session.event`, and `session.status` reaches `idle`.
3. `./gradlew buildPlugin` still succeeds with the new code included.
4. No new external dependency beyond the already-approved `jackson-module-kotlin`
   (tests use the IPGP platform test framework).
5. The user reviews the code in the IDE and approves the merge.

## Result
2026-08-26: criteria 1–4 verified on the remote — `./gradlew test` passes (8 synchronous
`JsonRpcCodec` tests + the headless e2e driving the real checkout runtime against a mock
LLM), `buildPlugin` still succeeds, and no new external dependency was added (JUnit 4 is
test-scope, recorded in tech-stack.md). Awaiting the user's review (criterion 5).
