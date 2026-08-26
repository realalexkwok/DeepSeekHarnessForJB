import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform")
}

group = "io.dsh.jb"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Compile against the oldest supported IDE (2025.1). The plugin loads on 2025.1+
        // in both IntelliJ IDEA and Android Studio because it depends only on the platform.
        intellijIdeaCommunity("2025.1.3")
        pluginVerifier()
        zipSigner()
    }
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.3")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellijPlatform {
    pluginConfiguration {
        // sinceBuild 251 = 2025.1; untilBuild intentionally left open for a personal
        // plugin — tighten to a specific range before wider distribution.
        ideaVersion {
            sinceBuild = "251"
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
