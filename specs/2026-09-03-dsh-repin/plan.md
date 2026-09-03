# DSH re-pin: plan

1. Spec files (this directory); roadmap + tech-stack pins updated to rc.1.
2. Dev clone moved to dsh-v0.1.2-rc.1 (branch jb-dev-a66e4702).
3. Compatibility: e2e suite against rc.1 — GREEN.
4. Linux carrier: DSH_BUILD_CLIENT_PROFILE=official
   `pnpm exec tsx scripts/build-exe-for-python-sdk.ts --targets=node24-linux-x64`
   in the dev clone → stage dist-exe outputs into runtime-dist/linux-x64/.
5. macOS carrier: user builds node24-macos-arm64 on the Mac (instructions
   below) and transfers the three artifacts into runtime-dist/macos-arm64/.
6. RUNTIME_VERSION bump + full suite + buildPlugin + artifact report.
