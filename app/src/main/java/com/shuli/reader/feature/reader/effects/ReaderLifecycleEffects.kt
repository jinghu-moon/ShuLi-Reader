package com.shuli.reader.feature.reader.effects

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.WindowManager
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shuli.reader.feature.reader.ReaderViewModel

/**
 * 阅读器生命周期与平台适配 Effect 集合。
 *
 * 职责：density 同步、bookId 加载、亮度监听、屏幕常亮、
 *       电池广播、生命周期观察（暂停/恢复/释放）。
 */
@Composable
fun ReaderLifecycleEffects(
    bookId: Long,
    density: Float,
    brightness: Float,
    keepScreenOn: Boolean,
    context: Context,
    activity: Activity?,
    viewModel: ReaderViewModel,
    onBatteryLevelChanged: (Int) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // density 同步
    LaunchedEffect(density) {
        viewModel.setDensity(density)
    }

    // 打开书籍
    LaunchedEffect(bookId) {
        viewModel.openBook(bookId)
    }

    // 亮度监听
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
    LaunchedEffect(keepScreenOn) {
        activity?.window?.let { window ->
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // 电池广播
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    onBatteryLevelChanged((level.toFloat() / scale.toFloat() * 100).roundToInt())
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
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // 生命周期观察
    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.pauseTtsOnBackground()
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
