package com.shuli.reader.core.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * WebDAV 真实网络环境集成测试。
 * 如果要启用该测试，请在 adb 命令行测试时传入凭据参数，例如:
 * adb shell am instrument -w -e class com.shuli.reader.core.sync.WebDavIntegrationTest \
 *   -e webdav_url "https://dav.jianguoyun.com/dav/" \
 *   -e webdav_username "your_email" \
 *   -e webdav_password "your_app_password" \
 *   com.shuli.reader.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class WebDavIntegrationTest {

    private var url = ""
    private var username = ""
    private var password = ""
    private var isConfigured = false

    @Before
    fun setUp() {
        val arguments = InstrumentationRegistry.getArguments()
        url = arguments.getString("webdav_url").orEmpty()
        username = arguments.getString("webdav_username").orEmpty()
        password = arguments.getString("webdav_password").orEmpty()
        
        isConfigured = url.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()
    }

    @Test
    fun testRealWebDavIntegration() {
        // 如果未通过 adb 提供真实的 WebDAV 服务器信息，测试将静默跳过 (Passed/Ignored)
        assumeTrue("已跳过 WebDAV 真实环境验证 (未提供 adb 账密参数)", isConfigured)

        val config = WebDavConfig(
            baseUrl = url,
            username = username,
            password = password
        )
        val client = WebDavClient(config)

        println("WebDAV: 正在尝试测试连接: $url")
        val connectSuccess = client.testConnection()
        assertTrue("WebDAV 真实连接失败，请确认配置", connectSuccess)
        println("WebDAV: 连接成功！")

        val testFileName = "test_integration_${UUID.randomUUID()}.txt"
        val testPayload = "ShuLi-Reader-WebDAV-Integration-Test-Payload-${System.currentTimeMillis()}"

        println("WebDAV: 正在上传测试文件: $testFileName")
        val putResponse = client.put(testFileName, testPayload)
        assertTrue("PUT 失败: ${putResponse.code}", putResponse.isSuccessful)

        println("WebDAV: 正在下载验证测试文件: $testFileName")
        val getResponse = client.get(testFileName)
        assertTrue("GET 失败: ${getResponse.code}", getResponse.isSuccessful)
        assertEquals("WebDAV 同步内容与写入内容不一致！", testPayload, getResponse.body)
        println("WebDAV: 读写一致性校验通过！")
    }
}
