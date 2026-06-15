package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shuli.reader.feature.reader.settings.panel.controls.FontStepper
import com.shuli.reader.feature.reader.settings.panel.controls.GestureZoneGrid
import com.shuli.reader.feature.reader.settings.panel.controls.InkStepperSlider
import com.shuli.reader.feature.reader.settings.panel.controls.InkToggle
import com.shuli.reader.feature.reader.settings.GestureAction
import com.shuli.reader.feature.reader.settings.GestureConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 自绘原子控件交互测试（替代旧 FontSizeStepperTest / StepperSliderTest）。
 */
@RunWith(AndroidJUnit4::class)
class InkControlsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // ── FontStepper ──

    @Test
    fun fontStepper_showsValue() {
        compose.setContent { FontStepper(value = 18f, onValueChange = {}) }
        compose.onNodeWithTag("FontStepper_Value").assertTextEquals("18")
    }

    @Test
    fun fontStepper_increase_emitsIncrement() {
        var emitted = 18f
        compose.setContent { FontStepper(value = 18f, onValueChange = { emitted = it }) }
        compose.onNodeWithTag("FontStepper_Increase").performClick()
        assertEquals(19f, emitted, 0.001f)
    }

    @Test
    fun fontStepper_decrease_emitsDecrement() {
        var emitted = 18f
        compose.setContent { FontStepper(value = 18f, onValueChange = { emitted = it }) }
        compose.onNodeWithTag("FontStepper_Decrease").performClick()
        assertEquals(17f, emitted, 0.001f)
    }

    // ── InkStepperSlider ──

    @Test
    fun inkStepperSlider_showsFormattedValue() {
        compose.setContent {
            InkStepperSlider(
                value = 1.5f,
                onValueChange = {},
                valueRange = 1.0f..2.5f,
                step = 0.1f,
                label = "行距",
            )
        }
        compose.onNodeWithTag("InkStepperSlider_Value").assertTextEquals("1.5")
    }

    @Test
    fun inkStepperSlider_incButton_steps() {
        var emitted = 1.5f
        compose.setContent {
            InkStepperSlider(
                value = 1.5f,
                onValueChange = { emitted = it },
                valueRange = 1.0f..2.5f,
                step = 0.1f,
            )
        }
        compose.onNodeWithTag("InkStepperSlider_Inc").performClick()
        assertEquals(1.6f, emitted, 0.001f)
    }

    // ── InkToggle ──

    @Test
    fun inkToggle_click_togglesState() {
        var checked = false
        compose.setContent { InkToggle(checked = false, onCheckedChange = { checked = it }) }
        compose.onNodeWithTag("InkToggle").performClick()
        assertTrue(checked)
    }

    // ── GestureZoneGrid ──

    @Test
    fun gestureGrid_clickZone_selectsActionFromPopup() {
        var config = GestureConfig()
        compose.setContent {
            GestureZoneGrid(config = GestureConfig(), onConfigChange = { config = it })
        }

        compose.onNodeWithTag("GestureZone_TOP_LEFT").performClick()
        compose.onNodeWithTag("GestureActionPicker").assertIsDisplayed()
        compose.onNodeWithTag("GestureAction_ADD_BOOKMARK").performClick()

        assertEquals(GestureAction.ADD_BOOKMARK, config.topLeft)
    }

    @Test
    fun gestureGrid_isDisplayed() {
        compose.setContent {
            GestureZoneGrid(config = GestureConfig(), onConfigChange = {})
        }
        compose.onNodeWithTag("GestureZoneGrid").assertIsDisplayed()
    }
}
