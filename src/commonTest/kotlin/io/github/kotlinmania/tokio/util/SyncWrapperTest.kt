package io.github.kotlinmania.tokio.util

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncWrapperTest {
    @Test
    fun testSyncWrapper() {
        val wrapper = SyncWrapper("hello")
        assertEquals("hello", wrapper.get())
        wrapper.set("world")
        assertEquals("world", wrapper.intoInner())
    }
}
