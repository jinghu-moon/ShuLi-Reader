package com.shuli.reader.feature.reader.settings

import kotlinx.serialization.Serializable

@Serializable
enum class GestureAction {
    NONE,
    PREV_PAGE,
    NEXT_PAGE,
    TOGGLE_TOOLBAR,
    TOGGLE_DIRECTORY,
    ADD_BOOKMARK,
    TOGGLE_THEME,
    TOGGLE_IMMERSIVE,
    SCROLL_UP,
    SCROLL_DOWN,
}

@Serializable
data class GestureConfig(
    val topLeft: GestureAction = GestureAction.PREV_PAGE,
    val topCenter: GestureAction = GestureAction.TOGGLE_TOOLBAR,
    val topRight: GestureAction = GestureAction.NEXT_PAGE,
    val middleLeft: GestureAction = GestureAction.PREV_PAGE,
    val middleCenter: GestureAction = GestureAction.TOGGLE_TOOLBAR,
    val middleRight: GestureAction = GestureAction.NEXT_PAGE,
    val bottomLeft: GestureAction = GestureAction.PREV_PAGE,
    val bottomCenter: GestureAction = GestureAction.TOGGLE_TOOLBAR,
    val bottomRight: GestureAction = GestureAction.NEXT_PAGE,
    val doubleTap: GestureAction = GestureAction.NONE,
    val longPress: GestureAction = GestureAction.NONE,
    val swipeUp: GestureAction = GestureAction.NONE,
    val swipeDown: GestureAction = GestureAction.NONE,
) {
    fun getAction(zone: TouchZone): GestureAction = when (zone) {
        TouchZone.TOP_LEFT -> topLeft
        TouchZone.TOP_CENTER -> topCenter
        TouchZone.TOP_RIGHT -> topRight
        TouchZone.MIDDLE_LEFT -> middleLeft
        TouchZone.MIDDLE_CENTER -> middleCenter
        TouchZone.MIDDLE_RIGHT -> middleRight
        TouchZone.BOTTOM_LEFT -> bottomLeft
        TouchZone.BOTTOM_CENTER -> bottomCenter
        TouchZone.BOTTOM_RIGHT -> bottomRight
    }
}

enum class TouchZone {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
}
