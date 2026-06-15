package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shuli.reader.core.font.FontManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import org.junit.Assert.assertEquals

/**
 * FontPreviewRow 垂直行布局测试：
 * - 渲染内置与自定义字体行（tag 为 `FontRow_<key>`）
 * - 点击行触发 onSelect
 * - 长按自定义字体弹出删除对话框
 *
 * 注：导入入口已迁移至字体卡片标题右侧，本组件不再承载导入按钮。
 */
@RunWith(AndroidJUnit4::class)
class FontPreviewRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fontPreviewRow_rendersCorrectly() {
        val fakeFile = File.createTempFile("fake_font", ".ttf").apply { deleteOnExit() }
        val customEntry = FontManager.FontEntry(
            id = "1",
            name = "自定义字体",
            file = fakeFile
        )
        val customFonts = listOf(customEntry)
        val customTag = "FontRow_${customEntry.key}"

        var selectedKey = "harmony"
        var deleteEntry: FontManager.FontEntry? = null

        composeTestRule.setContent {
            FontPreviewRow(
                selectedKey = selectedKey,
                customFonts = customFonts,
                onSelect = { selectedKey = it },
                onImport = { },
                onDelete = { deleteEntry = it }
            )
        }

        composeTestRule.onNodeWithTag("FontRow_harmony").assertIsDisplayed()
        composeTestRule.onNodeWithTag("FontRow_system").assertIsDisplayed()
        composeTestRule.onNodeWithTag(customTag).assertIsDisplayed()

        composeTestRule.onNodeWithTag(customTag).performClick()
        assertEquals(customEntry.key, selectedKey)

        composeTestRule.onNodeWithTag(customTag).performTouchInput {
            longClick()
        }

        composeTestRule.onNodeWithText("删除字体").assertIsDisplayed()
        composeTestRule.onNodeWithText("删除").performClick()

        assertEquals(customEntry.key, deleteEntry?.key)
    }
}
