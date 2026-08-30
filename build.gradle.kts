import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform")
}

group = "io.dsh.jb"
// Build stamp after the version (2026-08-27): every build is uniquely identifiable
// in the artifact name AND in Settings → Plugins. Override with BUILD_NUMBER env.
val buildStamp: String = System.getenv("BUILD_NUMBER")?.takeIf { it.isNotBlank() }
    ?: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
version = "0.1.0.$buildStamp"

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
    // Plain JUnit 4, test scope only: our tests are pure JVM and must run as a
    // normal Gradle test task — not inside the IntelliJ platform test sandbox.
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        // The minimum supported IDE (2025.1) runs on JBR 21.
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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


// Item 12: embed locally built packaged runtimes (runtime-dist/<os>-<arch>/,
// gitignored, produced by the upstream pkg/SEA pipeline) into the plugin
// resources so the Bundled carrier is self-contained. The release workflow
// stages exactly one platform per leg before invoking buildPlugin.
val stageBundledRuntime = tasks.register("stageBundledRuntime") {
    inputs.dir(file("runtime-dist")).optional()
    outputs.dir(file("build/resources/main/runtime"))
    doLast {
        val src = file("runtime-dist")
        val dst = file("build/resources/main/runtime")
        if (src.exists()) {
            dst.deleteRecursively()
            src.copyRecursively(dst)
        }
    }
}
tasks.processResources {
    dependsOn(stageBundledRuntime)
}
tasks.test {
    dependsOn(stageBundledRuntime)
}

tasks.test {
    testLogging {
        events("started", "passed", "failed", "skipped")
        showStandardStreams = false
    }
}
