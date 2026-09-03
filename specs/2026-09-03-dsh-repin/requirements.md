# DSH re-pin to dsh-v0.1.2-rc.1 (FINAL CHORE, item 25): requirements

- Target: the LATEST harness tag, dsh-v0.1.2-rc.1 (commit a66e4702) — the
  alpha.3 plan is superseded (user 2026-09-03).
- Split carrier build: the Linux binaries are built HERE; the macOS binaries
  are built by the user on their Mac (Apple Silicon — macos-arm64, the only
  macOS target the resolver supports) and transferred back into
  runtime-dist/macos-arm64/ for embedding.
- Compatibility (verified 2026-09-03): the SDK JSON-RPC methods
  (initialize / session/prompt / shutdown) are unchanged at rc.1, and the
  full plugin suite — including the headless e2e against the REAL rc.1
  runtime (bridge patch, plan-mode gate, event vocabulary, fs diff meta) —
  passes.
- The runtime cache version bumps to "0.1.2-rc.1" so the new carrier never
  reuses the alpha.1 cache.
