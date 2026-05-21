package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.IoSlice
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

        val written = assertIs<Poll.Ready<Result<Int>>>(
            stream.pollWriteVectored(cx, listOf(IoSlice(bytes("abc")), IoSlice(bytes("def")))),
        )
        assertEquals(5, written.value.getOrThrow())

        val out = ReadBuf(UByteArray(5))
        assertIs<Poll.Ready<Result<Unit>>>(stream.pollRead(cx, out)).value.getOrThrow()
        assertContentEquals(bytes("abcde"), out.filled())
    }

    private fun bytes(value: String): UByteArray =
        UByteArray(value.length) { index -> value[index].code.toUByte() }
}
