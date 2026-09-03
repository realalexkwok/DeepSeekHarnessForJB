# Item 23 — validation

## Automatic
- `./gradlew test` green; `./gradlew buildPlugin` green; artifact path
  reported.
- Pure-JVM tests for the diff renderer + line counter.

## Manual (host machine)
- An edit/write tool card shows the diff INLINE (Kilo look-and-feel) with
  Accept/Reject per file; Accept dismisses the buttons; Reject undoes the
  change on disk and shows Undo Reject; Undo Reject re-applies and restores
  the pair.
- Added/deleted lines show colored backgrounds; the gutter shows old and new
  line numbers side by side.
- Each change section shows the underlined BASENAME link; hover shows the
  full path bubble (dismisses on mouse-out); click opens the file.
- Only the right-end arrow expands/collapses; an expanded diff card shows
  ONLY the diff content (no request JSON, no full path, no raw-meta button);
  non-diff cards expand as before.
- A >2000-line diff shows the "open in a diff tab" placeholder and the tab
  opens (Kilo cap behavior).
- No modal DshDiffDialog remains anywhere.

## Verification rounds (host)
1. Rounds 1–3 (2026-09-02): inline sections with icon Apply/Reject; text
   Apply/Reject buttons (icons were invisible); double-slash path fix;
   Accept/Reject/Undo-Reject state machine; basename links with hover
   balloon + click-to-open; arrow-only expansion with diff-only body;
   read-card links; PartHeader hover tint + whole-header click; diff
   decoration moved to EditorTextField.addSettingsProvider (plain-text bug).
2. Round 4 (2026-09-02/03): pure-text ">" / "∨" arrow; stripped diff body
   (no ---/+++/@@ lines, no +/- markers, op-driven backgrounds + gutter);
   all file links on aggregated write-card previews; state words removed,
   red tool name on failure; Accept All / Reject All above Todos.
3. Round 5 (2026-09-03): diff title (link + "+N −M") moved to the card
   header for single-change cards — verified.
4. Round 6 (2026-09-03): standalone expandable "Reasoning" card under tool
   cards — verified; hover preview bubbles (bash/glob/edit).
5. Round 7 (2026-09-03): bubble coverage extended (write = diff body, grep =
   response) — verified; MULTI-CHANGE SPLIT — one card per change for
   multi-file results — verified. Artifact
   build/distributions/DeepSeekHarnessForJB-0.1.0.202609031307.zip.
