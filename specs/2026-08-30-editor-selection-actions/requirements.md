# Item 11 remainder — editor-selection context actions: requirements

- On a text selection in the editor, a popup-menu group "DeepSeek Harness"
  offers **DSH: Ask about Selection**, **DSH: Explain Selection**, **DSH: Fix
  Selection**.
- Each action activates the DSH Community tool window and preloads the
  composer: Ask/Explain select the Ask action with a ready prompt (Explain
  sends immediately-ready text; Ask leaves a template for the user's
  question), Fix selects the Fix action with a repair prompt.
- The prompt assembly keeps injecting the live editor selection + file content
  at send time (existing PromptContext path), so the user can edit the
  preloaded text before sending.
- **Enable Fix** in the composer (was disabled pending semantics): Fix =
  diagnose and repair the selected code; edits allowed (subject to the
  workspace-write permission dialogs from item 9).
