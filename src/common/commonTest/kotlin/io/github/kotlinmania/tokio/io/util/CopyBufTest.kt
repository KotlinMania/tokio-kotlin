// port-lint: tests io/util/copy_buf.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncBufRead
import io.github.kotlinmania.tokio.io.AsyncWrite
import io.github.kotlinmania.tokio.io.ByteSliceAsyncBufRead
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import io.github.kotlinmania.tokio.io.VecAsyncWrite
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CopyBufTest {
    @Test
    fun copyBufTransfersAllBytesAndFlushesWriter() {
        val payload = "buffered hello world".encodeToByteArray().asUByteArray()
        val reader = ByteSliceAsyncBufRead(payload)
        var flushed = false
        val innerWriter = VecAsyncWrite()
        val writer: AsyncWrite =
            object : AsyncWrite by innerWriter {
                override fun pollFlush(cx: Context): Poll<Result<Unit>> {
                    flushed = true
                    return innerWriter.pollFlush(cx)
                }
            }

        val op = copyBuf(reader, writer)
        val copied = (op.poll(Context()) as Poll.Ready).value.getOrThrow()
        assertEquals(payload.size.toULong(), copied)
        assertTrue(flushed)
        assertContentEquals(payload, innerWriter.asSlice())
    }

    @Test
    fun copyBufHandlesPendingPartialWritesAndResumes() {
        val chunks =
            arrayOf(
                "alpha ".encodeToByteArray().asUByteArray(),
                "beta".encodeToByteArray().asUByteArray(),
            )
        var chunkIdx = 0
        var offsetInChunk = 0
        var fillStep = 0

        val scriptedReader: AsyncBufRead =
            object : AsyncBufRead {
                override fun pollFillBuf(cx: Context): Poll<Result<UByteArray>> {
                    fillStep += 1
                    if (fillStep == 2) {
                        cx.waker().invoke()
                        return Poll.pending()
                    }
                    if (chunkIdx >= chunks.size) {
                        return Poll.ready(Result.success(UByteArray(0)))
                    }
                    val current = chunks[chunkIdx]
                    return Poll.ready(Result.success(current.copyOfRange(offsetInChunk, current.size)))
                }

                override fun consume(amt: Int) {
                    offsetInChunk += amt
                    if (offsetInChunk >= chunks[chunkIdx].size) {
                        chunkIdx += 1
                        offsetInChunk = 0
                    }
                }

                override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> =
                    Poll.ready(Result.success(Unit))
            }

        val out = mutableListOf<UByte>()
        var writeCalls = 0
        val scriptedWriter: AsyncWrite =
            object : AsyncWrite {
                override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> {
                    writeCalls += 1
                    if (writeCalls == 2) {
                        cx.waker().invoke()
                        return Poll.pending()
                    }
                    val n = minOf(3, buf.size)
                    for (i in 0 until n) {
                        out.add(buf[i])
                    }
                    return Poll.ready(Result.success(n))
                }

                override fun pollFlush(cx: Context): Poll<Result<Unit>> =
                    Poll.ready(Result.success(Unit))

                override fun pollShutdown(cx: Context): Poll<Result<Unit>> =
                    Poll.ready(Result.success(Unit))
            }

        var wakes = 0
        val cx = Context(wakeup = { wakes += 1 })
        val op = copyBuf(scriptedReader, scriptedWriter)

        // Step 1: writes "alp" (3), next fill returns Pending (fillStep == 2).
        assertTrue(op.poll(cx).isPending)
        assertEquals(1, wakes)

        // Step 2: writes "ha " (3), tries "beta" -> writeCalls == 2 returns Pending.
        assertTrue(op.poll(cx).isPending)
        assertEquals(2, wakes)

        // Step 3: writes "bet" (3), "a" (1), hits EOF, flushes and completes.
        val total = (op.poll(cx) as Poll.Ready).value.getOrThrow()
        assertEquals(10uL, total)
        assertContentEquals("alpha beta".encodeToByteArray().asUByteArray(), out.toUByteArray())
    }

    @Test
    fun copyBufReportsWriteZeroAsError() {
        val reader = ByteSliceAsyncBufRead("nonempty".encodeToByteArray().asUByteArray())
        val zeroWriter: AsyncWrite =
            object : AsyncWrite {
                override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> =
                    Poll.ready(Result.success(0))

                override fun pollFlush(cx: Context): Poll<Result<Unit>> =
                    Poll.ready(Result.success(Unit))

                override fun pollShutdown(cx: Context): Poll<Result<Unit>> =
                    Poll.ready(Result.success(Unit))
            }

        val res = (copyBuf(reader, zeroWriter).poll(Context()) as Poll.Ready).value
        assertTrue(res.isFailure)
    }
}
