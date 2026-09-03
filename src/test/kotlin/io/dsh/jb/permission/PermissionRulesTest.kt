package io.dsh.jb.permission

import org.junit.Assert.assertEquals
import org.junit.Test

/** Roadmap item 24 (replanned): pure rule-engine decisions. */
class PermissionRulesTest {

    @Test
    fun defaultIsAsk() {
        assertEquals(PermissionLevel.ASK, PermissionRules().decide("bash", "ls"))
    }

    @Test
    fun toolLevelAppliesWithoutPatterns() {
        val rules = PermissionRules(levels = listOf(ToolLevel("bash", PermissionLevel.DENY)))
        assertEquals(PermissionLevel.DENY, rules.decide("bash", "ls"))
        assertEquals(PermissionLevel.ASK, rules.decide("write", null))
    }

    @Test
    fun longestMatchingPatternWins() {
        val rules = PermissionRules(
            patterns = listOf(
                PatternRule("bash", "*", PermissionLevel.ALLOW),
                PatternRule("bash", "git *", PermissionLevel.DENY),
            ),
        )
        assertEquals(PermissionLevel.DENY, rules.decide("bash", "git commit"))
        assertEquals(PermissionLevel.ALLOW, rules.decide("bash", "ls -la"))
    }

    @Test
    fun prefixAndExactMatching() {
        assertEquals(true, PermissionRules.matches("git *", "git status"))
        assertEquals(false, PermissionRules.matches("git *", "npm run"))
        assertEquals(true, PermissionRules.matches("ls", "ls"))
        assertEquals(false, PermissionRules.matches("ls", "ls -la"))
    }

    @Test
    fun withToolLevelAndPatternsCompose() {
        val rules = PermissionRules()
            .withToolLevel("bash", PermissionLevel.ALLOW)
            .withPattern(PatternRule("bash", "rm *", PermissionLevel.DENY))
            .withoutPattern("bash", "rm *")
        assertEquals(PermissionLevel.ALLOW, rules.decide("bash", "rm -rf ."))
    }
}
