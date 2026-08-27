# Validation: Composer tabs, model/effort selection, settings polish

How we know this feature succeeded and can merge.

1. `./gradlew test` green: new pure tests (cordis patcher — all four efforts +
   untouched sections; prompt assembly — section ordering/gating + action
   instructions; models parsing — valid/malformed/fallback; node validation via
   injected probe) plus all existing tests (68) including the headless e2e.
2. `./gradlew buildPlugin` green; the zip contains `agent.cordis.yml` inside the
   main jar.
3. No new dependency (JDK HttpClient, Swing, platform components only); no env reads
   in `src/main`.
4. Manual smoke in Android Studio: the three tab buttons open POPUP MENUS on
   click (Context: Current file + AGENTS.md default on; Action: Ask/Execute/Plan
   selectable, Fix disabled; Model: model + effort submenus + Settings…); the submit
   icon sits right of the buttons; switching model/effort restarts the runtime with a
   notice; a prompt with the defaults runs end-to-end; the settings page explains the
   bundled exe and shows the Node.js version WITH its resolved path (e.g. v22.23.2 at
   /opt/homebrew/bin/node) even though the IDE-process PATH lacks node; idea.log
   clean.
5. Specs updated everywhere; roadmap annotated for items 6/11.
6. The user reviews in the IDE and approves the merge.

## Result
2026-08-27: criteria 1-3 and 5 verified on the remote (criteria 4 and 6 are yours).
Fix round 2 (user smoke): (a) Node.js was reported missing although
/opt/homebrew/bin/node existed — GUI-launched Android Studio does not inherit the
shell PATH; NodeResolver now probes well-known absolute locations (plus homebrew
opt/node links) and the runtime spawn uses the resolved path; (b) the composer tabs
now open popup menus instead of a fixed panel.
Fix round 3 (user feedback): the Context-action tab now shows ONLY the selected
action ("Ask ▾") with the current item checked on every popup open. Re-verified:
87 tests green, buildPlugin green, artifact rebuilt.

1. `./gradlew test` green — 83 tests total: 3 `CordisEffortTest` (all four efforts +
   untouched sections), 5 `PromptAssemblyTest` (ordering/gating/instructions), 5
   `ModelCatalogTest` (parsing/malformed/merge/default), 8 `DshConfigValidationTest`
   (incl. injected node probes), 4 `DshSettingsResolveTest` (defaults updated to
   `deepseek-v4-flash`), plus the 58 existing (codec, event model, transcript model,
   headless e2e).
2. `./gradlew buildPlugin` green; `agent.cordis.yml` is inside the main jar
   (verified via unzip), so the SDK runner's required `DSH_CORDIS_CONFIG` is always
   supplied by the service (per-effort variants written to temp files at spawn).
3. No new dependency (JDK HttpClient + platform UI only); no `System.getenv` under
   `src/main`.
4. Pending your manual smoke: three tabs + icon submit; Current file + AGENTS.md
   default on; Ask/Execute/Plan selectable, Fix disabled; Model menu lists the
   catalog (or detected models) with a Custom… entry; Effort has Off/Low/High/Max;
   switching model/effort restarts the runtime with a notice; a prompt runs
   end-to-end; the settings page explains the bundled exe and shows the Node.js
   version; idea.log clean.
5. Specs updated: `specs/2026-08-27-composer`, roadmap items 6/11 annotated,
   tech-stack (runtime pool + model discovery + composer), chat-window + settings
   specs annotated, README composer section.
6. 2026-08-27: the user verified the UI/UX in Android Studio (tab popups,
   action-tab display, model/effort switching, settings page with node path) and
   approved the merge.
