package com.shuli.reader.feature.reader.component.quicksettings.v5.controls

import androidx.compose.ui.graphics.Color

internal fun onAccentColor(accent: Color): Color =
    if (accent.luminanceApprox() < 0.5f) Color.White else Color(0xFF1A130B)

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
