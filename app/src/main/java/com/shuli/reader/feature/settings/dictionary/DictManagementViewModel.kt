package com.shuli.reader.feature.settings.dictionary

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.database.dao.DictMetaDao
import com.shuli.reader.core.database.entity.DictMetaEntity
import com.shuli.reader.core.dictionary.manager.DictionaryManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 词典管理 ViewModel
 */
class DictManagementViewModel(
    private val dictMetaDao: DictMetaDao,
    private val dictionaryManager: DictionaryManager,
) : ViewModel() {

    /** 词典列表 */
    val dictList: Flow<List<DictMetaEntity>> = dictMetaDao.getAllFlow()

    /**
     * 导入词典
     */
    fun importDictionaries(uris: List<Uri>) {
        viewModelScope.launch {
            try {
                val count = dictionaryManager.importFromUri(uris)
                android.util.Log.d("DictManagement", "Imported $count dictionaries")
            } catch (e: Exception) {
                android.util.Log.e("DictManagement", "Import failed", e)
            }
        }
    }

    /**
     * 删除词典
     */
    fun deleteDictionary(dictKey: String) {
        viewModelScope.launch {
            try {
                dictionaryManager.deleteDictionary(dictKey)
            } catch (e: Exception) {
                android.util.Log.e("DictManagement", "Delete failed", e)
            }
        }
    }

    /**
     * 切换启用状态
     */
    fun toggleEnabled(dictKey: String) {
        viewModelScope.launch {
            try {
                val entity = dictMetaDao.getByKey(dictKey) ?: return@launch
                dictMetaDao.setEnabled(entity.id, !entity.isEnabled)
            } catch (e: Exception) {
                android.util.Log.e("DictManagement", "Toggle failed", e)
            }
        }
    }
}
