# Validation: Diff preview/apply (roadmap item 7)

How we know this feature succeeded and can merge.

1. `./gradlew test` green: `FsDiffParserTest` (valid/create-null/malformed/empty)
   plus the extended headless e2e (real read→write tool round-trip; the
   `tool/result` meta parses to the expected diff; the file was rewritten) plus
   all 90 existing tests.
2. `./gradlew buildPlugin` green with a stamped version.
3. No new dependency: platform Diff framework only.
4. Manual smoke in Android Studio: a prompt that edits an existing project file shows
   "Diff (N files)" on the tool card; the dialog shows the platform diff; Apply
   writes the file (undoable), Reject leaves it untouched; out-of-workspace paths
   are refused; creates show the plain result card.
5. The user reviews the code in the IDE and approves the merge.

## Result
2026-08-27: criteria 1-3 verified on the remote (criteria 4-5 are yours).

1. `./gradlew test` green — 93 tests total: 3 `FsDiffParserTest` (valid/create-null/
   malformed/empty, strict `oldText` presence mirroring the harness), the EXTENDED
   headless e2e (stateful mock: read → write → text; the real runtime's
   `tool/result` meta parsed to FileChange(hello.txt, "old content", "new content")
   and the file was rewritten on disk), plus all 90 existing.
2. `./gradlew buildPlugin` green with a stamped version.
3. No new dependency: platform Diff framework only.
4. Pending your manual smoke: an edit prompt shows "Diff (N files)" on the tool card;
   the dialog shows the platform diff; Apply writes (undoable) and Reject leaves the
   file untouched; out-of-workspace paths are refused. Creates NOW also show a Diff
   button (whole-file "(new file)" fallback, 2026-08-28) instead of the plain card.
5. Pending your review and merge approval; the branch is NOT committed yet.
