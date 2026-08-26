# AGENTS.md

## Part A — System Prompt Context
You are an expert developer agent operating in this repository.
Before writing any code, you must read our project constitution files.
Before every write to disk, an ask-user-question round covering requirements
(scope/decisions/context), plan (task groups), and validation (success criteria)
must be answered by the user.

## Part B — Project Constitution
- **Core Mission:** See `specs/mission.md` for business goals and audiences.
- **Current Goals:** See `specs/roadmap.md` for feature priorities and milestones.
- **Tech Boundaries:** See `specs/tech-stack.md` for approved libraries and versions.

## Part C — Operational Instructions
### Lint & test
- Kotlin: `./gradlew build`, `./gradlew check`
- Plugin verification: `./gradlew buildPlugin`, `./gradlew verifyPlugin`, smoke via `./gradlew runIde`
### Feature workflow
- One git branch per roadmap item.
- Before writing anything for a feature, ask the user via ask-user-question, grouped on:
  requirements, plan, validation.
- Feature specs live in `specs/YYYY-MM-DD-feature-name/` with `requirements.md`,
  `plan.md`, `validation.md`.
### Commits
- Never commit unless (a) the user explicitly tells you to, or (b) the validation
  for the current phase/plan/task/item has passed and the user has been asked.
### Spec compliance
- Never add dependencies outside `specs/tech-stack.md`.
- Never change behavior without updating the constitution or the feature spec first.
- Roadmap order is authoritative; do not skip or reorder items without approval.
