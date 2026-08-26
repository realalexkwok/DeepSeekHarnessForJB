# DeepSeekHarnessForJB — Roadmap

High-level implementation order, one small phrase of work per item.

## How each item is implemented
1. One git branch per item (or item group).
2. The feature spec is asked and agreed through ask-user-question before any write.
3. The feature lives under specs/YYYY-MM-DD-feature-name/ with:
   - requirements.md — scope, decisions, context
   - plan.md — numbered task groups
   - validation.md — how we know the implementation succeeded and can merge
4. Work refers to specs/mission.md and specs/tech-stack.md for guidance.

## Items
1. Constitution: specs/mission.md, specs/tech-stack.md, specs/roadmap.md, AGENTS.md
2. Gradle skeleton: build files, plugin.xml, platform target, empty tool window
3. Runtime bridge: spawn DSH runtime (bundled-exe + checkout carriers); JSON-RPC codec;
   initialize / session/prompt / shutdown
4. Event model: map session.event vocabulary to Kotlin types
5. Chat tool window: transcript, streaming text, tool cards, thinking disclosure
6. Context picker: AGENTS.md / current file / rules — all selected by default
7. Diff preview/apply: fs tool results rendered as editor diffs with Apply/Reject
8. Plan mode: exit_plan_mode rendered as an approve/reject review panel
9. Questions & permissions: ask_user_question answers; command approval dialogs
10. Settings: API key (optional in checkout mode), base URL, model, runtime path + carrier toggle
11. Context actions: Ask / Explain / Fix on editor selection
12. Packaging: bundle DSH runtime + agent composition; buildPlugin artifact
13. Verification: unit tests, runPlugin smoke, README
