package com.shuli.reader.core.reader.engine.animation

import org.junit.Assert.*
import org.junit.Test

class PageDelegateFactoryTest {

    @Test
    fun createNone_returnsNoAnimPageDelegate() {
        val delegate = PageDelegateFactory.create(PageDelegateFactory.PageAnimType.NONE)
        assertTrue(delegate is NoAnimPageDelegate)
    }

    @Test
    fun createCover_returnsCoverPageDelegate() {
        val delegate = PageDelegateFactory.create(PageDelegateFactory.PageAnimType.COVER)
        assertTrue(delegate is CoverPageDelegate)
    }

    @Test
    fun createHorizontal_returnsHorizontalPageDelegate() {
        val delegate = PageDelegateFactory.create(PageDelegateFactory.PageAnimType.HORIZONTAL)
        assertTrue(delegate is HorizontalPageDelegate)
    }

    @Test
    fun createSimulation_returnsSimulationPageDelegate() {
        val delegate = PageDelegateFactory.create(PageDelegateFactory.PageAnimType.SIMULATION)
        assertTrue(delegate is SimulationPageDelegate)
    }

    @Test
    fun createScroll_returnsScrollPageDelegate() {
        val delegate = PageDelegateFactory.create(PageDelegateFactory.PageAnimType.SCROLL)
        assertTrue(delegate is ScrollPageDelegate)
    }
}
