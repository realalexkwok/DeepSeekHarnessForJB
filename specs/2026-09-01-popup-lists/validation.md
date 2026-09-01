# Item 18 — validation

## Automatic
- `./gradlew test` green (109/109); `./gradlew buildPlugin` green; artifact
  path reported each round.

## Manual (host machine)
- Each tab opens the list popup; typing filters; Enter/click applies; Escape or
  outside click closes; check/radio states mirror the current selections.

## Verification rounds (host)
1. Round 1 (2026-09-01): popup lists implemented — user reported "quite good";
   found the popup-reuse bug (stale rows across tabs) → fixed with a fresh
   window per show.
2. Round 2 fine-tune (user request): tick in a SEPARATE fixed column so row
   content aligns; bold mode names in the Action tab; bold group labels
   Model/Effort/Settings…; rename Execute→Code and Fix→Debug (composer action
   display + editor action label). Verified: tick layout ✓, bold texts ✓,
   renaming ✓. Artifact
   build/distributions/DeepSeekHarnessForJB-0.1.0.202609010800.zip.
3. Round 3 (user feedback): "Settings…" is the GROUP NAME, not a row under
   Effort → promoted to its own bold group label (still opens the settings
   page). Verified ✓. Artifact
   build/distributions/DeepSeekHarnessForJB-0.1.0.202609010823.zip.
