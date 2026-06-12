package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.database.entity.BookReaderPrefsOverrides
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * BookReaderPrefsOverrides 反序列化容错测试（T-0a.7.1~7.3）。
 *
 * 验证：
 * - `ignoreUnknownKeys = true`：未知字段静默忽略
 * - `coerceInputValues = true`：null → 默认值，未知枚举值 → 默认值
 * - 类型不匹配（string → float 等）抛异常，由调用方 try-catch 兜底
 */
class BookReaderPrefsOverridesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
    }

    // T-0a.7.1: 未知字段不抛异常
    @Test
    fun deserialize_unknownFields_ignored() {
        val dirtyJson = """
            {
                "fontSize": 20.0,
                "unknownField1": "garbage",
                "unknownField2": 999,
                "unknownNested": {"a": 1, "b": [2, 3]}
            }
        """.trimIndent()

        val overrides = json.decodeFromString(BookReaderPrefsOverrides.serializer(), dirtyJson)

        assertNotNull(overrides)
        assertEquals(20.0f, overrides.fontSize)
    }

    // T-0a.7.2: coerceInputValues 处理 null → 默认值
    @Test
    fun deserialize_nullForNullable_coerceToNull() {
        val jsonWithNulls = """
            {
                "fontSize": null,
                "readingFont": null,
                "bionicReading": null,
                "gestureConfig": null
            }
        """.trimIndent()

        val overrides = json.decodeFromString(BookReaderPrefsOverrides.serializer(), jsonWithNulls)

        assertNotNull(overrides)
        assertNull(overrides.fontSize)
        assertNull(overrides.readingFont)
        assertNull(overrides.bionicReading)
        assertNull(overrides.gestureConfig)
    }

    // T-0a.7.2b: 类型不匹配抛异常（调用方 try-catch 兜底）
    @Test
    fun deserialize_typeMismatch_throwsException() {
        val dirtyJson = """
            {
                "fontSize": "not_a_number"
            }
        """.trimIndent()

        try {
            json.decodeFromString(BookReaderPrefsOverrides.serializer(), dirtyJson)
            fail("Expected JsonDecodingException for type mismatch")
        } catch (_: Exception) {
            // 类型不匹配抛异常，ReaderSettingsManager 的 try-catch 兜底回退到全局默认
        }
    }

    // T-0a.7.3: 有效字段正确解析（单字段异常由调用方 try-catch 保护）
    @Test
    fun deserialize_validFields_allPreserved() {
        val validJson = """
            {
                "fontSize": 18.0,
                "readingFont": "serif",
                "marginHorizontal": 32.0,
                "colorTemperature": 4000.0,
                "bionicReading": true
            }
        """.trimIndent()

        val overrides = json.decodeFromString(BookReaderPrefsOverrides.serializer(), validJson)

        assertNotNull(overrides)
        assertEquals(18.0f, overrides.fontSize)
        assertEquals("serif", overrides.readingFont)
        assertEquals(32.0f, overrides.marginHorizontal)
        assertEquals(4000.0f, overrides.colorTemperature)
        assertEquals(true, overrides.bionicReading)
    }

    @Test
    fun deserialize_emptyJson_allNull() {
        val overrides = json.decodeFromString(BookReaderPrefsOverrides.serializer(), "{}")

        assertNotNull(overrides)
        assertNull(overrides.fontSize)
        assertNull(overrides.readingFont)
        assertNull(overrides.gestureConfig)
    }

    @Test
    fun deserialize_deprecatedBlueLightFilter_ignored() {
        val oldJson = """
            {
                "fontSize": 16.0,
                "blueLightFilter": true,
                "colorTemperature": 3400.0
            }
        """.trimIndent()

        val overrides = json.decodeFromString(BookReaderPrefsOverrides.serializer(), oldJson)

        assertNotNull(overrides)
        assertEquals(16.0f, overrides.fontSize)
        assertEquals(3400.0f, overrides.colorTemperature)
    }

    @Test
    fun deserialize_gestureConfig_asTypedObject() {
        val jsonWithGesture = """
            {
                "gestureConfig": {
                    "topLeft": "ADD_BOOKMARK",
                    "topCenter": "TOGGLE_TOOLBAR",
                    "topRight": "NEXT_PAGE"
                }
            }
        """.trimIndent()

        val overrides = json.decodeFromString(BookReaderPrefsOverrides.serializer(), jsonWithGesture)

        assertNotNull(overrides.gestureConfig)
        assertEquals(GestureAction.ADD_BOOKMARK, overrides.gestureConfig?.topLeft)
    }

    @Test
    fun deserialize_gestureConfig_oldStringFormat_throwsException() {
        val oldFormatJson = """
            {
                "gestureConfig": "{\"topLeft\":\"PREV_PAGE\"}"
            }
        """.trimIndent()

        try {
            json.decodeFromString(BookReaderPrefsOverrides.serializer(), oldFormatJson)
            fail("Expected exception for String→GestureConfig type mismatch")
        } catch (_: Exception) {
            // 旧的 String 格式无法反序列化为 GestureConfig → 异常
            // ReaderSettingsManager try-catch 兜底回退到 BookReaderPrefsOverrides()
        }
    }

    @Test
    fun roundTrip_serializeDeserialize_consistent() {
        val original = BookReaderPrefsOverrides(
            fontSize = 20f,
            readingFont = "serif",
            colorTemperature = 4500f,
            gestureConfig = GestureConfig(
                topLeft = GestureAction.ADD_BOOKMARK,
            ),
        )

        val jsonStr = json.encodeToString(BookReaderPrefsOverrides.serializer(), original)
        val restored = json.decodeFromString(BookReaderPrefsOverrides.serializer(), jsonStr)

        assertEquals(original.fontSize, restored.fontSize)
        assertEquals(original.readingFont, restored.readingFont)
        assertEquals(original.colorTemperature, restored.colorTemperature)
        assertEquals(original.gestureConfig, restored.gestureConfig)
    }

    @Test
    fun deserialize_unknownEnumValue_coerceToDefault() {
        // coerceInputValues 对未知枚举值回退到默认值
        val jsonWithUnknownEnum = """
            {
                "gestureConfig": {
                    "topLeft": "UNKNOWN_FUTURE_ACTION",
                    "topCenter": "TOGGLE_TOOLBAR"
                }
            }
        """.trimIndent()

        val overrides = json.decodeFromString(BookReaderPrefsOverrides.serializer(), jsonWithUnknownEnum)

        assertNotNull(overrides.gestureConfig)
        // 未知枚举值被 coerce 到字段默认值（GestureConfig.topLeft 默认为 PREV_PAGE）
        assertEquals(GestureAction.PREV_PAGE, overrides.gestureConfig?.topLeft)
        assertEquals(GestureAction.TOGGLE_TOOLBAR, overrides.gestureConfig?.topCenter)
    }

    @Test
    fun deserialize_partialOverride_onlySetFieldsPresent() {
        val partialJson = """
            {
                "fontSize": 22.0,
                "colorTemperature": 3400.0
            }
        """.trimIndent()

        val overrides = json.decodeFromString(BookReaderPrefsOverrides.serializer(), partialJson)

        assertEquals(22.0f, overrides.fontSize)
        assertEquals(3400.0f, overrides.colorTemperature)
        // 未设置的字段为 null（继承全局）
        assertNull(overrides.readingFont)
        assertNull(overrides.lineSpacing)
        assertNull(overrides.marginHorizontal)
        assertNull(overrides.gestureConfig)
    }
}
