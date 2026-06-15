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
import com.shuli.reader.core.recorder.internal.synchronizedPool
import com.shuli.reader.core.recorder.pools.PicturePool

class CanvasRecorderApi23Impl : BaseCanvasRecorder() {

    private var picture: Picture? = null

    override val width get() = picture?.width ?: -1
    override val height get() = picture?.height ?: -1

    private fun initPicture() {
        if (picture == null) {
            picture = picturePool.obtain()
        }
    }

    override fun beginRecording(width: Int, height: Int): Canvas {
        initPicture()
        var pic = picture ?: throw IllegalStateException("picture is null after init")
        // 确保 Picture 不在录制状态，如果无法结束则丢弃并获取新的
        try {
            pic.endRecording()
        } catch (_: IllegalStateException) {
            // Picture 处于异常状态，丢弃并获取新的
            picturePool.recycle(pic)
            pic = picturePool.obtain()
            picture = pic
        }
        return pic.beginRecording(width, height)
    }

    override fun endRecording() {
        picture?.endRecording()
        super.endRecording()
    }

    override fun draw(canvas: Canvas) {
        if (picture == null) return
        canvas.drawPicture(picture!!)
    }

    override fun recycle() {
        super.recycle()
        // 确保 Picture 结束录制状态再回收
        try {
            picture?.endRecording()
        } catch (_: IllegalStateException) {
            // 已经结束录制，忽略
        }
        picture?.let { picturePool.recycle(it) }
        picture = null
    }

    companion object {
        private val picturePool = PicturePool().synchronizedPool()
    }

}
