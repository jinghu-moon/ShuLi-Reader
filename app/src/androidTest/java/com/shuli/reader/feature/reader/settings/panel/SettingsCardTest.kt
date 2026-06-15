package com.shuli.reader.feature.reader.settings.panel

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shuli.reader.feature.reader.settings.panel.SettingsCard
import androidx.compose.material3.Text
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsCardTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersTitleAndExpandedContent() {
        compose.setContent {
            SettingsCard(title = "基础排版", initiallyExpanded = true) {
                Text("测试设置项 A")
                Text("测试设置项 B")
            }
        }
        compose.onNodeWithText("基础排版").assertIsDisplayed()
        compose.onNodeWithText("测试设置项 A").assertIsDisplayed()
        compose.onNodeWithText("测试设置项 B").assertIsDisplayed()
    }

    @Test
    fun collapsedInitially_hidesContent() {
        compose.setContent {
            SettingsCard(title = "高级", initiallyExpanded = false) {
                Text("隐藏的内容")
            }
        }
        compose.onNodeWithText("高级").assertIsDisplayed()
        compose.onNodeWithText("隐藏的内容").assertDoesNotExist()
    }

    @Test
    fun clickHeader_togglesContent() {
        compose.setContent {
            SettingsCard(title = "可折叠", initiallyExpanded = false) {
                Text("展开后显示")
            }
        }
        compose.onNodeWithText("展开后显示").assertDoesNotExist()
        compose.onNodeWithTag("SettingsCard_可折叠_Toggle").performClick()
        compose.onNodeWithText("展开后显示").assertIsDisplayed()
        compose.onNodeWithTag("SettingsCard_可折叠_Toggle").performClick()
        compose.onNodeWithText("展开后显示").assertDoesNotExist()
    }
}
