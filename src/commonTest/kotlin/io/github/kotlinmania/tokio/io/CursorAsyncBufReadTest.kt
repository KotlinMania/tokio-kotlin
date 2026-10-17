// port-lint: source io/async_buf_read.rs; tests tests/io_buf_reader.rs
package io.github.kotlinmania.tokio.io

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CursorAsyncBufReadTest {
    @Test
    fun fillPastSignedIndexBoundaryReturnsEof() {
        val position = Int.MAX_VALUE.toULong() + 1u
        val reader = CursorAsyncBufRead(ubyteArrayOf(1u, 2u, 3u), position)
        val filled = assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(Context())).value.getOrThrow()
        assertEquals(0, filled.size)
        assertEquals(position, reader.position())
    }

    @Test
    fun fillPastUnsignedIndexBoundaryDoesNotReplayBytes() {
        for (position in listOf(UInt.MAX_VALUE.toULong() + 1u, UInt.MAX_VALUE.toULong() + 2u)) {
            val reader = CursorAsyncBufRead(ubyteArrayOf(1u, 2u, 3u), position)
            val filled = assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(Context())).value.getOrThrow()
            assertEquals(0, filled.size)
            assertEquals(position, reader.position())
        }
    }

    @Test
    fun fillAtMaximumPositionReturnsEof() {
        val reader = CursorAsyncBufRead(ubyteArrayOf(1u, 2u, 3u), ULong.MAX_VALUE)
        val filled = assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(Context())).value.getOrThrow()
        assertEquals(0, filled.size)
        assertEquals(ULong.MAX_VALUE, reader.position())
    }

    @Test
    fun consumingZeroAtEofPreservesTheFullPosition() {
        val positions = listOf(3uL, 4uL, Int.MAX_VALUE.toULong() + 1u, UInt.MAX_VALUE.toULong() + 1u, Long.MAX_VALUE.toULong() + 1u, ULong.MAX_VALUE)
        for (position in positions) {
            val reader = CursorAsyncBufRead(ubyteArrayOf(1u, 2u, 3u), position)
            reader.consume(0)
            val output = ReadBuf(UByteArray(2))
            output.putSlice(ubyteArrayOf(99u))
            assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(Context(), output)).value.getOrThrow()
            assertContentEquals(ubyteArrayOf(99u), output.filled())
            assertEquals(position, reader.position())
        }
    }

    @Test
    fun consumingPastEofDoesNotUseATruncatedPosition() {
        val position = UInt.MAX_VALUE.toULong() + 1u
        val reader = CursorAsyncBufRead(ubyteArrayOf(1u, 2u, 3u), position)
        assertFailsWith<IllegalArgumentException> { reader.consume(1) }
        assertEquals(position, reader.position())
    }

    @Test
    fun emptyCursorRetainsItsPositionAtEof() {
        for (position in listOf(0uL, Int.MAX_VALUE.toULong() + 1u, ULong.MAX_VALUE)) {
            val reader = CursorAsyncBufRead(ubyteArrayOf(), position)
            val filled = assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(Context())).value.getOrThrow()
            assertEquals(0, filled.size)
            reader.consume(0)
            assertEquals(position, reader.position())
        }
    }

    @Test
    fun fillConsumeAndReadShareOnePosition() {
        val reader = CursorAsyncBufRead(ubyteArrayOf(10u, 20u, 30u, 40u), 1u)
        val cx = Context()
        repeat(2) {
            val filled = assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(cx)).value.getOrThrow()
            assertContentEquals(ubyteArrayOf(20u, 30u, 40u), filled)
            assertEquals(1uL, reader.position())
        }
        reader.consume(1)
        val output = ReadBuf(UByteArray(3))
        output.putSlice(ubyteArrayOf(99u))
        assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(cx, output)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(99u, 30u, 40u), output.filled())
        assertEquals(4uL, reader.position())
        assertEquals(0, assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(cx)).value.getOrThrow().size)
        reader.consume(0)
        assertEquals(4uL, reader.position())
    }

    @Test
    fun invalidConsumeDoesNotChangeTheBufferedPosition() {
        val reader = CursorAsyncBufRead(ubyteArrayOf(10u, 20u, 30u), 1u)
        assertFailsWith<IllegalArgumentException> { reader.consume(-1) }
        assertFailsWith<IllegalArgumentException> { reader.consume(3) }
        assertEquals(1uL, reader.position())
        val filled = assertIs<Poll.Ready<Result<UByteArray>>>(reader.pollFillBuf(Context())).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(20u, 30u), filled)
    }
}
