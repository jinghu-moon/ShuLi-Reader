package com.shuli.reader.core.reader

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.TextPage
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderCanvasViewTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun readerCanvasView_canBeCreated() {
        val view = ReaderCanvasView(context)
        assertNotNull("View 应能创建", view)
    }

    @Test
    fun readerCanvasView_canSetPage() {
        val view = ReaderCanvasView(context)
        val page = TextPage(
            startCharOffset = 0,
            endCharOffset = 100,
            chapterIndex = 0,
            pageIndex = 0,
            pageSize = PageSize(1080, 1920),
            marginHorizontal = 24f,
            lines = emptyList(),
            columns = emptyList(),
        )

        view.setPage(page)
        // 如果没有异常，测试通过
    }

    @Test
    fun readerCanvasView_canSetTheme() {
        val view = ReaderCanvasView(context)

        view.setTheme(
            backgroundColor = 0xFFEAE5DC.toInt(),
            textColor = 0xFF2C231A.toInt(),
            headerColor = 0xFF7D7162.toInt(),
            footerColor = 0xFF7D7162.toInt(),
            progressColor = 0xFF453B2E.toInt(),
        )
        // 如果没有异常，测试通过
    }
}
