// port-lint: source io/util/buf_writer.rs; tests tests/io_buf_writer.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncWrite
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.IoSlice
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.VecAsyncWrite
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BufWriterVectoredTest {
    @Test
    fun emptyVectorsDoNotWriteBufferedData() {
        for (vectored in listOf(false, true)) {
            val inner = MockWriter(4, vectored)
            val writer = BufWriter.withCapacity(8, inner)
            val cx = Context()
            assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, ubyteArrayOf(1u))).value.getOrThrow()
            assertEquals(0, assertIs<Poll.Ready<Result<Int>>>(writer.pollWriteVectored(cx, emptyList())).value.getOrThrow())
            assertEquals(0, assertIs<Poll.Ready<Result<Int>>>(writer.pollWriteVectored(cx, List(3) { IoSlice(UByteArray(0)) })).value.getOrThrow())
            assertContentEquals(ubyteArrayOf(1u), writer.buffer())
            assertEquals(0, inner.data.asSlice().size)
            assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx)).value.getOrThrow()
            assertContentEquals(ubyteArrayOf(1u), inner.data.asSlice())
        }
    }

    @Test
    fun smallVectorsAreBufferedForBothBackendCapabilities() {
        for (vectored in listOf(false, true)) {
            val inner = MockWriter(4, vectored)
            val writer = BufWriter.withCapacity(16, inner)
            val cx = Context()
            val bytes = UByteArray(11) { it.toUByte() }
            val slices = listOf(IoSlice(bytes.copyOfRange(0, 4)), IoSlice(bytes.copyOfRange(4, 8)), IoSlice(bytes.copyOfRange(8, 11)))
            assertEquals(11, assertIs<Poll.Ready<Result<Int>>>(writer.pollWriteVectored(cx, slices)).value.getOrThrow())
            assertContentEquals(bytes, writer.buffer())
            assertEquals(0, inner.data.asSlice().size)
            assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx)).value.getOrThrow()
            assertContentEquals(bytes, inner.data.asSlice())
            assertEquals(0, writer.buffer().size)
        }
    }

    @Test
    fun nonVectoredBackendBuffersOnlyWholeSlicesThatFit() {
        val inner = MockWriter(4, false)
        val writer = BufWriter.withCapacity(8, inner)
        val cx = Context()
        val bytes = UByteArray(11) { it.toUByte() }
        val first = IoSlice(bytes.copyOfRange(0, 4))
        val second = IoSlice(bytes.copyOfRange(4, 9))
        val third = IoSlice(bytes.copyOfRange(9, 11))
        val empty = IoSlice(UByteArray(0))
        assertEquals(4, assertIs<Poll.Ready<Result<Int>>>(writer.pollWriteVectored(cx, listOf(empty, first, empty, second, third))).value.getOrThrow())
        assertContentEquals(bytes.copyOfRange(0, 4), writer.buffer())
        assertEquals(7, assertIs<Poll.Ready<Result<Int>>>(writer.pollWriteVectored(cx, listOf(second, third))).value.getOrThrow())
        assertContentEquals(bytes.copyOfRange(0, 4), inner.data.asSlice())
        assertContentEquals(bytes.copyOfRange(4, 11), writer.buffer())
        assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx)).value.getOrThrow()
        assertContentEquals(bytes, inner.data.asSlice())
    }

    @Test
    fun largeVectorUsesTheVectoredBackendAndPreservesItsPartialCount() {
        val inner = MockWriter(10, true)
        val writer = BufWriter.withCapacity(8, inner)
        val cx = Context()
        val bytes = UByteArray(11) { it.toUByte() }
        val slices = listOf(IoSlice(bytes.copyOfRange(0, 4)), IoSlice(bytes.copyOfRange(4, 8)), IoSlice(bytes.copyOfRange(8, 11)))
        assertEquals(10, assertIs<Poll.Ready<Result<Int>>>(writer.pollWriteVectored(cx, slices)).value.getOrThrow())
        assertContentEquals(bytes.copyOfRange(0, 10), inner.data.asSlice())
        assertEquals(0, writer.buffer().size)
        assertEquals(1, inner.vectoredCalls)
        assertEquals(1, assertIs<Poll.Ready<Result<Int>>>(writer.pollWriteVectored(cx, listOf(IoSlice(bytes.copyOfRange(10, 11))))).value.getOrThrow())
        assertContentEquals(bytes.copyOfRange(10, 11), writer.buffer())
    }

    @Test
    fun largeFirstSliceBypassesTheBufferForNonVectoredBackend() {
        val inner = MockWriter(4, false)
        val writer = BufWriter.withCapacity(8, inner)
        val bytes = UByteArray(11) { it.toUByte() }
        val slices = listOf(IoSlice(UByteArray(0)), IoSlice(bytes.copyOfRange(0, 9)), IoSlice(bytes.copyOfRange(9, 11)))
        assertEquals(4, assertIs<Poll.Ready<Result<Int>>>(writer.pollWriteVectored(Context(), slices)).value.getOrThrow())
        assertContentEquals(bytes.copyOfRange(0, 4), inner.data.asSlice())
        assertEquals(0, writer.buffer().size)
        assertEquals(0, inner.vectoredCalls)
        assertTrue(writer.isWriteVectored())
    }

    @Test
    fun vectorLengthBeyondIntMaxBypassesBufferWithoutOverflow() {
        val inner = MockWriter(4, true)
        val writer = BufWriter.withCapacity(8, inner)
        // The vector describes 2 GiB, but all entries borrow the same 64 KiB slice.
        val slice = IoSlice(UByteArray(65_536) { it.toUByte() })
        val slices = List(32_768) { slice }
        assertEquals(4, assertIs<Poll.Ready<Result<Int>>>(writer.pollWriteVectored(Context(), slices)).value.getOrThrow())
        assertEquals(1, inner.vectoredCalls)
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u), inner.data.asSlice())
        assertEquals(0, writer.buffer().size)
    }

    @Test
    fun zeroCapacityBypassesBufferAndIntoInnerDoesNotFlush() {
        val inner = MockWriter(4, true)
        val direct = BufWriter.withCapacity(0, inner)
        assertEquals(2, assertIs<Poll.Ready<Result<Int>>>(direct.pollWrite(Context(), ubyteArrayOf(0u, 1u))).value.getOrThrow())
        assertEquals(0, direct.buffer().size)
        val buffered = BufWriter.withCapacity(8, inner)
        assertIs<Poll.Ready<Result<Int>>>(buffered.pollWrite(Context(), ubyteArrayOf(2u, 3u))).value.getOrThrow()
        assertSame(inner, buffered.getPinMut())
        assertSame(inner, buffered.intoInner())
        assertContentEquals(ubyteArrayOf(0u, 1u), inner.data.asSlice())
    }

    private class MockWriter(
        private val writeLen: Int,
        private val vectored: Boolean,
    ) : AsyncWrite {
        val data = VecAsyncWrite()
        var vectoredCalls = 0

        override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> =
            data.pollWrite(cx, buf.copyOfRange(0, minOf(buf.size, writeLen)))

        override fun pollWriteVectored(cx: Context, bufs: List<IoSlice>): Poll<Result<Int>> {
            vectoredCalls++
            var written = 0
            for (buf in bufs) {
                val count = minOf(buf.size, writeLen - written)
                data.pollWrite(cx, buf.asSlice().copyOfRange(0, count))
                written += count
                if (written == writeLen) {
                    break
                }
            }
            return Poll.ready(Result.success(written))
        }

        override fun isWriteVectored(): Boolean = vectored

        override fun pollFlush(cx: Context): Poll<Result<Unit>> = data.pollFlush(cx)

        override fun pollShutdown(cx: Context): Poll<Result<Unit>> = data.pollShutdown(cx)
    }
}
