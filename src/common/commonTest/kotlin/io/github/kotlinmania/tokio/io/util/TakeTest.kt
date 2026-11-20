// port-lint: tests io/util/take.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncBufRead
import io.github.kotlinmania.tokio.io.AsyncRead
import io.github.kotlinmania.tokio.io.ByteSliceAsyncBufRead
import io.github.kotlinmania.tokio.io.ByteSliceAsyncRead
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Upstream bad_reader_fails tests reassigning `&mut ReadBuf` with a different backing pointer
// (`*read_buf = buf`), which Kotlin object parameters cannot swap on the caller's ReadBuf.
class TakeTest {
    @Test
    fun take() {
        val raw = UByteArray(6)
        val rd = take(ByteSliceAsyncRead("hello world".encodeToByteArray().asUByteArray()), 4uL)
        assertEquals(4uL, rd.limit())

        val readBuf = ReadBuf.new(raw)
        assertTrue(rd.pollRead(Context(), readBuf).isReady)
        assertEquals(4, readBuf.filled().size)
        assertEquals(0uL, rd.limit())

        assertContentEquals(
            byteArrayOf('h'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(), 0, 0).asUByteArray(),
            raw,
        )
    }

    @Test
    fun issue4435() {
        val raw = UByteArray(8)
        val rd = take(ByteSliceAsyncRead("hello world".encodeToByteArray().asUByteArray()), 4uL)

        val readBuf = ReadBuf.new(raw)
        readBuf.putSlice("AB".encodeToByteArray().asUByteArray())

        val res = (rd.pollRead(Context(), readBuf) as Poll.Ready).value
        assertTrue(res.isSuccess)
        assertEquals(0uL, rd.limit())
        assertContentEquals(
            byteArrayOf(
                'A'.code.toByte(),
                'B'.code.toByte(),
                'h'.code.toByte(),
                'e'.code.toByte(),
                'l'.code.toByte(),
                'l'.code.toByte(),
                0,
                0,
            ).asUByteArray(),
            raw,
        )
    }

    @Test
    fun limitAccessorsZeroGuardAndBufferedTake() {
        var innerPolledAtZero = false
        val guardedInner: AsyncRead =
            object : AsyncRead {
                override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
                    innerPolledAtZero = true
                    return Poll.ready(Result.success(Unit))
                }
            }
        val zeroTake = take(guardedInner, 0uL)
        assertSame(guardedInner, zeroTake.getRef())
        assertSame(guardedInner, zeroTake.getMut())
        assertSame(guardedInner, zeroTake.getPinMut())
        assertTrue(zeroTake.pollRead(Context(), ReadBuf.uninit(4)).isReady)
        assertFalse(innerPolledAtZero)
        assertSame(guardedInner, zeroTake.intoInner())

        val bufInner = ByteSliceAsyncBufRead("abcdef".encodeToByteArray().asUByteArray())
        val limitedBuf = take(bufInner, 4uL)
        val cx = Context()

        val chunk = (limitedBuf.pollFillBuf(cx) as Poll.Ready).value.getOrThrow()
        assertContentEquals("abcd".encodeToByteArray().asUByteArray(), chunk)

        // Overlarge consume clamps cleanly to remainingLimit without underflow.
        limitedBuf.consume(100)
        assertEquals(0uL, limitedBuf.limit())
        assertEquals(0, (limitedBuf.pollFillBuf(cx) as Poll.Ready).value.getOrThrow().size)

        limitedBuf.setLimit(2uL)
        assertEquals(2uL, limitedBuf.limit())
        val next = (limitedBuf.pollFillBuf(cx) as Poll.Ready).value.getOrThrow()
        assertContentEquals("ef".encodeToByteArray().asUByteArray(), next)
    }

    @Test
    fun bufferedTakeDoesNotPollInnerWhenLimitZero() {
        var polled = false
        val inner: AsyncBufRead =
            object : AsyncBufRead {
                override fun pollFillBuf(cx: Context): Poll<Result<UByteArray>> {
                    polled = true
                    return Poll.ready(Result.success(UByteArray(0)))
                }

                override fun consume(amt: Int) {}

                override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
                    polled = true
                    return Poll.ready(Result.success(Unit))
                }
            }
        val t = take(inner, 0uL)
        val slice = (t.pollFillBuf(Context()) as Poll.Ready).value.getOrThrow()
        assertEquals(0, slice.size)
        assertFalse(polled)
    }
}
