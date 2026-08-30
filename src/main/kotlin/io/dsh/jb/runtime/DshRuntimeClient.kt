package io.dsh.jb.runtime

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.dsh.jb.protocol.InitializeParams
import io.dsh.jb.protocol.InitializeResult
import io.dsh.jb.protocol.JsonRpcPeer
import io.dsh.jb.protocol.SessionEventNotification
import io.dsh.jb.protocol.SessionPromptParams
import io.dsh.jb.protocol.SessionPromptResult
import io.dsh.jb.protocol.SessionStatusNotification
import io.dsh.jb.util.FsTree
import io.dsh.jb.protocol.StdioLineTransport
import io.dsh.jb.protocol.textContentBlock
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** A configuration problem the user must fix in the settings page (roadmap item 10 ask-flow). */
class DshConfigException(message: String) : IllegalStateException(message)

/**
 * Configuration for one DSH runtime process. Values come from the settings
 * page via [DshRuntimeConfig.fromSettings] (roadmap item 10, pulled forward
 * 2026-08-27: the item-3 env-var staging was removed); tests inject values.
 */
data class DshRuntimeConfig(
    /** `node` uses a DSH checkout; `bundled` uses the single-file exe (item 12 packaging). */
    val mode: String,
    val bundledExe: String = "",
    val checkoutPath: String = "",
    /**
     * Absolute node executable used for the checkout carrier; resolved by
     * NodeResolver (GUI-launched IDEs do not inherit the shell PATH). Null falls
     * back to a bare `node` lookup and fails validation for the node carrier.
     */
    val nodeExecutable: String? = null,
    val apiKey: String = "",
    val baseUrl: String = "",
    val provider: String = "deepseek-official",
    val model: String = "deepseek-chat",
    /** Adapter-owned reasoning effort, sent at initialize (dsh-v0.1.2-alpha.1+). */
    val reasoningEffort: String = "max",
    /** Explicit harness home (`DSH_HOME`) required by the built-in `sdk` profile. */
    val harnessHome: String = "${System.getProperty("user.home")}/.dsh",
    /** Permission mode until roadmap item 8 wires real dialogs. */
    val permissionMode: String = "danger-full-access",
    /** Agent workspace (becomes `initialize.cwd`); default: current dir. */
    val cwd: String = System.getProperty("user.dir"),
    /** Localhost bridge the runtime-side jb-bridge plugin calls (item 8). */
    val bridgeUrl: String = "",
    /** Per-runtime bridge bearer token. */
    val bridgeToken: String = "",
    /** Stable session id used for every prompt. */
    val sessionId: String,
    val maxTokens: Int? = null,
) {
    /**
     * Pre-flight validation: returns a human-readable problem or null. Pure JVM —
     * unit-tested headlessly. The chat panel turns a reported problem into the
     * proactive ask (notice + settings page), see DshChatPanel.
     */
    fun validateForStart(): String? = when {
        mode == "bundled" && bundledExe.isBlank() ->
            "Bundled runtime executable path is not configured"
        mode == "bundled" && !File(bundledExe).isFile ->
            "Bundled runtime executable not found: $bundledExe"
        mode == "node" && checkoutPath.isBlank() ->
            "DeepSeek Harness checkout path is not configured"
        mode == "node" && nodeExecutable == null ->
            "Node.js was not found — checked PATH plus common install locations " +
                "(see Settings → Tools → DeepSeek Harness)"
        mode == "node" && !File(checkoutPath).isDirectory ->
            "DSH_CHECKOUT is not a directory: $checkoutPath"
        else -> null
    }

    companion object {
        /**
         * Builds the config from the settings snapshot plus the keychain-held API key.
         * No user-config environment variables are read anywhere on this path.
         */
        fun fromSettings(
            settings: io.dsh.jb.settings.DshSettingsSnapshot,
            apiKey: String?,
            sessionId: String,
            cwd: String,
            nodeExecutable: String? = null,
        ): DshRuntimeConfig = DshRuntimeConfig(
            mode = settings.mode.ifBlank { "node" },
            bundledExe = settings.bundledExe.trim(),
            checkoutPath = settings.checkoutPath.trim(),
            nodeExecutable = nodeExecutable,
            apiKey = apiKey ?: "",
            baseUrl = settings.baseUrl.trim(),
            model = settings.model.trim().ifBlank { io.dsh.jb.settings.ModelCatalog.DEFAULT_MODEL },
            cwd = cwd,
            sessionId = sessionId,
        )
    }
}

/**
 * Pure-JVM client for the DSH SDK JSON-RPC runtime: spawns the process,
 * runs `initialize` / `session/prompt` / `shutdown`, and fans out
 * `session.event` / `session.status` notifications for the owned session.
 * No IntelliJ dependencies, so it is testable headlessly.
 */
