package com.shuli.reader.core.recorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasRecorderBitmapImplTest {

    @Test
    fun recordThenDraw_producesVisibleContent() {
        val recorder = CanvasRecorderBitmapImpl()
        try {
            // Record: fill with red
            recorder.record(100, 100) {
                drawColor(Color.RED)
            }

            // Draw onto a target bitmap
            val target = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(target)
            recorder.draw(canvas)

            // Verify: center pixel should be red
            val pixel = target.getPixel(50, 50)
            assertEquals(Color.RED, pixel)
            target.recycle()
        } finally {
            recorder.recycle()
        }
    }

    @Test
    fun needRecord_returnsTrue_initially() {
        val recorder = CanvasRecorderBitmapImpl()
        try {
            assertTrue(recorder.needRecord())
        } finally {
            recorder.recycle()
        }
    }

    @Test
    fun needRecord_returnsFalse_afterRecord() {
        val recorder = CanvasRecorderBitmapImpl()
        try {
            recorder.record(50, 50) {
                drawColor(Color.BLUE)
            }
            assertFalse(recorder.needRecord())
        } finally {
            recorder.recycle()
        }
    }

    @Test
    fun invalidate_setsNeedRecord() {
        val recorder = CanvasRecorderBitmapImpl()
        try {
            recorder.record(50, 50) {
                drawColor(Color.GREEN)
            }
            assertFalse(recorder.needRecord())

            recorder.invalidate()
            assertTrue(recorder.needRecord())
        } finally {
            recorder.recycle()
        }
    }

    @Test
    fun record_drawsText() {
        val recorder = CanvasRecorderBitmapImpl()
        try {
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 24f
            }
            recorder.record(200, 50) {
                drawText("Hello", 10f, 30f, paint)
            }

            val target = Bitmap.createBitmap(200, 50, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(target)
            recorder.draw(canvas)

            // Text should produce non-transparent pixels
            val pixel = target.getPixel(20, 25)
            assertTrue("Expected non-transparent pixel for text", Color.alpha(pixel) > 0)
            target.recycle()
        } finally {
            recorder.recycle()
        }
    }
}
