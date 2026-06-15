package com.shuli.reader.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shuli.reader.core.reader.SlotContent
import com.shuli.reader.feature.reader.component.quicksettings.v5.SlotMatrix
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SlotMatrixTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersHeaderAndFooterRows() {
        compose.setContent {
            SlotMatrix(
                headerSlots = Triple(SlotContent.NONE, SlotContent.CHAPTER_TITLE, SlotContent.NONE),
                footerSlots = Triple(SlotContent.NONE, SlotContent.BOOK_PROGRESS_PERCENT, SlotContent.NONE),
                onHeaderSlotChange = { _, _ -> },
                onFooterSlotChange = { _, _ -> },
            )
        }
        compose.onNodeWithTag("SlotMatrix").assertIsDisplayed()
        compose.onNodeWithTag("SlotMatrix_Header").assertIsDisplayed()
        compose.onNodeWithTag("SlotMatrix_Footer").assertIsDisplayed()
        compose.onNodeWithText("页眉").assertIsDisplayed()
        compose.onNodeWithText("页脚").assertIsDisplayed()
    }

    @Test
    fun headerSlots_allThreeRendered() {
        compose.setContent {
            SlotMatrix(
                headerSlots = Triple(
                    SlotContent.CHAPTER_TITLE,
                    SlotContent.BOOK_TITLE,
                    SlotContent.TIME,
                ),
                footerSlots = Triple(SlotContent.NONE, SlotContent.NONE, SlotContent.NONE),
                onHeaderSlotChange = { _, _ -> },
                onFooterSlotChange = { _, _ -> },
            )
        }
        compose.onNodeWithTag("SlotMatrix_Header_Slot0").assertIsDisplayed()
        compose.onNodeWithTag("SlotMatrix_Header_Slot1").assertIsDisplayed()
        compose.onNodeWithTag("SlotMatrix_Header_Slot2").assertIsDisplayed()
        compose.onNodeWithText("章节").assertIsDisplayed()
        compose.onNodeWithText("书名").assertIsDisplayed()
        compose.onNodeWithText("时间").assertIsDisplayed()
    }

    @Test
    fun footerSlots_allThreeRendered() {
        compose.setContent {
            SlotMatrix(
                headerSlots = Triple(SlotContent.NONE, SlotContent.NONE, SlotContent.NONE),
                footerSlots = Triple(
                    SlotContent.BATTERY,
                    SlotContent.DATE,
                    SlotContent.BOOK_PROGRESS_PERCENT,
                ),
                onHeaderSlotChange = { _, _ -> },
                onFooterSlotChange = { _, _ -> },
            )
        }
        compose.onNodeWithTag("SlotMatrix_Footer_Slot0").assertIsDisplayed()
        compose.onNodeWithTag("SlotMatrix_Footer_Slot1").assertIsDisplayed()
        compose.onNodeWithTag("SlotMatrix_Footer_Slot2").assertIsDisplayed()
        compose.onNodeWithText("电量").assertIsDisplayed()
        compose.onNodeWithText("日期").assertIsDisplayed()
        compose.onNodeWithText("全书%").assertIsDisplayed()
    }

    @Test
    fun emptySlot_showsPlaceholderText() {
        compose.setContent {
            SlotMatrix(
                headerSlots = Triple(SlotContent.NONE, SlotContent.NONE, SlotContent.NONE),
                footerSlots = Triple(SlotContent.NONE, SlotContent.NONE, SlotContent.NONE),
                onHeaderSlotChange = { _, _ -> },
                onFooterSlotChange = { _, _ -> },
            )
        }
        compose.onNodeWithText("留白", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun clickSlot_invokesCallbackWithSelectedContent() {
        val receivedIndex = AtomicInteger(-1)
        var receivedContent: SlotContent? = null

        compose.setContent {
            SlotMatrix(
                headerSlots = Triple(SlotContent.NONE, SlotContent.NONE, SlotContent.NONE),
                footerSlots = Triple(SlotContent.NONE, SlotContent.NONE, SlotContent.NONE),
                onHeaderSlotChange = { idx, content ->
                    receivedIndex.set(idx)
                    receivedContent = content
                },
                onFooterSlotChange = { _, _ -> },
            )
        }

        // 点击页眉中间槽位 → 弹出 Picker
        compose.onNodeWithTag("SlotMatrix_Header_Slot1").performClick()
        compose.onNodeWithTag("SlotPicker").assertIsDisplayed()

        // 选择 "书名"
        compose.onNodeWithText("书名", useUnmergedTree = true).performClick()

        assertEquals(1, receivedIndex.get())
        assertEquals(SlotContent.BOOK_TITLE, receivedContent)
    }
}
