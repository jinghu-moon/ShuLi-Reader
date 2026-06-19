package com.shuli.reader.core.dictionary.manager

import android.content.Context
import com.shuli.reader.core.database.dao.DictHistoryDao
import com.shuli.reader.core.database.dao.DictMetaDao
import com.shuli.reader.core.database.dao.WordBookDao
import com.shuli.reader.core.database.entity.DictHistoryEntity
import com.shuli.reader.core.database.entity.DictMetaEntity
import com.shuli.reader.core.database.entity.WordBookEntity
import com.shuli.reader.core.dictionary.engine.DictLookupEngine
import com.shuli.reader.core.dictionary.engine.StardictParser
import com.shuli.reader.core.dictionary.model.DictEntry
import com.shuli.reader.core.dictionary.model.DictFormat
import com.shuli.reader.core.dictionary.model.DictionaryMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 词典管理器
 *
 * 负责词库增删改查、索引加载/卸载、LRU 管理
 */
class DictionaryManager(
    private val context: Context,
    private val dictMetaDao: DictMetaDao,
    private val dictHistoryDao: DictHistoryDao,
    private val wordBookDao: WordBookDao,
) {
    /** 查询引擎 */
    private val lookupEngine = DictLookupEngine(dictMetaDao)

    /** 词典存储目录 */
    private val dictDir: File
        get() = File(context.filesDir, "dictionaries").also { it.mkdirs() }

    /**
     * 初始化
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        // 安装内置词典
        BuiltinDictInstaller.installIfNeeded(context, dictMetaDao)
        // 初始化查询引擎
        lookupEngine.initialize()
    }

    /**
     * 导入词典文件
     *
     * @param sourceFile 源文件（.ifo 或 .mdx）
     * @param displayName 显示名称
     * @return 导入结果
     */
    suspend fun importDictionary(
        sourceFile: File,
        displayName: String? = null,
    ): DictionaryMeta = withContext(Dispatchers.IO) {
        val format = DictFormat.fromFileName(sourceFile.name)
            ?: throw IllegalArgumentException("Unsupported format: ${sourceFile.name}")

        when (format) {
            DictFormat.STAR_DICT -> importStardict(sourceFile, displayName)
            DictFormat.MDX -> importMdx(sourceFile, displayName)
        }
    }

    /**
     * 导入 Stardict 词典
     */
    private suspend fun importStardict(
        ifoFile: File,
        displayName: String?,
    ): DictionaryMeta {
        val basePath = ifoFile.absolutePath.substringBeforeLast('.')
        val idxFile = File("$basePath.idx")
        val dictFile = File("$basePath.dict")
        val dictDzFile = File("$basePath.dict.dz")

        // 检查文件完整性
        if (!ifoFile.exists()) throw IllegalArgumentException("IFO file not found")
        if (!idxFile.exists()) throw IllegalArgumentException("IDX file not found")
        if (!dictFile.exists() && !dictDzFile.exists()) {
            throw IllegalArgumentException("Dict file not found")
        }

        // 复制文件到词典目录
        val destIfo = File(dictDir, ifoFile.name)
        ifoFile.copyTo(destIfo, overwrite = true)
        idxFile.copyTo(File(dictDir, idxFile.name), overwrite = true)
        if (dictFile.exists()) {
            dictFile.copyTo(File(dictDir, dictFile.name), overwrite = true)
        }
        if (dictDzFile.exists()) {
            dictDzFile.copyTo(File(dictDir, dictDzFile.name), overwrite = true)
        }

        // 解析词典
        val parser = StardictParser(destIfo.absolutePath)
        val meta = parser.loadIndex()
        parser.close()

        // 保存到数据库
        val entity = DictMetaEntity(
            dictKey = meta.dictKey,
            displayName = displayName ?: meta.displayName,
            format = "stardict",
            langPair = meta.langPair,
            filePath = destIfo.absolutePath,
            indexPath = File(dictDir, idxFile.name).absolutePath,
            dataPath = if (dictDzFile.exists()) {
                File(dictDir, dictDzFile.name).absolutePath
            } else {
                File(dictDir, dictFile.name).absolutePath
            },
            entryCount = meta.entryCount,
        )
        dictMetaDao.insert(entity)

        return meta
    }

    /**
     * 导入 MDX 词典
     */
    private suspend fun importMdx(
        mdxFile: File,
        displayName: String?,
    ): DictionaryMeta {
        val destMdx = File(dictDir, mdxFile.name)
        mdxFile.copyTo(destMdx, overwrite = true)

        val dictKey = mdxFile.nameWithoutExtension
        val entity = DictMetaEntity(
            dictKey = dictKey,
            displayName = displayName ?: mdxFile.nameWithoutExtension,
            format = "mdx",
            langPair = "",
            filePath = destMdx.absolutePath,
        )
        dictMetaDao.insert(entity)

        return DictionaryMeta(
            dictKey = dictKey,
            displayName = displayName ?: mdxFile.nameWithoutExtension,
            format = DictFormat.MDX,
            filePath = destMdx.absolutePath,
        )
    }

    /**
     * 删除词典
     */
    suspend fun deleteDictionary(dictKey: String) = withContext(Dispatchers.IO) {
        // 卸载解析器
        lookupEngine.unloadDictionary(dictKey)

        // 删除文件
        val entity = dictMetaDao.getByKey(dictKey)
        if (entity != null) {
            File(entity.filePath).delete()
            entity.indexPath?.let { File(it).delete() }
            entity.dataPath?.let { File(it).delete() }
        }

        // 删除数据库记录
        dictMetaDao.deleteByKey(dictKey)
    }

    /**
     * 启用/禁用词典
     */
    suspend fun setEnabled(dictKey: String, enabled: Boolean) {
        val entity = dictMetaDao.getByKey(dictKey) ?: return
        dictMetaDao.setEnabled(entity.id, enabled)
        if (!enabled) {
            lookupEngine.unloadDictionary(dictKey)
        } else {
            lookupEngine.initialize()
        }
    }

    /**
     * 设置词典优先级
     */
    suspend fun setPriority(dictKey: String, priority: Int) {
        val entity = dictMetaDao.getByKey(dictKey) ?: return
        dictMetaDao.setPriority(entity.id, priority)
    }

    /**
     * 查询单词
     */
    suspend fun lookup(word: String, contextSentence: String = ""): List<DictEntry> {
        val results = lookupEngine.smartLookup(word)

        // 记录查询历史
        if (results.isNotEmpty()) {
            dictHistoryDao.insert(DictHistoryEntity(
                word = word,
                contextSentence = contextSentence,
            ))
        }

        return results
    }

    /**
     * 前缀搜索
     */
    suspend fun searchByPrefix(prefix: String, limit: Int = 20): List<String> {
        return lookupEngine.searchByPrefix(prefix, limit)
    }

    /**
     * 添加到生词本
     */
    suspend fun addToWordBook(
        word: String,
        definition: String = "",
        contextSentence: String = "",
        bookId: Long = 0,
        chapterIndex: Int = 0,
        charOffset: Int = 0,
    ): Long {
        // 检查是否已存在
        val existing = wordBookDao.getByWord(word)
        if (existing != null) {
            return existing.id
        }

        return wordBookDao.insert(WordBookEntity(
            word = word,
            definition = definition,
            contextSentence = contextSentence,
            bookId = bookId,
            chapterIndex = chapterIndex,
            charOffset = charOffset,
        ))
    }

    /**
     * 从生词本移除
     */
    suspend fun removeFromWordBook(word: String) {
        val entity = wordBookDao.getByWord(word) ?: return
        wordBookDao.delete(entity)
    }

    /**
     * 检查单词是否在生词本中
     */
    suspend fun isInWordBook(word: String): Boolean {
        return wordBookDao.exists(word)
    }

    /**
     * 检查单词是否在生词本中（Flow）
     */
    fun isInWordBookFlow(word: String): Flow<Boolean> {
        return wordBookDao.existsFlow(word)
    }

    /**
     * 获取生词本列表
     */
    fun getWordBookFlow(): Flow<List<WordBookEntity>> {
        return wordBookDao.getAllFlow()
    }

    /**
     * 获取查词历史
     */
    fun getHistoryFlow(): Flow<List<DictHistoryEntity>> {
        return dictHistoryDao.getRecentFlow()
    }

    /**
     * 清空查词历史
     */
    suspend fun clearHistory() {
        dictHistoryDao.clearAll()
    }

    /**
     * 获取所有词典
     */
    fun getAllDictsFlow(): Flow<List<DictMetaEntity>> {
        return dictMetaDao.getAllFlow()
    }

    /**
     * 获取已启用词典
     */
    fun getEnabledDictsFlow(): Flow<List<DictMetaEntity>> {
        return dictMetaDao.getEnabledDictsFlow()
    }

    /**
     * 释放资源
     */
    fun release() {
        lookupEngine.unloadAll()
    }
}
