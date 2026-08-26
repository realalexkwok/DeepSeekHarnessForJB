package io.dsh.jb.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.dsh.jb.protocol.InitializeResult
import io.dsh.jb.protocol.SessionEventNotification
import io.dsh.jb.protocol.SessionPromptResult
import io.dsh.jb.protocol.SessionStatusNotification
import io.dsh.jb.runtime.DshRuntimeClient
import io.dsh.jb.runtime.DshRuntimeConfig

/**
 * Roadmap item 3: project-scoped owner of the embedded DSH runtime process.
 * The UI (item 5) and settings page (item 10) build on top of this service.
 */
@Service(Service.Level.PROJECT)
class DshRuntimeService(private val project: Project) : Disposable {

    private val logger = Logger.getInstance(DshRuntimeService::class.java)

    @Volatile
    private var client: DshRuntimeClient? = null

    @Volatile
    private var started = false

    fun isRunning(): Boolean = started

    fun sessionId(): String = "jb-" + project.locationHash

    suspend fun start(): InitializeResult {
        check(!started) { "DSH runtime already started" }
        val cfg = DshRuntimeConfig(
            sessionId = sessionId(),
            cwd = project.basePath ?: System.getProperty("user.dir"),
        )
        val c = DshRuntimeClient(cfg) { line -> logger.warn("[dsh-runtime] $line") }
        val result = c.start()
        client = c
        started = true
        return result
    }

    fun addEventListener(listener: (SessionEventNotification) -> Unit) {
        client?.addEventListener(listener)
    }

    fun addStatusListener(listener: (SessionStatusNotification) -> Unit) {
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
        started = false
    }

    override fun dispose() {
        client?.close()
        client = null
        started = false
    }

    companion object {
        fun getInstance(project: Project): DshRuntimeService =
            project.getService(DshRuntimeService::class.java)
    }
}
