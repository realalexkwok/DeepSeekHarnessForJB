# Item 21 — validation

## Automatic
- `./gradlew test` green; `./gradlew buildPlugin` green; artifact path
  reported.
- Pure-JVM tests cover toolPreviewLine (result preferred, args fallback, first
  non-blank line, truncation + ellipsis, blank inputs).

## Manual (host machine)
- Tool cards appear collapsed with one gray preview line.
- Clicking the header expands args + result; clicking again collapses.
- Cards re-collapse when the tool name changes; streaming keeps the state.
- Diff button and raw-meta toggle still work; assistant text still streams
  inline; card headers/preview keep the item-19 typography.

## Verification rounds (host)
1. Round 1 (host-verified 2026-09-01): collapsed cards with the gray preview
   line ✓; header click expands/collapses ✓; raw-meta toggle ✓; Diff ✓;
   assistant streaming inline ✓; each NEW tool card arrives collapsed ✓.
   Noted as a trivial known behavior: an expanded card stays expanded while
   its stream keeps outputting — per the approved requirements streaming keeps
   the current state (collapsing mid-stream would hide the live output).
   Artifact build/distributions/DeepSeekHarnessForJB-0.1.0.202609011845.zip.
