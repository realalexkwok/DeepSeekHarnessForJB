# Item 20 — validation

## Automatic
- `./gradlew test` green; `./gradlew buildPlugin` green; artifact path
  reported.
- Pure-JVM tests cover the height clamp (below min, above max, mid-range).

## Manual (host machine)
- Type several lines → the composer grows line by line to the cap (~10 lines),
  then scrolls internally.
- Shift+Enter inserts a newline; Enter sends.
- Pasting multi-line text keeps all lines and grows the composer.
- @-mentions still work; Ctrl/Cmd+wheel-nothing stays out of scope; the
  item-19 typography still applies to the composer (font follows Settings →
  Editor → Font).
- Follow-up (host feedback): no border between the editor and the tab row;
  clicking into the composer shows ONE frame around the whole block (editor +
  tabs + send button); clicking elsewhere removes it.

## Verification rounds (host)
1. Round 1 (host-verified 2026-09-01): growth-to-cap, Shift+Enter, Enter-sends,
   multi-line paste all OK. Follow-up requested: composer too narrow — remove
   editor/tab border; focus frame must surround editor + tab together
   (Kilo-style). Artifact
   build/distributions/DeepSeekHarnessForJB-0.1.0.202609011244.zip.
2. Round 2 (host-verified 2026-09-01): border removed + whole-block focus frame
   verified; follow-up: borderless editor got the placeholder hint ("Type
   here; use / or @, drop / paste files; ⏎ send, ⇧⏎ newline", visible while
   focused) — verified. Artifact
   build/distributions/DeepSeekHarnessForJB-0.1.0.202609011740.zip.
