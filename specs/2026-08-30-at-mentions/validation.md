# Item 14 — Composer @-mentions: validation

## Automatic
- `./gradlew test` green (incl. mention parsing/assembly cases).
- `./gradlew buildPlugin` green; artifact path reported.
## Manual (host machine)
- Type `@` in the composer: the picker lists project files, filters while
  typing, Enter inserts `@path`, Escape dismisses; sending includes the file
  content and the agent can see it.
