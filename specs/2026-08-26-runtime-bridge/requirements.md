# Feature: Runtime bridge (roadmap item 3)

Branch: `feature/2026-08-26-runtime-bridge`
Spec date: 2026-08-26

## Scope
- A newline-delimited JSON-RPC 2.0 codec (`JsonRpcPeer`) and the wire-type data classes
  for the DSH SDK runtime protocol.
- A project service (`DshRuntimeService`) that spawns the DSH runtime for one project,
  runs `initialize` / `session/prompt` / `shutdown`, and fans out `session.event` /
  `session.status` notifications filtered to the project's session.
- Unit tests for the codec and a headless mock-LLM end-to-end test against the real DSH
  runtime from the checkout.
- No chat UI, no event-view rendering (item 4), no composition authoring (item 12).

## Decisions (agreed with the user)
- Config source for this item: environment variables (`DSH_RUNTIME_MODE`,
  `DSH_RUNTIME_EXE`, `DSH_CHECKOUT`, `DSH_CORDIS_CONFIG`, `DEEPSEEK_API_KEY`,
  `DEEPSEEK_BASE_URL`, `DSH_MODEL`); the settings page (item 10) replaces this later.
  Superseded 2026-08-27: the settings page (item 10, pulled forward — see
  `specs/2026-08-27-settings/requirements.md`) replaced the env-var staging, and all
  user-config env reads were removed from production code.
- Carriers:
  1. bundled — single-file executable `dsh-jsonrpc-agent-pkg-<platform>-<arch>` from
     `DSH_RUNTIME_EXE` (its `-rg` sidecar must sit beside it; DSH packaging requirement);
  2. node — `node <checkout>/packages/examples/jsonrpc-demo/lib/bin.js`
     (`DSH_RUNTIME_MODE=node`, Node >= 22.19); when the checkout is not built, the
     source bin runs instead: `node --import tsx <checkout>/packages/examples/jsonrpc-demo/src/bin.ts`
     (process cwd = checkout so `tsx` and bare plugin specifiers resolve).
  Default carrier is `bundled`.
- The plugin's `agent.cordis.yml` composition is deferred to item 12; the bridge takes
  its path from `DSH_CORDIS_CONFIG` (tests point it at a keyless mock composition).
- One stable session id per project: `"jb-" + project.locationHash`.
- Process contract: stdout is the protocol (stderr goes to the IDE log), stdin is kept
  open for the process lifetime (EOF aborts in-flight work), `shutdown` is sent before
  process disposal.
- Provider default `deepseek-official`; model default `deepseek-chat` (env override).
- Serialization: Jackson via `jackson-module-kotlin` (already in tech-stack.md).
- Concurrency: kotlinx-coroutines (bundled with the platform).
- Tests use the IPGP platform test framework (`TestFrameworkType.Platform`, bundled
  JUnit 4) — no new external dependency.

## Context
- DSH wire contract: `packages/sdk/protocol/README.md` and `packages/sdk/server/README.md`
  in the harness checkout (`/home/superguo/Projects/deepseek-harness`).
- Bytecode target moved JVM 17 → 21 during this item: the minimum IDE (2025.1)
  runs on JBR 21; `specs/tech-stack.md` was updated in the same change.
- Wire limitations honored: no per-prompt result (`messageId` is an enqueue receipt),
  no prompt-cancel, no server-to-client requests.
- The harness repo's own keyless JSON-RPC smoke (mock LLM over HTTP) is the reference
  pattern for the e2e test.

## Adaptation to dsh-v0.1.2-alpha.1 (2026-08-28)
- The dedicated SDK runner (`packages/examples/jsonrpc-demo`, bin `dsh-jsonrpc-agent`)
  and `examples/jsonrpc-agent/cordis.yml` were removed upstream. The SDK runtime is
  now the main CLI's built-in profile: `node --import tsx/esm <checkout>/apps/cli/src/bin.ts --profile sdk`
  (built fallback `<checkout>/node_modules/@deepseek-ai/dsh/lib/bin.js --profile sdk`);
  the bundled single-file exe was renamed
  `deepseek-harness-sdk-runtime-<platform>-<arch>` and takes `--profile sdk`.
- `DSH_CORDIS_CONFIG` is gone; the process env is now `DSH_HOME` (explicit, required),
  `DSH_PERMISSION_MODE`, `DSH_TELEMETRY_DISABLED`; the agent workspace comes from
  `initialize.cwd` only (`DSH_CWD`/`DSH_SESSION_ROOT` are dead).
- `initialize` gained optional `reasoningEffort` and now validates the provider/model
  route via `resolveCallConfig` at handshake.
- Supersedes the original carrier/config decisions above; `specs/tech-stack.md` owns
  the current wording.
