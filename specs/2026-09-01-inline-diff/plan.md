# Item 23 — inline diff editor: plan

1. Spec files (this directory).
2. Kilo diff research (subagent): CacheDiffRequestChainProcessor, card
   embedding, apply/reject, cap fallback — feeds the requirements doc.
3. FileChange pure helpers: unified-diff renderer + diff-line counter, with
   pure-JVM tests.
4. Tool card hosts the inline diff (Kilo look-and-feel, per the research):
   per-file Apply/Reject, cap fallback to the platform diff tab.
5. Delete the old modal DshDiffDialog usage.
6. Full suite + buildPlugin + artifact report.
