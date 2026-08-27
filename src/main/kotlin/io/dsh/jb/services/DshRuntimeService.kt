package io.dsh.jb.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.dsh.jb.protocol.InitializeResult
import io.dsh.jb.protocol.SessionEventNotification
import io.dsh.jb.protocol.SessionPromptResult
import io.dsh.jb.protocol.SessionStatusNotification
import io.dsh.jb.runtime.CordisEffort
import io.dsh.jb.runtime.DshConfigException
import io.dsh.jb.runtime.DshRuntimeClient
import io.dsh.jb.runtime.DshRuntimeConfig
import io.dsh.jb.runtime.EffortLevel
import io.dsh.jb.runtime.NodeResolver
import io.dsh.jb.runtime.RuntimeKey
import io.dsh.jb.settings.DshApiKey
import io.dsh.jb.settings.DshSettingsState
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-scoped owner of the embedded DSH runtime (roadmap item 3), reworked
 * for the (model, effort) runtime pool (roadmap item 6/10/11 pulled forward,
 * 2026-08-27): ONE live process per project, keyed by [RuntimeKey]. Effort is
 * realized without protocol changes — the bundled agent composition is patched
 * per effort level and handed to the process via DSH_CORDIS_CONFIG; the model is
 * pinned at initialize. Switching the key closes the previous runtime; the next
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

    // Listeners may register before the runtime starts (the chat panel subscribes
    // on construction); they re-attach to every spawned client.
    private val eventListeners = CopyOnWriteArrayList<(SessionEventNotification) -> Unit>()
    private val statusListeners = CopyOnWriteArrayList<(SessionStatusNotification) -> Unit>()

    fun isRunning(): Boolean = started

    fun activeKey(): RuntimeKey? = activeKey

    /** One stable session id per pool key: \"jb-<locationHash>-<effort>-<model>\". */
    fun sessionIdFor(key: RuntimeKey): String =
        "jb-" + project.locationHash + "-" + key.effort.wire + "-" +
            key.model.replace(Regex("[^A-Za-z0-9._-]"), "-")

    /**
     * Ensures a runtime for [key] is live: closes the previous one when the key
     * changed (restart semantics), spawns with the per-effort cordis and the key's
     * model, and returns the initialize result. Configuration problems throw
     * [DshConfigException] for the chat panel's proactive ask.
     */
    suspend fun startFor(key: RuntimeKey): InitializeResult {
        if (started && key == activeKey && initResult != null) return initResult!!
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
        // Validate carrier basics BEFORE generating the cordis file (the file needs
        // a resolvable location — see CordisEffort.directoryFor).
        val provisional = DshRuntimeConfig.fromSettings(
            settings = snapshot,
            apiKey = DshApiKey.get(),
            sessionId = sessionIdFor(key),
            cwd = project.basePath ?: System.getProperty("user.dir"),
            nodeExecutable = nodeExecutable,
        )
        provisional.validateForStart()?.let { throw DshConfigException(it) }
        val cfg = provisional.copy(cordisConfig = writeEffortCordis(key.effort, snapshot))
        val c = DshRuntimeClient(cfg) { line -> logger.warn("[dsh-runtime] $line") }
        eventListeners.forEach(c::addEventListener)
        statusListeners.forEach(c::addStatusListener)
        val result = c.start()
        client = c
        activeKey = key
        initResult = result
        started = true
        return result
    }

    /**
     * Writes the bundled agent composition patched for [effort] and returns its
     * path. The file lives under <checkout>/.dsh-jb (node mode) or next to the
     * bundled exe — NOT in the temp dir: the harness resolves bare plugin packages
     * by walking up from the config file's directory (fix round 4, 2026-08-27).
     */
    private fun writeEffortCordis(effort: EffortLevel, snapshot: io.dsh.jb.settings.DshSettingsSnapshot): String {
        val base = javaClass.getResourceAsStream("/agent.cordis.yml")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("bundled agent.cordis.yml resource is missing")
        val patched = CordisEffort.apply(base, effort)
        val dir = CordisEffort.directoryFor(snapshot.mode, snapshot.checkoutPath, snapshot.bundledExe)
        if (dir == null || !dir.isDirectory) {
            throw DshConfigException(
                "The harness checkout is missing its canonical SDK config directory " +
                    "(expected <checkout>/examples/jsonrpc-agent)",
            )
        }
        dir.mkdirs()
        val file = File(dir, "agent-effort-${effort.wire}.yml")
        file.writeText(patched)
        return file.path
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
    }

    override fun dispose() {
        client?.close()
        client = null
        activeKey = null
        initResult = null
        started = false
    }

    companion object {
        fun getInstance(project: Project): DshRuntimeService =
            project.getService(DshRuntimeService::class.java)
    }
}
