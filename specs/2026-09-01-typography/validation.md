# Item 19 — validation

## Automatic
- `./gradlew test` green; `./gradlew buildPlugin` green; artifact path
  reported.
- Pure-JVM unit tests cover derive() (family/size/style derivation, small-size
  ratio).

## Manual (host machine)
- Side-by-side with Kilo Code (user-selected): the chat window matches Kilo's
  session typography — transcript body in the UI family at the editor size,
  code surfaces editor-mono, card headers h4-bold, secondary text small gray,
  dialogs/composer follow the same body font.
- Zoom-follow check: Preferences → Editor → Font → Size → Apply (a big step,
  e.g. 14 → 20) while the chat is open — transcript + composer resize; card
  headers and small gray text stay at UI scale. Switching the color scheme
  (Darcula ↔ Light) must also restyle.
- KNOWN PLATFORM LIMITATION (2026-09-01, Kilo parity): Cmd/Ctrl+wheel zoom over
  an editor is per-editor and does NOT propagate to the chat — on this
  platform Kilo Code behaves identically. Wheel gestures over the chat only
  scroll.

## Verification rounds (host)
1. Round 1 (2026-09-01): initial build — Settings → Editor → Font and theme
   switch did NOT restyle the chat. Root cause (bytecode evidence, ideaIC
   2025.1.3): the TOPIC fires, but syncPublisher delivers off the EDT and the
   handler touched Swing directly; wheel zoom is per-editor and never reaches
   the global scheme (Kilo parity). Fix: invokeLater EDT hop +
   LafManagerListener.TOPIC; limitation recorded.
2. Round 2 (host-verified 2026-09-01): (1) Settings → Editor → Font → Size →
   Apply restyles transcript + composer ✓; (2) theme switch restyles ✓;
   per-editor wheel zoom closed as KNOWN LIMITATION per user. Artifact
   build/distributions/DeepSeekHarnessForJB-0.1.0.202609011213.zip.
