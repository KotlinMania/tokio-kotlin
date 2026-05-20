// port-lint: source io/util/mem.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncRead
import io.github.kotlinmania.tokio.io.AsyncWrite
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.IoSlice
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import io.github.kotlinmania.tokio.io.ReadHalf
import io.github.kotlinmania.tokio.io.WriteHalf
import io.github.kotlinmania.tokio.io.split

/**
 * In-process memory I/O types.
 */

/**
 * A bidirectional pipe to read and write bytes in memory.
 *
 * A pair of `DuplexStream`s are created together, and they act as a channel
 * that can be used as in-memory I/O types. Writing to one of the pairs will
 * allow that data to be read from the other, and vice versa.
 *
 * Closing a `DuplexStream`
 *
 * If one end of the `DuplexStream` channel is closed, any pending reads on the
 * other side will continue to read data until the buffer is drained, then they
 * will signal end-of-file by returning zero bytes. Any writes to the other
 * side, including pending ones that are waiting for free space in the buffer,
 * will fail immediately.
 */
public class DuplexStream internal constructor(
    private val read: SimplexStream,
    private val write: SimplexStream,
) : AsyncRead, AsyncWrite {
    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> =
        read.pollRead(cx, buf)

    override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> =
        write.pollWrite(cx, buf)

    override fun pollWriteVectored(cx: Context, bufs: List<IoSlice>): Poll<Result<Int>> =
        write.pollWriteVectored(cx, bufs)

    override fun isWriteVectored(): Boolean = true

    override fun pollFlush(cx: Context): Poll<Result<Unit>> =
        write.pollFlush(cx)

    override fun pollShutdown(cx: Context): Poll<Result<Unit>> =
        write.pollShutdown(cx)

    public fun close() {
        write.closeWrite()
        read.closeRead()
    }

    public fun drop() {
        close()
    }

    public fun fmt(): String = toString()

    override fun toString(): String = "DuplexStream"
}

/**
 * A unidirectional pipe to read and write bytes in memory.
 *
 * It can be constructed by the `simplex` function, which creates a pair of
 * reader and writer handles, or by calling `SimplexStream.newUnsplit`, which
 * creates a handle for both reading and writing.
 */
public class SimplexStream private constructor(
    private val buffer: MutableList<UByte>,
    private var isClosed: Boolean,
    private val maxBufSize: Int,
    private var readWaker: (() -> Unit)?,
    private var writeWaker: (() -> Unit)?,
) : AsyncRead, AsyncWrite {
    /**
     * Creates an unidirectional buffer that acts like an in-memory pipe. To
     * create a split version with separate reader and writer, use `simplex`.
     *
     * The `maxBufSize` argument is the maximum amount of bytes that can be
     * written to a buffer before it returns `Poll.Pending`.
     */
    public companion object {
        public fun newUnsplit(maxBufSize: Int): SimplexStream =
            SimplexStream(
                buffer = mutableListOf(),
                isClosed = false,
                maxBufSize = maxBufSize,
                readWaker = null,
                writeWaker = null,
            )
    }

    internal fun closeWrite() {
        isClosed = true
        val waker = readWaker
        readWaker = null
        waker?.invoke()
    }

    internal fun closeRead() {
        isClosed = true
        val waker = writeWaker
        writeWaker = null
        waker?.invoke()
    }

    internal fun pollReadInternal(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
        if (buffer.isNotEmpty()) {
            val max = minOf(buffer.size, buf.remaining())
            val bytes = UByteArray(max) { buffer.removeAt(0) }
            buf.putSlice(bytes)
            if (max > 0) {
                val waker = writeWaker
                writeWaker = null
                waker?.invoke()
            }
            return Poll.ready(Result.success(Unit))
        }

        if (isClosed) {
            return Poll.ready(Result.success(Unit))
        }

        readWaker = cx.waker()
        return Poll.pending()
    }

    internal fun pollWriteInternal(cx: Context, buf: UByteArray): Poll<Result<Int>> {
        if (isClosed) {
            return Poll.ready(Result.failure(BrokenPipeException()))
        }

        val avail = maxBufSize - buffer.size
        if (avail == 0) {
            writeWaker = cx.waker()
            return Poll.pending()
        }

        val len = minOf(buf.size, avail)
        for (index in 0..<len) {
            buffer.add(buf[index])
        }

        val waker = readWaker
        readWaker = null
        waker?.invoke()

        return Poll.ready(Result.success(len))
    }

    internal fun pollWriteVectoredInternal(
        cx: Context,
        bufs: List<IoSlice>,
    ): Poll<Result<Int>> {
        if (isClosed) {
            return Poll.ready(Result.failure(BrokenPipeException()))
        }

        val avail = maxBufSize - buffer.size
        if (avail == 0) {
            writeWaker = cx.waker()
            return Poll.pending()
        }

        var rem = avail
        for (buf in bufs) {
            if (rem == 0) {
                break
            }

            val slice = buf.asSlice()
            val len = minOf(slice.size, rem)
            for (index in 0..<len) {
                buffer.add(slice[index])
            }
            rem -= len
        }

        val waker = readWaker
        readWaker = null
        waker?.invoke()

        return Poll.ready(Result.success(avail - rem))
    }

    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> =
        pollReadInternal(cx, buf)

    override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> =
        pollWriteInternal(cx, buf)

    override fun pollWriteVectored(cx: Context, bufs: List<IoSlice>): Poll<Result<Int>> =
        pollWriteVectoredInternal(cx, bufs)

    override fun isWriteVectored(): Boolean = true

    override fun pollFlush(cx: Context): Poll<Result<Unit>> =
        Poll.ready(Result.success(Unit))

    override fun pollShutdown(cx: Context): Poll<Result<Unit>> {
        closeWrite()
        return Poll.ready(Result.success(Unit))
    }

    public fun fmt(): String = toString()

    override fun toString(): String =
        "SimplexStream(buffered=${buffer.size}, closed=$isClosed, maxBufSize=$maxBufSize)"
}

/**
 * Create a new pair of `DuplexStream`s that act like a pair of connected
 * sockets.
 *
 * The `maxBufSize` argument is the maximum amount of bytes that can be written
 * to a side before the write returns `Poll.Pending`.
 */
public fun duplex(maxBufSize: Int): Pair<DuplexStream, DuplexStream> {
    val one = SimplexStream.newUnsplit(maxBufSize)
    val two = SimplexStream.newUnsplit(maxBufSize)

    return Pair(
        DuplexStream(read = one, write = two),
        DuplexStream(read = two, write = one),
    )
}

/**
 * Creates an unidirectional buffer that acts like an in-memory pipe.
 *
 * The `maxBufSize` argument is the maximum amount of bytes that can be written
 * to a buffer before it returns `Poll.Pending`.
 *
 * The reader and writer half can be unified into a single `SimplexStream` that
 * supports both reading and writing, or the `SimplexStream` can be created as
 * a unified structure using `SimplexStream.newUnsplit`.
 */
public fun simplex(maxBufSize: Int): Pair<ReadHalf<SimplexStream>, WriteHalf<SimplexStream>> =
    split(SimplexStream.newUnsplit(maxBufSize))

public class BrokenPipeException : IllegalStateException("broken pipe")
