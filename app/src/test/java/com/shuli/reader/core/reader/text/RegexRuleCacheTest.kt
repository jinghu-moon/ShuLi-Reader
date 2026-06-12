package com.shuli.reader.core.reader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexRuleCacheTest {

    // T-3.4.5: 缓存预编译正则
    @Test
    fun cache_compilesEnabledRules() {
        val rules = listOf(
            RegexRule("foo", "bar", enabled = true),
            RegexRule("baz", "qux", enabled = true),
            RegexRule("disabled", "no", enabled = false),
        )
        val cache = RegexRuleCache(rules)
        assertEquals(2, cache.compiled.size)
    }

    @Test
    fun cache_emptyRules_emptyCompiled() {
        val cache = RegexRuleCache(emptyList())
        assertTrue(cache.compiled.isEmpty())
    }

    // T-3.4.6: 规则变化时重建缓存
    @Test
    fun cache_newRulesList_newCompiled() {
        val rules1 = listOf(RegexRule("a", "b", enabled = true))
        val cache1 = RegexRuleCache(rules1)
        assertEquals(1, cache1.compiled.size)

        val rules2 = listOf(
            RegexRule("a", "b", enabled = true),
            RegexRule("c", "d", enabled = true),
        )
        val cache2 = RegexRuleCache(rules2)
        assertEquals(2, cache2.compiled.size)
    }

    // 无效正则在缓存中被跳过
    @Test
    fun cache_invalidRegex_skipped() {
        val rules = listOf(
            RegexRule("[invalid", "bar", enabled = true),
            RegexRule("valid", "replaced", enabled = true),
        )
        val cache = RegexRuleCache(rules)
        assertEquals(1, cache.compiled.size)
        assertEquals("valid", cache.compiled[0].regex.pattern)
    }
}
