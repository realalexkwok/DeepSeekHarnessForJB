# Feature: Settings page + env-var config removal (roadmap item 10, pulled forward)

Branch: `feature/2026-08-27-settings`
Spec date: 2026-08-27
Base: `main` after merging roadmap item 5 (merge commit 5f640c3).

## Scope
- Replace the item-3 staging config (environment variables `DSH_RUNTIME_MODE`,
  `DSH_RUNTIME_EXE`, `DSH_CHECKOUT`, `DSH_CORDIS_CONFIG`, `DEEPSEEK_API_KEY`,
  `DEEPSEEK_BASE_URL`, `DSH_MODEL`) with an application-level settings page
  (Settings → Tools → DeepSeek Harness): runtime carrier toggle (node checkout /
  bundled exe), checkout directory picker, bundled-exe file picker, API key password
  field, base URL, model.
- Store the API key in the OS keychain via IntelliJ `PasswordSafe`; the plugin never
  writes the key to disk.
- The plugin reads NO user-config environment variables anymore.
- Roadmap reorder: item 10 pulled forward per user approval (2026-08-27); item 12
  (packaging) stays where it is. Agent composition (`DSH_CORDIS_CONFIG`) remains
  item 12 — until then the runtime's own default composition applies in checkout mode.

## Decisions (agreed with the user, 2026-08-27)
- Application-level page (one machine-wide config; `provider` stays fixed at
  `deepseek-official`).
- `PasswordSafe` credential attributes with service name
  `io.dsh.jb.deepseek-api-key` — the key lives in the OS keychain.
- The runtime child process still receives the key through its own process
  environment when one is entered in settings (that is the DSH runtime's input
  contract); with no key entered in checkout mode, the harness may pick its key up
  from the checkout's own `.env` (the harness's own file — never read by the plugin).
- Defaults: carrier = node checkout (the bundled exe ships with item 12), model =
  `deepseek-chat`, base URL blank (provider default). Starting with an unset carrier
  path fails with an in-panel notice pointing at the settings page.
- The headless e2e keeps injecting config directly via the `DshRuntimeConfig`
  constructor (test-only; it reads `DSH_CHECKOUT` only to locate the checkout).

## Additional decisions (agreed 2026-08-27, ask-flow round)
- Proactive ask: when the runtime carrier is not configured, the chat panel shows a
  notice row and opens Settings → Tools → DeepSeek Harness automatically; the config
  is pre-validated in `DshRuntimeService.start()` (`DshRuntimeConfig.validateForStart()`
  → `DshConfigException`) before the process spawns.
- The API key is never hard-required in checkout mode (the checkout's own `.env` is a
  legitimate source); the settings form carries a hint label saying so.
- The settings page shows a Node.js indicator with the resolved VERSION and PATH
  (NodeResolver candidate list — GUI-launched IDEs do not inherit the shell PATH).

## Context
- Security driver: a key in the global shell env is visible to every 3rd-party
  process, and forcing IDE launch env changes breaks macOS sandbox expectations —
  unacceptable for an open-source plugin. Item 3 documented env vars as staging only
  ("the settings page (item 10) replaces this later").
- `PasswordSafe`/`CredentialAttributes` are part of the platform module — no new
  dependency.
