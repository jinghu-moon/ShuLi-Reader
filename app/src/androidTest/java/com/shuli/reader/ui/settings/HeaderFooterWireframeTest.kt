package com.shuli.reader.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shuli.reader.core.reader.SlotContent
import com.shuli.reader.feature.reader.component.quicksettings.v5.HeaderFooterWireframe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeaderFooterWireframeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersHeaderBodyFooter() {
        compose.setContent {
            HeaderFooterWireframe(
                headerSlots = Triple(SlotContent.NONE, SlotContent.CHAPTER_TITLE, SlotContent.NONE),
                footerSlots = Triple(SlotContent.NONE, SlotContent.BOOK_PROGRESS_PERCENT, SlotContent.NONE),
                onHeaderSlotChange = { _, _ -> },
                onFooterSlotChange = { _, _ -> },
            )
        }
        compose.onNodeWithTag("HeaderFooterWireframe").assertIsDisplayed()
        compose.onNodeWithTag("HeaderFooterWireframe_PageBody").assertIsDisplayed()
        compose.onNodeWithText("页眉").assertIsDisplayed()
        compose.onNodeWithText("页脚").assertIsDisplayed()
    }

    @Test
    fun headerSlots_rendered() {
        compose.setContent {
            HeaderFooterWireframe(
                headerSlots = Triple(SlotContent.CHAPTER_TITLE, SlotContent.BOOK_TITLE, SlotContent.TIME),
                footerSlots = Triple(SlotContent.NONE, SlotContent.NONE, SlotContent.NONE),
                onHeaderSlotChange = { _, _ -> },
                onFooterSlotChange = { _, _ -> },
            )
        }
        compose.onNodeWithTag("HeaderSlot_0").assertIsDisplayed()
        compose.onNodeWithTag("HeaderSlot_1").assertIsDisplayed()
        compose.onNodeWithTag("HeaderSlot_2").assertIsDisplayed()
    }

    @Test
    fun footerSlots_rendered() {
        compose.setContent {
            HeaderFooterWireframe(
                headerSlots = Triple(SlotContent.NONE, SlotContent.NONE, SlotContent.NONE),
                footerSlots = Triple(SlotContent.BATTERY, SlotContent.DATE, SlotContent.BOOK_PROGRESS_PERCENT),
                onHeaderSlotChange = { _, _ -> },
                onFooterSlotChange = { _, _ -> },
            )
        }
        compose.onNodeWithTag("FooterSlot_0").assertIsDisplayed()
        compose.onNodeWithTag("FooterSlot_1").assertIsDisplayed()
        compose.onNodeWithTag("FooterSlot_2").assertIsDisplayed()
    }
}
