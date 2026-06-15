package com.shuli.reader.feature.reader.component.quicksettings.v5

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.feature.reader.component.quicksettings.v5.controls.GestureZoneGrid
import com.shuli.reader.feature.reader.settings.GestureConfig

/**
 * 阅读页触控区域编辑层。
 *
 * 从设置弹窗进入后覆盖在真实阅读界面上，九宫格直接对应屏幕点击区域。
 */
@Composable
fun GestureZoneEditorOverlay(
    config: GestureConfig,
    onConfigChange: (GestureConfig) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .testTag("GestureZoneEditorOverlay"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.28f))
                .statusBarsPadding()
                .padding(start = 20.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "点击区域设置",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("GestureZoneEditor_Close"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 2.dp, end = 2.dp, bottom = 2.dp),
        ) {
            GestureZoneGrid(
                config = config,
                onConfigChange = onConfigChange,
                modifier = Modifier.fillMaxSize(),
                fillHeight = true,
                showHint = false,
            )
        }
    }
}
