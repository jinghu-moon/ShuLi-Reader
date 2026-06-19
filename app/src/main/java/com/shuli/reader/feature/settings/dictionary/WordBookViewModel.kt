package com.shuli.reader.feature.settings.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.database.dao.WordBookDao
import com.shuli.reader.core.database.entity.WordBookEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 生词本 ViewModel
 */
class WordBookViewModel(
    private val wordBookDao: WordBookDao,
) : ViewModel() {

    /** 生词列表 */
    val wordList: Flow<List<WordBookEntity>> = wordBookDao.getAllFlow()

    /**
     * 删除生词
     */
    fun deleteWord(wordId: Long) {
        viewModelScope.launch {
            try {
                val word = wordBookDao.getById(wordId) ?: return@launch
                wordBookDao.delete(word)
            } catch (e: Exception) {
                android.util.Log.e("WordBook", "Delete failed", e)
            }
        }
    }

    /**
     * 清空生词本
     */
    fun clearAll() {
        viewModelScope.launch {
            try {
                wordBookDao.clearAll()
            } catch (e: Exception) {
                android.util.Log.e("WordBook", "Clear failed", e)
            }
        }
    }
}
