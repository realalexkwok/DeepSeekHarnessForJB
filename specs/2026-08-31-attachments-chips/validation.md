# Item 15 (replanned) — validation

## Automatic
- `./gradlew test` green; `./gradlew buildPlugin` green; artifact path reported.
## Manual (host machine)
- With a file open and AGENTS.md present, chips appear above the composer;
  typing `@path` adds a mention chip; clicking a chip previews its content;
  X removes it (and the token for mentions); toggling the context checkboxes
  updates the bar.
