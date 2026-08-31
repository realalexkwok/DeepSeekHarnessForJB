# Item 5 follow-up / 17b — Kilo-style transcript rebuild: requirements

- Rebuild the chat transcript on Kilo Code's SessionMessageListPanel
  architecture instead of the growing BoxLayout panel:
  1. VISIBILITY-GATED UPDATE QUEUE: incoming events/statuses enqueue; a 150 ms
     EDT tick flushes one CONDENSED batch (Kilo SessionUpdateQueue +
     SessionQueueCondenser): text deltas concatenate, tool/status snapshots
     keep the latest, turn boundaries and asks preserve order. No per-chunk
     full re-render.
  2. PER-PART VIEWS updated in place (Kilo ViewFactory exhaustive when):
     assistant text/thinking, tool cards, notices — rows are created once and
     mutated, never rebuilt per event.
  3. SCROLL-FOLLOW: snapshot the bottom position BEFORE the batch applies;
     pin only when the user was following; intent-based disengage (wheel-up /
     thumb drag) stays. This finally fixes the streaming re-pin limitation.
- Model behavior is unchanged; the transcript rows and the e2e expectations
  stay the same.
- CLOSED AS KNOWN LIMITATION (2026-09-01, host-verified): after multiple fix
  rounds (150 ms batched queue, pre-batch follow snapshot, intent disengage,
  View-API row measurement, width-guarded reflow, user-position pinning — the
  last one reverted, it fought the user's wheel), streaming scroll-follow still
  re-pins/oscillates in the host IDE; the residual viewport mover was not
  root-caused. Kilo ships no additional pinning mechanism (their list panel
  hit the same re-measure class of bug, SessionMessageListPanel.kt:607). The
  known remedy is a JList-based transcript rewrite; deferred.
