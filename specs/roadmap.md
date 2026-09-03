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
   — REPLANNED (2026-08-31, item 17 group): rebuild the transcript on Kilo
   Code's SessionMessageListPanel architecture — list-based messages, per-part
   views updated in place (ViewFactory), a visibility-gated update queue +
   condenser (150 ms batching), and scroll-follow that pins only when the user
   was at the bottom BEFORE the update. This replaces the growing-panel
   approach and its streaming auto-scroll re-pin limitation.
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
15. Composer context attachments — REPLANNED (2026-08-31, item 17 group):
    Kilo Code's prompt-attachment model — compact context CHIPS above the
    composer (current file, AGENTS.md, selection, each @-mention): collapsed by
    default, click to preview, remove to drop the context; @-mentions also
    render as chips in the transcript (Kilo PromptMention/linkifyMentions look).
16. Settings simplification (grouped, added 2026-08-30): remove the Bundled
    executable path input (embedded runtime only — the pinned harness version
    is what matters while DSH is in active development); remove Base URL and
    Model fields (Model is chosen in the composer); mask a stored API key so
    the page shows it is set.
17. Kilo-style UX alignment + naming/attribution (grouped, added 2026-08-31):
    (a) item 15 replanned as Kilo prompt-attachment chips; (b) item 5 follow-up
    replanned as the Kilo SessionMessageListPanel transcript architecture;
    (c) done-item audit — permission asks get Kilo's FIFO pending queue +
    inline card look, plan/generic questions get Kilo's keyed question-card
    look; naming: display name "DeepSeek Harness Agent — Kilo-style", slug
    "dsh-kilo-ux" for non-spaced use, plugin id + tool-window id unchanged,
    MIT attribution to Kilo Code (THIRD_PARTY_NOTICES).
18. Kilo-style popup lists for the composer's bottom tabs (grouped, added
    2026-09-01): the Context / Action / Model popup menus get Kilo's list
    look (searchable rows, icons, check/radio states, grouped sections).
19. Kilo-style typography (grouped, added 2026-09-01): align the composer and
    transcript font family and sizes with Kilo's session UI scale
    (SessionEditorStyle / SessionUiStyle).
20. Multiline composer support (grouped, added 2026-09-01): the composer input
    grows/shrinks with content, Enter still sends (Shift+Enter for newline, or
    a configurable send binding), pasted multi-line text stays intact.
21. Terse input/output rendering (grouped, added 2026-09-01): tool args and
    results render COLLAPSED by default with expand-on-click (Kilo tool-card
    style) instead of the current verbose always-expanded blocks; assistant
    text stays streamed inline.
22. Session history (grouped, added 2026-09-01): a history list of past
    sessions (persisted, resumable) in the current Chat mode; the design
    SHARES the store with a future Agent mode (one history, two modes).
    Follow-up (host feedback 2026-09-01): Kilo-style history management —
    icon-only New session/History header buttons, the history list INSIDE the
    main window (not a popup), per-item Rename + Delete affordances.
23. Inline diff editor (grouped, added 2026-09-01, user-selected): Kilo's
    virtual-file diff editor (CacheDiffRequestChainProcessor + per-file
    producers) replaces/upgrades the current diff dialog.
24. Permission management — REPLANNED 2026-09-03 (user cancelled the
    revert/undo item): Kilo-aligned permission UX — permission-mode selection,
    approval dialogs/cards, and auto-approve rule management; the item-25
    auto-approve quick-toggle is folded into this scope unless the interview
    says otherwise.
25. Auto-approve mode toggle (grouped, added 2026-09-01, user-selected):
    FOLDED into item 24 (verified there) — the composer shield toggle shipped
    with permission management.
    FINAL CHORE (updated 2026-09-03): re-pin the bundled DSH carrier to the
    LATEST tag — now dsh-v0.1.2-rc.1 (the alpha.3 plan is superseded). The
    dev clone moves to rc.1; per-platform carrier exes + ripgrep sidecar
    rebuild through the item-12 pipeline (Linux built here, macOS built by the
    user with written instructions); compatibility (SDK protocol + bridge
    answerer patch) verified by the e2e suite; pinned-version references
    updated.
