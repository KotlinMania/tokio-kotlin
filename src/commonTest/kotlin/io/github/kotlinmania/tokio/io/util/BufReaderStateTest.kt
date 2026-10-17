// port-lint: source io/util/buf_reader.rs; tests tests/io_buf_reader.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncRead
import io.github.kotlinmania.tokio.io.ByteSliceAsyncRead
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class BufReaderStateTest {
    @Test
    fun largeConsumeClampsWithoutOverflowingThePosition() {
        val reader = BufReader.withCapacity(4, ByteSliceAsyncRead(ubyteArrayOf(0u, 1u, 2u, 3u, 4u, 5u)))
        val cx = Context()
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u), assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(cx)).value.getOrThrow())
        reader.consume(1)
        // BufReader clamps at its buffered end even when the requested count is larger.
        reader.consume(Int.MAX_VALUE)
        assertEquals(0, reader.buffer().size)
        assertContentEquals(ubyteArrayOf(4u, 5u), assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(cx)).value.getOrThrow())
    }

    @Test
    fun negativeConsumeCannotRewindTheBuffer() {
        val reader = BufReader.withCapacity(4, ByteSliceAsyncRead(ubyteArrayOf(0u, 1u, 2u, 3u)))
        assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(Context())).value.getOrThrow()
        reader.consume(2)
        assertFailsWith<IllegalArgumentException> { reader.consume(-1) }
        assertContentEquals(ubyteArrayOf(2u, 3u), reader.buffer())
        reader.consume(0)
        assertContentEquals(ubyteArrayOf(2u, 3u), reader.buffer())
    }

    @Test
    fun bufferedBytesAreReturnedWithoutReadingAhead() {
        val inner = ByteSliceAsyncRead(ubyteArrayOf(0u, 1u, 2u, 3u, 4u, 5u))
        val reader = BufReader.withCapacity(4, inner)
        val cx = Context()
        assertEquals(0, reader.buffer().size)
        repeat(2) {
            assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u), assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(cx)).value.getOrThrow())
            assertContentEquals(ubyteArrayOf(4u, 5u), inner.remaining())
        }
        reader.consume(1)
        val output = ReadBuf(UByteArray(8))
        output.putSlice(ubyteArrayOf(99u))
        // Return the short buffered remainder without adding a second underlying read.
        assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, output)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(99u, 1u, 2u, 3u), output.filled())
        assertEquals(0, reader.buffer().size)
        assertContentEquals(ubyteArrayOf(4u, 5u), inner.remaining())
        output.clear()
        assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, output)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(4u, 5u), output.filled())
        assertEquals(0, reader.buffer().size)
    }

    @Test
    fun pendingFillUsesTheLatestWakerAndResumesWithoutConsumption() {
        val stream = SimplexStream.newUnsplit(8)
        val reader = BufReader.withCapacity(4, stream)
        var firstWakes = 0
        var secondWakes = 0
        val output = ReadBuf(UByteArray(2))
        output.putSlice(ubyteArrayOf(99u))
        assertSame(Poll.Pending, reader.pollRead(Context { firstWakes++ }, output))
        assertContentEquals(ubyteArrayOf(99u), output.filled())
        assertEquals(0, reader.buffer().size)
        assertSame(Poll.Pending, reader.pollFillBuf(Context { secondWakes++ }))
        val cx = Context()
        assertEquals(3, assertIs<Poll.Ready<Result<Int>>>(stream.pollWrite(cx, ubyteArrayOf(0u, 1u, 2u))).value.getOrThrow())
        assertEquals(0, firstWakes)
        assertEquals(1, secondWakes)
        repeat(2) {
            assertContentEquals(ubyteArrayOf(0u, 1u, 2u), assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(cx)).value.getOrThrow())
        }
        assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, output)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(99u, 0u), output.filled())
        assertIs<Poll.Ready<Result<Unit>>>(stream.pollShutdown(cx)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(1u, 2u), assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(cx)).value.getOrThrow())
        reader.consume(2)
        assertEquals(0, assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(cx)).value.getOrThrow().size)
    }

    @Test
    fun pendingBypassCanResumeWithANewDestination() {
        val stream = SimplexStream.newUnsplit(8)
        val reader = BufReader.withCapacity(2, stream)
        val abandoned = ReadBuf(UByteArray(3))
        abandoned.putSlice(ubyteArrayOf(99u))
        var wakes = 0
        assertSame(Poll.Pending, reader.pollRead(Context { wakes++ }, abandoned))
        val cx = Context()
        assertIs<Poll.Ready<Result<Int>>>(stream.pollWrite(cx, ubyteArrayOf(0u, 1u, 2u))).value.getOrThrow()
        assertEquals(1, wakes)
        val resumed = ReadBuf(UByteArray(4))
        assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, resumed)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u), resumed.filled())
        assertContentEquals(ubyteArrayOf(99u), abandoned.filled())
        assertEquals(0, reader.buffer().size)
    }

    @Test
    fun readErrorPreservesTheDestinationAndAllowsRetry() {
        for (capacity in listOf(1, 4)) {
            val failure = IllegalStateException("read failed")
            val data = ByteSliceAsyncRead(ubyteArrayOf(42u))
            var failNext = true
            val inner =
                object : AsyncRead {
                    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
                        if (failNext) {
                            failNext = false
                            return Poll.ready(Result.failure(failure))
                        }
                        return data.pollRead(cx, buf)
                    }
                }
            val reader = BufReader.withCapacity(capacity, inner)
            val output = ReadBuf(UByteArray(2))
            output.putSlice(ubyteArrayOf(99u))
            val cx = Context()
            assertSame(failure, assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, output)).value.exceptionOrNull())
            assertContentEquals(ubyteArrayOf(99u), output.filled())
            assertEquals(0, reader.buffer().size)
            assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, output)).value.getOrThrow()
            assertContentEquals(ubyteArrayOf(99u, 42u), output.filled())
        }
    }

    @Test
    fun fullDestinationDoesNotConsumeBufferedBytes() {
        val inner = ByteSliceAsyncRead(ubyteArrayOf(0u, 1u, 2u, 3u, 4u))
        val reader = BufReader.withCapacity(4, inner)
        val cx = Context()
        val output = ReadBuf(UByteArray(1))
        output.putSlice(ubyteArrayOf(99u))
        assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, output)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(99u), output.filled())
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u), reader.buffer())
        assertContentEquals(ubyteArrayOf(4u), inner.remaining())
        reader.consume(0)
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u), reader.buffer())
    }

    @Test
    fun zeroCapacityBypassesTheInternalBuffer() {
        val inner = ByteSliceAsyncRead(ubyteArrayOf(0u, 1u, 2u))
        val reader = BufReader.withCapacity(0, inner)
        val cx = Context()
        assertEquals(0, assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(cx)).value.getOrThrow().size)
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u), inner.remaining())
        val output = ReadBuf(UByteArray(2))
        assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, output)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(0u, 1u), output.filled())
        assertEquals(0, reader.buffer().size)
        output.clear()
        assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, output)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(2u), output.filled())
        assertEquals(0, reader.buffer().size)
    }

    @Test
    fun unwrappingLeavesBufferedBytesBehind() {
        val inner = ByteSliceAsyncRead(ubyteArrayOf(0u, 1u, 2u, 3u, 4u, 5u))
        val reader = BufReader.withCapacity(4, inner)
        assertSame(inner, reader.getRef())
        assertSame(inner, reader.getMut())
        assertSame(inner, reader.getPinMut())
        assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(Context())).value.getOrThrow()
        reader.consume(1)
        assertContentEquals(ubyteArrayOf(1u, 2u, 3u), reader.buffer())
        val returned = reader.intoInner()
        assertSame(inner, returned)
        val output = ReadBuf(UByteArray(8))
        assertIs<Poll.Ready<Result<Unit>>>(returned.pollRead(Context(), output)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(4u, 5u), output.filled())
    }

    @Test
    fun shortReadsIncludingZeroDoNotLatchEof() {
        val lengths = ArrayDeque(listOf(0, 1, 2, 0, 1, 0))
        val inner =
            object : AsyncRead {
                override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
                    buf.advance(lengths.removeFirstOrNull() ?: 0)
                    return Poll.ready(Result.success(Unit))
                }
            }
        val reader = BufReader.new(inner)
        for (expected in listOf(0, 1, 2, 0, 1, 0, 0)) {
            val output = ReadBuf(UByteArray(2))
            assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(Context(), output)).value.getOrThrow()
            assertEquals(expected, output.filled().size)
            assertEquals(0, reader.buffer().size)
        }
    }
}
