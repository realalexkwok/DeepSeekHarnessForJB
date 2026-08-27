# Plan: Settings page + env-var config removal (roadmap item 10, pulled forward)

Task groups, in execution order.

A. Feature spec files (this folder): `requirements.md`, `plan.md`, `validation.md`.
B. `io.dsh.jb.settings`: `DshSettingsSnapshot` (plain data), `DshSettingsState`
   (`PersistentStateComponent`), `DshApiKey` (`PasswordSafe`), and
   `DshSettingsConfigurable` (Swing form with directory/file pickers); `plugin.xml`
   registrations (`applicationService` + `applicationConfigurable` under tools).
C. `DshRuntimeConfig`: remove every env fallback; add `fromSettings`;
   `DshRuntimeService` builds the config from the settings snapshot + keychain key;
   `DshChatPanel` failure notice points at the settings page.
D. Unit tests: settings → config resolution (defaults, trimming, key passthrough).
E. Spec updates: `specs/tech-stack.md` (config section), `specs/roadmap.md`
   (reorder note), `specs/2026-08-26-runtime-bridge/requirements.md` (decision
   superseded), `specs/2026-08-26-chat-window/requirements.md` (settings no longer
   deferred), `README.md` (settings + keychain).
F. Verification: grep proves zero `getenv` in production code; `./gradlew test`;
   `./gradlew buildPlugin`; results in `validation.md`.
