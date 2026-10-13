// port-lint: tests tests/io_buf_reader.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.ByteSliceAsyncRead
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BufReaderTest {
    @Test
    fun testBufferedReader() {
        val inner = ByteSliceAsyncRead(ubyteArrayOf(5u, 6u, 7u, 0u, 1u, 2u, 3u, 4u))
        val reader = BufReader.withCapacity(2, inner)
        val cx = Context()

        val buf1 = ReadBuf(UByteArray(3))
        val poll1 = reader.pollRead(cx, buf1)
        assertTrue(poll1.isReady)
        assertEquals(3, buf1.filled().size)
        assertContentEquals(ubyteArrayOf(5u, 6u, 7u), buf1.filled())
        assertEquals(0, reader.buffer().size)

        val buf2 = ReadBuf(UByteArray(2))
        val poll2 = reader.pollRead(cx, buf2)
        assertTrue(poll2.isReady)
        assertEquals(2, buf2.filled().size)
        assertContentEquals(ubyteArrayOf(0u, 1u), buf2.filled())
        assertEquals(0, reader.buffer().size)

        val buf3 = ReadBuf(UByteArray(1))
        val poll3 = reader.pollRead(cx, buf3)
        assertTrue(poll3.isReady)
        assertEquals(1, buf3.filled().size)
        assertContentEquals(ubyteArrayOf(2u), buf3.filled())
        assertContentEquals(ubyteArrayOf(3u), reader.buffer())

        val buf4 = ReadBuf(UByteArray(1))
        val poll4 = reader.pollRead(cx, buf4)
        assertTrue(poll4.isReady)
        assertEquals(1, buf4.filled().size)
        assertContentEquals(ubyteArrayOf(3u), buf4.filled())
        assertEquals(0, reader.buffer().size)

        val buf5 = ReadBuf(UByteArray(1))
        val poll5 = reader.pollRead(cx, buf5)
        assertTrue(poll5.isReady)
        assertEquals(1, buf5.filled().size)
        assertContentEquals(ubyteArrayOf(4u), buf5.filled())
        assertEquals(0, reader.buffer().size)
    }

    @Test
    fun testBufReaderFillBufAndConsume() {
        val inner = ByteSliceAsyncRead(ubyteArrayOf(1u, 2u, 3u, 4u))
        val reader = BufReader.withCapacity(4, inner)
        val cx = Context()

        val fillPoll = reader.pollFillBuf(cx)
        assertTrue(fillPoll.isReady)
        val ready = (fillPoll as Poll.Ready).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(1u, 2u, 3u, 4u), ready)

        reader.consume(2)
        assertContentEquals(ubyteArrayOf(3u, 4u), reader.buffer())

        reader.discardBuffer()
        assertEquals(0, reader.buffer().size)
    }

    @Test
    fun testBufReaderIntoInner() {
        val inner = ByteSliceAsyncRead(ubyteArrayOf(10u, 20u))
        val reader = BufReader.new(inner)
        assertEquals(inner, reader.getRef())
        assertEquals(inner, reader.getMut())
        assertEquals(inner, reader.intoInner())
    }
}
