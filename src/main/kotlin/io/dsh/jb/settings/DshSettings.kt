package io.dsh.jb.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.xmlb.XmlSerializerUtil
import io.dsh.jb.runtime.NodeResolver
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPasswordField

/**
 * Platform-free settings values used to resolve the runtime config (roadmap item 10,
 * pulled forward 2026-08-27). [DshSettingsState] maps to/from this snapshot; the
 * API key deliberately lives OUTSIDE this snapshot (OS keychain via [DshApiKey]).
 */
data class DshSettingsSnapshot(
    val mode: String = "node",
    val bundledExe: String = "",
    val checkoutPath: String = "",
    val baseUrl: String = "",
    val model: String = "deepseek-v4-flash",
    /** Reasoning effort wire id (off/low/high/max); owned by the composer Model tab. */
    val effort: String = "max",
    /** Sandbox permission mode (workspace-write | danger-full-access); item 9, default workspace-write. */
    val permissionMode: String = "workspace-write",
)

/**
 * The DeepSeek Harness API key, held in the OS keychain through the platform
 * [PasswordSafe]. The plugin never writes the key to disk.
 */
object DshApiKey {
    private const val SERVICE = "io.dsh.jb.deepseek-api-key"
    private val attributes = CredentialAttributes(SERVICE)

    fun get(): String? = PasswordSafe.instance.getPassword(attributes)

    fun set(value: String) {
        PasswordSafe.instance.setPassword(attributes, value)
    }

    fun clear() {
        PasswordSafe.instance.set(attributes, null)
    }
}

/**
 * Application-level persisted settings (Settings → Tools → DeepSeek Harness).
 * The API key is NOT persisted here — see [DshApiKey].
 *
 * Registered with the @Service annotation (no plugin.xml element): Android
 * Studio 2026.1.1 did not register the legacy XML `applicationService` entry,
 * while annotation-registered services (like the project-level
 * DshRuntimeService) resolve fine — smoke-tested 2026-08-27.
 */
@Service(Service.Level.APP)
@State(name = "DshSettings", storages = [Storage("dsh-settings.xml")])
class DshSettingsState : PersistentStateComponent<DshSettingsState> {

    var mode: String = "node"
    var bundledExe: String = ""
    var checkoutPath: String = ""
    var baseUrl: String = ""
    var model: String = "deepseek-v4-flash"
    var effort: String = "max"
    var permissionMode: String = "workspace-write"

    fun snapshot(): DshSettingsSnapshot =
        DshSettingsSnapshot(mode, bundledExe, checkoutPath, baseUrl, model, effort, permissionMode)

    override fun getState(): DshSettingsState = this

    override fun loadState(state: DshSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        @JvmStatic
        fun getInstance(): DshSettingsState =
            ApplicationManager.getApplication().getService(DshSettingsState::class.java)
    }
}

/**
 * The settings page: carrier toggle, checkout/bundled-exe path pickers, optional
 * base URL, model name, and the keychain-held API key.
 */
class DshSettingsConfigurable : Configurable {

    private val carrier = JComboBox(arrayOf("Node checkout", "Bundled executable"))
    private val checkoutPath = TextFieldWithBrowseButton()
    private val bundledExe = TextFieldWithBrowseButton()
    private val bundledHint = JBLabel(
        "<html>Single-file executable built from the DeepSeek Harness repo — " +
            "dsh-jsonrpc-agent-pkg-&lt;platform&gt;-&lt;arch&gt; (Node SEA binary).<br>" +
            "Ships bundled with the plugin in roadmap item 12; leave blank until then.</html>",
    ).apply { foreground = JBColor.GRAY }
    private val nodeStatus = JBLabel("Node.js: checking…").apply { foreground = JBColor.GRAY }
    private val baseUrl = JBTextField()
    private val model = JBTextField()
    private val permissionMode = JComboBox(arrayOf("workspace-write", "danger-full-access"))
    private val permissionHint = JBLabel(
        "<html>workspace-write: writes and shell commands outside the project ask for approval first (item 9).<br>" +
            "danger-full-access: no approvals — the agent can modify anything.",
    ).apply { foreground = JBColor.GRAY }
    private val apiKey = JPasswordField()
    private val keyHint = JBLabel("Leave blank in checkout mode to use the checkout's own .env").apply {
        foreground = JBColor.GRAY
    }
    private val clearKey = JButton("Clear stored key")
    private var clearRequested = false

