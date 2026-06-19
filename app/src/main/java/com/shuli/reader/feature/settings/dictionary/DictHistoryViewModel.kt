package com.shuli.reader.feature.settings.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.database.dao.DictHistoryDao
import com.shuli.reader.core.database.entity.DictHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 查词历史 ViewModel
 */
class DictHistoryViewModel(
    private val dictHistoryDao: DictHistoryDao,
) : ViewModel() {

    /** 历史列表 */
    val historyList: Flow<List<DictHistoryEntity>> = dictHistoryDao.getRecentFlow()

    /**
     * 删除单条记录
     */
    fun deleteItem(id: Long) {
        viewModelScope.launch {
            try {
                val item = dictHistoryDao.getById(id) ?: return@launch
                dictHistoryDao.delete(item)
            } catch (e: Exception) {
                android.util.Log.e("DictHistory", "Delete failed", e)
            }
        }
    }

    /**
     * 清空所有历史
     */
    fun clearAll() {
        viewModelScope.launch {
            try {
                dictHistoryDao.clearAll()
            } catch (e: Exception) {
                android.util.Log.e("DictHistory", "Clear failed", e)
            }
        }
    }
}
