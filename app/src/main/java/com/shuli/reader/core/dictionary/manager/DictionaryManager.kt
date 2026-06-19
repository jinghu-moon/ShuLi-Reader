package com.shuli.reader.core.dictionary.manager

import android.content.Context
import android.net.Uri
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

        // 检查同名 .mdd 文件
        val mddFile = File(mdxFile.parent, mdxFile.nameWithoutExtension + ".mdd")
        if (mddFile.exists()) {
            val destMdd = File(dictDir, mddFile.name)
            mddFile.copyTo(destMdd, overwrite = true)
        }

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
     * 从 SAF Uri 导入词典
     *
     * @param uris SAF 选择的文件 Uri 列表（用户可能同时选择 .ifo + .idx + .dict）
     * @return 导入的词典数量
     */
    suspend fun importFromUri(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var count = 0

        // 第一阶段：复制所有文件到词典目录
        val copiedFiles = mutableMapOf<String, String>() // fileName -> destPath
        for (uri in uris) {
            try {
                val fileName = getFileNameFromUri(uri) ?: continue
                val destFile = File(dictDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                copiedFiles[fileName] = destFile.absolutePath
            } catch (e: Exception) {
                android.util.Log.w("DictionaryManager", "Failed to copy: $uri", e)
            }
        }

        // 第二阶段：按文件类型分组处理
        val ifoFiles = copiedFiles.keys.filter { it.endsWith(".ifo") }
        val mdxFiles = copiedFiles.keys.filter { it.endsWith(".mdx") }

        // 处理 MDX 词典
        for (mdxFileName in mdxFiles) {
            val dictKey = fileNameWithoutExt(mdxFileName)
            val existing = dictMetaDao.getByKey(dictKey)
            if (existing == null) {
                // 检查同名 .mdd
                val mddFileName = "$dictKey.mdd"
                val mddPath = copiedFiles[mddFileName]

                dictMetaDao.insert(DictMetaEntity(
                    dictKey = dictKey,
                    displayName = dictKey,
                    format = "mdx",
                    filePath = copiedFiles[mdxFileName]!!,
                ))
                count++
            }
        }

        // 处理 Stardict 词典
        for (ifoFileName in ifoFiles) {
            val dictKey = fileNameWithoutExt(ifoFileName)
            val basePath = fileNameWithoutExt(ifoFileName)

            // 查找配套的 .idx 和 .dict/.dict.dz 文件
            val idxFileName = "$basePath.idx"
            val dictFileName = "$basePath.dict"
            val dictDzFileName = "$basePath.dict.dz"

            val idxPath = copiedFiles[idxFileName]
            val dictPath = copiedFiles[dictFileName]
            val dictDzPath = copiedFiles[dictDzFileName]

            // 检查文件是否齐全
            if (idxPath != null && (dictPath != null || dictDzPath != null)) {
                val existing = dictMetaDao.getByKey(dictKey)
                if (existing == null) {
                    dictMetaDao.insert(DictMetaEntity(
                        dictKey = dictKey,
                        displayName = dictKey,
                        format = "stardict",
                        filePath = copiedFiles[ifoFileName]!!,
                        indexPath = idxPath,
                        dataPath = dictDzPath ?: dictPath,
                    ))
                    count++
                }
            } else {
                android.util.Log.w("DictionaryManager", "Stardict files incomplete: $dictKey (idx=${idxPath != null}, dict=${dictPath != null}, dictDz=${dictDzPath != null})")
            }
        }

        // 处理单独的 .mdd 文件（可能配套已存在的 .mdx）
        for ((fileName, filePath) in copiedFiles) {
            if (fileName.endsWith(".mdd")) {
                val mdxKey = fileNameWithoutExt(fileName)
                val existingMdx = dictMetaDao.getByKey(mdxKey)
                if (existingMdx != null) {
                    // .mdx 已存在，.mdd 已复制到 dictDir，无需额外操作
                }
            }
        }

        // 重新初始化查询引擎
        if (count > 0) {
            lookupEngine.initialize()
        }

        count
    }

    /**
     * 从 Uri 获取文件名
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) it.getString(nameIndex) else null
            } else null
        }
    }

    /**
     * 获取不含扩展名的文件名
     */
    private fun fileNameWithoutExt(fileName: String): String {
        return fileName.substringBeforeLast('.')
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

            // 删除同名 .mdd 文件（MDX 词典的资源文件）
            if (entity.format == "mdx") {
                val mddFile = File(entity.filePath.substringBeforeLast('.') + ".mdd")
                if (mddFile.exists()) {
                    mddFile.delete()
                }
            }
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
