// port-lint: tests tests/io_buf_writer.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.IoSlice
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.VecAsyncWrite
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BufWriterTest {
    @Test
    fun testBufWriter() {
        val inner = VecAsyncWrite()
        val writer = BufWriter.withCapacity(2, inner)
        val cx = Context()

        val poll1 = writer.pollWrite(cx, ubyteArrayOf(0u, 1u))
        assertTrue(poll1.isReady)
        assertEquals(2, (poll1 as Poll.Ready).value.getOrThrow())
        assertEquals(0, writer.buffer().size)
        assertContentEquals(ubyteArrayOf(0u, 1u), inner.asSlice())

        val poll2 = writer.pollWrite(cx, ubyteArrayOf(2u))
        assertTrue(poll2.isReady)
        assertEquals(1, (poll2 as Poll.Ready).value.getOrThrow())
        assertContentEquals(ubyteArrayOf(2u), writer.buffer())
        assertContentEquals(ubyteArrayOf(0u, 1u), inner.asSlice())

        val poll3 = writer.pollWrite(cx, ubyteArrayOf(3u))
        assertTrue(poll3.isReady)
        assertEquals(1, (poll3 as Poll.Ready).value.getOrThrow())
        assertContentEquals(ubyteArrayOf(2u, 3u), writer.buffer())
        assertContentEquals(ubyteArrayOf(0u, 1u), inner.asSlice())

        val flushPoll = writer.pollFlush(cx)
        assertTrue(flushPoll.isReady)
        assertEquals(0, writer.buffer().size)
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u), inner.asSlice())

        writer.pollWrite(cx, ubyteArrayOf(4u))
        writer.pollWrite(cx, ubyteArrayOf(5u))
        assertContentEquals(ubyteArrayOf(4u, 5u), writer.buffer())
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u), inner.asSlice())

        writer.pollWrite(cx, ubyteArrayOf(6u))
        assertContentEquals(ubyteArrayOf(6u), writer.buffer())
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u, 4u, 5u), inner.asSlice())

        writer.pollWrite(cx, ubyteArrayOf(7u, 8u))
        assertEquals(0, writer.buffer().size)
        assertContentEquals(ubyteArrayOf(0u, 1u, 2u, 3u, 4u, 5u, 6u, 7u, 8u), inner.asSlice())
    }

    @Test
    fun testBufWriterVectored() {
        val inner = VecAsyncWrite()
        val writer = BufWriter.withCapacity(4, inner)
        val cx = Context()

        val slices =
            listOf(
                IoSlice(ubyteArrayOf(1u, 2u)),
                IoSlice(ubyteArrayOf(3u)),
            )
        val poll = writer.pollWriteVectored(cx, slices)
        assertTrue(poll.isReady)
        assertEquals(3, (poll as Poll.Ready).value.getOrThrow())
        assertContentEquals(ubyteArrayOf(1u, 2u, 3u), writer.buffer())

        writer.pollFlush(cx)
        assertEquals(0, writer.buffer().size)
        assertContentEquals(ubyteArrayOf(1u, 2u, 3u), inner.asSlice())
    }

    @Test
    fun testBufWriterIntoInner() {
        val inner = VecAsyncWrite()
        val writer = BufWriter.new(inner)
        assertEquals(inner, writer.getRef())
        assertEquals(inner, writer.getMut())
        assertEquals(inner, writer.intoInner())
    }
}
