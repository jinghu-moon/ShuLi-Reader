package com.shuli.reader.ui.settings

import com.shuli.reader.ui.settings.sync.LocalSyncPathFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

// Part of T-41 本地路径友好显示
class LocalSyncPathFormatterTest {

    @Test
    fun `primary storage URI is formatted as Documents slash ShuLiReader`() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FShuLiReader"
        val formatted = LocalSyncPathFormatter.format(uri)
        assertEquals("Documents / ShuLiReader", formatted.displayPath)
        assertEquals("内部存储", formatted.storageLabel)
    }

    @Test
    fun `external storage URI shows external storage label`() {
        val uri = "content://com.android.externalstorage.documents/tree/ABCD-1234%3ADocuments%2FBackup"
        val formatted = LocalSyncPathFormatter.format(uri)
        assertEquals("Documents / Backup", formatted.displayPath)
        assertEquals("外部存储", formatted.storageLabel)
    }

    @Test
    fun `cancel sync explanation text is correct`() {
        val explanation = LocalSyncPathFormatter.getCancelSyncExplanation()
        assertEquals("取消不会丢失已完成的部分，下次同步时继续", explanation)
    }
}
