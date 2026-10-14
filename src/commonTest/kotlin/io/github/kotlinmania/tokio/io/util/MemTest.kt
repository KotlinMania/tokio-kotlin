// port-lint: source tests/io_mem_stream.rs; tests/duplex_stream.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.IoSlice
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class MemTest {
    @Test
    fun duplexTransfersBytesBothDirections() {
        val (client, server) = duplex(64)
        val cx = Context()

        val clientWrite = assertIs<Poll.Ready<Result<Int>>>(client.pollWrite(cx, bytes("ping")))
        assertEquals(4, clientWrite.value.getOrThrow())

        val serverBuf = ReadBuf(UByteArray(4))
        assertIs<Poll.Ready<Result<Unit>>>(server.pollRead(cx, serverBuf)).value.getOrThrow()
        assertContentEquals(bytes("ping"), serverBuf.filled())

        val serverWrite = assertIs<Poll.Ready<Result<Int>>>(server.pollWrite(cx, bytes("pong")))
        assertEquals(4, serverWrite.value.getOrThrow())

        val clientBuf = ReadBuf(UByteArray(4))
        assertIs<Poll.Ready<Result<Unit>>>(client.pollRead(cx, clientBuf)).value.getOrThrow()
        assertContentEquals(bytes("pong"), clientBuf.filled())
    }

    @Test
    fun simplexCanUnsplitReaderAndWriter() {
        val (reader, writer) = simplex(8)
        assertEquals(true, reader.isPairOf(writer))

        val stream = reader.unsplit(writer)
        val cx = Context()
        assertEquals(3, assertIs<Poll.Ready<Result<Int>>>(stream.pollWrite(cx, bytes("hey"))).value.getOrThrow())

        val out = ReadBuf(UByteArray(3))
        assertIs<Poll.Ready<Result<Unit>>>(stream.pollRead(cx, out)).value.getOrThrow()
        assertContentEquals(bytes("hey"), out.filled())
    }

    @Test
    fun vectoredWriteStopsAtCapacity() {
        val stream = SimplexStream.newUnsplit(5)
        val cx = Context()

        val written =
            assertIs<Poll.Ready<Result<Int>>>(
                stream.pollWriteVectored(cx, listOf(IoSlice(bytes("abc")), IoSlice(bytes("def")))),
            )
        assertEquals(5, written.value.getOrThrow())

        val out = ReadBuf(UByteArray(5))
        assertIs<Poll.Ready<Result<Unit>>>(stream.pollRead(cx, out)).value.getOrThrow()
        assertContentEquals(bytes("abcde"), out.filled())
    }

    @Test
    fun disconnectDrainsBufferedDataBeforeEof() {
        val (client, server) = duplex(32)
        val cx = Context()
        assertIs<Poll.Ready<Result<Int>>>(client.pollWrite(cx, bytes("ping"))).value.getOrThrow()
        client.close()
        val buf = ReadBuf(UByteArray(32))
        assertIs<Poll.Ready<Result<Unit>>>(server.pollRead(cx, buf)).value.getOrThrow()
        assertContentEquals(bytes("ping"), buf.filled())
        buf.clear()
        assertIs<Poll.Ready<Result<Unit>>>(server.pollRead(cx, buf)).value.getOrThrow()
        assertEquals(0, buf.filled().size)
        val write = assertIs<Poll.Ready<Result<Int>>>(server.pollWrite(cx, bytes("pong")))
        assertIs<BrokenPipeException>(write.value.exceptionOrNull())
    }

    @Test
    fun scopedEndpointClosesWhenItsBodyThrows() {
        val (client, server) = duplex(4)
        val failure = IllegalArgumentException("body failed")
        assertSame(failure, assertFailsWith<IllegalArgumentException> { client.use { throw failure } })
        val out = ReadBuf(UByteArray(1))
        assertIs<Poll.Ready<Result<Unit>>>(server.pollRead(Context(), out)).value.getOrThrow()
        assertEquals(0, out.filled().size)
        val write = assertIs<Poll.Ready<Result<Int>>>(server.pollWrite(Context(), bytes("x")))
        assertIs<BrokenPipeException>(write.value.exceptionOrNull())
    }

    @Test
    fun disconnectReaderWakesTheBlockedWriter() {
        val (reader, writer) = duplex(2)
        var wakes = 0
        val cx = Context { wakes++ }
        assertEquals(2, assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, bytes("ping"))).value.getOrThrow())
        assertSame(Poll.Pending, writer.pollWrite(cx, bytes("ng")))
        reader.close()
        assertEquals(1, wakes)
        val write = assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, bytes("ng")))
        assertIs<BrokenPipeException>(write.value.exceptionOrNull())
        reader.close()
        assertEquals(1, wakes)
    }

    @Test
    fun maxWriteSizeAndReadProgressWakeTheWriter() {
        val (writer, reader) = duplex(32)
        var wakes = 0
        val cx = Context { wakes++ }
        val input = UByteArray(64)
        assertEquals(32, assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, input)).value.getOrThrow())
        assertSame(Poll.Pending, writer.pollWrite(cx, input))
        assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(Context(), ReadBuf(UByteArray(0)))).value.getOrThrow()
        assertEquals(0, wakes)
        assertIs<Poll.Ready<Result<Unit>>>(reader.pollRead(Context(), ReadBuf(UByteArray(4)))).value.getOrThrow()
        assertEquals(1, wakes)
        assertEquals(4, assertIs<Poll.Ready<Result<Int>>>(writer.pollWrite(cx, input)).value.getOrThrow())
    }

    @Test
    fun writeVectoredDrainsAfterEndpointClose() {
        val (client, server) = duplex(64)
        val cx = Context()
        val hello = bytes("hello world...")
        client.use {
            val written = it.pollWriteVectored(cx, listOf(IoSlice(hello), IoSlice(hello)))
            assertEquals(hello.size * 2, assertIs<Poll.Ready<Result<Int>>>(written).value.getOrThrow())
            assertIs<Poll.Ready<Result<Unit>>>(it.pollFlush(cx)).value.getOrThrow()
        }
        val out = ReadBuf(UByteArray(64))
        assertIs<Poll.Ready<Result<Unit>>>(server.pollRead(cx, out)).value.getOrThrow()
        assertContentEquals(hello + hello, out.filled())
        out.clear()
        assertIs<Poll.Ready<Result<Unit>>>(server.pollRead(cx, out)).value.getOrThrow()
        assertEquals(0, out.filled().size)
    }

    @Test
    fun writeVectoredAndShutdownDrainBeforeEof() {
        val (client, server) = duplex(64)
        val cx = Context()
        val hello = bytes("hello world...")
        val written = client.pollWriteVectored(cx, listOf(IoSlice(hello), IoSlice(hello)))
        assertEquals(hello.size * 2, assertIs<Poll.Ready<Result<Int>>>(written).value.getOrThrow())
        assertIs<Poll.Ready<Result<Unit>>>(client.pollFlush(cx)).value.getOrThrow()
        assertIs<Poll.Ready<Result<Unit>>>(client.pollShutdown(cx)).value.getOrThrow()
        val out = ReadBuf(UByteArray(64))
        assertIs<Poll.Ready<Result<Unit>>>(server.pollRead(cx, out)).value.getOrThrow()
        assertContentEquals(hello + hello, out.filled())
        out.clear()
        assertIs<Poll.Ready<Result<Unit>>>(server.pollRead(cx, out)).value.getOrThrow()
        assertEquals(0, out.filled().size)
        assertEquals(1, assertIs<Poll.Ready<Result<Int>>>(server.pollWrite(cx, bytes("x"))).value.getOrThrow())
    }

    @Test
    fun shutdownWakesTheMostRecentPendingReader() {
        val (client, server) = duplex(4)
        var oldWakes = 0
        var newWakes = 0
        assertSame(Poll.Pending, server.pollRead(Context { oldWakes++ }, ReadBuf(UByteArray(1))))
        assertSame(Poll.Pending, server.pollRead(Context { newWakes++ }, ReadBuf(UByteArray(1))))
        assertIs<Poll.Ready<Result<Unit>>>(client.pollShutdown(Context())).value.getOrThrow()
        assertEquals(0, oldWakes)
        assertEquals(1, newWakes)
        val out = ReadBuf(UByteArray(1))
        assertIs<Poll.Ready<Result<Unit>>>(server.pollRead(Context(), out)).value.getOrThrow()
        assertEquals(0, out.filled().size)
    }

    @Test
    fun panickingWakerDoesNotPoisonDuplexBuffers() {
        val (client, server) = duplex(4)
        val panic = IllegalArgumentException("waker panicked")
        assertSame(Poll.Pending, server.pollRead(Context { throw panic }, ReadBuf(UByteArray(1))))
        assertSame(panic, assertFailsWith<IllegalArgumentException> { client.pollWrite(Context(), bytes("x")) })
        val out = ReadBuf(UByteArray(1))
        assertIs<Poll.Ready<Result<Unit>>>(server.pollRead(Context(), out)).value.getOrThrow()
        assertContentEquals(bytes("x"), out.filled())
        client.close()
        server.close()
    }

    @Test
    fun partialReadsAndRefillsPreserveByteOrderAndCapacity() {
        val stream = SimplexStream.newUnsplit(5)
        val cx = Context()
        assertEquals(5, assertIs<Poll.Ready<Result<Int>>>(stream.pollWrite(cx, ubyteArrayOf(0u, 127u, 128u, 254u, 255u))).value.getOrThrow())
        val prefix = ReadBuf(UByteArray(2))
        assertIs<Poll.Ready<Result<Unit>>>(stream.pollRead(cx, prefix)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(0u, 127u), prefix.filled())
        val refill = stream.pollWriteVectored(cx, listOf(IoSlice(ubyteArrayOf(1u)), IoSlice(ubyteArrayOf(2u, 3u))))
        assertEquals(2, assertIs<Poll.Ready<Result<Int>>>(refill).value.getOrThrow())
        assertSame(Poll.Pending, stream.pollWrite(cx, ubyteArrayOf(4u)))
        val out = ReadBuf(UByteArray(5))
        assertIs<Poll.Ready<Result<Unit>>>(stream.pollRead(cx, out)).value.getOrThrow()
        assertContentEquals(ubyteArrayOf(128u, 254u, 255u, 1u, 2u), out.filled())
        repeat(32) { index ->
            val bytes = UByteArray(5) { (index * 5 + it).toUByte() }
            assertEquals(5, assertIs<Poll.Ready<Result<Int>>>(stream.pollWrite(cx, bytes)).value.getOrThrow())
            out.clear()
            assertIs<Poll.Ready<Result<Unit>>>(stream.pollRead(cx, out)).value.getOrThrow()
            assertContentEquals(bytes, out.filled())
        }
        assertSame(Poll.Pending, stream.pollRead(cx, out.apply { clear() }))
    }

    private fun bytes(value: String): UByteArray =
        UByteArray(value.length) { index -> value[index].code.toUByte() }
}
