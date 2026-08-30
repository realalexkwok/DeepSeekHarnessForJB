package io.dsh.jb.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.dsh.jb.protocol.InitializeResult
import io.dsh.jb.protocol.SessionEventNotification
import io.dsh.jb.protocol.SessionPromptResult
import io.dsh.jb.protocol.SessionStatusNotification
import io.dsh.jb.runtime.DshConfigException
import io.dsh.jb.runtime.DshRuntimeClient
import io.dsh.jb.runtime.DshRuntimeConfig
import io.dsh.jb.runtime.NodeResolver
import io.dsh.jb.runtime.RuntimeKey
import io.dsh.jb.runtime.buildSessionId
import io.dsh.jb.runtime.newSessionNonce
import io.dsh.jb.settings.DshApiKey
import io.dsh.jb.settings.DshSettingsState
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-scoped owner of the embedded DSH runtime (roadmap item 3), reworked
 * for the (model, effort) runtime pool (roadmap item 6/10/11 pulled forward,
 * 2026-08-27): ONE live process per project, keyed by [RuntimeKey]. Since the
 * dsh-v0.1.2-alpha.1 adaptation (2026-08-28), effort rides the
 * `initialize.reasoningEffort` wire field against the built-in `sdk` profile —
 * no cordis patching. Switching the key closes the previous runtime; the next
 * start/prompt spawns the new one (a restart per the agreed UX).
 */
@Service(Service.Level.PROJECT)
class DshRuntimeService(private val project: Project) : Disposable {

    private val logger = Logger.getInstance(DshRuntimeService::class.java)

    @Volatile
    private var client: DshRuntimeClient? = null

    @Volatile
    private var activeKey: RuntimeKey? = null

    @Volatile
    private var initResult: InitializeResult? = null

    @Volatile
    private var started = false

    /** Item 8: localhost bridge answering plan reviews and relaying commands. */
    @Volatile
    private var bridge: io.dsh.jb.bridge.BridgeServer? = null

    /** Rotated on every runtime start: per-runtime session ids avoid persisted-log collisions. */
    @Volatile
    private var runtimeNonce: String = newSessionNonce()

    // Listeners may register before the runtime starts (the chat panel subscribes
    // on construction); they re-attach to every spawned client.
    private val eventListeners = CopyOnWriteArrayList<(SessionEventNotification) -> Unit>()
    private val statusListeners = CopyOnWriteArrayList<(SessionStatusNotification) -> Unit>()

    fun isRunning(): Boolean = started

    fun activeKey(): RuntimeKey? = activeKey

    /** One session id per pool key AND runtime nonce: \"jb-<hash>-<effort>-<model>-<nonce>\". */
    fun sessionIdFor(key: RuntimeKey): String =
        buildSessionId(project.locationHash, key, runtimeNonce)

    /**
     * Ensures a runtime for [key] is live: closes the previous one when the key
     * changed (restart semantics), spawns with the per-effort cordis and the key's
     * model, and returns the initialize result. Configuration problems throw
     * [DshConfigException] for the chat panel's proactive ask.
     */
    suspend fun startFor(key: RuntimeKey): InitializeResult {
        if (started && key == activeKey && initResult != null) return initResult!!
        runtimeNonce = newSessionNonce()
        val old = client
        client = null
        started = false
        initResult = null
        activeKey = null
        if (old != null) {
            try {
                old.shutdown()
            } catch (_: Exception) {
                old.close()
            }
        }

        val snapshot = DshSettingsState.getInstance().snapshot()
        val nodeExecutable = if (snapshot.mode.ifBlank { "node" } == "node") {
            NodeResolver.resolve()?.path
        } else null
        val provisional = DshRuntimeConfig.fromSettings(
            settings = snapshot,
            apiKey = DshApiKey.get(),
            sessionId = sessionIdFor(key),
            cwd = project.basePath ?: System.getProperty("user.dir"),
            nodeExecutable = nodeExecutable,
        )
        provisional.validateForStart()?.let { throw DshConfigException(it) }
        // Effort rides the initialize wire field against the built-in sdk profile.
        // Item 8: a fresh bridge (random port + token) answers this runtime's
        // plan reviews and relays plan-mode commands.
        closeBridge()
        val newBridge = io.dsh.jb.bridge.BridgeServer(onQuestions = ::answerPlanQuestions).also { it.start() }
        val cfg = provisional.copy(
            reasoningEffort = key.effort.wire,
            bridgeUrl = newBridge.url,
            bridgeToken = newBridge.token,
        )
        val c = DshRuntimeClient(cfg) { line -> logger.warn("[dsh-runtime] $line") }
        try {
            eventListeners.forEach(c::addEventListener)
            statusListeners.forEach(c::addStatusListener)
            val result = c.start()
            bridge = newBridge
            client = c
            activeKey = key
            initResult = result
            started = true
            return result
        } catch (e: Exception) {
            newBridge.close()
            throw e
        }
    }

    /**
     * Item 8: shows the plan-review dialog for each forwarded question and maps
     * the decision to the harness answer shape. A dismissed dialog fails safe
     * to "keep planning" (the runtime answerer falls through otherwise, which
     * plan-mode reports as an unavailable channel).
     */
    private fun answerPlanQuestions(questions: List<io.dsh.jb.bridge.PlanQuestion>): List<io.dsh.jb.bridge.PlanAnswer> =
        questions.map { question ->
            val answer = io.dsh.jb.ui.PlanReviewDialog.ask(question)
            if (answer != null) {
                answer
            } else {
                val keep = question.options.firstOrNull { it != question.approveLabel }.orEmpty()
                io.dsh.jb.bridge.PlanAnswer(question.id, listOf(keep), null)
            }
        }

    /** Item 8: queues a command (e.g. `/plan`) for the runtime-side relay. */
    fun enqueueCommand(line: String) {
        bridge?.enqueueCommand(line)
    }

    private fun closeBridge() {
        bridge?.close()
        bridge = null
    }

    fun addEventListener(listener: (SessionEventNotification) -> Unit) {
        eventListeners += listener
        client?.addEventListener(listener)
    }

    fun addStatusListener(listener: (SessionStatusNotification) -> Unit) {
        statusListeners += listener
        client?.addStatusListener(listener)
    }

    suspend fun prompt(text: String): SessionPromptResult {
        val c = client ?: throw IllegalStateException("DSH runtime not started")
        return c.prompt(text)
    }

    suspend fun shutdown() {
        val c = client ?: return
        c.shutdown()
        client = null
        activeKey = null
        initResult = null
        started = false
        closeBridge()
    }

    /**
     * Kilo-style hard stop (Stop button): kills the runtime process tree
     * (SIGTERM → grace → SIGKILL) instead of the graceful JSON-RPC shutdown,
     * which can block while a turn is mid-tool-execution. The next prompt
     * restarts the runtime lazily.
     */
    fun interrupt() {
        val c = client ?: return
        c.interrupt()
        client = null
        activeKey = null
        initResult = null
        started = false
        closeBridge()
    }

    override fun dispose() {
        client?.close()
        client = null
        activeKey = null
        initResult = null
        started = false
        closeBridge()
    }

    companion object {
        fun getInstance(project: Project): DshRuntimeService =
            project.getService(DshRuntimeService::class.java)
    }
}
