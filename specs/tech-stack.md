# DeepSeekHarnessForJB — Tech Stack

## Language & toolchain
- Kotlin (JVM 21 bytecode — the minimum supported IDE, 2025.1, runs on JBR 21),
  Gradle Kotlin DSL, IntelliJ Platform Gradle Plugin 2.x; the Kotlin stdlib comes
  from the platform (no bundled stdlib dependency).
- Platform plugin: depends only on `com.intellij.modules.platform` so it loads in both
  IntelliJ IDEA and Android Studio; `sinceBuild` 251 (2025.1), `untilBuild` open (adjustable).

## Plugin side
- UI: Swing tool window with a composer (three bottom tabs — Context / Context
  action / Model — plus an icon submit); platform Diff framework for edit previews;
  Notification API; settings via an application-level page (`applicationConfigurable`,
  Settings → Tools → DeepSeek Harness).
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
- Node.js resolution: GUI-launched IDEs do not inherit the shell PATH, so the plugin
  resolves node via a candidate list (bare `node`, homebrew/MacPorts/system paths,
  volta/asdf/nvm/fnm) and spawns the runtime with the resolved absolute path.
- Runtime pool: one process per (model, effort) selection; the plugin bundles the
  SDK's agent composition (`agent.cordis.yml`) and generates a per-effort variant
  (`llm-deepseek: { thinking, reasoningEffort }`) handed to each runtime via
  `DSH_CORDIS_CONFIG` — effort needs no protocol change. The generated config is
  written next to the checkout's own cordis.yml
  (`<checkout>/examples/jsonrpc-agent/`, node mode) or next to the bundled exe:
  bare plugin packages resolve through the workspace symlinks under
  `examples/node_modules`. Model discovery is the
  plugin's own `GET {base}/models` HTTP call with the keychain key (fallback: the
  harness adapter catalog); the chosen id is passed to `initialize.model`.
- Config: the settings page (Settings → Tools → DeepSeek Harness) holds the runtime
  carrier (bundled exe / node checkout with a path picker), the API key stored in the
  OS keychain via `PasswordSafe` (optional in checkout mode when the checkout already
  carries it via its own `.env`), an optional `DEEPSEEK_BASE_URL`, and the model
  name; sandbox policy scoped to the project workspace. The plugin reads NO
  user-config environment variables (removed 2026-08-27 — env vars were item-3
  staging only).

## Inputs the user supplies
- DeepSeek API key (primary, entered in settings and stored in the OS keychain via
  `PasswordSafe` — never in environment variables or settings files), optional base
  URL for self-hosted/proxied endpoints, model name (default `deepseek-v4-flash`),
  and reasoning effort (Off/Low/High/Max, default Max).
- First-run ask: when the runtime carrier is not configured, the plugin asks the
  user directly — an in-panel notice plus the DeepSeek Harness settings page opening
  automatically — instead of failing silently.
- Context selection per prompt: AGENTS.md, current editor file, and workspace rules —
  all selected by default, each toggleable.
- No dependency on the DSH Web GUI (`localhost:3080`); the plugin renders its own UI.

## Testing & verification
- Unit tests with JUnit 4 (test scope only, never packaged in the plugin artifact).
- Tests are pure JVM and run as a plain Gradle test task — deliberately NOT through
  the IntelliJ platform test-framework sandbox (slow/headless-hostile for pure-JVM tests).
- Unit tests for the JSON-RPC codec and event mapping.
- `buildPlugin` / `runPlugin` smoke runs; DSH-side protocol behavior is already covered
  by the harness repo's e2e suites.
