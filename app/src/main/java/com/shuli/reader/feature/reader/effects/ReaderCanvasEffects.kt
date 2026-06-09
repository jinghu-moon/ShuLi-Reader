package com.shuli.reader.feature.reader.effects

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shuli.reader.core.reader.ReaderCanvasView
import com.shuli.reader.feature.reader.ReaderViewModel
import kotlin.math.roundToInt

/**
 * 阅读器 Canvas 副作用组 —— 仅保留生命周期与电量采集。
 *
 * **视觉参数同步已迁移到 `ReaderRenderOrchestrator` + `applySnapshot` 单向数据流**：
 * 排版 / 外观 / 行为设置通过 [com.shuli.reader.feature.reader.render.ReaderRenderInput]
 * → `ReaderRenderSnapshotFactory.buildShellSnapshot` → `ReaderCanvasView.applySnapshot`
 * 统一应用，不再在此处用 `LaunchedEffect` 逐个 setter 调用。
 *
 * 本文件只保留：
 * - 密度同步（[ReaderViewModel.setDensity]）
 * - 亮度 / 屏幕常亮（Activity window 副作用，非 Canvas）
 * - 电量广播采集（Screen 层运行时数据，注入 snapshot 的 ShellSnapshot）
 * - 生命周期暂停/恢复（阅读会话）
 */
@Composable
internal fun ReaderCanvasEffects(
    viewModel: ReaderViewModel,
    @Suppress("UNUSED_PARAMETER") canvasView: ReaderCanvasView?,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    // ── Lifecycle Effects ──

    LaunchedEffect(density) {
        viewModel.setDensity(density)
    }

    // 亮度
    val brightness = uiState.readerPreferences.brightness
    LaunchedEffect(brightness) {
        activity?.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.screenBrightness = if (brightness < 0f) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                brightness.coerceIn(0.01f, 1f)
            }
            window.attributes = layoutParams
        }
    }

    // 屏幕常亮
    val keepScreenOn = uiState.readerPreferences.keepScreenOn
    LaunchedEffect(keepScreenOn) {
        activity?.window?.let { window ->
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // 生命周期：暂停/恢复
    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.pauseReadingSession()
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.resumeReadingSession()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.releaseReaderResources()
        }
    }
}
