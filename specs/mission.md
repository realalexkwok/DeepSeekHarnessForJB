# DeepSeekHarnessForJB — Mission

## What this project is
An IntelliJ Platform plugin that embeds a DeepSeek Harness (DSH) coding agent inside
JetBrains IDEs — IntelliJ IDEA and Android Studio — as a first-class AI pair programmer,
in the spirit of the built-in AI agent (AI Assistant Agent mode / Gemini in Android Studio).

## What it is not
- Not a fork of the IDE: no Cursor-style editor rewrite; the IDE stays the IDE.
- Not a fork of DeepSeek Harness: every agent capability is reused from DSH's shipped
  seams (agent presets, tools, sandbox, plan mode, subagents) over its SDK JSON-RPC runtime.
- Not a cloud service: the plugin talks to a model endpoint the user configures.

## The experience (target)
1. A docked "DeepSeek Harness" chat tool window per project.
2. Agent mode on the open project: read/search/edit files, run shell commands in the
   project root, plan before changing code, track todos, delegate to subagents, and
   ask the user when it needs a decision.
3. Live tool cards and thinking disclosure while the agent works.
4. Edits arrive as diffs to review, apply, or reject inside the editor.
5. Plan mode: explore read-only, present a plan, implement only after approval.
6. Permissions: explicit approval for commands and out-of-workspace writes.
7. Context actions: Ask / Explain / Debug on an editor selection.

## Audiences
- Developers who use DeepSeek Harness for AI coding, including vibe coding.
- Senior engineers who use DeepSeek Harness for code review, system analysis, system
  design, and technical research.
- Tech leads who guide the dev team with it: setting coding standards, conducting code
  reviews, and removing blockers that hinder progress.

## Principles
- Spec-driven: this `specs/` directory is the constitution; behavior changes start here.
- Human-in-the-loop: before every write to disk, an ask-user-question round covering
  requirements (scope/decisions/context), plan (task groups), and validation (success
  criteria) must be answered by the user.
- Two-sided verification: every verification has an automatic half (the agent's
  gradle test/build) AND a manual half (the user installs the built plugin from the
  host machine and checks it in the IDE); every report names the built artifact's
  absolute path.
- IDE-native: platform look and feel; no embedded web shell for core UX.
- Harness-native: DSH owns the agent; the plugin owns the bridge and presentation only.
- Open model input: DeepSeek API key plus any OpenAI-compatible base URL.
- Minimal surface: smallest plugin that delivers the target experience; no speculative features.
