package com.shuli.reader

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.junit.Rule

/**
 * 协程测试基类。
 * 子类自动获得 [MainDispatcherRule]，默认使用 [StandardTestDispatcher]，
 * 需要显式 advanceUntilIdle() 才会推进协程，避免时序隐式依赖。
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class CoroutineTestBase(
    testDispatcher: TestDispatcher = StandardTestDispatcher(),
) {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)
}
