# Validation: Settings page + env-var config removal (roadmap item 10, pulled forward)

How we know this feature succeeded and can merge.

1. Zero `DEEPSEEK_API_KEY`/`DSH_*` env reads in production config paths
   (grep-verified: no `System.getenv` under `src/main`).
2. `./gradlew test` passes: settings-resolution and config-validation unit tests
   plus all existing tests (codec, event model, transcript model, headless e2e).
3. `./gradlew buildPlugin` succeeds with the settings page included.
4. No new dependency: `PasswordSafe`/`CredentialAttributes` are platform-provided.
5. Manual smoke in Android Studio: with NOTHING configured, opening the tool window
   opens Settings → Tools → DeepSeek Harness automatically (proactive ask) with an
   in-panel notice; after configuring carrier + checkout path (+ key) the agent runs
   from the chat window; the key is in the OS keychain and not in any plugin-written
   file; no env vars set anywhere.
6. Product specs updated: `tech-stack.md` config + first-run-ask sections,
   `roadmap.md` reorder note, item-3/item-5 specs annotated, `README.md` settings
   section, this spec's ask-flow decisions.
7. The user reviews the code in the IDE and approves the merge.

## Result
2026-08-27: criteria 1-4 and 6 verified on the remote; criterion 5 drove three
fix rounds (all re-verified green); criterion 7 is yours.

- Round 1 (user smoke): the platform (Android Studio 2026.1.1, platform 261) did not
  register the legacy XML `applicationService` entry — `DshSettingsState` now
  registers with `@Service(Service.Level.APP)` (same mechanism as the project-level
  `DshRuntimeService`, which resolves fine in the user's IDE) and is fetched via
  `ApplicationManager.getApplication().getService`. Also `applicationConfigurable`
  was mis-placed outside `<extensions>` and moved inside, so the settings page
  actually appears.
- Round 2 (user feedback): the plugin must ASK, not fail silently. Added the
  proactive ask-flow: `DshRuntimeConfig.validateForStart()` pre-validates config
  before the process spawns, `DshRuntimeService.start()` throws
  `DshConfigException` on configuration problems, and `DshChatPanel` shows a
  notice and opens the settings page automatically. The API key is never
  hard-required in checkout mode (checkout `.env` is legitimate); the form carries
  a hint label.
- Verification state: `./gradlew test` green — 68 tests total (16 transcript, 33
  event model, 8 codec, 6 `DshConfigValidationTest`, 4 `DshSettingsResolveTest`,
  1 headless e2e); `./gradlew buildPlugin` green; no env reads in `src/main`.
- 2026-08-27: the user verified the UI/UX in Android Studio (criteria 5-7
  satisfied). The one pasted node error originated from the pre-fix build (its message
  string no longer exists); the re-smoke confirmed the ask-flow, popups, and settings
  work.
