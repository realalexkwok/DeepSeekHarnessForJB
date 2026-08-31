# Item 5 follow-up / 17b — transcript rebuild: plan

1. Spec files (this directory).
2. TranscriptUpdateQueue (Kilo SessionUpdateQueue): synchronized pending list,
   150 ms EDT flush, gated on the panel being shown; condenser merges
   assistant deltas and status snapshots.
3. ChatTranscriptModel gains a batched apply path (events + statuses per
   flush); DshChatPanel renders ONLY on flush, with in-place row updates.
4. Scroll-follow: pre-batch bottom snapshot + pin-at-execution guard
   (existing intent disengage retained).
5. Full suite + buildPlugin + host retest of the streaming scroll case.
