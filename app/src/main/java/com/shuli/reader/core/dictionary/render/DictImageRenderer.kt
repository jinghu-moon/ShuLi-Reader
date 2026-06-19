package com.shuli.reader.core.dictionary.render

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.dictionary.engine.MddResourceLoader

/**
 * 词典图片渲染器
 *
 * 从 MDD 资源加载器中加载图片并显示
 */
object DictImageRenderer {

    /** 最大图片宽度 */
    private val MAX_IMAGE_WIDTH = 300.dp

    /** 最大图片高度 */
    private val MAX_IMAGE_HEIGHT = 200.dp

    /**
     * 渲染词典图片
     *
     * @param path 图片路径（如 \img\xxx.png）
     * @param resourceLoader MDD 资源加载器
     * @param modifier Modifier
     */
    @Composable
    fun RenderImage(
        path: String,
        resourceLoader: MddResourceLoader?,
        modifier: Modifier = Modifier,
    ) {
        if (resourceLoader == null) return

        val bitmap = resourceLoader.loadImage(path)
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = modifier
                    .widthIn(max = MAX_IMAGE_WIDTH)
                    .heightIn(max = MAX_IMAGE_HEIGHT)
                    .fillMaxWidth(),
            )
        }
    }

    /**
     * 检查路径是否为图片
     */
    fun isImagePath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".gif") ||
            lower.endsWith(".bmp") ||
            lower.endsWith(".webp")
    }
}
