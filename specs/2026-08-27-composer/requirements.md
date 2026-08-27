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
  design. Effort is implemented WITHOUT protocol changes: the plugin bundles the SDK's
  agent composition (`agent.cordis.yml`, copied from the checkout's
  `examples/jsonrpc-agent/cordis.yml` — the SDK runner requires `DSH_CORDIS_CONFIG`
  and has no built-in fallback) and generates a per-effort variant
  (`llm-deepseek: { thinking, reasoningEffort }` — off = thinking disabled, low/high/max
  = thinking enabled with that wire effort; ids verified against the harness adapter).
- Settings page polish: "Bundled executable" description label (single-file
  `dsh-jsonrpc-agent-pkg-<platform>-<arch>` Node SEA binary; ships with the plugin in
  item 12 — leave blank until then), a Node.js version indicator, and pre-spawn
  Node.js validation for the checkout carrier (injectable probe, unit-tested).

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

## Context
- Cordis placement (fix round 5, 2026-08-27): the generated per-effort cordis is
  written as `<checkout>/examples/jsonrpc-agent/.dsh-jb-effort-<level>.yml` (node
  mode) or next to the bundled exe (bundled mode). Verified mechanism: the harness
  resolves bare plugin packages via the workspace symlinks under
  `examples/node_modules/@deepseek-ai`, so the config MUST live under `examples/`
  — next to the checkout's own cordis.yml. A missing canonical directory fails with a
  clear settings-page error.
- Wire constraints honored: no effort field and no model-listing method on the SDK
  protocol (verified against `packages/sdk/protocol/src/types.ts` and the sdk
  packages); the cordis-injection and HTTP-discovery designs avoid both.
- Harness facts verified: adapter catalog ids/names (`packages/llm/llm-deepseek`
  index.ts), effort ids (`serialize.spec.ts`: off → disabled thinking; low/high/max →
  wire reasoning_effort), public base URL `https://api.deepseek.com`, SDK runner
  requires `DSH_CORDIS_CONFIG`.
- Pulls forward: item 6's context picker subset (Current file + AGENTS.md; "workspace
  rules" remains for item 6) and item 11's composer actions (Ask/Execute/Plan; Fix
  pending). Roadmap annotated accordingly.
