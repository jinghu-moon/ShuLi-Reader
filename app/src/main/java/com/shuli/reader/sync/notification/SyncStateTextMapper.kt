package com.shuli.reader.sync.notification

import com.shuli.reader.core.i18n.AppStrings
import com.shuli.reader.sync.state.SyncState

/**
 * 同步状态文案映射器（T-25）
 */
object SyncStateTextMapper {

    fun map(state: SyncState, strings: AppStrings): String {
        return when (state) {
            SyncState.IDLE -> ""
            SyncState.SCANNING -> strings.sync.scanningLocalChanges
            SyncState.DOWNLOADING -> strings.sync.downloadingRemoteData
            SyncState.MERGING -> strings.sync.mergingData
            SyncState.UPLOADING -> strings.sync.uploadingBookmarksNotes
            SyncState.SUCCESS -> strings.sync.syncComplete
            SyncState.FAILED -> strings.sync.syncFailed
            SyncState.RATE_LIMITED -> strings.sync.rateLimitedWaitRetry
            SyncState.WAITING_RETRY -> strings.sync.waitingRetry
            SyncState.CRYPTO_LOCKED -> strings.sync.cryptoLocked
        }
    }
}
