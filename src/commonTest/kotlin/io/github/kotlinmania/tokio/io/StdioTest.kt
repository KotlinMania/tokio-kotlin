// port-lint: tests tests/io_read_buf.rs
package io.github.kotlinmania.tokio.io

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StdioTest {
    @Test
    fun testStdin() {
        val bytes = ubyteArrayOf(1u, 2u, 3u)
        val inHandle = Stdin.create(ByteSliceAsyncRead(bytes))
        val cx = Context()
        val readBuf = ReadBuf(UByteArray(5))

        val poll = inHandle.pollRead(cx, readBuf)
        assertTrue(poll.isReady)
        assertEquals(3, readBuf.filled().size)
        assertContentEquals(bytes, readBuf.filled())

        val defaultStdin = stdin()
        val emptyBuf = ReadBuf(UByteArray(5))
        val defaultPoll = defaultStdin.pollRead(cx, emptyBuf)
        assertTrue(defaultPoll.isReady)
        assertEquals(0, emptyBuf.filled().size)
    }

    @Test
    fun testStdout() {
        val inner = VecAsyncWrite()
        val outHandle = Stdout.create(inner)
        val cx = Context()

        val poll = outHandle.pollWrite(cx, ubyteArrayOf(42u, 43u))
        assertTrue(poll.isReady)
        assertEquals(2, (poll as Poll.Ready).value.getOrThrow())
        assertContentEquals(ubyteArrayOf(42u, 43u), inner.asSlice())

        val defaultStdout = stdout()
        val defaultPoll = defaultStdout.pollWrite(cx, ubyteArrayOf(1u))
        assertTrue(defaultPoll.isReady)
    }

    @Test
    fun testStderr() {
        val inner = VecAsyncWrite()
        val errHandle = Stderr.create(inner)
        val cx = Context()

        val poll = errHandle.pollWrite(cx, ubyteArrayOf(99u))
        assertTrue(poll.isReady)
        assertEquals(1, (poll as Poll.Ready).value.getOrThrow())
        assertContentEquals(ubyteArrayOf(99u), inner.asSlice())

        val defaultStderr = stderr()
        val defaultPoll = defaultStderr.pollWrite(cx, ubyteArrayOf(2u))
        assertTrue(defaultPoll.isReady)
    }
}
