# DeepSeekHarnessForJB — Roadmap

High-level implementation order, one small phrase of work per item.

## How each item is implemented
1. One git branch per item (or item group).
2. The feature spec is asked and agreed through ask-user-question before any write.
3. The feature lives under specs/YYYY-MM-DD-feature-name/ with:
   - requirements.md — scope, decisions, context
   - plan.md — numbered task groups
   - validation.md — how we know the implementation succeeded and can merge;
     always lists BOTH automatic criteria (gradle test/build) and manual criteria
     (user installs and checks the plugin in the IDE from the host machine), and
     every report names the built artifact's absolute path
     (`build/distributions/DeepSeekHarnessForJB-0.1.0.<stamp>.zip`)
4. Work refers to specs/mission.md and specs/tech-stack.md for guidance.
5. When the item is done and both verification halves pass, the user approves
   merging the branch into main; the next item's branch is created FROM the
   updated main (branches never stack on finished feature branches).

## Items
1. Constitution: specs/mission.md, specs/tech-stack.md, specs/roadmap.md, AGENTS.md
2. Gradle skeleton: build files, plugin.xml, platform target, empty tool window
3. Runtime bridge: spawn DSH runtime (bundled-exe + checkout carriers); JSON-RPC codec;
   initialize / session/prompt / shutdown
4. Event model: map session.event vocabulary to Kotlin types
5. Chat tool window: transcript, streaming text, tool cards, thinking disclosure
   — follow-up (2026-08-30): rebuild the transcript around a list/table model;
   the growing-panel approach cannot fix the streaming auto-scroll re-pin
   (known limitation in specs/tech-stack.md; skip approved by user)
6. Context picker: AGENTS.md / current file / rules — all selected by default
   — subset pulled forward 2026-08-27 (specs/2026-08-27-composer): Current file +
   AGENTS.md checkboxes in the composer; "workspace rules" remains for this item.
7. Diff preview/apply: fs tool results rendered as editor diffs with Apply/Reject
8. Plan mode: exit_plan_mode rendered as an approve/reject review panel
9. Questions & permissions: ask_user_question answers; command approval dialogs
10. Settings: API key (optional in checkout mode), base URL, model, runtime path + carrier toggle
    — pulled forward and implemented 2026-08-27 (specs/2026-08-27-settings) per user
    approval: the item-3 env-var staging was removed and the key now lives in the OS
    keychain via PasswordSafe. Includes the proactive first-run ask: missing carrier
    config opens the settings page automatically.
11. Context actions: Ask / Explain / Fix on editor selection
    — composer action tab pulled forward 2026-08-27 (specs/2026-08-27-composer):
    Ask / Execute / Plan selectable, Fix shown disabled (semantics pending);
    editor-selection context actions on demand remain for this item.
12. Packaging: bundle DSH runtime + agent composition; buildPlugin artifact
13. Verification: unit tests, runPlugin smoke, README
14. Composer @-mentions: type '@' to pick project files and insert them as context
    (added 2026-08-27 per user request; implemented in roadmap order)
15. Composer context attachments: collapsed compact view instead of inline full
    content (added 2026-08-27; deferred — replanned after all tasks per user)
16. Settings simplification (grouped, added 2026-08-30): remove the Bundled
    executable path input (embedded runtime only — the pinned harness version
    is what matters while DSH is in active development); remove Base URL and
    Model fields (Model is chosen in the composer); mask a stored API key so
    the page shows it is set.
