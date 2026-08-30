package io.dsh.jb.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.dsh.jb.chat.ComposerAction
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-scoped bus between the editor context actions (roadmap item 11
 * remainder) and the chat composer: an action submits (composer action, prompt
 * text); the chat panel applies them on the EDT.
 */
@Service(Service.Level.PROJECT)
class ComposerRequests {

    private val listeners = CopyOnWriteArrayList<(ComposerAction, String) -> Unit>()

    fun submit(action: ComposerAction, text: String) {
        listeners.forEach { it(action, text) }
    }

    fun addListener(listener: (ComposerAction, String) -> Unit) {
        listeners += listener
    }
}
