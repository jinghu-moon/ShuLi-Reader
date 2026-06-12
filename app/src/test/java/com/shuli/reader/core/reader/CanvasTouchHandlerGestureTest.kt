package com.shuli.reader.core.reader

import com.shuli.reader.feature.reader.settings.GestureAction
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.feature.reader.settings.TouchZone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasTouchHandlerGestureTest {

    // T-2.4.1: 自定义 topLeft action 生效
    @Test
    fun gestureConfig_customTopLeft_returnsCorrectAction() {
        val config = GestureConfig(topLeft = GestureAction.ADD_BOOKMARK)
        assertEquals(GestureAction.ADD_BOOKMARK, config.getAction(TouchZone.TOP_LEFT))
    }

    @Test
    fun gestureConfig_defaultTopLeft_isPrevPage() {
        val config = GestureConfig()
        assertEquals(GestureAction.PREV_PAGE, config.getAction(TouchZone.TOP_LEFT))
    }

    @Test
    fun gestureConfig_allZones_returnCorrectActions() {
        val config = GestureConfig(
            topLeft = GestureAction.ADD_BOOKMARK,
            topCenter = GestureAction.TOGGLE_THEME,
            topRight = GestureAction.TOGGLE_IMMERSIVE,
            middleLeft = GestureAction.SCROLL_UP,
            middleCenter = GestureAction.NONE,
            middleRight = GestureAction.SCROLL_DOWN,
            bottomLeft = GestureAction.TOGGLE_DIRECTORY,
            bottomCenter = GestureAction.TOGGLE_TOOLBAR,
            bottomRight = GestureAction.NEXT_PAGE,
        )

        assertEquals(GestureAction.ADD_BOOKMARK, config.getAction(TouchZone.TOP_LEFT))
        assertEquals(GestureAction.TOGGLE_THEME, config.getAction(TouchZone.TOP_CENTER))
        assertEquals(GestureAction.TOGGLE_IMMERSIVE, config.getAction(TouchZone.TOP_RIGHT))
        assertEquals(GestureAction.SCROLL_UP, config.getAction(TouchZone.MIDDLE_LEFT))
        assertEquals(GestureAction.NONE, config.getAction(TouchZone.MIDDLE_CENTER))
        assertEquals(GestureAction.SCROLL_DOWN, config.getAction(TouchZone.MIDDLE_RIGHT))
        assertEquals(GestureAction.TOGGLE_DIRECTORY, config.getAction(TouchZone.BOTTOM_LEFT))
        assertEquals(GestureAction.TOGGLE_TOOLBAR, config.getAction(TouchZone.BOTTOM_CENTER))
        assertEquals(GestureAction.NEXT_PAGE, config.getAction(TouchZone.BOTTOM_RIGHT))
    }

    // T-2.4.4: GestureConfig 序列化/反序列化往返
    @Test
    fun gestureConfig_jsonRoundTrip() {
        val original = GestureConfig(
            topLeft = GestureAction.ADD_BOOKMARK,
            doubleTap = GestureAction.TOGGLE_THEME,
            longPress = GestureAction.TOGGLE_IMMERSIVE,
            swipeUp = GestureAction.SCROLL_UP,
            swipeDown = GestureAction.SCROLL_DOWN,
        )
        val json = Json.encodeToString(original)
        val restored = Json.decodeFromString<GestureConfig>(json)
        assertEquals(original, restored)
    }

    @Test
    fun gestureConfig_defaultValues_serializeCorrectly() {
        val original = GestureConfig()
        val json = Json.encodeToString(original)
        val restored = Json.decodeFromString<GestureConfig>(json)
        assertEquals(original, restored)
    }

    // GestureAction 枚举含 10 个值
    @Test
    fun gestureAction_hasTenValues() {
        assertEquals(10, GestureAction.entries.size)
    }

    // TouchZone 枚举含 9 个值（3x3 网格）
    @Test
    fun touchZone_hasNineValues() {
        assertEquals(9, TouchZone.entries.size)
    }
}
