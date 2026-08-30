# Item 16 — Settings simplification: requirements

- Settings → Tools → DeepSeek Harness keeps: Runtime carrier toggle (Node
  checkout / Bundled executable), checkout path (node carrier only), the
  embedded-runtime DESCRIPTION text (bundled carrier — no path input; the
  plugin always uses the pinned embedded runtime), permission mode, API key.
- REMOVE the Bundled executable path field: the official DSH is in active
  development and cross-version incompatibilities make a user-picked external
  exe unsafe; the embedded runtime is built from the exact pinned harness tag.
- REMOVE Base URL and Model fields: MVP simplicity; the model is chosen in the
  composer Model tab (which already writes DshSettingsState.model).
- API key shows a mask (********) when a key is stored; leaving the mask
  unchanged keeps the key; typing a real value replaces it; Clear key still
  empties it.
- Persisted state fields (bundledExe/baseUrl/model) remain for backward
  compatibility but are no longer editable from the page.
