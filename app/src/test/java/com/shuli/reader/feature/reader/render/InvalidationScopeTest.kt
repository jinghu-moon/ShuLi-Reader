package com.shuli.reader.feature.reader.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvalidationScopeTest {

    @Test
    fun order_pageDelegateIsZero() {
        assertEquals(0, InvalidationScope.PAGE_DELEGATE.order)
    }

    @Test
    fun order_reflowIsOne() {
        assertEquals(1, InvalidationScope.REFLOW.order)
    }

    @Test
    fun order_pageIsTwo() {
        assertEquals(2, InvalidationScope.PAGE.order)
    }

    @Test
    fun impliedByFlow_reflowIsFalse() {
        assertFalse(InvalidationScope.REFLOW.impliedByReflow)
    }

    @Test
    fun impliedByFlow_pageIsTrue() {
        assertTrue(InvalidationScope.PAGE.impliedByReflow)
    }

    @Test
    fun reflowImplied_containsPageOnly() {
        val implied = InvalidationScope.REFLOW_IMPLIED
        assertEquals(1, implied.size)
        assertTrue(implied.contains(InvalidationScope.PAGE))
    }

    @Test
    fun reflowImplied_doesNotContainReflowOrPageDelegate() {
        val implied = InvalidationScope.REFLOW_IMPLIED
        assertFalse(implied.contains(InvalidationScope.REFLOW))
        assertFalse(implied.contains(InvalidationScope.PAGE_DELEGATE))
    }

    @Test
    fun sortedBy_order_returnsCorrectSequence() {
        val sorted = InvalidationScope.entries.sortedBy { it.order }
        assertEquals(InvalidationScope.PAGE_DELEGATE, sorted[0])
        assertEquals(InvalidationScope.REFLOW, sorted[1])
        assertEquals(InvalidationScope.PAGE, sorted[2])
    }

    @Test
    fun entries_hasThreeValues() {
        assertEquals(3, InvalidationScope.entries.size)
    }
}
