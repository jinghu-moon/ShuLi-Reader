package com.shuli.reader.feature.bookshelf.component

import android.os.SystemClock

private const val POST_LONG_PRESS_CLICK_SUPPRESS_MS = 350L

internal fun nextPostLongPressClickDeadline(): Long =
    SystemClock.uptimeMillis() + POST_LONG_PRESS_CLICK_SUPPRESS_MS

internal fun shouldSuppressPostLongPressClick(
    isLongPressActive: Boolean,
    suppressClickUntilMillis: Long,
): Boolean = isLongPressActive || SystemClock.uptimeMillis() <= suppressClickUntilMillis
