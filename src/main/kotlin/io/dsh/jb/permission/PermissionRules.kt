package io.dsh.jb.permission

/** Item 24 (replanned): Kilo's Allow/Ask/Deny levels. */
enum class PermissionLevel(val wire: String) {
    ALLOW("allow"),
    ASK("ask"),
    DENY("deny"),
}

data class ToolLevel(val tool: String, val level: PermissionLevel)

data class PatternRule(val tool: String, val pattern: String, val level: PermissionLevel)

/**
 * Item 24 (replanned): pure permission rule engine — per-tool levels with
 * pattern exceptions (Kilo's PermissionRuleDto model). Longest matching
 * pattern wins; "*" matches everything; a trailing "*" is a prefix match;
 * for bash the pattern matches the COMMAND text, otherwise the tool name.
 * Default is ASK. Fully pure for the JVM suite.
 */
class PermissionRules(
    val levels: List<ToolLevel> = emptyList(),
    val patterns: List<PatternRule> = emptyList(),
) {
    fun decide(tool: String, command: String?): PermissionLevel {
        val target = command ?: tool
        val match = patterns
            .filter { it.tool == tool }
            .filter { matches(it.pattern, target) }
            .maxByOrNull { it.pattern.length }
        if (match != null) return match.level
        return levels.firstOrNull { it.tool == tool }?.level ?: PermissionLevel.ASK
    }

    fun withToolLevel(tool: String, level: PermissionLevel): PermissionRules =
        PermissionRules(
            levels.filter { it.tool != tool } + ToolLevel(tool, level),
            patterns,
        )

    fun withPattern(rule: PatternRule): PermissionRules =
        PermissionRules(
            levels,
            patterns.filterNot { it.tool == rule.tool && it.pattern == rule.pattern } + rule,
        )

    fun withoutPattern(tool: String, pattern: String): PermissionRules =
        PermissionRules(levels, patterns.filterNot { it.tool == tool && it.pattern == pattern })

    companion object {
        fun matches(pattern: String, target: String): Boolean {
            val p = pattern.trim()
            return when {
                p == "*" -> true
                p.endsWith("*") -> target.startsWith(p.dropLast(1))
                else -> target == p
            }
        }
    }
}
