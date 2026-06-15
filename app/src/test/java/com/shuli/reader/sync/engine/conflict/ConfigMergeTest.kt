package com.shuli.reader.sync.engine.conflict

import org.junit.Assert.assertEquals
import org.junit.Test

// Part of T-20 Config key-level merge
class ConfigMergeTest {

    @Test
    fun `dirty key uses local value, non-dirty key uses remote value`() {
        val local = UserPreferences(fontSize = 18f, themeMode = "dark", lineSpacing = 1.5f)
        val remote = UserPreferences(fontSize = 14f, themeMode = "light", lineSpacing = 2.0f)
        val dirtyKeys = setOf("fontSize") // 只改了字号
        val merged = ConflictResolver.mergePreferences(local, remote, dirtyKeys)
        assertEquals(18f, merged.fontSize, 0.001f)       // local 胜出（dirty）
        assertEquals("light", merged.themeMode)           // remote 胜出（not dirty）
        assertEquals(2.0f, merged.lineSpacing, 0.001f)    // remote 胜出
    }

    @Test
    fun `empty dirtyKeys uses all remote values`() {
        val local = UserPreferences(fontSize = 18f, themeMode = "dark", lineSpacing = 1.5f)
        val remote = UserPreferences(fontSize = 14f, themeMode = "light", lineSpacing = 2.0f)
        val merged = ConflictResolver.mergePreferences(local, remote, emptySet())
        assertEquals(14f, merged.fontSize, 0.001f)
        assertEquals("light", merged.themeMode)
    }
}
