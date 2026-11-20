package io.github.kotlinmania.tokio.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CachelineTest {
    @Test
    fun testCachePadded() {
        val padded = CachePadded(42)
        assertEquals(42, padded.value)
        padded.value = 100
        assertEquals(100, padded.value)
    }
}
