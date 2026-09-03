# DSH re-pin: validation

## Automatic
- `./gradlew test` green against the rc.1 dev clone (e2e included);
  `./gradlew buildPlugin` green; artifact path reported.

## Manual (host machine)
- Install the new zip; the cache dir ~/.deepseek-harness-for-jb/runtime/
  0.1.2-rc.1/macos-arm64 is populated on first start; a chat runs end-to-end
  (tools, diffs, approvals) on the rc.1 runtime.

## macOS build instructions (user side)
1. On the Mac: `git clone --depth 1 --branch dsh-v0.1.2-rc.1
   https://github.com/deepseek-ai/deepseek-harness.git` (or check out the tag
   in an existing clone).
2. `cd deepseek-harness && corepack enable && pnpm install` (per the repo's
   own setup).
3. Build: `DSH_BUILD_CLIENT_PROFILE=official pnpm exec tsx
   scripts/build-exe-for-python-sdk.ts --targets=node24-macos-arm64`
   (Apple Silicon; an Intel Mac needs a new resolver branch — tell the agent).
4. Outputs appear in `dist-exe/`:
   - deepseek-harness-sdk-runtime-macos-arm64
   - deepseek-harness-sdk-runtime-macos-arm64-rg
   - deepseek-harness-sdk-runtime-macos-arm64-spawn-helper
5. Transfer all three into THIS repo on the Linux build machine:
   `scp dist-exe/deepseek-harness-sdk-runtime-macos-arm64*
   <user>@<linux-host>:/home/superguo/Projects/DeepSeekHarnessForJB/runtime-dist/macos-arm64/`
   (create the directory first if missing).

## Verification (host, 2026-09-03)
- macOS leg built by the user at rc.1 and transferred; both carriers embedded
  and the rc.1 zip installed + chat verified on the host ✓. Build notes: the
  original dev clone's stale alpha.1 lib/ outputs + frozen-install linker
  state broke the carrier build — a fresh rc.1 clone built cleanly and
  replaced the dev clone (branch jb-dev-a66e4702); a non-frozen `pnpm
  install` restored the importer links the source-bin e2e needs.
  Artifact build/distributions/DeepSeekHarnessForJB-0.1.0.202609032026.zip.
