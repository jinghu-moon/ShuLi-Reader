package com.shuli.reader.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shuli.reader.feature.reader.component.quicksettings.v5.StepperSlider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StepperSliderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersAllParts() {
        compose.setContent {
            StepperSlider(
                value = 1.5f,
                onValueChange = {},
                valueRange = 0.5f..3.0f,
                label = "行距",
            )
        }
        compose.onNodeWithTag("StepperSlider").assertIsDisplayed()
        compose.onNodeWithTag("StepperSlider_Slider").assertIsDisplayed()
        compose.onNodeWithTag("StepperSlider_Dec").assertIsDisplayed()
        compose.onNodeWithTag("StepperSlider_Inc").assertIsDisplayed()
        compose.onNodeWithTag("StepperSlider_Value").assertTextEquals("1.5")
    }

    @Test
    fun clickInc_incrementsValue() {
        var emitted: Float? = null
        compose.setContent {
            StepperSlider(
                value = 1.5f,
                onValueChange = { emitted = it },
                valueRange = 0.5f..3.0f,
                step = 0.1f,
            )
        }
        compose.onNodeWithTag("StepperSlider_Inc").performClick()
        compose.runOnIdle {
            assert(emitted != null)
            assert((emitted!! - 1.6f).let { kotlin.math.abs(it) } < 0.001f)
        }
    }

    @Test
    fun clickDec_decrementsValue() {
        var emitted: Float? = null
        compose.setContent {
            StepperSlider(
                value = 1.5f,
                onValueChange = { emitted = it },
                valueRange = 0.5f..3.0f,
                step = 0.1f,
            )
        }
        compose.onNodeWithTag("StepperSlider_Dec").performClick()
        compose.runOnIdle {
            assert(emitted != null)
            assert((emitted!! - 1.4f).let { kotlin.math.abs(it) } < 0.001f)
        }
    }
}
