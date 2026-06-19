package com.shuli.reader.core.dictionary.engine

import com.shuli.reader.core.dictionary.model.DictEntry
import com.shuli.reader.core.dictionary.model.DictFormat
import com.shuli.reader.core.database.dao.DictMetaDao
import com.shuli.reader.core.database.entity.DictMetaEntity
import kotlinx.coroutines.Dispatchers
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

    /** LRU 缓存（word -> results） */
    private val cache = LruCache<String, List<DictEntry>>(256)

    /** 是否已初始化 */
    @Volatile
    private var initialized = false

    /**
     * 初始化：预加载所有启用词典的索引
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        val dicts = dictMetaDao.getEnabledDicts()
        dicts.forEach { entity ->
            try {
                loadDictionary(entity)
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
                // MDX 支持已在 mdict 模块实现，这里使用 MdictParser
                val mdictClass = try {
                    Class.forName("com.shuli.reader.mdict.MdictParser")
                } catch (e: ClassNotFoundException) {
                    null
                }
                if (mdictClass != null) {
                    val parser = mdictClass.getConstructor(String::class.java)
                        .newInstance(entity.filePath)
                    parser
                } else {
                    null
                }
            }
            else -> null
        }

        if (parser != null) {
            parsers[entity.dictKey] = parser
        }
    }

    /**
     * 智能查词
     *
     * 1. 尝试精确匹配
     * 2. 尝试词干提取后匹配
     * 3. 尝试中文前向最大匹配
     *
     * @param word 查询单词
     * @param timeoutMs 超时时间（毫秒）
     * @return 查询结果列表
     */
    suspend fun smartLookup(
        word: String,
        timeoutMs: Long = 3000,
    ): List<DictEntry> = withContext(Dispatchers.IO) {
        val normalized = WordNormalizer.normalize(word)

        // 检查缓存
        cache.get(normalized)?.let { return@withContext it }

        // 带超时的查询
        val results = withTimeoutOrNull(timeoutMs) {
            lookupInternal(normalized)
        } ?: emptyList()

        // 写入缓存
        if (results.isNotEmpty()) {
            cache.put(normalized, results)
        }

        results
    }

    /**
     * 内部查询实现
     */
    private suspend fun lookupInternal(word: String): List<DictEntry> {
        val results = mutableListOf<DictEntry>()

        // 1. 精确匹配
        for ((dictKey, parser) in parsers) {
            val entry = lookupInParser(parser, word)
            if (entry != null) {
                results.add(entry)
            }
        }

        // 2. 如果精确匹配无结果，尝试词干提取
        if (results.isEmpty() && WordNormalizer.containsChinese(word).not()) {
            val stemmed = EnglishStemmer.stem(word)
            if (stemmed != word) {
                for ((dictKey, parser) in parsers) {
                    val entry = lookupInParser(parser, stemmed)
                    if (entry != null) {
                        results.add(entry)
                    }
                }
            }
        }

        // 3. 如果是中文，尝试前向最大匹配
        if (results.isEmpty() && WordNormalizer.containsChinese(word)) {
            val matched = WordNormalizer.forwardMaxMatch(word, 0)
            if (matched != word && matched.length > 1) {
                for ((dictKey, parser) in parsers) {
                    val entry = lookupInParser(parser, matched)
                    if (entry != null) {
                        results.add(entry)
                    }
                }
            }
        }

        return results
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
                    val method = parser.javaClass.getMethod("lookup", String::class.java)
                    method.invoke(parser, word) as? DictEntry
                } catch (e: Exception) {
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
                    else -> emptyList()
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
