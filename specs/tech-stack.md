# DeepSeekHarnessForJB — Tech Stack

## Language & toolchain
- Kotlin (JVM 17 bytecode), Gradle Kotlin DSL, IntelliJ Platform Gradle Plugin 2.x.
- Platform plugin: depends only on `com.intellij.modules.platform` so it loads in both
  IntelliJ IDEA and Android Studio; `sinceBuild` 251 (2025.1), `untilBuild` open (adjustable).

## Plugin side
- UI: Swing tool window; platform Diff framework for edit previews; Notification API;
  settings via `projectConfigurable`.
- Concurrency: kotlinx-coroutines for process IO and event streaming.
- Serialization: Jackson (jackson-module-kotlin) for the JSON-RPC wire protocol and the
  session-event vocabulary.

## DSH side (embedded runtime)
- Two runtime configurations, switchable in settings:
  1. Bundled single-file executable: `dsh-jsonrpc-agent-pkg-<platform>-<arch>` built from
     the harness repo (pkg `--sea`, Node 24 inside, ~174 MB, plus its `-rg` sidecar).
  2. Node + installed DSH checkout: `node <checkout>/packages/examples/jsonrpc-demo/lib/bin.js`
     (bin `dsh-jsonrpc-agent`, Node ≥ 22.19); the user points the plugin at the checkout
     path. When the checkout's repo-root `.env` (or environment) already carries
     `DEEPSEEK_API_KEY`, the user does not need to enter the key in settings.
- Both carriers receive the plugin's bundled `agent.cordis.yml` via `DSH_CORDIS_CONFIG`.
- Wire: newline-delimited JSON-RPC 2.0 over stdio — requests `initialize`, `session/prompt`,
  `shutdown`; notifications `session.event`, `session.status`, `subagent.*`.
- Composition mirrors the DSH `standard` preset (bash/pwsh, fs read/write/edit/search,
  plan mode, todo, skills, goals, subagents, web, ask-user) and includes
  `agent-instructions`, so the agent reads the workspace `AGENTS.md` (root, plus
  subdirectory files on touch) by default; stdout kept protocol-pure.
- Config: `DEEPSEEK_API_KEY` (optional in checkout mode when the checkout already carries
  it), optional `DEEPSEEK_BASE_URL`, model name; sandbox policy scoped to the project
  workspace.

## Inputs the user supplies
- DeepSeek API key (primary), optional base URL for self-hosted/proxied endpoints, model name.
- Context selection per prompt: AGENTS.md, current editor file, and workspace rules —
  all selected by default, each toggleable.
- No dependency on the DSH Web GUI (`localhost:3080`); the plugin renders its own UI.

## Testing & verification
- Unit tests for the JSON-RPC codec and event mapping.
- `buildPlugin` / `runPlugin` smoke runs; DSH-side protocol behavior is already covered
  by the harness repo's e2e suites.
