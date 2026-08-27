package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.IoSlice
import io.github.kotlinmania.tokio.io.Poll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SinkTest {
    @Test
    fun sinkConsumesAllWrittenBytes() {
        val writer = sink()
        val cx = Context()
        val data = UByteArray(12) { it.toUByte() }

        val writeResult = assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, data))
        assertEquals(12, writeResult.value.getOrThrow())

        val vectoredResult = assertIs<Poll.Ready<Result<Int>>>(
            writer.pollWriteVectored(cx, listOf(IoSlice(UByteArray(4)), IoSlice(UByteArray(6))))
        )
        assertEquals(10, vectoredResult.value.getOrThrow())

        val flushResult = assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx))
        flushResult.value.getOrThrow()

        val shutdownResult = assertIs<Poll.Ready<Result<Unit>>>(writer.pollShutdown(cx))
        shutdownResult.value.getOrThrow()
    }
}
