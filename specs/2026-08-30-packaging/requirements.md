# Item 12 — Packaging (bundled runtime): requirements

- The plugin artifact becomes SELF-CONTAINED: it embeds the harness's packaged
  single-file SDK-runtime executable(s) built by the upstream pkg/SEA pipeline
  (`scripts/build-exe-for-python-sdk.ts` at tag dsh-v0.1.2-alpha.1).
- Per-platform artifacts: one plugin zip per platform (linux-x64, macos-arm64,
  win-x64) so each zip stays ~260 MB instead of one ~780 MB fat zip.
- Bundled carrier launch: `exe --profile sdk --patch <patch>` with an ISOLATED
  harness home (`~/.deepseek-harness-for-jb/dsh`, dsh-cline's isolation
  decision), ripgrep sidecar extracted BESIDE the exe, no node/npm/PATH.
- First-run extraction: embedded resource -> versioned cache
  (`~/.deepseek-harness-for-jb/runtime/<plugin-version>/<os>-<arch>/`),
  idempotent, dsh-cline's ensurePluginInstalled pattern.
- Settings: Bundled executable carrier with an AUTO option (embedded runtime;
  manual override still accepted for externally built exes).
- Production: a GitHub Actions workflow builds the three exes from the pinned
  harness tag and produces the three platform plugin zips.
- Sizes (measured): exe 255-259 MB + ripgrep 4-6 MB (+ macOS spawn-helper
  52 KB); boot-to-initialize ~1.1 s (vs 0.9 s checkout carrier — accepted).
- WASM option ruled out (research 2026-08-30): no JS->WASM compiler runs the
  workload; Endive (~0.6 MB pure Java) recorded for a future sandbox role only.
