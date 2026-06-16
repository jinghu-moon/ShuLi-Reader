/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.recorder.impl

import com.shuli.reader.core.recorder.BaseCanvasRecorder

import android.graphics.Canvas
import android.graphics.Picture

/**
 * API 23-28 实现：仅 Picture，无 RenderNode。
 *
 * PR-1: Picture 不再池化，每次 init 直接 new Picture()，
 * 避免 recording 状态跨实例污染。
 */
class CanvasRecorderApi23Impl : BaseCanvasRecorder() {

    private var picture: Picture? = null

    override val width get() = picture?.width ?: -1
    override val height get() = picture?.height ?: -1

    private fun initPicture() {
        if (picture == null) {
            // PR-1: 直接创建，不从池中获取
            picture = Picture()
        }
    }

    override fun beginRecording(width: Int, height: Int): Canvas {
        initPicture()
        val pic = picture ?: throw IllegalStateException("picture is null after init")
        // PR-1: Picture 是新创建的，不需要检查旧状态
        return pic.beginRecording(width, height)
    }

    override fun endRecording() {
        picture?.endRecording()
        super.endRecording()
    }

    override fun draw(canvas: Canvas) {
        val pic = picture ?: return
        canvas.drawPicture(pic)
    }

    override fun recycle() {
        super.recycle()
        // 确保 Picture 结束录制状态
        try {
            picture?.endRecording()
        } catch (_: IllegalStateException) {
            // 已经结束录制，忽略
        }
        // PR-1: Picture 不回池，直接丢弃等待 GC
        picture = null
    }

    // PR-1: 移除 companion object 中的 picturePool

}
