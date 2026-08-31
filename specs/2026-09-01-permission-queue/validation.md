# Item 17c — validation

## Automatic
- `./gradlew test` green; `./gradlew buildPlugin` green; artifact path reported.
## Manual (host machine)
- Trigger two permission asks in one turn: dialogs appear one at a time, in
  order; each decision lands on its own request.