    init {
        checkoutPath.addBrowseFolderListener(
            "DeepSeek Harness checkout",
            "Select the deepseek-harness checkout directory",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        )
        bundledExe.addBrowseFolderListener(
            "DeepSeek Harness bundled runtime",
            "Select the dsh-jsonrpc-agent-pkg executable",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor(),
        )
        clearKey.addActionListener { clearRequested = true }
    }

    override fun getDisplayName(): String = "DeepSeek Harness"

    override fun createComponent(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(4, 8)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }
        fun addRow(y: Int, label: String, field: JComponent) {
            gbc.gridy = y
            gbc.gridx = 0
            gbc.weightx = 0.0
            panel.add(JBLabel(label), gbc)
            gbc.gridx = 1
            gbc.weightx = 1.0
            panel.add(field, gbc)
        }
        addRow(0, "Runtime carrier:", carrier)
        addRow(1, "Harness checkout (node):", checkoutPath)
        addRow(2, "Bundled executable:", bundledExe)
        addRow(3, "", bundledHint)
        addRow(4, "Base URL (optional):", baseUrl)
        addRow(5, "Model:", model)
        addRow(6, "API key (stored in the OS keychain):", apiKey)
        addRow(7, "", keyHint)
        addRow(8, "", clearKey)
        addRow(9, "Permission mode:", permissionMode)
        addRow(10, "", permissionHint)
        addRow(11, "", nodeStatus)
        refreshNodeStatus()
        return panel
    }

    /** Resolves node off the EDT and updates the status label with version + path. */
    private fun refreshNodeStatus() {
        nodeStatus.text = "Node.js: checking…"
        Thread {
            val info = NodeResolver.resolve()
            ApplicationManager.getApplication().invokeLater {
                nodeStatus.text = info?.let {
                    "Node.js: ${it.version} (${it.path}) ✓ (required for the checkout carrier)"
                } ?: "Node.js: not found — required for the checkout carrier"
            }
        }.start()
    }

    override fun isModified(): Boolean {
        val s = DshSettingsState.getInstance().snapshot()
        return clearRequested ||
            modeFromUi() != s.mode ||
            checkoutPath.text.trim() != s.checkoutPath ||
            bundledExe.text.trim() != s.bundledExe ||
            baseUrl.text.trim() != s.baseUrl ||
            model.text.trim() != s.model ||
            permissionMode.selectedItem as? String != s.permissionMode ||
            apiKey.password.isNotEmpty()
    }

    override fun apply() {
        val s = DshSettingsState.getInstance()
        s.mode = modeFromUi()
        s.checkoutPath = checkoutPath.text.trim()
        s.bundledExe = bundledExe.text.trim()
        s.baseUrl = baseUrl.text.trim()
        s.model = model.text.trim().ifBlank { "deepseek-chat" }
        s.permissionMode = permissionMode.selectedItem as? String ?: "workspace-write"
        if (clearRequested) {
            DshApiKey.clear()
        } else {
            val entered = String(apiKey.password).trim()
            if (entered.isNotEmpty()) DshApiKey.set(entered)
        }
        clearRequested = false
    }

    override fun reset() {
        val s = DshSettingsState.getInstance().snapshot()
        carrier.selectedIndex = if (s.mode == "bundled") 1 else 0
        checkoutPath.text = s.checkoutPath
        bundledExe.text = s.bundledExe
        baseUrl.text = s.baseUrl
        model.text = s.model
        permissionMode.selectedItem = s.permissionMode
        apiKey.text = ""
        clearRequested = false
    }

    private fun modeFromUi(): String = if (carrier.selectedIndex == 1) "bundled" else "node"
}
