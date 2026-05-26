package com.shuli.reader.feature.reader.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import kotlinx.coroutines.launch

/**
 * 通用选择面板：ModalBottomSheet + 单选列表
 *
 * @param title 面板标题
 * @param options 所有可选项，Pair<值, 显示文字>
 * @param selected 当前选中项
 * @param onSelect 选中回调
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PickerSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = readerColors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = readerColors.textSecondary) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = readerColors.textPrimary,
            )
            options.forEach { (key, optionLabel) ->
                val isSelected = key == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(key)
                            scope.launch {
                                sheetState.hide()
                            }.invokeOnCompletion {
                                if (!sheetState.isVisible) onDismiss()
                            }
                        }
                        .background(
                            if (isSelected) readerColors.accent.copy(alpha = 0.15f)
                            else Color.Transparent,
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左侧选中指示条
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(readerColors.accent),
                        )
                    }
                    Text(
                        text = optionLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) readerColors.accent else readerColors.textPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (isSelected) 12.dp else 15.dp),
                    )
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn() + scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            )
                        ),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = strings.selected,
                            tint = readerColors.accent,
                        )
                    }
                }
            }
        }
    }
}
