package com.shuli.reader.core.recorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasRecorderApi23ImplTest {

    private fun requireApi23() {
        Assume.assumeTrue("Requires API 23+", android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
    }

    @Test
    fun recordThenDraw_producesVisibleContent() {
        requireApi23()
        val recorder = CanvasRecorderApi23Impl()
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
    fun needRecord_worksCorrectly() {
        requireApi23()
        val recorder = CanvasRecorderApi23Impl()
        try {
            assertTrue(recorder.needRecord())

            recorder.record(50, 50) { drawColor(Color.BLUE) }
            assertFalse(recorder.needRecord())

            recorder.invalidate()
            assertTrue(recorder.needRecord())
        } finally {
            recorder.recycle()
        }
    }

    @Test
    fun factory_createsApi23Impl_onApi23To28() {
        Assume.assumeTrue(
            "Requires API 23-28",
            android.os.Build.VERSION.SDK_INT in
                android.os.Build.VERSION_CODES.M until android.os.Build.VERSION_CODES.Q
        )
        CanvasRecorderFactory.optimizeRender = true
        val recorder = CanvasRecorderFactory.create()
        try {
            assertTrue(
                "Expected CanvasRecorderApi23Impl on API 23-28",
                recorder is CanvasRecorderApi23Impl
            )
        } finally {
            recorder.recycle()
        }
    }
}
