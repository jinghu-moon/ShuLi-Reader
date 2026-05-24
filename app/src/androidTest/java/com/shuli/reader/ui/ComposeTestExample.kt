package com.shuli.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shuli.reader.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 最小 Compose UI 测试，验证 Compose 测试基础设施可用。
 */
@RunWith(AndroidJUnit4::class)
class ComposeTestExample {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun stableTestTagIsDisplayed() {
        composeTestRule.setContent {
            Box(modifier = Modifier.testTag(UiTestTags.BOOKSHELF_SCREEN)) {
                Text("ShuLi Reader")
            }
        }
        composeTestRule.onNodeWithTag(UiTestTags.BOOKSHELF_SCREEN).assertIsDisplayed()
    }
}
