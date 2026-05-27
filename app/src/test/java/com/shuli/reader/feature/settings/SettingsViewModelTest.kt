package com.shuli.reader.feature.settings

import com.shuli.reader.MainDispatcherRule
import com.shuli.reader.core.data.PageAnimConst
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.data.TestDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var userPreferences: UserPreferences
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        val dataStore = TestDataStoreFactory.create()
        userPreferences = UserPreferences(dataStore)
        viewModel = SettingsViewModel(userPreferences)
    }

    @Test
    fun initialState_hasCorrectDefaults() = runTest {
        // 等待 StateFlow 从 combine 收集初始值
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("zh-CN", state.language)
        assertEquals("system", state.themeMode)
        assertEquals("harmony", state.appFont)
        assertEquals(16f, state.defaultFontSize, 0.01f)
        assertEquals(1.5f, state.defaultLineSpacing, 0.01f)
        assertEquals(1.0f, state.defaultParagraphSpacing, 0.01f)
        assertEquals(2.0f, state.defaultIndent, 0.01f)
        assertFalse(state.fullScreen)
        assertFalse(state.keepScreenOn)
        assertEquals(-1f, state.brightness, 0.01f)
        assertTrue(state.duplicateCheckEnabled)
        assertTrue(state.importCopyFile)
        assertTrue(state.readingTimeEnabled)
        assertEquals(30, state.readingDailyTarget)
        assertTrue(state.gpuAcceleration)
        assertFalse(state.loggingEnabled)
    }

    @Test
    fun updateDefaultFontSize_persistsThroughUserPreferences() = runTest {
        viewModel.updateDefaultFontSize(20f)
        advanceUntilIdle()
        // 直接验证 DataStore 写入
        val value = userPreferences.defaultFontSize.first()
        assertEquals(20f, value, 0.01f)
    }

    @Test
    fun updateDefaultLineSpacing_persistsThroughUserPreferences() = runTest {
        viewModel.updateDefaultLineSpacing(2.0f)
        advanceUntilIdle()
        val value = userPreferences.defaultLineSpacing.first()
        assertEquals(2.0f, value, 0.01f)
    }

    @Test
    fun updateDefaultParagraphSpacing_persistsThroughUserPreferences() = runTest {
        viewModel.updateDefaultParagraphSpacing(1.5f)
        advanceUntilIdle()
        val value = userPreferences.defaultParagraphSpacing.first()
        assertEquals(1.5f, value, 0.01f)
    }

    @Test
    fun updateDefaultIndent_persistsThroughUserPreferences() = runTest {
        viewModel.updateDefaultIndent(3.0f)
        advanceUntilIdle()
        val value = userPreferences.defaultIndent.first()
        assertEquals(3.0f, value, 0.01f)
    }

    @Test
    fun updateFullScreen_persistsThroughUserPreferences() = runTest {
        viewModel.updateFullScreen(true)
        advanceUntilIdle()
        val value = userPreferences.fullScreen.first()
        assertTrue(value)
    }

    @Test
    fun updateKeepScreenOn_persistsThroughUserPreferences() = runTest {
        viewModel.updateKeepScreenOn(true)
        advanceUntilIdle()
        val value = userPreferences.keepScreenOn.first()
        assertTrue(value)
    }

    @Test
    fun updateBrightness_persistsThroughUserPreferences() = runTest {
        viewModel.updateBrightness(0.7f)
        advanceUntilIdle()
        val value = userPreferences.brightness.first()
        assertEquals(0.7f, value, 0.01f)
    }

    @Test
    fun updateDefaultPageAnim_persistsThroughUserPreferences() = runTest {
        viewModel.updateDefaultPageAnim(PageAnimConst.SIMULATION)
        advanceUntilIdle()
        val value = userPreferences.defaultPageAnim.first()
        assertEquals(PageAnimConst.SIMULATION, value)
    }

    @Test
    fun updateDuplicateCheckEnabled_persistsThroughUserPreferences() = runTest {
        viewModel.updateDuplicateCheckEnabled(false)
        advanceUntilIdle()
        val value = userPreferences.duplicateCheckEnabled.first()
        assertFalse(value)
    }

    @Test
    fun updateReadingDailyTarget_persistsThroughUserPreferences() = runTest {
        viewModel.updateReadingDailyTarget(60)
        advanceUntilIdle()
        val value = userPreferences.readingDailyTarget.first()
        assertEquals(60, value)
    }
}
