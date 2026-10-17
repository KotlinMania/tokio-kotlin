// port-lint: source io/util/buf_writer.rs; tests tests/io_buf_writer.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncWrite
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.VecAsyncWrite
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class BufWriterStateTest {
    @Test
    fun flushErrorRetainsOnlyTheUnwrittenSuffix() {
        val failure = IllegalArgumentException("write failed")
        val inner = ScriptedWriter(listOf(Poll.ready(Result.success(2)), Poll.ready(Result.failure(failure))))
        val writer = BufWriter.withCapacity(8, inner)
        val cx = Context()
        assertEquals(4, assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, ubyteArrayOf(0u, 1u, 2u, 3u))).value.getOrThrow())

        assertSame(failure, assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx)).value.exceptionOrNull())
        assertContentEquals(ubyteArrayOf(0u, 1u), inner.data.asSlice())
        assertContentEquals(ubyteArrayOf(2u, 3u), writer.buffer())
        assertEquals(0, inner.flushCalls)

        // A completed flush error releases the already-written prefix before another write.
        assertEquals(6, assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, ubyteArrayOf(4u, 5u, 6u, 7u, 8u, 9u))).value.getOrThrow())
        assertContentEquals(ubyteArrayOf(2u, 3u, 4u, 5u, 6u, 7u, 8u, 9u), writer.buffer())
        assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx)).value.getOrThrow()
        assertContentEquals(UByteArray(10) { it.toUByte() }, inner.data.asSlice())
        assertEquals(0, writer.buffer().size)
    }

    @Test
    fun zeroWriteRetainsOnlyTheUnwrittenSuffix() {
        val inner = ScriptedWriter(listOf(Poll.ready(Result.success(2)), Poll.ready(Result.success(0))))
        val writer = BufWriter.withCapacity(8, inner)
        val cx = Context()
        assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, ubyteArrayOf(0u, 1u, 2u, 3u))).value.getOrThrow()

        val failed = assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx)).value
        assertIs<IllegalStateException>(failed.exceptionOrNull())
        assertContentEquals(ubyteArrayOf(2u, 3u), writer.buffer())
        assertEquals(0, inner.flushCalls)

        assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u), inner.data.asSlice())
        assertEquals(0, writer.buffer().size)
    }

    @Test
    fun abandonedPendingFlushCanResumeWithoutReplayingBytes() {
        val inner = ScriptedWriter(listOf(Poll.ready(Result.success(2)), Poll.pending()))
        val writer = BufWriter.withCapacity(8, inner)
        assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(Context(), ubyteArrayOf(0u, 1u, 2u, 3u))).value.getOrThrow()
        assertSame(Poll.Pending, writer.pollFlush(Context()))
        assertContentEquals(ubyteArrayOf(0u, 1u), inner.data.asSlice())
        // Pending keeps the full buffer and a separate progress cursor until it is resumed.
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u), writer.buffer())

        // Stop polling that operation, append data, and flush from a fresh task context.
        assertEquals(2, assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(Context(), ubyteArrayOf(4u, 5u))).value.getOrThrow())
        assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(Context())).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u, 4u, 5u), inner.data.asSlice())
        assertEquals(0, writer.buffer().size)
    }

    @Test
    fun pendingFlushRegistersTheLatestTask() {
        val inner = ScriptedWriter(listOf(Poll.ready(Result.success(1)), Poll.pending(), Poll.pending()))
        val writer = BufWriter.withCapacity(8, inner)
        assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(Context(), ubyteArrayOf(0u, 1u, 2u, 3u))).value.getOrThrow()
        var firstWakes = 0
        var secondWakes = 0
        assertSame(Poll.Pending, writer.pollFlush(Context { firstWakes++ }))
        assertSame(Poll.Pending, writer.pollFlush(Context { secondWakes++ }))
        requireNotNull(inner.pendingWaker).invoke()
        assertEquals(0, firstWakes)
        assertEquals(1, secondWakes)
        assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(Context())).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u), inner.data.asSlice())
    }

    @Test
    fun shutdownWaitsForBufferedDataAndInnerShutdown() {
        val inner = ScriptedWriter(listOf(Poll.ready(Result.success(2)), Poll.pending()))
        val writer = BufWriter.withCapacity(8, inner)
        val cx = Context()
        assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, ubyteArrayOf(0u, 1u, 2u, 3u))).value.getOrThrow()
        assertSame(Poll.Pending, writer.pollShutdown(cx))
        assertEquals(0, inner.shutdownCalls)
        inner.shutdownResult = Poll.pending()
        assertSame(Poll.Pending, writer.pollShutdown(cx))
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u), inner.data.asSlice())
        assertEquals(0, writer.buffer().size)
        assertEquals(1, inner.shutdownCalls)
        inner.shutdownResult = Poll.ready(Result.success(Unit))
        assertIs<Poll.Ready<Result<Unit>>>(writer.pollShutdown(cx)).value.getOrThrow()
        assertEquals(2, inner.shutdownCalls)
        assertEquals(3, inner.writeCalls)
        assertEquals(0, inner.flushCalls)
    }

    @Test
    fun innerFlushPendingAndErrorDoNotReplayDrainedData() {
        val inner = ScriptedWriter()
        val writer = BufWriter.withCapacity(8, inner)
        val cx = Context()
        assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, ubyteArrayOf(0u, 1u))).value.getOrThrow()
        inner.flushResult = Poll.pending()
        assertSame(Poll.Pending, writer.pollFlush(cx))
        assertEquals(0, writer.buffer().size)
        val failure = IllegalArgumentException("flush failed")
        inner.flushResult = Poll.ready(Result.failure(failure))
        assertSame(failure, assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx)).value.exceptionOrNull())
        inner.flushResult = Poll.ready(Result.success(Unit))
        assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(0u, 1u), inner.data.asSlice())
        assertEquals(1, inner.writeCalls)
        assertEquals(3, inner.flushCalls)
    }

    private class ScriptedWriter(
        writes: List<Poll<Result<Int>>> = emptyList(),
    ) : AsyncWrite {
        private val writes = ArrayDeque(writes)
        val data = VecAsyncWrite()
        var pendingWaker: (() -> Unit)? = null
        var flushResult: Poll<Result<Unit>> = Poll.ready(Result.success(Unit))
        var shutdownResult: Poll<Result<Unit>> = Poll.ready(Result.success(Unit))
        var writeCalls = 0
        var flushCalls = 0
        var shutdownCalls = 0

        override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> {
            writeCalls++
            val result = writes.removeFirstOrNull() ?: Poll.ready(Result.success(buf.size))
            if (result.isPending) {
                pendingWaker = cx.waker()
            } else {
                val count = (result as Poll.Ready).value.getOrNull()
                if (count != null) {
                    data.pollWrite(cx, buf.copyOfRange(0, count))
                }
            }
            return result
        }

        override fun pollFlush(cx: Context): Poll<Result<Unit>> {
            flushCalls++
            return flushResult
        }

        override fun pollShutdown(cx: Context): Poll<Result<Unit>> {
            shutdownCalls++
            return shutdownResult
        }
    }
}
