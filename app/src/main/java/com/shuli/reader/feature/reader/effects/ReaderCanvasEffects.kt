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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.reader.ReaderCanvasView
import com.shuli.reader.feature.reader.ReaderViewModel
import kotlin.math.roundToInt

/**
 * 阅读器 Canvas 副作用组 —— prefs / runtime / lifecycle 三类 LaunchedEffect。
 *
 * 从 ReaderScreen 提取，保持主文件聚焦于 UI 布局。
 */
@Composable
internal fun ReaderCanvasEffects(
    viewModel: ReaderViewModel,
    canvasView: ReaderCanvasView?,
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

    // openBook LaunchedEffect 已移至 ReaderScreen 主体（必须始终在组合树中，
    // 不能放在 isLoading 条件分支内，否则每次 isLoading 切换都会 dispose/recreate）

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

    // 电量广播
    var batteryLevel = remember { mutableIntStateOf(100) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    batteryLevel.intValue = (level.toFloat() / scale.toFloat() * 100).roundToInt()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val flags = if (android.os.Build.VERSION.SDK_INT >= 33) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter, flags)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // 生命周期：暂停/恢复
    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.ttsPlaybackManager.pauseTtsOnBackground()
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

    // ── Prefs Effects ──

    val prefs = uiState.readerPreferences

    // 排版属性（字号/字距/字重/字体/对齐）
    LaunchedEffect(canvasView, prefs.fontSize, prefs.letterSpacing, prefs.fontWeight, prefs.readingFont, prefs.textAlign) {
        canvasView?.updatePaintSnapshot(
            textSize = prefs.fontSize * density,
            letterSpacing = prefs.letterSpacing,
            fakeBold = prefs.fontWeight == ReaderFontWeight.BOLD,
            fontKey = prefs.readingFont,
            textAlign = prefs.textAlign,
            invalidateContent = true,
        )
        canvasView?.textPaint?.let { viewModel.syncTextMeasurerPaint(it) }
    }

    // 页眉页脚
    LaunchedEffect(canvasView, prefs.headerFooterAlpha, prefs.showProgress, prefs.showHeaderLine, prefs.showFooterLine) {
        val (headerRes, footerRes) = viewModel.readerProgressResolver.resolveHeaderAndFooterSlots()
        canvasView?.updateHeaderFooter(
            headerRes,
            footerRes,
            prefs.headerFooterAlpha,
            prefs.showProgress,
            prefs.showHeaderLine,
            prefs.showFooterLine,
        )
    }

    // 标题样式
    LaunchedEffect(canvasView, prefs.titleStyle) {
        canvasView?.setTitleStyle(prefs.titleStyle)
    }

    // 边缘翻页
    LaunchedEffect(canvasView, prefs.edgeTurnPage) {
        canvasView?.setEdgeTurnPageEnabled(prefs.edgeTurnPage)
    }

    // 边缘触摸宽度
    LaunchedEffect(canvasView, prefs.edgeWidthPercent) {
        canvasView?.setEdgeWidthPercent(prefs.edgeWidthPercent)
    }

    // 页眉页脚字号比例
    LaunchedEffect(canvasView, prefs.headerFontSizeRatio, prefs.footerFontSizeRatio) {
        canvasView?.setHeaderTextRatio(prefs.headerFontSizeRatio)
        canvasView?.setFooterTextRatio(prefs.footerFontSizeRatio)
    }

    // ── Runtime Effects ──

    // 翻页动画类型
    val pageAnimType = uiState.pageAnimType
    LaunchedEffect(canvasView, pageAnimType) {
        canvasView?.setPageDelegate(com.shuli.reader.core.reader.animation.PageDelegateFactory.create(pageAnimType))
    }

    // 主题颜色
    val themeColors = uiState.themeColors
    LaunchedEffect(canvasView, themeColors) {
        canvasView?.setThemeColors(themeColors)
    }

    // 电池
    LaunchedEffect(canvasView, batteryLevel.intValue) {
        canvasView?.setBatteryLevel(batteryLevel.intValue)
    }

    // TTS 高亮
    val ttsActiveRange = uiState.ttsActiveRange
    LaunchedEffect(canvasView, ttsActiveRange) {
        canvasView?.setTtsActiveRange(ttsActiveRange)
    }

    // 选区清除
    val selectedRange = uiState.selectedRange
    LaunchedEffect(canvasView, selectedRange) {
        if (selectedRange == null) {
            canvasView?.clearSelection()
        }
    }

    // 笔记高亮
    val noteHashes = uiState.notes.hashCode() to uiState.chapterIndex
    LaunchedEffect(canvasView, noteHashes) {
        canvasView?.setNoteRanges(viewModel.bookmarkNotesManager.getVisibleNoteRanges())
    }
}
