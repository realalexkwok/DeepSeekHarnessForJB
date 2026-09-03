# Item 24 (replanned) — validation

## Automatic
- `./gradlew test` green; `./gradlew buildPlugin` green; artifact path
  reported.
- Pure PermissionRules tests: levels, "*" and prefix patterns, longest-match,
  bash-command matching, default ask.

## Manual (host machine)
- An out-of-workspace effect shows an INLINE "Permission required" card
  (tool + reason) with Reject / "Allow once"; no modal dialog appears.
- Shield toggle ON: asks auto-answer (cards flash by); OFF restores asking.
- "Always allow/deny <tool>" in the card and per-tool/pattern edits in the
  Settings page persist and drive future decisions (a denied ask fails the
  tool; an allowed ask never prompts).
- Reject fails the tool gracefully (its error shows on the tool card).

## Verification rounds (host)
1. Round 1 (2026-09-03): blocking NPE — syncAutoApproveIcon() ran inside the
   autoApproveBtn field initializer (field still null) → tool-window init
   failed ("Nothing to show"). Fixed: icon state set in init.
2. Round 2 (2026-09-03): a generic ask_user_question still rendered through
   the old modal QuestionDialog → replaced by the inline question card
   (single-select buttons answer on click; multi-select + Submit; custom
   details field); QuestionDialog deleted.
3. Round 3 (2026-09-03, verified): inline permission card, shield toggle,
   rules (card + Settings), inline question card all verified. Artifact
   build/distributions/DeepSeekHarnessForJB-0.1.0.202609031753.zip.
