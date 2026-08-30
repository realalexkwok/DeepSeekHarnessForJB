# Item 12 — Packaging (bundled runtime): validation

## Automatic
- `./gradlew test` green (incl. the bundled-exe e2e when staged); dev clone
  0-dirty.
- `./gradlew buildPlugin` green; report artifact absolute path AND size.
## Manual (host machine)
- Install the platform zip; Settings -> Runtime carrier -> Bundled executable
  (auto); run a turn with NO checkout and NO system node on PATH if possible;
  permission dialogs and plan mode still work (item 9).
