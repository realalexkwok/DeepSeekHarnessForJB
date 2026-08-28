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
- Verified baseline: dsh-v0.1.2-alpha.1 (2026-08-28). The SDK runtime is the main
  CLI's built-in `sdk` profile — `dsh ... --profile sdk`; no external cordis file.
- Two runtime configurations, switchable in settings:
  1. Bundled single-file executable: `deepseek-harness-sdk-runtime-<platform>-<arch>`
     built from the harness repo (pkg `--sea`, Node 24 inside, ~174 MB, plus its
     `-rg` sidecar), invoked as `<exe> --profile sdk`.
  2. Node + installed DSH checkout: `node --import tsx/esm <checkout>/apps/cli/src/bin.ts --profile sdk`
     (built fallback: `<checkout>/node_modules/@deepseek-ai/dsh/lib/bin.js --profile sdk`,
     Node ≥ 22.19). When the checkout's repo-root `.env` (or environment) already
     carries `DEEPSEEK_API_KEY`, the user does not need to enter the key in settings.
- Process env: `DSH_HOME` (explicit — required by the `sdk` profile; default the
  user's `~/.dsh`), `DSH_PERMISSION_MODE` (danger-full-access until roadmap item 8
  wires dialogs), `DSH_TELEMETRY_DISABLED=1`, plus `DEEPSEEK_API_KEY` /
  `DEEPSEEK_BASE_URL` when set. The agent workspace comes from `initialize.cwd` only.
- Wire: newline-delimited JSON-RPC 2.0 over stdio — requests `initialize`,
  `session/prompt`, `shutdown`; notifications `session.event`, `session.status`,
  `subagent.*`. `initialize` accepts optional `reasoningEffort`
  (`off`|`low`|`high`|`max`) and validates the provider/model route at handshake.
- The `sdk` profile carries the coding toolset (bash/pwsh, fs read/write/edit/search,
  plan mode, todo, skills, goals, subagents, web) over the harness base bundle;
  stdout is protocol-pure by design.
- Node.js resolution: GUI-launched IDEs do not inherit the shell PATH, so the plugin
  resolves node via a candidate list (bare `node`, homebrew/MacPorts/system paths,
  volta/asdf/nvm/fnm) and spawns the runtime with the resolved absolute path.
- Runtime pool: one process per (model, effort) selection; effort rides the
  `initialize.reasoningEffort` wire field (adapter vocabulary `off`/`low`/`high`/`max`
  maps 1:1 to the UI's Off/Low/High/Max). The previous per-effort cordis generation
  (`agent.cordis.yml` + `DSH_CORDIS_CONFIG`) was removed in the 2026-08-28 DSH
  adaptation. Model discovery is the plugin's own `GET {base}/models` HTTP call with
  the keychain key (fallback: the harness adapter catalog); the chosen id is passed
  to `initialize.model`.
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
