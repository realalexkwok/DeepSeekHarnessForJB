# Plan: Runtime bridge (roadmap item 3)

Task groups, in execution order.

A. Feature spec files (this folder): `requirements.md`, `plan.md`, `validation.md`.
B. JSON-RPC codec: `JsonRpcPeer` (framing, request/response correlation, notifications,
   incoming-request handling for test doubles) plus unit tests.
C. Wire types: `InitializeParams/Result`, `SessionPromptParams/Result`, notification
   payloads, and the session-event envelope.
D. Runtime service: `DshRuntimeService` — carrier launch (bundled-exe / node-checkout),
   env plumbing, `initialize` / `session/prompt` / `shutdown`, stderr logging, event and
   status fan-out filtered by session id; project-scoped lifecycle.
E. Verification: unit tests green; headless e2e — Kotlin test driver spawns the checkout
   runtime against a JDK-hosted mock LLM, completes initialize + prompt, and asserts
   message receipt, events, and idle status; full `./gradlew buildPlugin` still green.
