package com.shuli.reader.core.recorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasRecorderApi29ImplTest {

    private fun requireApi29() {
        Assume.assumeTrue("Requires API 29+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    }

    @Test
    fun recordThenDraw_producesVisibleContent() {
        requireApi29()
        val recorder = CanvasRecorderApi29Impl()
        try {
            recorder.record(100, 100) {
                drawColor(Color.RED)
            }

            val target = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(target)
            recorder.draw(canvas)

            val pixel = target.getPixel(50, 50)
            assertEquals(Color.RED, pixel)
            target.recycle()
        } finally {
            recorder.recycle()
        }
    }

    @Test
    fun needRecord_returnsTrue_initially() {
        requireApi29()
        val recorder = CanvasRecorderApi29Impl()
        try {
            assertTrue(recorder.needRecord())
        } finally {
            recorder.recycle()
        }
    }

    @Test
    fun needRecord_returnsFalse_afterRecord() {
        requireApi29()
        val recorder = CanvasRecorderApi29Impl()
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
        requireApi29()
        val recorder = CanvasRecorderApi29Impl()
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
    fun draw_afterInvalidate_redrawsContent() {
        requireApi29()
        val recorder = CanvasRecorderApi29Impl()
        try {
            // First record: red
            recorder.record(100, 100) {
                drawColor(Color.RED)
            }
            recorder.invalidate()

            // Second record: blue
            recorder.record(100, 100) {
                drawColor(Color.BLUE)
            }

            val target = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(target)
            recorder.draw(canvas)

            assertEquals(Color.BLUE, target.getPixel(50, 50))
            target.recycle()
        } finally {
            recorder.recycle()
        }
    }

    @Test
    fun factory_createsApi29Impl_onApi29Plus() {
        Assume.assumeTrue("Requires API 29+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        CanvasRecorderFactory.optimizeRender = true
        val recorder = CanvasRecorderFactory.create()
        try {
            assertTrue(
                "Expected CanvasRecorderApi29Impl on API 29+",
                recorder is CanvasRecorderApi29Impl
            )
        } finally {
            recorder.recycle()
        }
    }

    @Test
    fun factory_createsImpl_whenOptimizeDisabled() {
        CanvasRecorderFactory.optimizeRender = false
        val recorder = CanvasRecorderFactory.create()
        try {
            assertTrue(
                "Expected CanvasRecorderBitmapImpl when optimize disabled",
                recorder is CanvasRecorderBitmapImpl
            )
        } finally {
            recorder.recycle()
        }
        CanvasRecorderFactory.optimizeRender = true
    }
}
