// port-lint: tests io/util/chain.rs
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class PendingOnceAsyncRead(
    private val payload: UByteArray,
) : AsyncRead {
    private var yieldedPending = false
    private var readDone = false

    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
        if (!yieldedPending) {
            yieldedPending = true
            cx.waker().invoke()
            return Poll.pending()
        }
        if (!readDone) {
            val amount = minOf(payload.size, buf.remaining())
            buf.putSlice(payload.copyOfRange(0, amount))
            readDone = true
        }
        return Poll.ready(Result.success(Unit))
    }
}

class ChainTest {
    @Test
    fun chain() {
        val rd1 = ByteSliceAsyncRead("hello ".encodeToByteArray().asUByteArray())
        val rd2 = ByteSliceAsyncRead("world".encodeToByteArray().asUByteArray())

        val rd = chain(rd1, rd2)
        val out = mutableListOf<UByte>()
        val scratch = UByteArray(3)
        val cx = Context()

        while (true) {
            val readBuf = ReadBuf.new(scratch)
            val res = rd.pollRead(cx, readBuf)
            assertTrue(res.isReady)
            val filled = readBuf.filled()
            if (filled.isEmpty()) {
                break
            }
            for (b in filled) {
                out.add(b)
            }
        }

        assertContentEquals("hello world".encodeToByteArray().asUByteArray(), out.toUByteArray())
    }

    @Test
    fun chainBufferedReadsAndInnerAccessors() {
        val first = ByteSliceAsyncBufRead("foo".encodeToByteArray().asUByteArray())
        val second = ByteSliceAsyncBufRead("bar".encodeToByteArray().asUByteArray())
        val chained = chain(first, second)

        val refs = chained.getRef()
        assertSame(first, refs.first)
        assertSame(second, refs.second)
        assertSame(first, chained.getMut().first)
        assertSame(second, chained.getPinMut().second)

        val cx = Context()
        val firstSlice = (chained.pollFillBuf(cx) as Poll.Ready).value.getOrThrow()
        assertContentEquals("foo".encodeToByteArray().asUByteArray(), firstSlice)
        chained.consume(3)

        val secondSlice = (chained.pollFillBuf(cx) as Poll.Ready).value.getOrThrow()
        assertContentEquals("bar".encodeToByteArray().asUByteArray(), secondSlice)
        chained.consume(2)

        val tail = (chained.pollFillBuf(cx) as Poll.Ready).value.getOrThrow()
        assertContentEquals("r".encodeToByteArray().asUByteArray(), tail)
        chained.consume(1)

        val eof = (chained.pollFillBuf(cx) as Poll.Ready).value.getOrThrow()
        assertEquals(0, eof.size)

        val inner = chained.intoInner()
        assertSame(first, inner.first)
        assertSame(second, inner.second)
    }

    @Test
    fun chainPropagatesPendingBeforeResumingSecondReader() {
        val first = ByteSliceAsyncRead("ab".encodeToByteArray().asUByteArray())
        val second = PendingOnceAsyncRead("cd".encodeToByteArray().asUByteArray())
        val rd = chain(first, second)
        var wakes = 0
        val cx = Context(wakeup = { wakes += 1 })

        val firstBuf = ReadBuf.uninit(4)
        assertTrue(rd.pollRead(cx, firstBuf).isReady)
        assertContentEquals("ab".encodeToByteArray().asUByteArray(), firstBuf.filled())

        // First reader returns 0 on EOF and polls second, which yields Pending once.
        val pendingBuf = ReadBuf.uninit(4)
        assertTrue(rd.pollRead(cx, pendingBuf).isPending)
        assertEquals(1, wakes)

        val secondBuf = ReadBuf.uninit(4)
        assertTrue(rd.pollRead(cx, secondBuf).isReady)
        assertContentEquals("cd".encodeToByteArray().asUByteArray(), secondBuf.filled())

        val eofBuf = ReadBuf.uninit(4)
        assertTrue(rd.pollRead(cx, eofBuf).isReady)
        assertEquals(0, eofBuf.filled().size)
    }

    @Test
    fun chainBufferedPropagatesFailure() {
        val failing: AsyncBufRead =
            object : AsyncBufRead {
                override fun pollFillBuf(cx: Context): Poll<Result<UByteArray>> =
                    Poll.ready(Result.failure(IllegalStateException("boom")))

                override fun consume(amt: Int) {}

                override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> =
                    Poll.ready(Result.failure(IllegalStateException("boom")))
            }
        val rd = chain(failing, ByteSliceAsyncBufRead("ok".encodeToByteArray().asUByteArray()))
        val fillRes = (rd.pollFillBuf(Context()) as Poll.Ready).value
        assertTrue(fillRes.isFailure)
    }
}
