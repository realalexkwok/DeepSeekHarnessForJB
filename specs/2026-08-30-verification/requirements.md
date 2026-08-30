# Item 13 — Verification (unit tests, runPlugin smoke, README): requirements

- A README.md for the repository: what the plugin is, carriers (embedded
  bundled runtime vs node checkout), settings, usage (composer actions,
  /plan, permission dialogs, editor context actions), building & testing,
  the production build workflow, roadmap status, and known limitations.
- runPlugin/runIde smoke documented as a manual step (headless machines cannot
  drive the IDE UI; the e2e suite already covers the runtime protocol paths).
- The automatic suite stays the primary gate: `./gradlew test` (pure-JVM unit
  tests + headless e2e against the real runtime) and `./gradlew buildPlugin`.
