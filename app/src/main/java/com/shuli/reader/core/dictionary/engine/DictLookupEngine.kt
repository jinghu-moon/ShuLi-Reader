package com.shuli.reader.core.dictionary.engine

import com.shuli.reader.core.dictionary.model.DefinitionType
import com.shuli.reader.core.dictionary.model.DictEntry
import com.shuli.reader.core.dictionary.model.DictFormat
import com.shuli.reader.core.database.dao.DictMetaDao
import com.shuli.reader.core.database.entity.DictMetaEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * 词典查询引擎
 *
 * 支持多词典聚合查询、LRU 缓存、分层超时、取消支持
 */
class DictLookupEngine(
    private val dictMetaDao: DictMetaDao,
) {
    /** 已加载的词典解析器（dictKey -> parser） */
    private val parsers = ConcurrentHashMap<String, Any>()

    /** 已加载词典的元数据（用于排序） */
    private val dictMetaMap = ConcurrentHashMap<String, DictMetaEntity>()

    /** LRU 缓存（word -> results） */
    private val cache = LruCache<String, List<DictEntry>>(256)

    /** 是否已初始化 */
    @Volatile
    private var initialized = false

    /** 当前查询 Job（用于取消） */
    @Volatile
    private var currentLookupJob: Job? = null

    companion object {
        /** 首次查询超时（冷启动） */
        private const val COLD_TIMEOUT_MS = 500L
        /** 热路径查询超时 */
        private const val HOT_TIMEOUT_MS = 100L
        /** 完整查询超时（无结果时的兜底） */
        private const val FULL_TIMEOUT_MS = 2000L
    }

    /**
     * 初始化：预加载所有启用词典的索引
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        val dicts = dictMetaDao.getEnabledDicts()
        dicts.forEach { entity ->
            try {
                loadDictionary(entity)
                dictMetaMap[entity.dictKey] = entity
            } catch (e: Exception) {
                // 加载失败，跳过
                android.util.Log.w("DictLookupEngine", "Failed to load dict: ${entity.dictKey}", e)
            }
        }

        initialized = true
    }

    /**
     * 加载词典
     */
    private suspend fun loadDictionary(entity: DictMetaEntity) = withContext(Dispatchers.IO) {
        val parser = when (entity.format) {
            "stardict" -> {
                val stardict = StardictParser(entity.filePath)
                stardict.loadIndex()
                stardict
            }
            "mdx" -> {
                // MDX 支持已在 mdict 模块实现
                // MdictParser.open(file: File, cacheDir: File? = null): MdictParser
                loadMdxParser(entity.filePath, entity.dictKey)
            }
            else -> null
        }

        if (parser != null) {
            parsers[entity.dictKey] = parser
        }
    }

    /**
     * 通过反射加载 MDX 词典
     *
     * MdictParser.open 是普通函数（非 suspend），签名：
     * fun open(file: File, cacheDir: File? = null): MdictParser
     */
    private fun loadMdxParser(filePath: String, dictKey: String): Any? {
        return try {
            val mdictClass = Class.forName("com.shuli.reader.mdict.MdictParser")
            val companionField = mdictClass.getDeclaredField("Companion")
            val companion = companionField.get(null)
            val companionClass = companion.javaClass

            // open(file: File, cacheDir: File?)
            val openMethod = companionClass.getMethod(
                "open",
                java.io.File::class.java,
                java.io.File::class.java,
            )
            openMethod.invoke(companion, java.io.File(filePath), null)
        } catch (e: Exception) {
            android.util.Log.w("DictLookupEngine", "Failed to load MDX: $dictKey", e)
            null
        }
    }

    /**
     * 取消当前查询
     */
    fun cancelPending() {
        currentLookupJob?.cancel()
        currentLookupJob = null
    }

    /**
     * 智能查词
     *
     * 1. 先查缓存
     * 2. 快速查询（热路径超时）
     * 3. 如果无结果，完整查询（冷启动超时）
     *
     * @param word 查询单词
     * @param isColdStart 是否为冷启动（首次查询）
     * @return 查询结果列表（按词典优先级排序）
     */
    suspend fun smartLookup(
        word: String,
        isColdStart: Boolean = false,
    ): List<DictEntry> = withContext(Dispatchers.IO) {
        val normalized = WordNormalizer.normalize(word)

        // 检查缓存
        cache.get(normalized)?.let { return@withContext it }

        // 分层超时
        val timeoutMs = if (isColdStart) COLD_TIMEOUT_MS else HOT_TIMEOUT_MS

        // 快速查询
        var results = withTimeoutOrNull(timeoutMs) {
            lookupInternal(normalized)
        }

        // 如果快速查询无结果，尝试完整查询
        if (results.isNullOrEmpty() && !isColdStart) {
            results = withTimeoutOrNull(FULL_TIMEOUT_MS) {
                lookupInternal(normalized)
            }
        }

        val finalResults = results ?: emptyList()

        // 写入缓存
        if (finalResults.isNotEmpty()) {
            cache.put(normalized, finalResults)
        }

        finalResults
    }

    /**
     * 内部查询实现
     */
    private suspend fun lookupInternal(word: String): List<DictEntry> {
        val results = mutableListOf<DictEntry>()

        // 1. 精确匹配
        for ((dictKey, parser) in parsers) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive() // 检查取消
            val entry = lookupInParser(parser, word)
            if (entry != null) {
                results.add(entry.copy(dictKey = dictKey, dictName = dictMetaMap[dictKey]?.displayName ?: dictKey))
            }
        }

        // 2. 如果精确匹配无结果，尝试词干候选（英文）
        if (results.isEmpty() && !WordNormalizer.containsChinese(word)) {
            val candidates = EnglishStemmer.stemCandidates(word)
            for (candidate in candidates) {
                if (candidate == word) continue
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                for ((dictKey, parser) in parsers) {
                    val entry = lookupInParser(parser, candidate)
                    if (entry != null) {
                        results.add(entry.copy(
                            dictKey = dictKey,
                            dictName = dictMetaMap[dictKey]?.displayName ?: dictKey,
                            isSynonymMatch = true,
                        ))
                    }
                }
                // 找到结果就停止尝试其他候选
                if (results.isNotEmpty()) break
            }
        }

        // 3. 如果是中文，尝试前向最大匹配
        if (results.isEmpty() && WordNormalizer.containsChinese(word)) {
            val matched = WordNormalizer.forwardMaxMatch(word, 0, { candidate ->
                parsers.values.any { parser -> lookupInParser(parser, candidate) != null }
            })
            if (matched != word && matched.length > 1) {
                for ((dictKey, parser) in parsers) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val entry = lookupInParser(parser, matched)
                    if (entry != null) {
                        results.add(entry.copy(
                            dictKey = dictKey,
                            dictName = dictMetaMap[dictKey]?.displayName ?: dictKey,
                            isSynonymMatch = true,
                        ))
                    }
                }
            }
        }

        // 按词典优先级排序
        return results.sortedBy { entry ->
            dictMetaMap[entry.dictKey]?.priority ?: Int.MAX_VALUE
        }
    }

    /**
     * 在指定解析器中查询
     */
    private fun lookupInParser(parser: Any, word: String): DictEntry? {
        return when (parser) {
            is StardictParser -> parser.lookup(word)
            else -> {
                // MDX 通过反射调用
                try {
                    // MdictParser.lookup(word: String): MdxEntry?
                    val lookupMethod = parser.javaClass.getMethod("lookup", String::class.java)
                    val mdxEntry = lookupMethod.invoke(parser, word)

                    if (mdxEntry != null) {
                        // MdictParser.readDefinition(entry: MdxEntry, depth: Int = 0): String
                        // Kotlin 默认参数不生成重载，需要显式传入所有参数
                        val readDefMethod = parser.javaClass.getMethod(
                            "readDefinition",
                            mdxEntry.javaClass,
                            Int::class.java,
                        )
                        val definition = readDefMethod.invoke(parser, mdxEntry, 0) as? String ?: ""

                        DictEntry(
                            word = word,
                            definition = definition,
                            dictKey = "",
                            dictName = "",
                            definitionType = if (definition.contains("<") && definition.contains(">")) DefinitionType.HTML else DefinitionType.TEXT,
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.w("DictLookupEngine", "MDX lookup failed for: $word", e)
                    null
                }
            }
        }
    }

    /**
     * 前缀搜索
     */
    suspend fun searchByPrefix(prefix: String, limit: Int = 20): List<String> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<String>()

            for ((_, parser) in parsers) {
                val words = when (parser) {
                    is StardictParser -> parser.searchByPrefix(prefix, limit)
                    else -> {
                        try {
                            val method = parser.javaClass.getMethod("prefixRange", String::class.java, Int::class.java)
                            val entries = method.invoke(parser, prefix, limit) as? List<*>
                            entries?.mapNotNull { entry ->
                                entry?.javaClass?.getMethod("getWord")?.invoke(entry) as? String
                            } ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
                results.addAll(words)
            }

            results.distinct().take(limit)
        }

    /**
     * 卸载词典
     */
    fun unloadDictionary(dictKey: String) {
        val parser = parsers.remove(dictKey)
        dictMetaMap.remove(dictKey)
        when (parser) {
            is StardictParser -> parser.close()
            is AutoCloseable -> parser.close()
        }
        cache.evictAll()
    }

    /**
     * 卸载所有词典
     */
    fun unloadAll() {
        parsers.forEach { (_, parser) ->
            when (parser) {
                is StardictParser -> parser.close()
                is AutoCloseable -> parser.close()
            }
        }
        parsers.clear()
        dictMetaMap.clear()
        cache.evictAll()
        initialized = false
    }

    /**
     * 获取已加载的词典数量
     */
    fun getLoadedDictCount(): Int = parsers.size

    /**
     * LRU 缓存实现
     */
    private class LruCache<K, V>(private val maxSize: Int) {
        private val cache = LinkedHashMap<K, V>(maxSize, 0.75f, true)

        @Synchronized
        fun get(key: K): V? = cache[key]

        @Synchronized
        fun put(key: K, value: V) {
            cache[key] = value
            if (cache.size > maxSize) {
                val eldest = cache.entries.iterator().next()
                cache.remove(eldest.key)
            }
        }

        @Synchronized
        fun evictAll() = cache.clear()
    }
}
