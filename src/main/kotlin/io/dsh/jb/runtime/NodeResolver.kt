package io.dsh.jb.runtime

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Node.js resolution (2026-08-27 fix): GUI-launched IDEs on macOS inherit a
 * minimal PATH, so a bare \"node\" probe fails even when the shell PATH carries
 * homebrew. The resolver probes the IDE-process PATH first, then well-known
 * absolute locations (homebrew Intel/ARM, MacPorts, volta, asdf, nvm, fnm), and
 * the RUNTIME SPAWN uses the resolved absolute path (see DshRuntimeConfig.
 * nodeExecutable). Pure JVM; probe is injectable for tests.
 */
object NodeResolver {

    data class NodeInfo(val path: String, val version: String)

    /** Runs \"<executable> --version\" and returns the version line, or null. */
    fun versionOf(executable: String): String? = try {
        val p = ProcessBuilder(executable, "--version").redirectErrorStream(true).start()
        if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
            p.inputStream.bufferedReader().use { it.readText().trim().ifBlank { null } }
        } else null
    } catch (_: Exception) {
        null
    }

    /** Candidate executable paths in probe order (PATH lookup first). */
    fun candidates(home: String = System.getProperty("user.home") ?: ""): List<String> {
        val list = mutableListOf(
            "node",
            "/opt/homebrew/bin/node",
            "/usr/local/bin/node",
            "/opt/local/bin/node",
            "/usr/bin/node",
            "/opt/homebrew/opt/node/bin/node",
            "/usr/local/opt/node/bin/node",
        )
        if (home.isNotEmpty()) {
            list += "$home/.volta/bin/node"
            list += "$home/.asdf/shims/node"
            // nvm: <root>/vX.Y.Z/bin/node; fnm: <root>/vX.Y.Z/installation/bin/node
            for (root in listOf(File(home, ".nvm/versions/node"), File(home, ".local/share/fnm/node-versions"))) {
                if (root.isDirectory) {
                    root.listFiles()?.sortedByDescending { it.name }?.forEach { dir ->
                        list += File(dir, "bin/node").path
                        list += File(dir, "installation/bin/node").path
                    }
                }
            }
        }
        return list
    }

    /** First candidate whose version probe succeeds, or null when no node is usable. */
    fun resolve(probe: ((String) -> String?)? = null): NodeInfo? {
        val p = probe ?: ::versionOf
        for (candidate in candidates()) {
            val version = p(candidate) ?: continue
            return NodeInfo(candidate, version)
        }
        return null
    }
}
