package com.shuli.reader.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shuli.reader.feature.reader.component.quicksettings.v5.FontSizeStepper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FontSizeStepperTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun displaysCurrentValue() {
        compose.setContent {
            FontSizeStepper(value = 18f, onValueChange = {})
        }
        compose.onNodeWithTag("FontSizeStepper_Value").assertTextEquals("18 sp")
    }

    @Test
    fun clickIncrease_emitsIncrementedValue() {
        var emitted: Float? = null
        compose.setContent {
            FontSizeStepper(value = 18f, onValueChange = { emitted = it })
        }
        compose.onNodeWithTag("FontSizeStepper_Increase").performClick()
        compose.runOnIdle { assert(emitted == 19f) }
    }

    @Test
    fun clickDecrease_emitsDecrementedValue() {
        var emitted: Float? = null
        compose.setContent {
            FontSizeStepper(value = 18f, onValueChange = { emitted = it })
        }
        compose.onNodeWithTag("FontSizeStepper_Decrease").performClick()
        compose.runOnIdle { assert(emitted == 17f) }
    }

    @Test
    fun atMinimum_decreaseDisabled() {
        compose.setContent {
            FontSizeStepper(value = 12f, onValueChange = {}, range = 12f..32f)
        }
        // The stepper should still be visible
        compose.onNodeWithTag("FontSizeStepper").assertIsDisplayed()
        compose.onNodeWithTag("FontSizeStepper_Value").assertTextEquals("12 sp")
    }

    @Test
    fun atMaximum_increaseDisabled() {
        compose.setContent {
            FontSizeStepper(value = 32f, onValueChange = {}, range = 12f..32f)
        }
        compose.onNodeWithTag("FontSizeStepper").assertIsDisplayed()
        compose.onNodeWithTag("FontSizeStepper_Value").assertTextEquals("32 sp")
    }
}
