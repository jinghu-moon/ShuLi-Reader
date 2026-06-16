package com.shuli.reader.core.reader.engine

import android.graphics.Paint

/**
 * 画笔的不可变快照。
 *
 * Phase 3: 在 submitRenderTask 入口处（主线程）创建 Paint 冻结副本，
 * 传递给后台线程的 StatelessReaderPageRenderer 使用。
 * 后台线程只使用这些副本，不接触主线程可变的 Paint 实例。
 *
 * 每个 Paint 是通过 Paint(src) 构造函数创建的独立副本，
 * 保留了原 Paint 的全部属性（color、textSize、typeface、letterSpacing 等）。
 */
data class PaintSnapshot(
    val text: Paint,
    val background: Paint,
    val selection: Paint,
    val header: Paint,
    val footer: Paint,
    val progress: Paint,
)

fun createPaintSnapshot(
    textPaint: Paint,
    backgroundPaint: Paint,
    selectionPaint: Paint,
    headerPaint: Paint,
    footerPaint: Paint,
    progressPaint: Paint,
): PaintSnapshot = PaintSnapshot(
    text = Paint(textPaint),
    background = Paint(backgroundPaint),
    selection = Paint(selectionPaint),
    header = Paint(headerPaint),
    footer = Paint(footerPaint),
    progress = Paint(progressPaint),
)