class DshRuntimeClient(
    private val config: DshRuntimeConfig,
    private val stderrSink: (String) -> Unit = {},
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mapper = jacksonObjectMapper()
    private val eventListeners = CopyOnWriteArrayList<(SessionEventNotification) -> Unit>()
    private val statusListeners = CopyOnWriteArrayList<(SessionStatusNotification) -> Unit>()

    @Volatile
    private var peer: JsonRpcPeer? = null

    @Volatile
    private var process: Process? = null

    @Volatile
    private var running = false

    /** Per-runtime dir holding the staged jb-bridge patch + answerer. */
    @Volatile
    private var bridgeDir: File? = null

    fun addEventListener(listener: (SessionEventNotification) -> Unit) {
        eventListeners += listener
    }

    fun addStatusListener(listener: (SessionStatusNotification) -> Unit) {
        statusListeners += listener
    }

    fun isRunning(): Boolean = running

    suspend fun start(): InitializeResult {
        check(!running) { "DSH runtime already running" }
        val stagedPatch = stageBridgePatch()
        val proc = ProcessBuilder(resolveCommand(stagedPatch))
            .directory(processDirectory())
            .apply {
                val env = environment()
                if (config.apiKey.isNotBlank()) env["DEEPSEEK_API_KEY"] = config.apiKey
                if (config.baseUrl.isNotBlank()) env["DEEPSEEK_BASE_URL"] = config.baseUrl
                // The built-in `sdk` profile requires an explicit harness home.
                env["DSH_HOME"] = config.harnessHome
                env["DSH_PERMISSION_MODE"] = config.permissionMode
                env["DSH_TELEMETRY_DISABLED"] = "1"
                if (config.bridgeUrl.isNotBlank()) {
                    env["DSH_JB_BRIDGE_URL"] = config.bridgeUrl
                    env["DSH_JB_BRIDGE_TOKEN"] = config.bridgeToken
                }
            }
            .start()
        process = proc
        val p = JsonRpcPeer(
            StdioLineTransport(
                proc.inputStream.bufferedReader(Charsets.UTF_8),
                proc.outputStream.bufferedWriter(Charsets.UTF_8),
            ),
            mapper,
        )
        p.onNotification { method, params -> handleNotification(method, params) }
        p.start()
        peer = p
        // stderr is diagnostics; stdout is the protocol.
        scope.launch(Dispatchers.IO) {
            proc.errorStream.bufferedReader(Charsets.UTF_8).forEachLine { stderrSink(it) }
        }
        val resultNode = withTimeout(60_000) {
            p.request(
                "initialize",
                mapper.valueToTree<JsonNode>(
                    InitializeParams(
                        cwd = config.cwd,
                        provider = config.provider,
                        model = config.model,
                        reasoningEffort = config.reasoningEffort,
                        maxTokens = config.maxTokens,
                    ),
                ),
            )
        }
        val result = mapper.treeToValue(resultNode, InitializeResult::class.java)
        running = true
        return result
    }

    suspend fun prompt(text: String): SessionPromptResult {
        val p = peer ?: throw IllegalStateException("DSH runtime not started")
        val resultNode = withTimeout(60_000) {
            p.request(
                "session/prompt",
                mapper.valueToTree<JsonNode>(
                    SessionPromptParams(
                        sessionId = config.sessionId,
                        contentBlocks = listOf(textContentBlock(text, mapper)),
                    ),
                ),
            )
        }
        return mapper.treeToValue(resultNode, SessionPromptResult::class.java)
    }

    /** Protocol-level shutdown: answers `shutdown`, then waits for process exit. */
    suspend fun shutdown() {
        val p = peer ?: return
        try {
            withTimeout(30_000) {
                p.request("shutdown", null)
            }
        } catch (_: Exception) {
            // Timeout or dead transport; disposal below still reaches quiescence.
        }
        p.stop()
        val proc = process
        if (proc != null) {
            withContext(Dispatchers.IO) {
                if (!proc.waitFor(5, TimeUnit.SECONDS)) proc.destroyForcibly()
            }
        }
        running = false
        process = null
        peer = null
    }

    /**
     * Kilo-style hard stop (see kilocode's `killCliProcessTree`): SIGTERM to the
     * whole process tree, a short grace period, then a RE-ENUMERATED SIGKILL
     * escalation (a tool/shell can fork new descendants during the grace window).
     * Blocks up to ~2×grace on the calling thread — call from an IO coroutine.
     */
    fun interrupt() {
        val proc = process ?: return
        killTree(proc, wait = true, graceSeconds = 3)
    }

    /** Kill the runtime process tree; [wait] false = fire SIGTERM+SIGKILL and return. */
    private fun killTree(proc: Process, wait: Boolean, graceSeconds: Long) {
        val first = proc.toHandle().descendants().toList().asReversed()
        first.forEach { runCatching { it.destroy() } }
        runCatching { proc.destroy() }
        if (!wait) {
            first.forEach { runCatching { it.destroyForcibly() } }
            runCatching { proc.destroyForcibly() }
            return
        }
        val parentExited = proc.waitFor(graceSeconds, TimeUnit.SECONDS)
        val kids = (first + proc.toHandle().descendants().toList().asReversed()).distinctBy { it.pid() }
        if (parentExited && kids.none { it.isAlive }) return
        kids.forEach { runCatching { it.destroyForcibly() } }
        runCatching { proc.destroyForcibly() }
        proc.waitFor(graceSeconds, TimeUnit.SECONDS)
    }

    override fun close() {
        running = false
        val proc = process
        process = null
        peer = null
        // Kilo-style tree kill (no wait — dispose paths must not block): the direct
        // destroyForcibly() alone would orphan agent-spawned child processes.
        proc?.let { killTree(it, wait = false, graceSeconds = 3) }
        // Symlink-safe: never deleteRecursively() a tree that may contain
        // the runtime's profile-fallback symlinks (they point INTO the checkout).
        bridgeDir?.let { FsTree.deleteNoFollow(it) }
        bridgeDir = null
        scope.cancel()
    }

    private fun resolveCommand(stagedPatch: File?): List<String> = when (config.mode) {
        "node" -> {
            val node = config.nodeExecutable ?: "node"
            val checkout = File(config.checkoutPath)
            require(checkout.isDirectory) { "DSH_CHECKOUT is not a directory: ${config.checkoutPath}" }
            // Built entries first (self-contained bundles), then the dev source:
            // 1. the installed @deepseek-ai/dsh package bin, 2. the source checkout's
            // built CLI, 3. apps/cli/src/bin.ts through the checkout's tsx loader
            // (absolute file path — the runtime's cwd is the WORKSPACE, so a bare
            // tsx/esm import would not resolve there).
            val installedBin = File(checkout, "node_modules/@deepseek-ai/dsh/lib/bin.js")
            val builtCliBin = File(checkout, "apps/cli/lib/bin.js")
            val sourceBin = File(checkout, "apps/cli/src/bin.ts")
            val tsxLoader = File(checkout, "node_modules/tsx/dist/esm/index.mjs")
            val base = when {
                installedBin.isFile -> listOf(node, installedBin.path)
                builtCliBin.isFile -> listOf(node, builtCliBin.path)
                sourceBin.isFile && tsxLoader.isFile ->
                    listOf(node, "--import", tsxLoader.path, sourceBin.path)
                else -> throw IllegalStateException(
                    "no dsh CLI bin under checkout: ${checkout.path} " +
                        "(expected node_modules/@deepseek-ai/dsh/lib/bin.js, apps/cli/lib/bin.js, " +
                        "or apps/cli/src/bin.ts + tsx)",
                )
            }
            base + profileArgs(stagedPatch)
        }
        else -> {
            require(File(config.bundledExe).isFile) { "bundled runtime exe missing: ${config.bundledExe}" }
            listOf(config.bundledExe) + profileArgs(stagedPatch)
        }
    }

    /** `--profile sdk` plus the staged jb-bridge patch, when one exists. */
    private fun profileArgs(stagedPatch: File?): List<String> =
        buildList {
            add("--profile")
            add("sdk")
            if (stagedPatch != null) {
                add("--patch")
                add(stagedPatch.path)
            }
        }

    /**
     * Extracts the bundled jb-bridge resources (answerer.mjs + cordis.patch.yml)
     * into a per-runtime temp dir and returns the patch file. The patch row
     * resolves `./answerer.mjs` relative to the patch file, so the two must stay
     * together. Null when no bridge is configured.
     */
    private fun stageBridgePatch(): File? {
        if (config.bridgeUrl.isBlank()) return null
        val dir = kotlin.io.path.createTempDirectory("dsh-jb-bridge-").toFile()
        val answerer = File(dir, "answerer.mjs")
        val patch = File(dir, "cordis.patch.yml")
        val answererOk = javaClass.getResourceAsStream("/jb-bridge/answerer.mjs")
            ?.use { it.copyTo(answerer.outputStream()) } != null
        val patchOk = javaClass.getResourceAsStream("/jb-bridge/cordis.patch.yml")
            ?.use { it.copyTo(patch.outputStream()) } != null
        if (!answererOk || !patchOk) {
            dir.deleteRecursively()
            return null
        }
        bridgeDir = dir
        return patch
    }

    /**
     * The spawned runtime's working directory. ALWAYS the project workspace: the
     * 0.1.2 `sdk` profile reads its sandbox root from `process.cwd()`, and a
     * runtime whose cwd is the harness checkout can clean the checkout's own
     * fixtures (the 2026-08-29 worker-outage incident — see the session rules).
     */
    private fun processDirectory(): File = File(config.cwd)

    private fun handleNotification(method: String, params: JsonNode) {
        when (method) {
            "session.event" -> {
                val n = try {
                    mapper.treeToValue(params, SessionEventNotification::class.java)
                } catch (_: Exception) {
                    return
                }
                if (n.sessionId == config.sessionId) eventListeners.forEach { it(n) }
            }
            "session.status" -> {
                val n = try {
                    mapper.treeToValue(params, SessionStatusNotification::class.java)
                } catch (_: Exception) {
                    return
                }
                if (n.sessionId == config.sessionId) statusListeners.forEach { it(n) }
            }
            // subagent.started / subagent.finished are consumed by roadmap item 4's event model.
            else -> Unit
        }
    }
}
