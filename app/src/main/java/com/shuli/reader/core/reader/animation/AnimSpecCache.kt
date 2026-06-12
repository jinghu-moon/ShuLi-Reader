package com.shuli.reader.core.reader.animation

import com.shuli.reader.core.data.PageAnimSpeed

/**
 * 翻页动画规格缓存，按 [PageAnimSpeed] 预构建时长参数。
 *
 * 避免每次创建 PageDelegate 时重复计算。
 */
data class AnimSpec(
    val durationMs: Long,
)

object AnimSpecCache {
    private val cache = PageAnimSpeed.entries.associateWith { speed ->
        AnimSpec(durationMs = speed.durationMs.toLong())
    }

    fun create(speed: PageAnimSpeed): AnimSpec = cache.getValue(speed)
}
