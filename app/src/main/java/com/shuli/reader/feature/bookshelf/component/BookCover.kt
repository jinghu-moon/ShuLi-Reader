package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.shuli.reader.ui.theme.AppSurface
import com.shuli.reader.ui.theme.MoTuInk300
import com.shuli.reader.ui.theme.MoTuInk600
import com.shuli.reader.ui.theme.MoTuInk700
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.ui.theme.MoTuInk800
import com.shuli.reader.ui.theme.MoTuInk950

/**
 * 墨土艺术 20 组色盘。色相围绕墨土暖灰棕主轴展开，按色族分布：
 * 燕麦米白(3) + 暖灰棕(4) + 陶土驼(3) + 茶木(3) + 雾蓝青(3) + 玫瑰沉粉(2) + 烟紫灰绿(2)。
 * 配合 [morandiPaletteIndex] 雪崩散列最大化书架视觉多样性。
 */
internal val MorandiPalettes = listOf(
    // 燕麦/米白系（参考 cover.html 燕麦色）
    listOf(Color(0xFFEAE8DF), Color(0xFFDFDCD2)),  // 0 燕麦米
    listOf(Color(0xFFEFEAD7), Color(0xFFDFD8BD)),  // 1 米黄
    listOf(Color(0xFFF2EFE8), Color(0xFFE5E0D5)),  // 2 暖白

    // 暖灰棕系（墨土主轴 T100→T400）
    listOf(Color(0xFFEAE5DC), Color(0xFFD4CCC0)),  // 3 浅灰棕
    listOf(Color(0xFFD4CCC0), Color(0xFFB9AFA0)),  // 4 中灰棕
    listOf(Color(0xFFB9AFA0), Color(0xFF9C9082)),  // 5 深灰棕
    listOf(Color(0xFFD8D4CC), Color(0xFFBFB8AC)),  // 6 雾灰

    // 陶土/驼系
    listOf(Color(0xFFE5C9B5), Color(0xFFCFA890)),  // 7 浅陶土
    listOf(Color(0xFFD5C29A), Color(0xFFA6906A)),  // 8 暖驼
    listOf(Color(0xFFDDC0A0), Color(0xFFBFA078)),  // 9 沙褐

    // 茶/木系
    listOf(Color(0xFFC8C7B2), Color(0xFFA6A48A)),  // 10 茶绿
    listOf(Color(0xFFCEB8A4), Color(0xFF9E8474)),  // 11 木棕
    listOf(Color(0xFFD8B898), Color(0xFFA88068)),  // 12 焦糖

    // 雾蓝/青系（低饱和）
    listOf(Color(0xFFC8D2D8), Color(0xFF98A8B5)),  // 13 雾霾蓝
    listOf(Color(0xFFBFC8C5), Color(0xFF889A95)),  // 14 海雾灰
    listOf(Color(0xFFC5D0CC), Color(0xFF98A8A0)),  // 15 远山青

    // 玫瑰/沉粉系
    listOf(Color(0xFFD8C5C0), Color(0xFFB5988F)),  // 16 玫瑰灰
    listOf(Color(0xFFD5B9B0), Color(0xFFB89388)),  // 17 沉粉

    // 烟紫/灰绿系
    listOf(Color(0xFFC8C2CC), Color(0xFF9C95A8)),  // 18 烟雾紫
    listOf(Color(0xFFC5C8B5), Color(0xFF99A088)),  // 19 灰绿
)

/**
 * 基于书名的雪崩散列（Murmur-3 风格 finalizer），把 String.hashCode() 在
 * 短中文字符串上的聚集分布打散，避免不同书名落入同一色盘 idx。
 */
private fun morandiPaletteIndex(title: String): Int {
    var h = title.hashCode()
    h = h xor (h ushr 16)
    h *= 0x7feb352d
    h = h xor (h ushr 15)
    h *= 0x846ca68bL.toInt()
    h = h xor (h ushr 16)
    return (h and Int.MAX_VALUE) % MorandiPalettes.size
}

/**
 * 兜底封面组件，使用莫兰迪渐变背景与半透明宋体首字优雅融合
 */
@Composable
fun DefaultBookCover(
    title: String,
    fileType: FileType,
    modifier: Modifier = Modifier,
    isSmall: Boolean = false,
    isMini: Boolean = false,
    isFavorite: Boolean = false,
    readingProgress: Float = 0f,
    paletteIndexOverride: Int? = null,
) {
    // 三模式优先级：override（统一/自定义）> 散列自动
    val paletteIndex = paletteIndexOverride?.coerceIn(0, MorandiPalettes.size - 1)
        ?: morandiPaletteIndex(title)
    val colors = MorandiPalettes[paletteIndex]
    
    val coverGradient = Brush.linearGradient(
        colors = colors
    )
    
    // 首字提取并大写（英文），跳过标点符号，寻找第一个有效字符（字母或数字）
    val firstChar = title.trim().find { it.isLetterOrDigit() }?.toString()?.uppercase() ?: ""

    // 浅灰边缘勾勒
    val coverBorder = BorderStroke(1.dp, MoTuInk300.copy(alpha = 0.6f))
    val textColor = MoTuInk800
    
    val strings = LocalAppStrings.current
    val tagColor = when (fileType) {
        FileType.EPUB -> MoTuInk700
        FileType.TXT -> MoTuInk600
    }

    if (isSmall) {
        // 1. 列表模式下展示极简图形化封面（含背景首字隐现）
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(coverGradient)
                .border(coverBorder, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            // 背景层微弱首字
            if (firstChar.isNotEmpty()) {
                Text(
                    text = firstChar,
                    fontFamily = FontFamily.Serif,
                    fontSize = 24.sp,
                    color = Color.White.copy(alpha = 0.25f),
                    fontWeight = FontWeight.Bold
                )
            }
            // 覆盖的格式标签印章
            Text(
                text = fileType.name,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = tagColor,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                    .border(BorderStroke(1.dp, tagColor.copy(alpha = 0.2f)), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
            // 收藏标记
            if (isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = strings.favorite,
                    tint = Color(0xFFE53935),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(10.dp)
                )
            }
        }
    } else {
        // 2. 网格模式下艺术派封面（正中央浮现巨大宋体首字 + 顶部四行书名 + 底部沉入式腰封）
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(coverGradient)
                .border(coverBorder, RoundedCornerShape(4.dp))
        ) {
            // 收藏标记
            if (isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = strings.favorite,
                    tint = Color(0xFFE53935),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(14.dp)
                )
            }
            // 中央半透明 Serif 巨大首字，实现极其高雅的图文交融感
            if (firstChar.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = if (isMini) 0.dp else 30.dp), // 避开底部腰封高度
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = firstChar,
                        fontFamily = FontFamily.Serif,
                        fontSize = if (isMini) 28.sp else 56.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.35f),
                        letterSpacing = 0.sp
                    )
                }
            }

            if (!isMini) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 顶部书名文字
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Text(
                            text = title,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textColor,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MoTuInk950.copy(alpha = 0.08f)
                    )

                    // 底部精致下沉式腰封
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .background(AppSurface.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 已移除左侧的圆点装饰

                        // 阅读进度百分比（仅 > 0% 时显示）
                        if (readingProgress > 0f) {
                            Text(
                                text = "${(readingProgress * 100).toInt()}%",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor.copy(alpha = 0.7f),
                                // 移除圆点后不再需要额外的 start padding
                                modifier = Modifier
                            )
                        }

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
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = title,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        color = textColor.copy(alpha = 0.9f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 10.sp
                    )
                }
            }
        }
    }
}
