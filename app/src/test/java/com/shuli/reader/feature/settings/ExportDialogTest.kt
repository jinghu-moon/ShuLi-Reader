// Part of T-39 导出对话框
package com.shuli.reader.feature.settings

import com.shuli.reader.sync.export.ExportOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportDialogTest {

    @Test
    fun `default ExportOptions has all fields enabled`() {
        val options = ExportOptions()

        assertTrue("默认应包含书签", options.includeBookmarks)
        assertTrue("默认应包含笔记", options.includeNotes)
        assertTrue("默认应包含进度", options.includeProgress)
        assertTrue("默认应包含配置", options.includeConfig)
        assertTrue("默认应包含书籍文件", options.includeBookFiles)
        assertNull("默认不应有加密密码", options.encryptionPassword)
    }

    @Test
    fun `ExportOptions with custom values`() {
        val options = ExportOptions(
            includeBookFiles = false,
            includeBookmarks = true,
            includeNotes = false,
            includeProgress = true,
            includeConfig = false,
            encryptionPassword = "test123",
        )

        assertFalse("应不包含书籍文件", options.includeBookFiles)
        assertTrue("应包含书签", options.includeBookmarks)
        assertFalse("应不包含笔记", options.includeNotes)
        assertTrue("应包含进度", options.includeProgress)
        assertFalse("应不包含配置", options.includeConfig)
        assertEquals("密码应为 test123", "test123", options.encryptionPassword)
    }

    @Test
    fun `ExportOptions copy with password`() {
        val original = ExportOptions()
        val withPassword = original.copy(encryptionPassword = "newpass")

        assertNull("原始不应有密码", original.encryptionPassword)
        assertEquals("副本应有密码", "newpass", withPassword.encryptionPassword)
        // 其他字段应保持一致
        assertEquals(original.includeBookmarks, withPassword.includeBookmarks)
        assertEquals(original.includeNotes, withPassword.includeNotes)
    }
}
