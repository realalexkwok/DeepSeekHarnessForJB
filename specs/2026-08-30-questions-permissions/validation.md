# Item 9 — Questions & permissions: validation

## Automatic
- `JAVA_HOME=/home/superguo/tools/jdk-17 DSH_CHECKOUT=/home/superguo/Projects/dsh-for-jb-plugin-dev ./gradlew test` green
  (all existing + new tests; dev clone `git status` stays 0).
- `./gradlew buildPlugin` green; the report names the absolute artifact path.

## Manual (host machine, Android Studio)
- Trigger an ask_user_question (a model that calls the ask tool, or a plan
  review): the generic question dialog / plan dialog appears and the chosen
  option reaches the runtime (turn continues accordingly).
- With permission mode = workspace-write: prompt the model to write OUTSIDE the
  project (e.g. `/etc/...`); the permission dialog appears with the tool name +
  reason; Allow once lets the tool proceed (and fail on OS perms), Deny blocks
  it and the transcript shows the decided outcome.
- Switch permission mode to danger-full-access: no dialogs fire.
