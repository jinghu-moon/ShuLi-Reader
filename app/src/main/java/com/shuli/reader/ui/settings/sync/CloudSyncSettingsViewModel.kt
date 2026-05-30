package com.shuli.reader.ui.settings.sync

import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.sync.WebDavClient
import com.shuli.reader.core.sync.WebDavConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

// Part of T-34 云端同步设置子页
class CloudSyncSettingsViewModel(
    private val userPreferences: UserPreferences,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {

    private val _connectionTestResult = MutableSharedFlow<ConnectionTestResult>()
    val connectionTestResult: SharedFlow<ConnectionTestResult> = _connectionTestResult.asSharedFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    fun testConnection(url: String, username: String, password: String) {
        scope.launch {
            _isTesting.value = true
            try {
                val config = WebDavConfig(
                    baseUrl = url,
                    username = username,
                    password = password,
                )
                val client = WebDavClient(config)
                val response = client.propfind("", depth = 0)
                if (response.isSuccessful) {
                    _connectionTestResult.emit(ConnectionTestResult.SUCCESS)
                } else if (response.code == 401) {
                    _connectionTestResult.emit(ConnectionTestResult.AUTH_FAILED)
                } else {
                    _connectionTestResult.emit(ConnectionTestResult.UNKNOWN_ERROR)
                }
            } catch (e: IOException) {
                _connectionTestResult.emit(ConnectionTestResult.NETWORK_ERROR)
            } catch (e: Exception) {
                _connectionTestResult.emit(ConnectionTestResult.UNKNOWN_ERROR)
            } finally {
                _isTesting.value = false
            }
        }
    }

    suspend fun saveSyncSettings(url: String, username: String, password: String) {
        userPreferences.setWebdavUrl(url)
        userPreferences.setWebdavUser(username)
        userPreferences.setWebdavPassword(password)
        userPreferences.setSyncMethod("webdav")
    }
}
