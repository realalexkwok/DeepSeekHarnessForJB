package io.dsh.jb.runtime

import java.io.File

/**
 * Item 12: locates and extracts the embedded packaged runtime for the current
 * platform. The plugin jar ships `runtime/<os>-<arch>/` resources produced by
 * the upstream pkg/SEA pipeline (scripts/build-exe-for-python-sdk.ts at the
 * pinned harness tag dsh-v0.1.2-rc.1). The first start extracts them into a
 * versioned cache, ripgrep sidecar BESIDE the executable (the harness spawns it
 * relative to the exe), idempotently (dsh-cline's ensurePluginInstalled pattern).
 * Pure JVM — headless-testable.
 */
object BundledRuntimeResolver {

    /** Cache layout version; bump when the embedded runtime artifact changes. */
    const val RUNTIME_VERSION = "0.1.2-rc.1"

    /** The resource subdir for the current platform, or null when unsupported. */
    fun platformDir(): String? {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            os.contains("linux") && (arch.contains("64") || arch.contains("amd64")) -> "linux-x64"
            os.contains("linux") && arch.contains("aarch64") -> "linux-arm64"
            os.contains("mac") && arch.contains("aarch64") -> "macos-arm64"
            os.contains("windows") && (arch.contains("64") || arch.contains("amd64")) -> "win-x64"
            else -> null
        }
    }

    private fun fileNames(platform: String): List<String> = when (platform) {
        "linux-x64", "linux-arm64" -> listOf(
            "deepseek-harness-sdk-runtime-$platform",
            "deepseek-harness-sdk-runtime-$platform-rg",
        )
        "macos-arm64" -> listOf(
            "deepseek-harness-sdk-runtime-$platform",
            "deepseek-harness-sdk-runtime-$platform-rg",
            "deepseek-harness-sdk-runtime-$platform-spawn-helper",
        )
        "win-x64" -> listOf(
            "deepseek-harness-sdk-runtime-$platform.exe",
            "deepseek-harness-sdk-runtime-$platform-rg.exe",
        )
        else -> emptyList()
    }

    /** Cache root: system property (tests) → env (ops) → user home. */
    fun cacheRoot(): File =
        System.getProperty("dsh.jb.runtimeCache")?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: System.getenv("DSH_JB_RUNTIME_CACHE")?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: File(System.getProperty("user.home"), ".deepseek-harness-for-jb/runtime")

    /**
     * Extracts the embedded runtime for the current platform into the cache and
     * returns the executable file. Null when the plugin jar carries no runtime
     * for this platform or the platform is unsupported. A partial extraction is
     * never trusted (it is deleted and re-attempted next start).
     */
    fun resolve(): File? {
        val platform = platformDir() ?: return null
        val names = fileNames(platform)
        if (names.isEmpty()) return null
        val dir = File(cacheRoot(), "$RUNTIME_VERSION/$platform")
        val exeName = names.first()
        val exe = File(dir, exeName)
        if (File(dir, ".stamp").exists() && exe.isFile) return exe
        dir.mkdirs()
        var complete = true
        for (name in names) {
            val stream = BundledRuntimeResolver::class.java.getResourceAsStream("/runtime/$platform/$name")
                ?: run { complete = false; break }
            stream.use { input ->
                File(dir, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (!complete || !exe.isFile) {
            dir.listFiles()?.forEach { it.delete() }
            return null
        }
        // EVERY extracted artifact must be executable: jar resources lose the
        // exec bit, and the harness spawns the ripgrep sidecar beside the exe —
        // a non-executable sidecar breaks the glob tool ("ripgrep launch
        // failed", host-verified 2026-08-31).
        dir.listFiles()?.forEach { runCatching { it.setExecutable(true) } }
        File(dir, ".stamp").writeText(RUNTIME_VERSION)
        return exe
    }
}
