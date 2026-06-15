package com.shuli.reader.feature.sync.settings

import com.shuli.reader.core.i18n.AppStrings
import com.shuli.reader.feature.sync.settings.LocalSyncPathFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

// Part of T-41 本地路径友好显示
class LocalSyncPathFormatterTest {

    private val strings = AppStrings.ZhHans

    @Test
    fun `primary storage URI is formatted as Documents slash ShuLiReader`() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FShuLiReader"
        val formatted = LocalSyncPathFormatter.format(uri, strings)
        assertEquals("Documents / ShuLiReader", formatted.displayPath)
        assertEquals("内部存储", formatted.storageLabel)
    }

    @Test
    fun `external storage URI shows external storage label`() {
        val uri = "content://com.android.externalstorage.documents/tree/ABCD-1234%3ADocuments%2FBackup"
        val formatted = LocalSyncPathFormatter.format(uri, strings)
        assertEquals("Documents / Backup", formatted.displayPath)
        assertEquals("外部存储", formatted.storageLabel)
    }

    @Test
    fun `custom labels are used when provided via AppStrings`() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FShuLiReader"
        val formatted = LocalSyncPathFormatter.format(uri, strings)
        assertEquals("Documents / ShuLiReader", formatted.displayPath)
        assertEquals(strings.sync.internalStorage, formatted.storageLabel)
    }
}
