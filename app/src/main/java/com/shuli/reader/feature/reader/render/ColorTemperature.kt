package com.shuli.reader.feature.reader.render

import kotlin.math.ln
import kotlin.math.pow

/**
 * 色温（Kelvin）转 RGB 颜色值。
 *
 * 基于 Tanner Helland 的近似算法，将 1000K–40000K 色温映射为 (R, G, B) 三元组。
 * 用于阅读器色温叠加层的 MULTIPLY 混合模式渲染。
 *
 * @param temperature 色温值，单位 Kelvin（UI 滑块范围 3000–6500，算法接受 1000–40000）
 * @return Triple(R, G, B)，各分量 0–255
 */
fun colorTemperatureToRgb(temperature: Float): Triple<Int, Int, Int> {
    val temp = (temperature / 100f).coerceIn(10f, 400f)

    // Red
    val r = if (temp <= 66) {
        255
    } else {
        (329.698727446 * (temp - 60).toDouble().pow(-0.1332047592)).toInt().coerceIn(0, 255)
    }

    // Green
    val g = if (temp <= 66) {
        (99.4708025861 * ln(temp.toDouble()) - 161.1195681661).toInt().coerceIn(0, 255)
    } else {
        (288.1221695283 * (temp - 60).toDouble().pow(-0.0755148492)).toInt().coerceIn(0, 255)
    }

    // Blue
    val b = if (temp >= 66) {
        255
    } else if (temp <= 19) {
        0
    } else {
        (138.5177312231 * ln((temp - 10).toDouble()) - 305.0447927307).toInt().coerceIn(0, 255)
    }

    return Triple(r, g, b)
}
