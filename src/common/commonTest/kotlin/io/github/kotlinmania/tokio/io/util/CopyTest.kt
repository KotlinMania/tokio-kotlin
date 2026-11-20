// port-lint: tests io/util/copy.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.bytes.BytesMut
import io.github.kotlinmania.tokio.io.AsyncRead
import io.github.kotlinmania.tokio.io.AsyncWrite
import io.github.kotlinmania.tokio.io.ByteSliceAsyncRead
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import io.github.kotlinmania.tokio.io.VecAsyncWrite
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Upstream copy_is_cooperative requires `tokio::select!` plus task::coop runtime budgeting,
// which is not yet ported in tokio-kotlin.
class CopyTest {
    @Test
    fun copy() {
        class Rd : AsyncRead {
            private var hasData = true

            override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
                if (hasData) {
                    buf.putSlice("hello world".encodeToByteArray().asUByteArray())
                    hasData = false
                }
                return Poll.ready(Result.success(Unit))
            }
        }

        val rd = Rd()
        val wr = VecAsyncWrite()
        val copied = (copy(rd, wr).poll(Context()) as Poll.Ready).value.getOrThrow()

        assertEquals(11uL, copied)
        assertContentEquals("hello world".encodeToByteArray().asUByteArray(), wr.asSlice())
    }

    @Test
    fun proxy() {
        class BufferedWd(
            private val writer: DuplexStream,
        ) : AsyncWrite {
            private val buf = BytesMut.new()

            override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> {
                this.buf.extendFromSlice(buf.asByteArray())
                return Poll.ready(Result.success(buf.size))
            }

            override fun pollFlush(cx: Context): Poll<Result<Unit>> {
                while (!buf.isEmpty()) {
                    val slice = buf.chunk().asUByteArray()
                    val writePoll = writer.pollWrite(cx, slice)
                    if (writePoll.isPending) {
                        return Poll.pending()
                    }
                    val res = (writePoll as Poll.Ready).value
                    val err = res.exceptionOrNull()
                    if (err != null) {
                        return Poll.ready(Result.failure(err))
                    }
                    val n = res.getOrThrow()
                    buf.advance(n)
                }
                return writer.pollFlush(cx)
            }

            override fun pollShutdown(cx: Context): Poll<Result<Unit>> = writer.pollShutdown(cx)
        }

        val pair = duplex(1024)
        val rd = take(pair.first, 1024uL)
        val wd = BufferedWd(pair.second)
        val cx = Context()

        // Write starting bytes into BufferedWd and flush them into duplex before copying.
        assertEquals(512, (wd.pollWrite(cx, UByteArray(512) { 0x42u }) as Poll.Ready).value.getOrThrow())
        assertTrue((wd.pollFlush(cx) as Poll.Ready).value.isSuccess)

        val op = copy(rd, wd)
        // First poll reads 512 bytes, buffers them in wd, hits Pending read, and flushes needFlush.
        assertTrue(op.poll(cx).isPending)
        // Second poll reads remaining 512 bytes pushed by the flush, hits take(1024) limit, and completes.
        val n = (op.poll(cx) as Poll.Ready).value.getOrThrow()
        assertEquals(1024uL, n)
    }

    @Test
    fun copyTopsUpBufferOnPendingWriteAndRejectsWriteZero() {
        val part1 = "first-".encodeToByteArray().asUByteArray()
        val part2 = "second".encodeToByteArray().asUByteArray()
        var readCall = 0
        val rd: AsyncRead =
            object : AsyncRead {
                override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
                    readCall += 1
                    when (readCall) {
                        1 -> buf.putSlice(part1)
                        2 -> buf.putSlice(part2)
                    }
                    return Poll.ready(Result.success(Unit))
                }
            }

        var writeCall = 0
        val out = mutableListOf<UByte>()
        val wr: AsyncWrite =
            object : AsyncWrite {
                override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> {
                    writeCall += 1
                    if (writeCall == 1) {
                        cx.waker().invoke()
                        return Poll.pending()
                    }
                    for (b in buf) {
                        out.add(b)
                    }
                    return Poll.ready(Result.success(buf.size))
                }

                override fun pollFlush(cx: Context): Poll<Result<Unit>> =
                    Poll.ready(Result.success(Unit))

                override fun pollShutdown(cx: Context): Poll<Result<Unit>> =
                    Poll.ready(Result.success(Unit))
            }

        var wakes = 0
        val cx = Context(wakeup = { wakes += 1 })
        val op = copy(rd, wr)

        // First poll reads part1, write Pending tops up with part2, returns Pending.
        assertTrue(op.poll(cx).isPending)
        assertEquals(1, wakes)
        assertEquals(2, readCall)

        // Second poll writes combined "first-second" in one batch and finishes cleanly.
        val total = (op.poll(cx) as Poll.Ready).value.getOrThrow()
        assertEquals(12uL, total)
        assertContentEquals("first-second".encodeToByteArray().asUByteArray(), out.toUByteArray())

        val zeroWriter: AsyncWrite =
            object : AsyncWrite {
                override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> =
                    Poll.ready(Result.success(0))

                override fun pollFlush(cx: Context): Poll<Result<Unit>> =
                    Poll.ready(Result.success(Unit))

                override fun pollShutdown(cx: Context): Poll<Result<Unit>> =
                    Poll.ready(Result.success(Unit))
            }
        val errRes =
            (copy(ByteSliceAsyncRead("x".encodeToByteArray().asUByteArray()), zeroWriter).poll(Context()) as Poll.Ready)
                .value
        assertTrue(errRes.isFailure)
    }
}
