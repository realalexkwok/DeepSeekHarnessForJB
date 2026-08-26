# DeepSeekHarnessForJB

An [IntelliJ Platform](https://plugins.jetbrains.com/docs/intellij/welcome.html) plugin that embeds a
[DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness) coding agent inside JetBrains IDEs —
IntelliJ IDEA and Android Studio — as a first-class AI pair programmer in the spirit of the built-in
AI agent (AI Assistant Agent mode / Gemini in Android Studio).

## Status

Constitution phase. `specs/` and `AGENTS.md` are the committed artifacts so far;
implementation items 2–13 of the roadmap are pending.

## Repository layout

| Path | Purpose |
|---|---|
| `specs/mission.md` | What and why — goals, audiences, principles. |
| `specs/tech-stack.md` | Approved libraries, toolchain, runtime carriers, inputs. |
| `specs/roadmap.md` | Implementation order and the per-item feature workflow. |
| `AGENTS.md` | How to work in this repo (operational instructions). |
| `README.md` | This file. |

## Spec-driven development

This repository is spec-driven: the constitution in `specs/` is authoritative.
Every roadmap item is implemented on its own git branch with a feature spec under
`specs/YYYY-MM-DD-feature-name/` (`requirements.md`, `plan.md`, `validation.md`),
and every write to disk is preceded by an ask-user-question round.

## Prerequisites (roadmap items 2+)

- JDK 17+
- Node.js ≥ 22.19 (checkout carrier) or the bundled `dsh-jsonrpc-agent-pkg-*` executable
- A DeepSeek Harness checkout for building the bundled runtime

## Build

```sh
./gradlew buildPlugin   # plugin artifact
./gradlew runIde        # smoke run in a sandboxed IDE
```

## Settings

- DeepSeek API key (optional when a configured DSH checkout already carries it via `.env`)
- Optional base URL, model name, runtime carrier (bundled executable / Node checkout)
