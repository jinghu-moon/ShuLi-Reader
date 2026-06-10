package com.shuli.reader.feature.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class HeatLevelTest {

    @Test
    fun zeroMinutesReturnsL0() {
        assertEquals(HeatLevel.L0, StatsRepository.heatLevel(0, 100))
    }

    @Test
    fun sqrtTransformLowValueReturnsL1() {
        assertEquals(HeatLevel.L1, StatsRepository.heatLevel(1, 100))
    }

    @Test
    fun mediumValueReturnsL3() {
        assertEquals(HeatLevel.L3, StatsRepository.heatLevel(20, 100))
    }

    @Test
    fun maxValueReturnsL5() {
        assertEquals(HeatLevel.L5, StatsRepository.heatLevel(100, 100))
    }

    @Test
    fun maxMinutesZeroReturnsL1() {
        assertEquals(HeatLevel.L1, StatsRepository.heatLevel(10, 0))
    }
}
