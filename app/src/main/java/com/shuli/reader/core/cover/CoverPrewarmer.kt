package com.shuli.reader.core.cover

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import com.shuli.reader.core.repository.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * 封面图片预加载器，用于加速书架界面的图片渲染
 */
class CoverPrewarmer(
    private val bookRepository: BookRepository,
    private val context: Context,
) {
    /**
     * 在后台协程预热最近阅读的前 10 本书的封面图片
     */
    fun prewarm(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                // 获取书架上的书籍列表（第一页，通常是最近阅读的 10 本书）
                val books = bookRepository.getBookshelfPage(10, 0).first()
                books.forEach { book ->
                    book.coverPath?.let { path ->
                        val file = File(path)
                        if (file.exists() && file.isFile) {
                            val request = ImageRequest.Builder(context)
                                .data(file)
                                .build()
                            context.imageLoader.enqueue(request)
                        }
                    }
                }
            }
        }
    }
}
