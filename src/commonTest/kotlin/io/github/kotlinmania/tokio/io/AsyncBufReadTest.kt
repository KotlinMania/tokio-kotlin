// port-lint: tests tests/io_buf_reader.rs
package io.github.kotlinmania.tokio.io

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsyncBufReadTest {
    @Test
    fun testByteSliceAsyncBufRead() {
        val bytes = ubyteArrayOf(1u, 2u, 3u, 4u, 5u)
        val reader = ByteSliceAsyncBufRead(bytes)
        val cx = Context()

        val fillPoll = reader.pollFillBuf(cx)
        assertTrue(fillPoll.isReady)
        val ready = (fillPoll as Poll.Ready).value.getOrThrow()
        assertContentEquals(bytes, ready)

        reader.consume(2)
        val fillPoll2 = reader.pollFillBuf(cx)
        val ready2 = (fillPoll2 as Poll.Ready).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(3u, 4u, 5u), ready2)

        val readBuf = ReadBuf(UByteArray(2))
        val readPoll = reader.pollRead(cx, readBuf)
        assertTrue(readPoll.isReady)
        assertEquals(2, readBuf.filled().size)
        assertContentEquals(ubyteArrayOf(3u, 4u), readBuf.filled())

        val fillPoll3 = reader.pollFillBuf(cx)
        val ready3 = (fillPoll3 as Poll.Ready).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(5u), ready3)
    }

    @Test
    fun testCursorAsyncBufRead() {
        val bytes = ubyteArrayOf(10u, 20u, 30u, 40u)
        val reader = CursorAsyncBufRead(bytes)
        val cx = Context()

        val fillPoll = reader.pollFillBuf(cx)
        assertTrue(fillPoll.isReady)
        val ready = (fillPoll as Poll.Ready).value.getOrThrow()
        assertContentEquals(bytes, ready)

        reader.consume(1)
        assertEquals(1uL, reader.position())

        val fillPoll2 = reader.pollFillBuf(cx)
        val ready2 = (fillPoll2 as Poll.Ready).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(20u, 30u, 40u), ready2)

        val readBuf = ReadBuf(UByteArray(2))
        val readPoll = reader.pollRead(cx, readBuf)
        assertTrue(readPoll.isReady)
        assertContentEquals(ubyteArrayOf(20u, 30u), readBuf.filled())
        assertEquals(3uL, reader.position())
    }
}
