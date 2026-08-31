# DeepSeekHarnessForJB

An IntelliJ Platform plugin (IntelliJ IDEA + Android Studio) that embeds the
[DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness) coding agent
as a chat tool window with agentic editing, plan mode, and permission dialogs.

The plugin is **self-contained**: it ships the harness's packaged single-file
SDK runtime (built from the pinned tag `dsh-v0.1.2-alpha.1`, commit `cd5ef814`),
so no Node.js, npm, or separate harness installation is required.

## Features

- **Chat tool window** with streaming text, thinking disclosure, tool cards,
  diffs, and todo tracking.
- **Composer actions**: Ask (confirm-first), Execute, Plan (real harness plan
  mode with an approve/keep-planning review dialog), Fix; context pickers for
  the current file and AGENTS.md; model + reasoning-effort selection.
- **Slash commands**: `/plan` and `/plan off` are relayed to the harness.
- **Permissions**: `workspace-write` by default — out-of-workspace effects ask
  through an Approve-tool dialog; `danger-full-access` selectable in settings.
  Generic `ask_user_question` calls render in a question dialog.
- **Editor context actions**: on a text selection, right-click →
  *DeepSeek Harness* → Ask / Explain / Fix Selection.
- **Stop button**: the send button toggles to Stop while the agent runs and
  kills the runtime process tree (SIGTERM → grace → SIGKILL).

## Requirements

- IntelliJ IDEA 2025.1+ or Android Studio (platform-only dependency).
- **Bundled carrier (default)**: nothing else — the embedded runtime is
  extracted to `~/.deepseek-harness-for-jb/runtime` on first start, and the
  harness home is isolated under `~/.deepseek-harness-for-jb/dsh`.
- **Node checkout carrier (optional)**: a checkout of deepseek-harness at tag
  `dsh-v0.1.2-alpha.1` built with `pnpm install` + `pnpm run build`, plus
  Node 22.19+ (resolved automatically from common install locations).
- A DeepSeek API key (stored in the OS keychain via PasswordSafe; shown masked
  as `********` in settings once set).

## Usage

1. **Settings → Tools → DeepSeek Harness**: pick the carrier (Bundled
   executable = default), enter the API key, choose the permission mode.
2. Open the **DSH Community** tool window, type a prompt, and press Enter
   (the send button doubles as Stop while running).
3. `/plan` enters plan mode; approve or keep-planning at the review dialog.
4. Out-of-workspace writes/bash ask for approval in the permission dialog.

## Repository layout & spec-driven development

This repository is spec-driven: the constitution in `specs/` is authoritative.
Every roadmap item is implemented on its own git branch with a feature spec
under `specs/YYYY-MM-DD-feature-name/` (`requirements.md`, `plan.md`,
`validation.md`). See `AGENTS.md` for the operational instructions.

## Building & testing

```sh
JAVA_HOME=<jdk17> DSH_CHECKOUT=<dev-clone> ./gradlew test   # pure-JVM tests incl. headless e2e
JAVA_HOME=<jdk17> ./gradlew buildPlugin                    # zip under build/distributions/
./gradlew runIde                                            # manual UI smoke (requires a display)
```

The e2e suite executes the REAL harness runtime (from a dev clone at the
pinned tag, never the ambient checkout) against a JDK-hosted mock LLM — no API
key needed. The embedded bundled runtime is exercised by the same suite when
`runtime-dist/<os>-<arch>/` has been staged (see the release workflow).

## Producing the bundled runtimes

The embedded exes are built from the harness repo at the pinned tag with the
upstream pkg/SEA pipeline:

```sh
pnpm install --frozen-lockfile
DSH_BUILD_CLIENT_PROFILE=official pnpm exec tsx scripts/build-exe-for-python-sdk.ts --targets=node24-<platform>-<arch>
# copy dist-exe/* into this repo's runtime-dist/<os>-<arch>/ (gitignored)
```

`.github/workflows/build-runtime-exes.yml` does this for linux-x64,
macos-arm64, and win-x64 and uploads one self-contained plugin zip per
platform (~80 MB each).

## Known limitations

- Ask/Execute guidance is advisory; enforcement comes from the sandbox
  permission mode (`workspace-write` default asks before out-of-workspace
  effects).
- The transcript auto-scroll can re-pin to the bottom during heavy streaming;
  a list/table-based transcript rebuild is planned (17b).
- The initial Current-file context chip appears only after the first keystroke
  or focus event (cosmetic, recorded 2026-08-31).
- Diff previews are read-only; edits are applied by the agent itself.
