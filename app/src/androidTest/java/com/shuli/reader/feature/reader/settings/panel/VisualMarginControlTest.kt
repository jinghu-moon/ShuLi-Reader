package com.shuli.reader.feature.reader.settings.panel

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shuli.reader.feature.reader.settings.panel.MarginValues
import com.shuli.reader.feature.reader.settings.panel.VisualMarginControl
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualMarginControlTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersThumbnailAndAllFourSteppers() {
        compose.setContent {
            VisualMarginControl(
                margins = MarginValues(top = 48f, bottom = 48f, left = 24f, right = 24f),
                onMarginsChange = {},
            )
        }
        compose.onNodeWithTag("VisualMarginControl").assertIsDisplayed()
        compose.onNodeWithTag("VisualMarginControl_Thumbnail").assertIsDisplayed()
        compose.onNodeWithTag("MarginTop").assertIsDisplayed()
        compose.onNodeWithTag("MarginBottom").assertIsDisplayed()
        compose.onNodeWithTag("MarginLeft").assertIsDisplayed()
        compose.onNodeWithTag("MarginRight").assertIsDisplayed()
    }

    @Test
    fun syncSwitches_rendered() {
        compose.setContent {
            VisualMarginControl(
                margins = MarginValues(top = 48f, bottom = 48f, left = 24f, right = 24f),
                onMarginsChange = {},
            )
        }
        compose.onNodeWithTag("VisualMarginControl_SyncV").assertIsDisplayed()
        compose.onNodeWithTag("VisualMarginControl_SyncH").assertIsDisplayed()
    }
}
