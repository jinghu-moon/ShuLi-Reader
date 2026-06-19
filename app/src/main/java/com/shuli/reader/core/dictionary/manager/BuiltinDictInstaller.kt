package com.shuli.reader.core.dictionary.manager

import android.content.Context
import com.shuli.reader.core.database.dao.DictMetaDao
import com.shuli.reader.core.database.entity.DictMetaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 内置词典安装器
 *
 * 从 assets 解压内置词典到 filesDir
 */
object BuiltinDictInstaller {

    /** 内置词典资产目录 */
    private const val DICT_ASSETS_DIR = "dictionaries"

    /** 是否已安装的标记文件 */
    private const val INSTALLED_MARKER = ".dict_installed"

    /**
     * 检查并安装内置词典
     */
    suspend fun installIfNeeded(
        context: Context,
        dictMetaDao: DictMetaDao,
    ) = withContext(Dispatchers.IO) {
        val dictDir = File(context.filesDir, "dictionaries").also { it.mkdirs() }
        val marker = File(dictDir, INSTALLED_MARKER)

        // 如果已安装且词典数量匹配，跳过
        if (marker.exists() && dictMetaDao.getCount() > 0) {
            return@withContext
        }

        // 遍历 assets/dictionaries 目录
        try {
            val assets = context.assets.list(DICT_ASSETS_DIR) ?: emptyArray()

            // 第一阶段：复制所有文件到 filesDir
            for (fileName in assets) {
                if (fileName == INSTALLED_MARKER) continue
                val assetPath = "$DICT_ASSETS_DIR/$fileName"
                val destFile = File(dictDir, fileName)
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // 第二阶段：注册所有 .ifo 词典（此时 .idx/.dict 已确保存在）
            for (fileName in assets.filter { it.endsWith(".ifo") }) {
                registerStardict(dictMetaDao, File(dictDir, fileName))
            }

            // 写入安装标记
            marker.createNewFile()

        } catch (e: Exception) {
            // assets 目录可能不存在，忽略
            android.util.Log.w("BuiltinDictInstaller", "No built-in dictionaries found", e)
        }
    }

    /**
     * 注册 Stardict 词典到数据库
     */
    private suspend fun registerStardict(
        dictMetaDao: DictMetaDao,
        ifoFile: File,
    ) {
        val basePath = ifoFile.absolutePath.substringBeforeLast('.')
        val idxFile = File("$basePath.idx")
        val dictFile = File("$basePath.dict")
        val dictDzFile = File("$basePath.dict.dz")

        // 检查文件完整性
        if (!idxFile.exists()) return
        if (!dictFile.exists() && !dictDzFile.exists()) return

        // 解析 .ifo 文件获取词典信息
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

        // 推断语言对
        val name = info.bookName.lowercase()
        info.langPair = when {
            "cedict" in name || "chinese-english" in name || "汉英" in name -> "zh-en"
            "ecdict" in name || "english-chinese" in name || "英汉" in name -> "en-zh"
            else -> ""
        }

        return info
    }

    private data class IfInfo(
        var bookName: String = "",
        var wordCount: Int = 0,
        var langPair: String = "",
    )
}
