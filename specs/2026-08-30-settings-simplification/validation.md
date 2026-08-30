# Item 16 — Settings simplification: validation

## Automatic
- `./gradlew test` green; `./gradlew buildPlugin` green; artifact path reported.
## Manual (host machine)
- Settings page shows ONLY: carrier, checkout path, embedded-runtime note, API
  key, permission mode (+ node status).
- Stored key renders as ********; OK without touching it keeps the key; typing
  a new value replaces it; Clear key empties it.
- Model changes still come from the composer Model tab.
