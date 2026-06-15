package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 自绘胶囊开关（对应原型 .toggle）。
 *
 * 42×24 胶囊轨道 + 18dp 圆点，开启时轨道填强调色、圆点右移，关闭时灰轨道、圆点左侧。
 * 取代 Material3 Switch，保证与 [InkSlider] 一致的墨土造型。
 *
 * @param checked 是否开启
 * @param onCheckedChange 切换回调；为 null 时只读不可点击
 */
@Composable
fun InkToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.divider,
        label = "InkToggle_track",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        label = "InkToggle_knob",
    )
    Box(
        modifier = modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
            .let { base ->
                if (onCheckedChange != null) {
                    base.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onCheckedChange(!checked) }
                } else {
                    base
                }
            }
            .testTag("InkToggle"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 3.dp)
                .offset(x = knobOffset)
                .size(18.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
