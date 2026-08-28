# Feature: Composer tabs, model/effort selection, settings polish

Branch: `feature/2026-08-27-settings` (continued; roadmap items 6/11 pulled forward per
user approval, 2026-08-27)
Spec date: 2026-08-27

## Scope
- Composer redesign: three bottom TAB BUTTONS above the input — Context / Context
  action / Model — that open POPUP MENUS on click (user feedback 2026-08-27; no fixed
  panel above the tabs), plus a submit ICON button immediately right of the buttons.
- Context popup: checkable items "Current file" + "AGENTS.md" — default both
  selected, both may be unchecked. Selected content is injected into the prompt as
  delimited sections.
- Context action popup: radio items Ask (default, read-only advisory instruction) /
  Execute (direct) / Plan (plan-before-change instruction) / Fix (shown disabled —
  semantics pending until the difference vs Execute is settled). The tab button shows
  ONLY the selected action ("Ask ▾") and updates on change; every popup open
  re-asserts the radio checks from the stored selection (user feedback 2026-08-27).
- Model popup: a "Model" radio submenu (detected via the plugin's own
  `GET {base}/models` call with the keychain key; falls back to the harness adapter
  catalog: deepseek-v4-flash / deepseek-v4-pro / deepseek-v4-flash-vision-exp; plus a
  "Custom…" entry opening the settings page) and an "Effort" radio submenu (Off / Low
  / High / Max), plus a "Settings…" item. Selection state survives popup close/open.
- Runtime pool: one runtime process per (model, effort) selection, per the user's
  design. Since the dsh-v0.1.2-alpha.1 adaptation (2026-08-28), effort rides the
  `initialize.reasoningEffort` wire field against the built-in `sdk` profile
  (`off` = thinking disabled; `low`/`high`/`max` = thinking enabled with that wire
  effort — 1:1 with the adapter vocabulary). The previous per-effort cordis
  generation was removed in the same adaptation.
- Settings page polish: "Bundled executable" description label (single-file
  `deepseek-harness-sdk-runtime-<platform>-<arch>` Node SEA binary; ships with the
  plugin in item 12 — leave blank until then), a Node.js version indicator, and
  pre-spawn Node.js validation for the checkout carrier (injectable probe,
  unit-tested).

## Decisions (agreed with the user, 2026-08-27)
- Effort = one runtime per level (user's design); the pool key is (model, effort) —
  maxTokens is not exposed in the UI today. Switching model/effort persists to
  settings, closes the current runtime, and lazily starts the new one on the next
  start/prompt (a transcript notice records the switch).
- Model detection = the plugin's own HTTP (`GET {base}/models`, Authorization Bearer
  key), never a harness API; catalog membership is cosmetic — unknown ids are routed
  text-only by the adapter. Display names come from the known catalog when ids match.
  Default model: `deepseek-v4-flash` (the harness SDK's own default).
- Default effort: Max (the shipped composition's default: full thinking at max
  effort).
- Fix stays a visible-but-disabled radio until its semantics are settled.
- Ask's read-only behavior is an advisory instruction (true sandbox enforcement is a
  follow-up); Plan is an advisory plan-first instruction (harness plan mode is not
  reachable over the SDK wire yet).
- Context: the editor selection is included (as a "Selected code" section) only when
  the "Current file" checkbox is on. Section order: Selected code, File, AGENTS.md,
  then the user text, then the action instruction.
- One live runtime per project at a time (the pool holds at most one; per-key
  sessions keep their own logs: `jb-<locationHash>-<effort>-<model>`).
- Defaults: model menu = settings value (initial default deepseek-v4-flash); effort
  menu = settings value (initial default max).

## Additional decisions (2026-08-27, node/popup round)
- Node.js resolution: GUI-launched IDEs do not inherit the shell PATH (the reported
  macOS failure), so the plugin resolves node via a candidate list (bare `node`, then
  `/opt/homebrew/bin`, `/usr/local/bin`, `/opt/local/bin`, `/usr/bin`,
  homebrew `opt/node` links, volta, asdf, nvm, fnm locations) and the RUNTIME SPAWN
  uses the resolved absolute path
  (`DshRuntimeConfig.nodeExecutable`); the settings indicator shows version + path.
- Tab content is popup menus, not fixed panels (user feedback).
- Credential store (2026-08-28): the built-in `sdk` profile composes the harness
  `@deepseek-ai/dsh-settings-file` (`$DSH_HOME/settings.yaml`) and
  `@deepseek-ai/dsh-credentials-local` (`$DSH_HOME/.credentials.yaml`) over the
  base bundle — the same machine-local stores the harness web Models page writes —
  so a key entered in the web page flows into the plugin runtime automatically
  (mirrors the harness headless-agent composition). The plugin-side key remains an
  optional alternative.
- Session ids (2026-08-28): the SDK runtime does not resume persisted sessions (it
  creates with an empty seed), so stable ids collide on a second run in the same
  project. Session ids are per-runtime unique — `jb-<hash>-<effort>-<model>-<nonce>` —
  and each runtime start announces "New session <id> started". Failure notices are
  selectable/wrapping in the transcript and mirrored in full to idea.log.
- No-API-key ask (2026-08-27): checkout mode reuses the checkout's own `.env` (the
  runtime's `loadEnv` loads `<checkout>/.env` automatically); if the key lives only
  in the harness web page's storage, it is entered once in Settings → Tools → DeepSeek
  Harness (OS keychain). A no-API-key stream/turn failure appends a one-shot guidance
  notice (NoticeKind.API_KEY_MISSING) and the chat panel opens the settings page
  automatically on the first occurrence.

## Context
- Credential store (2026-08-28 update): the built-in `sdk` profile composes the
  harness settings/credentials plugins over `$DSH_HOME` (settings.yaml,
  .credentials.yaml) — the same machine-local stores the harness web Models page
  writes — so a key entered in the web page flows into the plugin runtime
  automatically. The plugin-side key remains an optional alternative.
- Wire constraints: `initialize.reasoningEffort` exists since dsh-v0.1.2-alpha.1
  (verified against `packages/sdk/protocol/src/types.ts` and the harness's own
  `apps/cli/tests/profiles/sdk/keyless-smoke.e2e.ts`, which asserts
  `reasoning_effort: max` reaches the provider); there is still no model-listing
  method on the SDK protocol, so model discovery stays the plugin's own HTTP call.
- Harness facts verified (dsh-v0.1.2-alpha.1): adapter effort vocabulary
  `off`/`low`/`high`/`max` (`packages/llm/llm-deepseek/src/serialize.ts`), public
  base URL `https://api.deepseek.com`, the SDK runtime is the main CLI's
  `--profile sdk` (no `DSH_CORDIS_CONFIG`).
- Pulls forward: item 6's context picker subset (Current file + AGENTS.md; "workspace
  rules" remains for item 6) and item 11's composer actions (Ask/Execute/Plan; Fix
  pending). Roadmap annotated accordingly.
