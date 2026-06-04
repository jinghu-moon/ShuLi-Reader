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
            SyncState.SCANNING -> strings.scanningLocalChanges
            SyncState.DOWNLOADING -> strings.downloadingRemoteData
            SyncState.MERGING -> strings.mergingData
            SyncState.UPLOADING -> strings.uploadingBookmarksNotes
            SyncState.SUCCESS -> strings.syncComplete
            SyncState.FAILED -> strings.syncFailed
            SyncState.RATE_LIMITED -> strings.rateLimitedWaitRetry
            SyncState.WAITING_RETRY -> strings.waitingRetry
            SyncState.CRYPTO_LOCKED -> strings.cryptoLocked
        }
    }
}
