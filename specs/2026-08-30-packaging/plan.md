# Item 12 — Packaging (bundled runtime): plan

1. Spec files (this directory).
2. Staging: runtime-dist/<os>-<arch>/ dirs (gitignored) hold locally built
   exes; Gradle task copies them into plugin resources.
3. BundledRuntimeResolver: platform mapping, versioned idempotent extraction,
   ripgrep sidecar placement.
4. DshRuntimeClient: bundled mode auto-resolves the embedded exe; isolated
   default harness home; validateForStart updated.
5. Settings UI: Bundled executable auto option + hint update.
6. E2E: bundled-exe initialize/shutdown against the mock LLM (skipped when the
   staged resource is absent).
7. .github/workflows: build the three exes from the pinned harness tag and
   package the three platform plugin zips.
8. Validation: suite + buildPlugin (artifact path + size reported); manual host
   check with the bundled carrier.
