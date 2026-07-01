package com.shuli.reader.feature.reader.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTabTest {

    @Test
    fun settingsTab_hasFourValues() {
        val names = SettingsTab.entries.map { it.name }.toSet()
        assertEquals(setOf("TYPESETTING", "LAYOUT", "PAGE_TURN", "AUXILIARY"), names)
    }

    @Test
    fun eachTab_hasNonEmptyGroups() {
        SettingsTab.entries.forEach { tab ->
            assertTrue("${tab.name}.groups should not be empty", tab.groups.isNotEmpty())
        }
    }

    @Test
    fun allUiGroups_areAssignedToSomeTab() {
        assertTrue("All UiGroup entries must appear in some SettingsTab", SettingsTab.allGroupsCovered)
    }

    @Test
    fun noUiGroupIsAssignedTwice() {
        val allGroups = SettingsTab.entries.flatMap { it.groups }
        assertEquals("UiGroup must appear exactly once across all tabs", allGroups.distinct().size, allGroups.size)
    }

    @Test
    fun typesetting_groupsMatch() {
        assertEquals(
            listOf(
                UiGroup.FONT_BASICS,
                UiGroup.TEXT_LAYOUT,
                UiGroup.TEXT_STYLE,
                UiGroup.ADVANCED_READING,
            ),
            SettingsTab.TYPESETTING.groups,
        )
    }

    @Test
    fun layout_groupsMatch() {
        assertEquals(
            listOf(
                UiGroup.PAGE_CHROME,
                UiGroup.PAGE_CONTENT,
            ),
            SettingsTab.LAYOUT.groups,
        )
    }

    @Test
    fun pageTurn_groupsMatch() {
        assertEquals(
            listOf(
                UiGroup.PAGE_TURN,
                UiGroup.GESTURE,
            ),
            SettingsTab.PAGE_TURN.groups,
        )
    }

    @Test
    fun auxiliary_groupsMatch() {
        assertEquals(
            listOf(
                UiGroup.EYE_CARE,
                UiGroup.GENERAL,
                UiGroup.DISPLAY_MODE,
                UiGroup.VISUAL_AIDS,
                UiGroup.THEME,
            ),
            SettingsTab.AUXILIARY.groups,
        )
    }

    @Test
    fun displayName_isNonEmptyForAllTabs() {
        SettingsTab.entries.forEach { tab ->
            assertTrue(SettingsTab.displayName(tab).isNotEmpty())
        }
    }
}
