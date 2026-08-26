# Feature: Gradle skeleton (roadmap item 2)

Branch: `feature/2026-08-26-gradle-skeleton`
Spec date: 2026-08-26

## Scope
- Create the Gradle-based IntelliJ Platform plugin project shell:
  `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, Gradle wrapper
  properties, `plugin.xml`, and the empty tool-window factory.
- No runtime bridge, no chat UI, no settings — those are later roadmap items.

## Decisions (agreed with the user)
- Language: Kotlin, JVM 17 bytecode (raised to JVM 21 during roadmap item 3 — the
  minimum IDE, 2025.1, runs on JBR 21; see `specs/tech-stack.md`).
- Build: Gradle Kotlin DSL, IntelliJ Platform Gradle Plugin 2.3.0, Kotlin 2.2.20.
- Settings script applies `org.jetbrains.intellij.platform.settings` 2.3.0 — required
  for the `intellijPlatform` repositories DSL in `settings.gradle.kts`.
- The main plugin version is pinned in `pluginManagement.plugins` and requested
  versionless in `build.gradle.kts` (the settings plugin preloads it).
- Compile target: IntelliJ IDEA Community 2025.1.3; `sinceBuild` 251; `untilBuild` open.
- Platform-only plugin: depends on `com.intellij.modules.platform` so it loads in
  IntelliJ IDEA and Android Studio.
- Serialization dependency declared now: `jackson-module-kotlin` 2.17.3 (per tech-stack.md).
- Naming: plugin display name "Community DSH Agent"; tool window "DSH Community";
  package `io.dsh.jb`; plugin id `io.dsh.jb.deepseek-harness`.
- Tool window: docked right, created by `io.dsh.jb.ui.DshToolWindowFactory` (placeholder panel).

## Context
- Repo constitution: `specs/mission.md`, `specs/tech-stack.md`, `specs/roadmap.md`.
- The project is opened in Android Studio via remote development; the user reviews files there.
