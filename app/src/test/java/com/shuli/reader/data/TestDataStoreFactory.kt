package com.shuli.reader.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * 测试专用 DataStore 工厂。
 * 创建临时文件存储，测试结束后自动清理，避免污染真实用户设置。
 * 使用 Dispatchers.Unconfined 确保测试中 DataStore 操作同步执行。
 */
object TestDataStoreFactory {

    fun create(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)): DataStore<Preferences> {
        val tempFile = File.createTempFile("test_preferences", ".preferences_pb")
        tempFile.deleteOnExit()
        return PreferenceDataStoreFactory.create(
            scope = scope,
        ) { tempFile }
    }
}
