# Item 11 remainder — editor-selection context actions: validation

## Automatic
- `./gradlew test` green (incl. the new FIX assembly case); dev clone 0-dirty.
- `./gradlew buildPlugin` green; artifact path reported.

## Manual (host machine)
- Select code in an editor → right-click → the DeepSeek Harness group shows the
  three actions (disabled without a selection).
- Ask preloads the Ask template; Explain sends an explanation; Fix repairs the
  selection with the permission dialogs where needed (item 9).
