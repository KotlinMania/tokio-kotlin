// port-lint: source tests/io_split.rs
package io.github.kotlinmania.tokio.io

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Rust's is_send_and_sync test checks compile-time traits that Kotlin does not have.
class SplitTest {
    @Test
    fun splitStreamId() {
        val (r1, w1) = split(RW())
        val (r2, w2) = split(RW())
        assertTrue(r1.isPairOf(w1))
        assertFalse(r1.isPairOf(w2))
        assertTrue(r2.isPairOf(w2))
        assertFalse(r2.isPairOf(w1))
        assertTrue(w1.isPairOf(r1))
        assertFalse(w1.isPairOf(r2))
    }

    @Test
    fun unsplitOk() {
        val stream = RW()
        val (reader, writer) = split(stream)
        assertSame(stream, reader.unsplit(writer))
    }

    @Test
    fun unsplitErr1() {
        val (reader, _) = split(RW())
        val (_, writer) = split(RW())
        assertFailsWith<IllegalStateException> { reader.unsplit(writer) }
    }

    @Test
    fun unsplitErr2() {
        val (_, writer) = split(RW())
        val (reader, _) = split(RW())
        assertFailsWith<IllegalStateException> { reader.unsplit(writer) }
    }

    @Test
    fun methodDelegation() {
        val (reader, writer) = split(RW())
        val cx = Context()
        val buf = ReadBuf(UByteArray(1))
        assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, buf)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(122u), buf.filled())
        assertEquals(1, assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, ubyteArrayOf(120u))).value.getOrThrow())
        val vectored = writer.pollWriteVectored(cx, listOf(IoSlice(ubyteArrayOf(120u))))
        assertEquals(2, assertIs<Poll.Ready<Result<Int>>>(vectored).value.getOrThrow())
        assertTrue(writer.isWriteVectored())
        assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx)).value.getOrThrow()
        assertIs<Poll.Ready<Result<Unit>>>(writer.pollShutdown(cx)).value.getOrThrow()
    }

    @Test
    fun pendingAndIoErrorsReleaseTheLockWithoutPoisoning() {
        val error = IllegalArgumentException("read error")
        var outcome: Poll<Result<Unit>> = Poll.Pending
        val stream = RW { outcome }
        val (reader, writer) = split(stream)
        val cx = Context()
        assertSame(Poll.Pending, reader.pollRead(cx, ReadBuf(UByteArray(1))))
        outcome = Poll.Ready(Result.failure(error))
        val failure = assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, ReadBuf(UByteArray(1))))
        assertSame(error, failure.value.exceptionOrNull())
        assertIs<Poll.Ready<Result<Unit>>>(writer.pollFlush(cx)).value.getOrThrow()
        assertSame(stream, reader.unsplit(writer))
    }

    @Test
    fun thrownPollPoisonsBothHalvesAndUnsplit() {
        val panic = IllegalArgumentException("poll panicked")
        val (reader, writer) = split(RW { throw panic })
        val cx = Context()
        assertSame(
            panic,
            assertFailsWith<IllegalArgumentException> { reader.pollRead(cx, ReadBuf(UByteArray(1))) },
        )
        assertFailsWith<IllegalStateException> { reader.pollRead(cx, ReadBuf(UByteArray(1))) }
        assertFailsWith<IllegalStateException> { writer.pollWrite(cx, ubyteArrayOf(1u)) }
        assertFailsWith<IllegalStateException> { writer.pollFlush(cx) }
        assertFailsWith<IllegalStateException> { writer.pollShutdown(cx) }
        assertFailsWith<IllegalStateException> { writer.pollWriteVectored(cx, emptyList()) }
        assertFailsWith<IllegalStateException> { reader.unsplit(writer) }
    }

    @Test
    fun thrownCancellationIsRethrown() {
        val cancellation = CancellationException("poll cancelled")
        val (reader, writer) = split(RW { throw cancellation })
        assertSame(
            cancellation,
            assertFailsWith<CancellationException> { reader.pollRead(Context(), ReadBuf(UByteArray(1))) },
        )
        assertFailsWith<IllegalStateException> { writer.pollFlush(Context()) }
    }

    @Test
    fun reentrantPollCannotReborrowTheStream() {
        val stream = RW()
        val (reader, writer) = split(stream)
        stream.read = { writer.pollFlush(Context()) }
        assertFailsWith<IllegalStateException> { reader.pollRead(Context(), ReadBuf(UByteArray(1))) }
        assertFailsWith<IllegalStateException> { reader.unsplit(writer) }
    }

    @Test
    fun unsplitInvalidatesBothHandles() {
        val (reader, writer) = split(RW())
        reader.unsplit(writer)
        assertFailsWith<IllegalStateException> { reader.pollRead(Context(), ReadBuf(UByteArray(1))) }
        assertFailsWith<IllegalStateException> { writer.pollWrite(Context(), ubyteArrayOf(1u)) }
        assertFailsWith<IllegalStateException> { reader.unsplit(writer) }
    }

    @Test
    fun unrelatedUnsplitLeavesBothPairsUsable() {
        val stream1 = RW()
        val stream2 = RW()
        val (reader1, writer1) = split(stream1)
        val (reader2, writer2) = split(stream2)
        assertFailsWith<IllegalStateException> { reader1.unsplit(writer2) }
        assertSame(stream1, reader1.unsplit(writer1))
        assertSame(stream2, reader2.unsplit(writer2))
    }

    private class RW(
        var read: ((ReadBuf) -> Poll<Result<Unit>>)? = null,
    ) : AsyncRead,
        AsyncWrite {
        override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
            read?.let { return it(buf) }
            buf.putSlice(ubyteArrayOf(122u))
            return Poll.Ready(Result.success(Unit))
        }

        override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> = Poll.Ready(Result.success(1))

        override fun pollFlush(cx: Context): Poll<Result<Unit>> = Poll.Ready(Result.success(Unit))

        override fun pollShutdown(cx: Context): Poll<Result<Unit>> = Poll.Ready(Result.success(Unit))

        override fun pollWriteVectored(cx: Context, bufs: List<IoSlice>): Poll<Result<Int>> = Poll.Ready(Result.success(2))

        override fun isWriteVectored(): Boolean = true
    }
}
