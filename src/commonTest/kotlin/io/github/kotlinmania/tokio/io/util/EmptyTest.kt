package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.IoSlice
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmptyTest {
    @Test
    fun emptyReadReturnsEofImmediately() {
        val reader = empty()
        val cx = Context()
        val buf = ReadBuf(UByteArray(16))

        val result = assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, buf))
        result.value.getOrThrow()
        assertEquals(0, buf.filled().size)
    }

    @Test
    fun emptyWriteConsumesAllBytes() {
        val writer = empty()
        val cx = Context()
        val data = UByteArray(8) { 42u }

        val writeResult = assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, data))
        assertEquals(8, writeResult.value.getOrThrow())

        val vectoredResult = assertIs<Poll.Ready<Result<Int>>>(
            writer.pollWriteVectored(cx, listOf(IoSlice(UByteArray(3)), IoSlice(UByteArray(5))))
        )
        assertEquals(8, vectoredResult.value.getOrThrow())

        val flushResult = assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx))
        flushResult.value.getOrThrow()

        val shutdownResult = assertIs<Poll.Ready<Result<Unit>>>(writer.pollShutdown(cx))
        shutdownResult.value.getOrThrow()
    }
}
