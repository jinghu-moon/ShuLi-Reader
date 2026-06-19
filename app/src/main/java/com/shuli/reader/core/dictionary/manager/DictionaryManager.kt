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
import com.shuli.reader.core.dictionary.engine.MddResourceLoader
import com.shuli.reader.core.dictionary.engine.StardictParser
import com.shuli.reader.core.dictionary.model.DictEntry
import com.shuli.reader.core.dictionary.model.DictFormat
import com.shuli.reader.core.dictionary.model.DictionaryMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 词典管理器
 *
 * 负责词库增删改查、索引加载/卸载、LRU 管理
 *
 * 词典存储位置：
 * - 内部存储：/data/data/<pkg>/files/dictionaries/
 * - 外部存储：/storage/emulated/0/ShuLi/dictionaries/
 *
 * 优先使用外部存储，用户可以通过文件管理器直接管理词典文件。
 */
class DictionaryManager(
    private val context: Context,
    private val dictMetaDao: DictMetaDao,
    private val dictHistoryDao: DictHistoryDao,
    private val wordBookDao: WordBookDao,
) {
    /** 查询引擎 */
    private val lookupEngine = DictLookupEngine(dictMetaDao)

    /** MDD 资源加载器缓存 */
    private val mddLoaders = ConcurrentHashMap<String, MddResourceLoader>()

    /**
     * 词典存储目录（优先外部存储）
     *
     * 外部存储：/storage/emulated/0/ShuLi/dictionaries/
     * 内部存储：/data/data/<pkg>/files/dictionaries/
     */
    val dictDir: File
        get() {
            // 优先使用外部存储
            val externalDir = File(
                android.os.Environment.getExternalStorageDirectory(),
                "ShuLi/dictionaries"
            )
            if (externalDir.exists() || externalDir.mkdirs()) {
                return externalDir
            }
            // 回退到内部存储
            return File(context.filesDir, "dictionaries").also { it.mkdirs() }
        }

    /**
     * 初始化
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        // 确保目录存在
        dictDir.mkdirs()

        // 安装内置词典
        BuiltinDictInstaller.installIfNeeded(context, dictMetaDao)

        // 扫描外部存储目录中的词典文件
        scanExternalDictionaries()

        // 初始化查询引擎
        lookupEngine.initialize()
    }

    /**
     * 扫描外部存储目录中的词典文件
     *
     * 支持两种目录结构：
     * 1. 扁平结构：dictionaries/词典名.mdx
     * 2. 子目录结构：dictionaries/词典名/词典名.mdx
     *
     * 自动发现并注册用户放入目录的词典
     */
    private suspend fun scanExternalDictionaries() = withContext(Dispatchers.IO) {
        val dir = dictDir
        if (!dir.exists()) return@withContext

        // 收集所有词典文件（支持子目录）
        val allIfoFiles = mutableListOf<File>()
        val allMdxFiles = mutableListOf<File>()

        // 扫描根目录
        dir.listFiles()?.forEach { file ->
            when {
                file.isFile && file.extension == "ifo" -> allIfoFiles.add(file)
                file.isFile && file.extension == "mdx" -> allMdxFiles.add(file)
                file.isDirectory -> {
                    // 扫描子目录
                    file.listFiles()?.forEach { subFile ->
                        when {
                            subFile.isFile && subFile.extension == "ifo" -> allIfoFiles.add(subFile)
                            subFile.isFile && subFile.extension == "mdx" -> allMdxFiles.add(subFile)
                        }
                    }
                }
            }
        }

        // 注册 Stardict 词典
        for (ifoFile in allIfoFiles) {
            val dictKey = ifoFile.nameWithoutExtension
            val existing = dictMetaDao.getByKey(dictKey)
            if (existing == null) {
                try {
                    registerStardictFromDir(ifoFile)
                } catch (e: Exception) {
                    android.util.Log.w("DictionaryManager", "Failed to register: $dictKey", e)
                }
            }
        }

        // 注册 MDX 词典
        for (mdxFile in allMdxFiles) {
            val dictKey = mdxFile.nameWithoutExtension
            val existing = dictMetaDao.getByKey(dictKey)
            if (existing == null) {
                try {
                    registerMdxFromDir(mdxFile)
                } catch (e: Exception) {
                    android.util.Log.w("DictionaryManager", "Failed to register: $dictKey", e)
                }
            }
        }
    }

    /**
     * 从目录注册 Stardict 词典
     */
    private suspend fun registerStardictFromDir(ifoFile: File) {
        val basePath = ifoFile.absolutePath.substringBeforeLast('.')
        val idxFile = File("$basePath.idx")
        val dictFile = File("$basePath.dict")
        val dictDzFile = File("$basePath.dict.dz")

        // 检查文件完整性
        if (!idxFile.exists()) return
        if (!dictFile.exists() && !dictDzFile.exists()) return

        // 解析 .ifo 获取词典信息
        val info = parseIfFile(ifoFile)

        val entity = DictMetaEntity(
            dictKey = ifoFile.nameWithoutExtension,
            displayName = info.bookName.ifBlank { ifoFile.nameWithoutExtension },
            format = "stardict",
            langPair = info.langPair,
            filePath = ifoFile.absolutePath,
            indexPath = idxFile.absolutePath,
            dataPath = if (dictDzFile.exists()) dictDzFile.absolutePath else dictFile.absolutePath,
            entryCount = info.wordCount,
            isEnabled = true,
            priority = 0,
        )

        dictMetaDao.insert(entity)
    }

    /**
     * 从目录注册 MDX 词典
     *
     * 尝试从 MDX header 中读取 Title 作为显示名称
     */
    private suspend fun registerMdxFromDir(mdxFile: File) {
        val dictKey = mdxFile.nameWithoutExtension

        // 尝试从 MDX header 获取真实标题
        val title = try {
            getMdxTitle(mdxFile)
        } catch (e: Exception) {
            null
        }

        val entity = DictMetaEntity(
            dictKey = dictKey,
            displayName = title?.takeIf { it.isNotBlank() } ?: dictKey,
            format = "mdx",
            filePath = mdxFile.absolutePath,
        )

        dictMetaDao.insert(entity)
    }

    /**
     * 从 MDX 文件读取标题
     *
     * 通过反射调用 mdict 模块的 HeaderParser
     */
    private fun getMdxTitle(mdxFile: File): String? {
        return try {
            val mdictClass = Class.forName("com.shuli.reader.mdict.MdictParser")
            val openMethod = mdictClass.getMethod("open", java.io.File::class.java, java.io.File::class.java)
            val parser = openMethod.invoke(null, mdxFile, null)

            // 获取 header.title
            val headerField = parser.javaClass.getDeclaredField("header")
            headerField.isAccessible = true
            val header = headerField.get(parser)

            val titleField = header.javaClass.getMethod("getTitle")
            val title = titleField.invoke(header) as? String

            // 关闭 parser
            val closeMethod = parser.javaClass.getMethod("close")
            closeMethod.invoke(parser)

            title
        } catch (e: Exception) {
            android.util.Log.w("DictionaryManager", "Failed to get MDX title", e)
            null
        }
    }

    /**
     * 简单解析 .ifo 文件
     */
    private fun parseIfFile(file: File): IfInfo {
        val info = IfInfo()
        file.readLines().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                when (key) {
                    "bookname" -> info.bookName = value
                    "wordcount" -> info.wordCount = value.toIntOrNull() ?: 0
                }
            }
        }

        val name = info.bookName.lowercase()
        info.langPair = when {
            "cedict" in name || "chinese-english" in name || "汉英" in name -> "zh-en"
            "ecdict" in name || "english-chinese" in name || "英汉" in name -> "en-zh"
            "成语" in name -> "zh-zh"
            else -> ""
        }

        return info
    }

    private data class IfInfo(
        var bookName: String = "",
        var wordCount: Int = 0,
        var langPair: String = "",
    )

    /**
     * 获取 MDD 资源加载器
     *
     * @param dictKey 词典标识
     * @return MDD 资源加载器，不存在返回 null
     */
    fun getMddResourceLoader(dictKey: String): MddResourceLoader? {
        // 检查缓存
        mddLoaders[dictKey]?.let { return it }

        // 查找 MDD 文件
        val mddFile = File(dictDir, "$dictKey.mdd")
        if (!mddFile.exists()) return null

        // 创建加载器
        val loader = MddResourceLoader.fromMddFile(mddFile.absolutePath)
        if (loader != null) {
            mddLoaders[dictKey] = loader
        }

        return loader
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
        val synFile = File("$basePath.syn")

        // 检查文件完整性
        if (!ifoFile.exists()) throw IllegalArgumentException("IFO file not found")
        if (!idxFile.exists()) throw IllegalArgumentException("IDX file not found")
        if (!dictFile.exists() && !dictDzFile.exists()) {
            throw IllegalArgumentException("Dict file not found")
        }

        // 验证 idxfilesize（设计文档 §5.1 要求）
        validateIdxFileSize(ifoFile, idxFile)

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
        // 复制 .syn 同义词文件（可选）
        if (synFile.exists()) {
            synFile.copyTo(File(dictDir, synFile.name), overwrite = true)
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
     * 验证 idxfilesize（设计文档 §5.1）
     *
     * 解析 .ifo 中的 idxfilesize 并与实际 .idx 文件大小比对
     */
    private fun validateIdxFileSize(ifoFile: File, idxFile: File) {
        val expectedSize = ifoFile.readLines()
            .firstOrNull { it.startsWith("idxfilesize=") }
            ?.substringAfter("=")
            ?.trim()
            ?.toLongOrNull()

        if (expectedSize != null && expectedSize != idxFile.length()) {
            throw IllegalArgumentException(
                "IDX file size mismatch: expected $expectedSize, actual ${idxFile.length()}. File may be corrupted."
            )
        }
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

            // 查找配套的 .idx、.dict/.dict.dz、.syn 文件
            val idxFileName = "$basePath.idx"
            val dictFileName = "$basePath.dict"
            val dictDzFileName = "$basePath.dict.dz"
            val synFileName = "$basePath.syn"

            val idxPath = copiedFiles[idxFileName]
            val dictPath = copiedFiles[dictFileName]
            val dictDzPath = copiedFiles[dictDzFileName]
            val synPath = copiedFiles[synFileName]

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
