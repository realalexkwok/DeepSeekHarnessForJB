package io.dsh.jb.settings

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.util.PropertiesComponent
import io.dsh.jb.permission.PatternRule
import io.dsh.jb.permission.PermissionLevel
import io.dsh.jb.permission.PermissionRules
import io.dsh.jb.permission.ToolLevel

/**
 * Item 24 (replanned): persistence for the permission management state —
 * the binary auto-approve flag and the rule set (levels + patterns) as one
 * JSON blob, mirroring Kilo's PropertiesComponent key pattern.
 */
object PermissionSettings {

    private const val AUTO_APPROVE_KEY = "dsh.permission.autoApprove"
    private const val RULES_KEY = "dsh.permission.rules"

    private val mapper = jacksonObjectMapper()

    fun getAutoApprove(): Boolean =
        PropertiesComponent.getInstance().getBoolean(AUTO_APPROVE_KEY, false)

    fun setAutoApprove(value: Boolean) {
        PropertiesComponent.getInstance().setValue(AUTO_APPROVE_KEY, value.toString())
    }

    fun loadRules(): PermissionRules {
        val raw = PropertiesComponent.getInstance().getValue(RULES_KEY) ?: return PermissionRules()
        return runCatching {
            val node = mapper.readTree(raw)
            val levels = node.path("levels").mapNotNull { l ->
                val tool = l.path("tool").takeIf { it.isTextual }?.asText() ?: return@mapNotNull null
                val level = l.path("level").takeIf { it.isTextual }?.asText()
                    ?.let { PermissionLevel.entries.firstOrNull { e -> e.wire == it } }
                    ?: return@mapNotNull null
                ToolLevel(tool, level)
            }
            val patterns = node.path("patterns").mapNotNull { p ->
                val tool = p.path("tool").takeIf { it.isTextual }?.asText() ?: return@mapNotNull null
                val pattern = p.path("pattern").takeIf { it.isTextual }?.asText() ?: return@mapNotNull null
                val level = p.path("level").takeIf { it.isTextual }?.asText()
                    ?.let { PermissionLevel.entries.firstOrNull { e -> e.wire == it } }
                    ?: return@mapNotNull null
                PatternRule(tool, pattern, level)
            }
            PermissionRules(levels, patterns)
        }.getOrDefault(PermissionRules())
    }

    fun saveRules(rules: PermissionRules) {
        val root = mapper.createObjectNode()
        val levelsArr = root.putArray("levels")
        for (l in rules.levels) {
            val o = levelsArr.addObject()
            o.put("tool", l.tool)
            o.put("level", l.level.wire)
        }
        val patternsArr = root.putArray("patterns")
        for (p in rules.patterns) {
            val o = patternsArr.addObject()
            o.put("tool", p.tool)
            o.put("pattern", p.pattern)
            o.put("level", p.level.wire)
        }
        PropertiesComponent.getInstance().setValue(RULES_KEY, mapper.writeValueAsString(root))
    }
}
