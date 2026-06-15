package com.shuli.reader.feature.sync.conflict

import com.shuli.reader.sync.engine.conflict.BookState

// Part of T-36 冲突解决弹窗
sealed class SyncUiEvent {
    data class ShowConflictDialog(
        val localState: BookState,
        val remoteState: BookState,
        val remoteDeviceName: String?,
    ) : SyncUiEvent()
}
