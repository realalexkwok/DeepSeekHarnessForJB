import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "DeepSeekHarnessForJB"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // Pin the main plugin version here: the settings plugin below already
        // puts it on the classpath, so project scripts must request it versionless.
        id("org.jetbrains.intellij.platform") version "2.3.0"
    }
}

plugins {
    // Provides the `intellijPlatform` repositories extension used below.
    id("org.jetbrains.intellij.platform.settings") version "2.3.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}
