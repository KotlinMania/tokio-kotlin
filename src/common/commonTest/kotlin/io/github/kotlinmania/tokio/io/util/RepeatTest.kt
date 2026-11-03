package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RepeatTest {
    @Test
    fun repeatFillsBufferWithRepeatedByte() {
        val reader = repeat(0b101u.toUByte())
        val cx = Context()
        val buf = ReadBuf(UByteArray(5))

        val result = assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, buf))
        result.value.getOrThrow()

        assertEquals(5, buf.filled().size)
        assertContentEquals(UByteArray(5) { 5u }, buf.filled())
    }
}
