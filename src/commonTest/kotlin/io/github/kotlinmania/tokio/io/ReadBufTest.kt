// port-lint: source tests/io_read_buf.rs
package io.github.kotlinmania.tokio.io

import io.github.kotlinmania.bytes.buf.BufMut
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReadBufTest {
    @Test
    fun negativeFilledLengthCannotCorruptTheCursor() {
        val buf = ReadBuf(ubyteArrayOf(1u, 2u, 3u))
        buf.setFilled(2)
        assertFailsWith<IllegalArgumentException> { buf.setFilled(-1) }
        assertContentEquals(ubyteArrayOf(1u, 2u), buf.filled())
        assertEquals(1, buf.remaining())
    }

    @Test
    fun negativeAdvanceCannotMoveTheCursorBackwards() {
        val buf = ReadBuf(ubyteArrayOf(1u, 2u, 3u))
        buf.setFilled(2)
        assertFailsWith<IllegalArgumentException> { buf.advance(-1) }
        assertEquals(2, buf.filled().size)
        assertEquals(1, buf.remaining())
    }

    @Test
    fun negativeInitializationCountIsRejected() {
        val buf = ReadBuf.uninit(UByteArray(3))
        assertFailsWith<IllegalArgumentException> { buf.assumeInit(-1) }
        assertEquals(0, buf.initialized().size)
        assertEquals(3, buf.remaining())
    }

    @Test
    fun invalidBoundsLeaveBothCursorsUnchanged() {
        val buf = ReadBuf.uninit(UByteArray(4))
        buf.assumeInit(2)
        buf.advance(1)
        assertFailsWith<IllegalArgumentException> { buf.advance(Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { buf.advance(2) }
        assertFailsWith<IllegalArgumentException> { buf.setFilled(3) }
        assertFailsWith<IllegalArgumentException> { buf.assumeInit(4) }
        assertFailsWith<IllegalArgumentException> { buf.initializeUnfilledTo(4) }
        assertFailsWith<IllegalArgumentException> { buf.initializeUnfilledTo(-1) }
        assertFailsWith<IllegalArgumentException> { buf.take(-1) }
        assertEquals(1, buf.filled().size)
        assertEquals(2, buf.initialized().size)
        assertEquals(3, buf.remaining())
    }

    @Test
    fun issue5588StepsToZero() {
        val buf = ReadBuf(UByteArray(8))
        assertEquals(8, buf.remainingMut())
        assertEquals(8, buf.chunkMut().len())
        for ((advance, remaining) in listOf(1 to 7, 5 to 2, 2 to 0)) {
            buf.advanceMut(advance)
            assertEquals(remaining, buf.remainingMut())
            assertEquals(remaining, buf.chunkMut().len())
        }
    }

    @Test
    fun issue5588DirectlyToZero() {
        val buf = ReadBuf(UByteArray(8))
        buf.advanceMut(8)
        assertEquals(0, buf.remainingMut())
        assertEquals(0, buf.chunkMut().len())
    }

    @Test
    fun issue5588UninitializedBuffer() {
        val buf = ReadBuf.uninit(UByteArray(8) { 1u })
        assertEquals(8, buf.remainingMut())
        assertEquals(8, buf.chunkMut().len())
        assertEquals(0, buf.initialized().size)
        buf.advanceMut(4)
        assertEquals(4, buf.remainingMut())
        assertEquals(4, buf.chunkMut().len())
        buf.putU8(1u)
        assertEquals(3, buf.remainingMut())
        assertEquals(3, buf.chunkMut().len())
        buf.putSlice(ubyteArrayOf(1u, 2u, 3u))
        assertEquals(0, buf.remainingMut())
        assertEquals(0, buf.chunkMut().len())
        assertContentEquals(ubyteArrayOf(1u, 1u, 1u, 1u, 1u, 1u, 2u, 3u), buf.filled())
    }

    @Test
    fun initializationOnlyZerosNewlyInitializedBytes() {
        val bytes = UByteArray(6) { 255u }
        val buf = ReadBuf.uninit(bytes)
        buf.innerMut()[0] = 7u
        buf.assumeInit(1)
        assertContentEquals(ubyteArrayOf(7u, 0u, 0u), buf.initializeUnfilledTo(3).copy())
        buf.advance(1)
        buf.initializeUnfilledTo(4)
        assertContentEquals(ubyteArrayOf(7u, 0u, 0u, 0u, 0u, 255u), bytes)
        assertEquals(5, buf.initialized().size)
        buf.clear()
        assertEquals(0, buf.filled().size)
        assertEquals(5, buf.initialized().size)
        assertEquals(6, buf.remaining())
    }

    @Test
    fun takeSharesTheUnfilledRegionWithoutAdvancingTheParent() {
        val parent = ReadBuf(UByteArray(6))
        parent.putSlice(ubyteArrayOf(1u, 2u))
        val child = parent.take(2)
        assertEquals(2, child.capacity())
        assertEquals(0, child.initialized().size)
        child.putSlice(ubyteArrayOf(3u, 4u))
        assertEquals(2, parent.filled().size)
        parent.advance(2)
        assertContentEquals(ubyteArrayOf(1u, 2u, 3u, 4u), parent.filled())
        assertEquals(2, parent.take(Int.MAX_VALUE).capacity())
    }

    @Test
    fun mutableViewsWriteThroughToTheOriginalBuffer() {
        val bytes = ubyteArrayOf(1u, 2u, 3u)
        val buf = ReadBuf(bytes)
        buf.advance(1)
        buf.filledMut()[0] = 4u
        buf.initializedMut()[1] = 5u
        buf.unfilledMut()[1] = 6u
        assertContentEquals(ubyteArrayOf(4u, 5u, 6u), bytes)
        assertContentEquals(ubyteArrayOf(4u), buf.filled())
    }

    @Test
    fun oversizedAppendDoesNotChangeTheBuffer() {
        val buf = ReadBuf.uninit(UByteArray(3))
        buf.putSlice(ubyteArrayOf(1u))
        assertFailsWith<IllegalArgumentException> { buf.putSlice(ubyteArrayOf(2u, 3u, 4u)) }
        assertContentEquals(ubyteArrayOf(1u), buf.filled())
        assertEquals(1, buf.initialized().size)
    }

    @Test
    fun siblingBufMutMethodsWriteIntoTheOriginalBuffer() {
        val bytes = UByteArray(8) { 255u }
        val buf = ReadBuf.uninit(bytes)
        val writer: BufMut = buf
        writer.putU16(0x1234u)
        writer.putBytes(0x56, 2)
        writer.putSlice(byteArrayOf(0x78))
        writer.chunkMut().writeByte(0, 0x7f)
        writer.advanceMut(1)
        assertContentEquals(ubyteArrayOf(0x12u, 0x34u, 0x56u, 0x56u, 0x78u, 0x7fu), buf.filled())
        assertEquals(6, buf.initialized().size)
        assertEquals(2, writer.remainingMut())
        assertContentEquals(buf.filled(), bytes.copyOfRange(0, 6))
        assertContentEquals(ubyteArrayOf(255u, 255u), bytes.copyOfRange(6, 8))
    }

    @Test
    fun siblingBufMutChunkRespectsTheTakenWindow() {
        val bytes = UByteArray(6) { 9u }
        val parent = ReadBuf(bytes)
        parent.advance(2)
        val child = parent.take(2)
        val writer: BufMut = child
        writer.chunkMut().copyFromSlice(byteArrayOf(1, 2))
        writer.advanceMut(2)
        assertEquals(0, writer.chunkMut().len())
        assertContentEquals(ubyteArrayOf(9u, 9u, 1u, 2u, 9u, 9u), bytes)
        assertContentEquals(ubyteArrayOf(1u, 2u), child.filled())
        assertEquals(2, parent.filled().size)
        assertFailsWith<IllegalArgumentException> { writer.advanceMut(-1) }
        assertFailsWith<IllegalArgumentException> { writer.advanceMut(1) }
        assertEquals(0, writer.remainingMut())
    }

    @Test
    fun overflowingSliceBoundsAreRejectedAtConstruction() {
        val bytes = UByteArray(1)
        assertFailsWith<IllegalArgumentException> { UByteSlice(bytes, Int.MAX_VALUE, 1) }
        assertFailsWith<IllegalArgumentException> { UByteSlice(bytes, 1, Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { UByteSlice(bytes, -1, 1) }
        assertFailsWith<IllegalArgumentException> { UByteSlice(bytes, 0, -1) }
        assertEquals(0, UByteSlice(bytes, 1, 0).size)
    }
}
