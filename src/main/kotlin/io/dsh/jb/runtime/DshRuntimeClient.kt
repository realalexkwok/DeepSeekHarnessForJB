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

/**
 * Configuration for one DSH runtime process. Defaults read environment
 * variables (roadmap item 3's agreed config source); tests inject values.
 */
data class DshRuntimeConfig(
    /** `bundled` (default) uses the single-file exe; `node` uses a DSH checkout. */
    val mode: String = System.getenv("DSH_RUNTIME_MODE") ?: "bundled",
    /** Path to the bundled `dsh-jsonrpc-agent-pkg-<platform>-<arch>` executable. */
    val bundledExe: String = System.getenv("DSH_RUNTIME_EXE") ?: "",
    /** Path to an installed DSH checkout (node carrier). */
    val checkoutPath: String = System.getenv("DSH_CHECKOUT") ?: "",
    /** cordis.yml path handed to the runtime via `DSH_CORDIS_CONFIG`. */
    val cordisConfig: String = System.getenv("DSH_CORDIS_CONFIG") ?: "",
    val apiKey: String = System.getenv("DEEPSEEK_API_KEY") ?: "",
    val baseUrl: String = System.getenv("DEEPSEEK_BASE_URL") ?: "",
    val provider: String = "deepseek-official",
    val model: String = System.getenv("DSH_MODEL") ?: "deepseek-chat",
    /** Agent workspace (becomes `DSH_CWD`); default: current dir. */
    val cwd: String = System.getProperty("user.dir"),
    /** JSONL session root (`DSH_SESSION_ROOT`); blank defaults to `<cwd>/.dsh-sessions`. */
    val sessionRoot: String = "",
    /** Stable session id used for every prompt. */
    val sessionId: String,
    val maxTokens: Int? = null,
)

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
            val checkout = File(config.checkoutPath)
            require(checkout.isDirectory) { "DSH_CHECKOUT is not a directory: ${config.checkoutPath}" }
            val built = File(checkout, "packages/examples/jsonrpc-demo/lib/bin.js")
            val source = File(checkout, "packages/examples/jsonrpc-demo/src/bin.ts")
            when {
                built.isFile -> listOf("node", built.path)
                // Development checkout: run the source bin through the checkout's tsx loader.
                source.isFile -> listOf("node", "--import", "tsx", source.path)
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
