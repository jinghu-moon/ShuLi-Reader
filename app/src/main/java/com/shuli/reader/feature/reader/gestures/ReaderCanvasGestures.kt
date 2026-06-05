package com.shuli.reader.feature.reader.gestures

import com.shuli.reader.core.reader.ReaderCanvasView
import com.shuli.reader.core.reader.animation.PageDelegate
import com.shuli.reader.feature.reader.ReaderViewModel

/**
 * 阅读器手势回调配置。
 *
 * 职责：将 ReaderCanvasView 的手势回调绑定到 ViewModel 方法。
 * 在 AndroidView factory 中调用 [bindCallbacks] 完成绑定。
 */
object ReaderCanvasGestures {

    /**
     * 绑定 CanvasView 手势回调到 ViewModel。
     */
    fun bindCallbacks(
        view: ReaderCanvasView,
        viewModel: ReaderViewModel,
    ) {
        view.onPageChanged = viewModel::handlePageDirection
        view.onPageChangedSlots = {
            viewModel.resolveHeaderAndFooterSlots()
        }
        view.onTextSelected = viewModel::selectText
        view.onCenterClicked = viewModel::toggleToolbar
    }
}
