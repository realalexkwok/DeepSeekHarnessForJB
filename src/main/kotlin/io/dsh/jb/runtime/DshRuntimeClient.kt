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
    /** cordis.yml path handed to the runtime via `DSH_CORDIS_CONFIG` (item 12 supplies one). */
    val cordisConfig: String = "",
    val apiKey: String = "",
    val baseUrl: String = "",
    val provider: String = "deepseek-official",
    val model: String = "deepseek-chat",
    /** Agent workspace (becomes `DSH_CWD`); default: current dir. */
    val cwd: String = System.getProperty("user.dir"),
    /** JSONL session root (`DSH_SESSION_ROOT`); blank defaults to `<cwd>/.dsh-sessions`. */
    val sessionRoot: String = "",
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
            cordisConfig: String = "",
            nodeExecutable: String? = null,
        ): DshRuntimeConfig = DshRuntimeConfig(
            mode = settings.mode.ifBlank { "node" },
            bundledExe = settings.bundledExe.trim(),
            checkoutPath = settings.checkoutPath.trim(),
            nodeExecutable = nodeExecutable,
            cordisConfig = cordisConfig,
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

    fun addEventListener(listener: (SessionEventNotification) -> Unit) {
        eventListeners += listener
    }

    fun addStatusListener(listener: (SessionStatusNotification) -> Unit) {
        statusListeners += listener
    }

    fun isRunning(): Boolean = running

    suspend fun start(): InitializeResult {
        check(!running) { "DSH runtime already running" }
        val proc = ProcessBuilder(resolveCommand())
            .directory(processDirectory())
            .apply {
                val env = environment()
                if (config.apiKey.isNotBlank()) env["DEEPSEEK_API_KEY"] = config.apiKey
                if (config.baseUrl.isNotBlank()) env["DEEPSEEK_BASE_URL"] = config.baseUrl
                env["DSH_CWD"] = config.cwd
                env["DSH_SESSION_ROOT"] = config.sessionRoot.ifBlank { File(config.cwd, ".dsh-sessions").path }
                if (config.cordisConfig.isNotBlank()) env["DSH_CORDIS_CONFIG"] = config.cordisConfig
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

    override fun close() {
        running = false
        val proc = process
        process = null
        peer = null
        proc?.destroyForcibly()
        scope.cancel()
    }

    private fun resolveCommand(): List<String> = when (config.mode) {
        "node" -> {
            val node = config.nodeExecutable ?: "node"
            val checkout = File(config.checkoutPath)
            require(checkout.isDirectory) { "DSH_CHECKOUT is not a directory: ${config.checkoutPath}" }
            val built = File(checkout, "packages/examples/jsonrpc-demo/lib/bin.js")
            val source = File(checkout, "packages/examples/jsonrpc-demo/src/bin.ts")
            when {
                built.isFile -> listOf(node, built.path)
                // Development checkout: run the source bin through the checkout's tsx loader.
                source.isFile -> listOf(node, "--import", "tsx", source.path)
                else -> throw IllegalStateException("no jsonrpc-demo bin under checkout: ${checkout.path}")
            }
        }
        else -> {
            require(File(config.bundledExe).isFile) { "bundled runtime exe missing: ${config.bundledExe}" }
            listOf(config.bundledExe)
        }
    }

    private fun processDirectory(): File = when (config.mode) {
        // The node carrier resolves `tsx` and bare plugin specifiers from the checkout.
        "node" -> File(config.checkoutPath)
        else -> File(config.cwd)
    }

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
