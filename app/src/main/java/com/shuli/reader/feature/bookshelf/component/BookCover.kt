package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.feature.bookshelf.model.FileType

/**
 * 兜底封面组件，使用精美的风格三（下沉腰封风）
 */
@Composable
fun DefaultBookCover(
    title: String,
    fileType: FileType,
    modifier: Modifier = Modifier,
    isSmall: Boolean = false,
) {
    // 燕麦色微渐变（135度光影感）
    val coverGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFEAE8DF), Color(0xFFDFDCD2))
    )
    
    // 微弱的深色边框，勾勒边缘
    val coverBorder = BorderStroke(1.dp, Color(0xFFD2CEBE))
    
    // 统一文字主色为深灰
    val textColor = Color(0xFF2C2A26)
    
    // 根据文件格式选择对应的陶土色或铁灰色
    val tagColor = when (fileType) {
        FileType.EPUB -> Color(0xFF8A644D) // EPUB：温暖的陶土色
        FileType.TXT -> Color(0xFF6D757A)  // TXT：沉稳的铁灰色
    }

    if (isSmall) {
        // 1. 列表模式下的极简图形化封面（不显示书名，避免与右侧标题冗余，仅居中展示格式印章）
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(coverGradient)
                .border(coverBorder, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fileType.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = tagColor,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .border(BorderStroke(1.dp, tagColor.copy(alpha = 0.3f)), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    } else {
        // 2. 网格模式下的完整风格三封面（有书名和下沉腰封）
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(coverGradient)
                .border(coverBorder, RoundedCornerShape(4.dp))
        ) {
            // 书名区域（带有宋体加持，文学感拉满）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    text = title,
                    fontFamily = FontFamily.Serif, // 衬线宋体
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            // 腰封分割线 (1px solid rgba(0, 0, 0, 0.08))
            HorizontalDivider(
                thickness = 1.dp,
                color = Color(0x14000000)
            )

            // 下沉腰封风区域（底部稍微亮一点 rgba(255, 255, 255, 0.15)）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(Color(0x26FFFFFF))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 小圆点装饰
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(tagColor, CircleShape)
                )

                // 格式标签
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = fileType.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = tagColor,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
