package io.github.kotlinmania.tokio.io

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SplitConcurrencyTest {
    @Test
    fun readAndWriteNeverEnterTheStreamTogether() {
        val stream = GatedStream()
        val (reader, writer) = split(stream)
        val executor = Executors.newFixedThreadPool(2)
        val writerStarted = CountDownLatch(1)
        try {
            val read = executor.submit { reader.pollRead(Context(), ReadBuf(UByteArray(1))) }
            assertTrue(stream.readEntered.await(5, TimeUnit.SECONDS))
            val write =
                executor.submit {
                    writerStarted.countDown()
                    writer.pollWrite(Context(), ubyteArrayOf(1u))
                }
            assertTrue(writerStarted.await(5, TimeUnit.SECONDS))
            assertFalse(stream.writeEntered.await(200, TimeUnit.MILLISECONDS), "write overlapped an active read")
            stream.releaseRead.countDown()
            read.get(5, TimeUnit.SECONDS)
            write.get(5, TimeUnit.SECONDS)
            assertTrue(stream.writeEntered.await(5, TimeUnit.SECONDS))
        } finally {
            stream.releaseRead.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun unsplitWaitsForAnActivePoll() {
        val stream = GatedStream()
        val (reader, writer) = split(stream)
        val executor = Executors.newFixedThreadPool(2)
        val unsplitStarted = CountDownLatch(1)
        val unsplitReturned = CountDownLatch(1)
        try {
            val read = executor.submit { reader.pollRead(Context(), ReadBuf(UByteArray(1))) }
            assertTrue(stream.readEntered.await(5, TimeUnit.SECONDS))
            val unsplit =
                executor.submit {
                    unsplitStarted.countDown()
                    assertSame(stream, reader.unsplit(writer))
                    unsplitReturned.countDown()
                }
            assertTrue(unsplitStarted.await(5, TimeUnit.SECONDS))
            assertFalse(unsplitReturned.await(200, TimeUnit.MILLISECONDS), "unsplit escaped an active poll")
            stream.releaseRead.countDown()
            read.get(5, TimeUnit.SECONDS)
            unsplit.get(5, TimeUnit.SECONDS)
            assertTrue(unsplitReturned.await(5, TimeUnit.SECONDS))
        } finally {
            stream.releaseRead.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private class GatedStream :
        AsyncRead,
        AsyncWrite {
        val readEntered = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val writeEntered = CountDownLatch(1)

        override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
            readEntered.countDown()
            check(releaseRead.await(5, TimeUnit.SECONDS)) { "test did not release the read" }
            return Poll.Ready(Result.success(Unit))
        }

        override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> {
            writeEntered.countDown()
            return Poll.Ready(Result.success(buf.size))
        }

        override fun pollFlush(cx: Context): Poll<Result<Unit>> = Poll.Ready(Result.success(Unit))

        override fun pollShutdown(cx: Context): Poll<Result<Unit>> = Poll.Ready(Result.success(Unit))
    }
}
