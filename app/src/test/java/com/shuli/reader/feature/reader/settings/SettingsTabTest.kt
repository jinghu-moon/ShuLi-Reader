package com.shuli.reader.feature.reader.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTabTest {

    @Test
    fun settingsTab_hasThreeValues() {
        val names = SettingsTab.entries.map { it.name }.toSet()
        assertEquals(setOf("TYPE_AND_FONT", "APPEARANCE", "BEHAVIOR"), names)
    }

    @Test
    fun eachTab_hasNonEmptyGroups() {
        SettingsTab.entries.forEach { tab ->
            assertTrue("${tab.name}.groups should not be empty", tab.groups.isNotEmpty())
        }
    }

    @Test
    fun allTwelveUiGroups_areAssignedToSomeTab() {
        assertTrue("All UiGroup entries must appear in some SettingsTab", SettingsTab.allGroupsCovered)
    }

    @Test
    fun noUiGroupIsAssignedTwice() {
        val allGroups = SettingsTab.entries.flatMap { it.groups }
        assertEquals("UiGroup must appear exactly once across all tabs", allGroups.distinct().size, allGroups.size)
    }

    @Test
    fun typeAndFont_groupsMatch() {
        assertEquals(
            listOf(
                UiGroup.FONT_BASICS,
                UiGroup.TEXT_LAYOUT,
                UiGroup.TEXT_STYLE,
                UiGroup.ADVANCED_READING,
            ),
            SettingsTab.TYPE_AND_FONT.groups,
        )
    }

    @Test
    fun appearance_groupsMatch() {
        assertEquals(
            listOf(
                UiGroup.THEME,
                UiGroup.PAGE_CHROME,
                UiGroup.DISPLAY_MODE,
                UiGroup.VISUAL_AIDS,
            ),
            SettingsTab.APPEARANCE.groups,
        )
    }

    @Test
    fun behavior_groupsMatch() {
        assertEquals(
            listOf(
                UiGroup.PAGE_TURN,
                UiGroup.GESTURE,
                UiGroup.EYE_CARE,
                UiGroup.GENERAL,
            ),
            SettingsTab.BEHAVIOR.groups,
        )
    }

    @Test
    fun displayName_isNonEmptyForAllTabs() {
        SettingsTab.entries.forEach { tab ->
            assertTrue(SettingsTab.displayName(tab).isNotEmpty())
        }
    }
}
