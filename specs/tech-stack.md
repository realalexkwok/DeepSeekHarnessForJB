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
  Harness checkout record (2026-08-28): verification uses the AMBIENT checkout at
  `/home/superguo/Projects/deepseek-harness` at commit `cd5ef814` (merge of
  PR #3248, tag `dsh-0.1.2-alpha.1`) — no separate clone exists; any checkout at
  the same tag qualifies as a node carrier. The user's host machine uses its own
  checkout at the same tag.
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

## DSH checkout discipline (do not disturb)
- The ambient checkout at `/home/superguo/Projects/deepseek-harness` is a
  **detached checkout of tag `dsh-0.1.2-alpha.1`** (commit `cd5ef814`), not a
  branch. Never `git checkout`/`switch`/`pull`/`reset`/`clean` it, and never edit
  or delete its tracked files while a `dsh web` server runs: the running server
  loads worker and code modules from this working tree on disk at every tool
  spawn, so any tree change breaks every session's tooling (`run_code` fails with
  `Cannot find module …/code-runtime-worker-thread/src/worker.ts`).
- Never run the harness repo's e2e suites (or any tool that mutates checkout
  files) inside this checkout — a failed cleanup e2e deletes its own tracked
  fixtures and has taken the worker module down with it.
- The workaround patch `/home/superguo/.dsh-workaround.patch` is applied by the
  `dsh_web` shell function on every start; do not revert it while the server
  runs, and restore the checkout only via that function (clean → install →
  build → apply patch → start).
- If a different DSH version/commit is unavoidable, clone the harness fresh at
  the same tag — e.g. `git clone --depth 1 --branch dsh-v0.1.2-alpha.1
  https://github.com/deepseek-ai/deepseek-harness.git
  ~/Projects/dsh-for-jb-plugin-dev` — and do experimental work (branch switches,
  e2e runs) there; the ambient checkout stays pinned.
- The plugin's headless e2e resolves its checkout from `DSH_CHECKOUT` (default:
  `~/Projects/dsh-for-jb-plugin-dev`) and REFUSES to execute when the resolved
  path is the ambient checkout — the runtime it spawns uses the project workspace
  as its working directory, never the checkout.
- The dev clone lives at `/home/superguo/Projects/dsh-for-jb-plugin-dev` on the
  dedicated branch `jb-dev-cd5ef814` (created from tag `dsh-0.1.2-alpha.1` @
  `cd5ef814`); any code changes for bug fixes in the dev clone go on THAT branch
  only.
- Deletion incident ROOT-CAUSED and fixed (2026-08-29, strace-confirmed): the
  harness boot heal (`healProfilesModuleFallback` in `packages/boot/app-boot`)
  creates `$DSH_HOME/profiles/node_modules/*` symlinks that point INTO the
  checkout's package trees. The plugin e2e teardown used Kotlin
  `File.deleteRecursively()`, which FOLLOWS those symlinks while descending (the
  JDK File API is symlink-blind) and so deleted thousands of tracked checkout
  files + build outputs (4388 deletions observed, incl. `apps/cli/*`).
  Fix: every plugin-side recursive tree delete MUST use the symlink-safe helper
  `io.dsh.jb.util.FsTree.deleteNoFollow` (NIO `walkFileTree` WITHOUT
  `FOLLOW_LINKS`; symlinks deleted as links) — never `File.deleteRecursively()`.
  Harness-side hazard (upstream-worthy): the profile-fallback symlinks pointing
  into the install root make ANY naive cleanup of `$DSH_HOME` destructive to the
  checkout.
- Symptom check: if `run_code` ever reports a missing `…/src/worker.ts`, the
  live checkout was disturbed — stop, run `cd /home/superguo/Projects/deepseek-harness
  && git restore . && git apply /home/superguo/.dsh-workaround.patch`, and
  restart `dsh_web`.

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
## Manual-validation fixes & known limitations (2026-08-30)
- Typed slash commands `/plan` and `/plan off` are parsed in the composer and
  relayed through the bridge command channel — they never reach the model as
  prompt text. The composer action tab mirrors the harness plan mode (mode on
  → Plan).
- Stop: the send button toggles to Stop while the agent runs (Kilo pattern),
  plus a header Stop button. Stopping kills the runtime process tree
  Kilo-style — `ProcessHandle.descendants()` SIGTERM → short grace →
  re-enumerated SIGKILL escalation (`DshRuntimeClient.interrupt`/`killTree`,
  modeled on kilocode's `killCliProcessTree`) — instead of the graceful
  JSON-RPC shutdown, which can block mid-tool-execution. The next send
  restarts the runtime lazily (same pool-restart path as a model/effort switch).
- Transcript auto-scroll: KNOWN LIMITATION (2026-08-30, skipped per user) —
  during streaming the panel still re-pins to the bottom while the user scrolls
  up. Three event/flag-based fixes (model ChangeListener, execution-time pin
  guards, intent-based disengage) did not resolve it in the host IDE. Root
  remedy: rebuild the transcript as a proper list/table model (new UI task,
  recorded in roadmap item 5) instead of the growing-panel approach. The
  current guards remain as mitigation.
- KNOWN LIMITATION: Ask/Execute are ADVISORY prompt instructions only — the
  runtime currently runs `danger-full-access` regardless of the selected action,
  so the agent can still modify files in Ask mode. Plan mode (`/plan`) is real
  (harness-enforced review gate), not advisory. Per-action permission-mode
  wiring is a follow-up roadmap item.

