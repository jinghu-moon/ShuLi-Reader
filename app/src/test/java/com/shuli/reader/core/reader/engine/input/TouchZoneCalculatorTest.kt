package com.shuli.reader.core.reader.engine.input

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchZoneCalculatorTest {

    @Test
    fun topLeftZone_isDetectedCorrectly() {
        val zone = TouchZoneCalculator.calculateZone(
            touchX = 100f,
            touchY = 100f,
            screenWidth = 1080,
            screenHeight = 1920,
        )
        assertEquals(TouchZone.TOP_LEFT, zone)
    }

    @Test
    fun topCenterZone_isDetectedCorrectly() {
        val zone = TouchZoneCalculator.calculateZone(
            touchX = 540f,
            touchY = 100f,
            screenWidth = 1080,
            screenHeight = 1920,
        )
        assertEquals(TouchZone.TOP_CENTER, zone)
    }

    @Test
    fun topRightZone_isDetectedCorrectly() {
        val zone = TouchZoneCalculator.calculateZone(
            touchX = 900f,
            touchY = 100f,
            screenWidth = 1080,
            screenHeight = 1920,
        )
        assertEquals(TouchZone.TOP_RIGHT, zone)
    }

    @Test
    fun middleLeftZone_isDetectedCorrectly() {
        val zone = TouchZoneCalculator.calculateZone(
            touchX = 100f,
            touchY = 960f,
            screenWidth = 1080,
            screenHeight = 1920,
        )
        assertEquals(TouchZone.MIDDLE_LEFT, zone)
    }

    @Test
    fun middleCenterZone_isDetectedCorrectly() {
        val zone = TouchZoneCalculator.calculateZone(
            touchX = 540f,
            touchY = 960f,
            screenWidth = 1080,
            screenHeight = 1920,
        )
        assertEquals(TouchZone.MIDDLE_CENTER, zone)
    }

    @Test
    fun middleRightZone_isDetectedCorrectly() {
        val zone = TouchZoneCalculator.calculateZone(
            touchX = 900f,
            touchY = 960f,
            screenWidth = 1080,
            screenHeight = 1920,
        )
        assertEquals(TouchZone.MIDDLE_RIGHT, zone)
    }

    @Test
    fun bottomLeftZone_isDetectedCorrectly() {
        val zone = TouchZoneCalculator.calculateZone(
            touchX = 100f,
            touchY = 1800f,
            screenWidth = 1080,
            screenHeight = 1920,
        )
        assertEquals(TouchZone.BOTTOM_LEFT, zone)
    }

    @Test
    fun bottomCenterZone_isDetectedCorrectly() {
        val zone = TouchZoneCalculator.calculateZone(
            touchX = 540f,
            touchY = 1800f,
            screenWidth = 1080,
            screenHeight = 1920,
        )
        assertEquals(TouchZone.BOTTOM_CENTER, zone)
    }

    @Test
    fun bottomRightZone_isDetectedCorrectly() {
        val zone = TouchZoneCalculator.calculateZone(
            touchX = 900f,
            touchY = 1800f,
            screenWidth = 1080,
            screenHeight = 1920,
        )
        assertEquals(TouchZone.BOTTOM_RIGHT, zone)
    }

    @Test
    fun leftZone_returnsPreviousPageAction() {
        val action = TouchZoneCalculator.getActionForZone(TouchZone.TOP_LEFT)
        assertEquals(TouchAction.PREV_PAGE, action)

        val action2 = TouchZoneCalculator.getActionForZone(TouchZone.MIDDLE_LEFT)
        assertEquals(TouchAction.PREV_PAGE, action2)

        val action3 = TouchZoneCalculator.getActionForZone(TouchZone.BOTTOM_LEFT)
        assertEquals(TouchAction.PREV_PAGE, action3)
    }

    @Test
    fun rightZone_returnsNextPageAction() {
        val action = TouchZoneCalculator.getActionForZone(TouchZone.TOP_RIGHT)
        assertEquals(TouchAction.NEXT_PAGE, action)

        val action2 = TouchZoneCalculator.getActionForZone(TouchZone.MIDDLE_RIGHT)
        assertEquals(TouchAction.NEXT_PAGE, action2)

        val action3 = TouchZoneCalculator.getActionForZone(TouchZone.BOTTOM_RIGHT)
        assertEquals(TouchAction.NEXT_PAGE, action3)
    }

    @Test
    fun centerZone_returnsToggleToolbarAction() {
        val action = TouchZoneCalculator.getActionForZone(TouchZone.TOP_CENTER)
        assertEquals(TouchAction.TOGGLE_TOOLBAR, action)

        val action2 = TouchZoneCalculator.getActionForZone(TouchZone.MIDDLE_CENTER)
        assertEquals(TouchAction.TOGGLE_TOOLBAR, action2)

        val action3 = TouchZoneCalculator.getActionForZone(TouchZone.BOTTOM_CENTER)
        assertEquals(TouchAction.TOGGLE_TOOLBAR, action3)
    }

    @Test
    fun leftZoneInScrollMode_returnsScrollUpAction() {
        val action = TouchZoneCalculator.getActionForZone(TouchZone.TOP_LEFT, isScrollMode = true)
        assertEquals(TouchAction.SCROLL_UP, action)

        val action2 = TouchZoneCalculator.getActionForZone(TouchZone.MIDDLE_LEFT, isScrollMode = true)
        assertEquals(TouchAction.SCROLL_UP, action2)

        val action3 = TouchZoneCalculator.getActionForZone(TouchZone.BOTTOM_LEFT, isScrollMode = true)
        assertEquals(TouchAction.SCROLL_UP, action3)
    }

    @Test
    fun rightZoneInScrollMode_returnsScrollDownAction() {
        val action = TouchZoneCalculator.getActionForZone(TouchZone.TOP_RIGHT, isScrollMode = true)
        assertEquals(TouchAction.SCROLL_DOWN, action)

        val action2 = TouchZoneCalculator.getActionForZone(TouchZone.MIDDLE_RIGHT, isScrollMode = true)
        assertEquals(TouchAction.SCROLL_DOWN, action2)

        val action3 = TouchZoneCalculator.getActionForZone(TouchZone.BOTTOM_RIGHT, isScrollMode = true)
        assertEquals(TouchAction.SCROLL_DOWN, action3)
    }
}
