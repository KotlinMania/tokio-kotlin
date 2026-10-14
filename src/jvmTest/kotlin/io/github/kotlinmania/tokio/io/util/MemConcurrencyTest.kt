package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MemConcurrencyTest {
    @Test
    fun sharedDirectionIsLockedWhileTheOtherDirectionCanProgress() {
        val (client, server) = duplex(4)
        val wakeEntered = CountDownLatch(1)
        val releaseWake = CountDownLatch(1)
        val readStarted = CountDownLatch(1)
        val readReturned = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val readerContext =
            Context {
                wakeEntered.countDown()
                check(releaseWake.await(5, TimeUnit.SECONDS)) { "test did not release the waker" }
            }
        assertSame(Poll.Pending, server.pollRead(readerContext, ReadBuf(UByteArray(1))))
        try {
            val write = executor.submit { client.pollWrite(Context(), ubyteArrayOf(7u)) }
            assertTrue(wakeEntered.await(5, TimeUnit.SECONDS))
            val read =
                executor.submit {
                    readStarted.countDown()
                    val buf = ReadBuf(UByteArray(1))
                    assertIs<Poll.Ready<Result<Unit>>>(server.pollRead(Context(), buf)).value.getOrThrow()
                    assertContentEquals(ubyteArrayOf(7u), buf.filled())
                    readReturned.countDown()
                }
            assertTrue(readStarted.await(5, TimeUnit.SECONDS))
            assertFalse(readReturned.await(200, TimeUnit.MILLISECONDS), "read overlapped an active write")
            assertEquals(1, assertIs<Poll.Ready<Result<Int>>>(server.pollWrite(Context(), ubyteArrayOf(9u))).value.getOrThrow())
            val reverse = ReadBuf(UByteArray(1))
            assertIs<Poll.Ready<Result<Unit>>>(client.pollRead(Context(), reverse)).value.getOrThrow()
            assertContentEquals(ubyteArrayOf(9u), reverse.filled())
            releaseWake.countDown()
            write.get(5, TimeUnit.SECONDS)
            read.get(5, TimeUnit.SECONDS)
        } finally {
            releaseWake.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun closeWaitsForThePeersActiveWrite() {
        val (client, server) = duplex(4)
        val wakeEntered = CountDownLatch(1)
        val releaseWake = CountDownLatch(1)
        val closeStarted = CountDownLatch(1)
        val closeReturned = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val readerContext =
            Context {
                wakeEntered.countDown()
                check(releaseWake.await(5, TimeUnit.SECONDS)) { "test did not release the waker" }
            }
        assertSame(Poll.Pending, server.pollRead(readerContext, ReadBuf(UByteArray(1))))
        try {
            val write = executor.submit { client.pollWrite(Context(), ubyteArrayOf(7u)) }
            assertTrue(wakeEntered.await(5, TimeUnit.SECONDS))
            val close =
                executor.submit {
                    closeStarted.countDown()
                    server.close()
                    closeReturned.countDown()
                }
            assertTrue(closeStarted.await(5, TimeUnit.SECONDS))
            assertFalse(closeReturned.await(200, TimeUnit.MILLISECONDS), "close overlapped an active write")
            releaseWake.countDown()
            write.get(5, TimeUnit.SECONDS)
            close.get(5, TimeUnit.SECONDS)
            val result = assertIs<Poll.Ready<Result<Int>>>(client.pollWrite(Context(), ubyteArrayOf(8u)))
            assertIs<BrokenPipeException>(result.value.exceptionOrNull())
        } finally {
            releaseWake.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun pingPongAcrossThreadsResumesPendingReads() {
        val (client, server) = duplex(4)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val clientTask =
                executor.submit {
                    val wake = Semaphore(0)
                    val cx = Context { wake.release() }
                    assertEquals(4, assertIs<Poll.Ready<Result<Int>>>(client.pollWrite(cx, ubyteArrayOf(1u, 2u, 3u, 4u))).value.getOrThrow())
                    val response = ReadBuf(UByteArray(4))
                    while (client.pollRead(cx, response).isPending) {
                        check(wake.tryAcquire(5, TimeUnit.SECONDS)) { "client was not woken" }
                    }
                    assertContentEquals(ubyteArrayOf(4u, 3u, 2u, 1u), response.filled())
                }
            val serverTask =
                executor.submit {
                    val wake = Semaphore(0)
                    val cx = Context { wake.release() }
                    val request = ReadBuf(UByteArray(4))
                    while (server.pollRead(cx, request).isPending) {
                        check(wake.tryAcquire(5, TimeUnit.SECONDS)) { "server was not woken" }
                    }
                    assertContentEquals(ubyteArrayOf(1u, 2u, 3u, 4u), request.filled())
                    assertEquals(4, assertIs<Poll.Ready<Result<Int>>>(server.pollWrite(cx, ubyteArrayOf(4u, 3u, 2u, 1u))).value.getOrThrow())
                }
            clientTask.get(10, TimeUnit.SECONDS)
            serverTask.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            client.close()
            server.close()
        }
    }
}
