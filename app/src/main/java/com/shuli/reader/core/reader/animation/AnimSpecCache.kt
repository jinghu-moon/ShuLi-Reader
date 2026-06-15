package com.shuli.reader.core.reader.animation

import com.shuli.reader.core.data.PageAnimSpeed

/**
 * 动画缓动曲线。
 *
 * 对应 Android Interpolator 类型，消费方按需转换为具体 Interpolator 实例。
 */
enum class Easing {
    LinearOutSlowIn,
    FastOutSlowIn,
    StandardDecelerate,
}

/**
 * 翻页动画规格：时长 + 缓动曲线。
 */
data class AnimSpec(
    val durationMs: Long,
    val easing: Easing,
)

object AnimSpecCache {
    private val cache = mapOf(
        PageAnimSpeed.FAST to AnimSpec(
            durationMs = PageAnimSpeed.FAST.durationMs.toLong(),
            easing = Easing.LinearOutSlowIn,
        ),
        PageAnimSpeed.NORMAL to AnimSpec(
            durationMs = PageAnimSpeed.NORMAL.durationMs.toLong(),
            easing = Easing.FastOutSlowIn,
        ),
        PageAnimSpeed.SLOW to AnimSpec(
            durationMs = PageAnimSpeed.SLOW.durationMs.toLong(),
            easing = Easing.StandardDecelerate,
        ),
    )

    fun create(speed: PageAnimSpeed): AnimSpec = cache.getValue(speed)
}
