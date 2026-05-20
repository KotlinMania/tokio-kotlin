// port-lint: source io/async_write.rs
package io.github.kotlinmania.tokio.io

/**
 * Writes bytes asynchronously.
 *
 * This trait is analogous to a blocking byte writer, but integrates with the
 * asynchronous task system. In particular, `pollWrite` queues the current task
 * for wakeup and returns when data is not yet available, rather than blocking
 * the calling thread.
 *
 * Specifically, `pollWrite` returns one of the following:
 *
 * - `Poll.Ready(Result.success(n))` means that `n` bytes of data were
 *   immediately written.
 * - `Poll.Pending` means that no data was written from the provided buffer. The
 *   I/O object is not currently writable but may become writable in the future.
 * - `Poll.Ready(Result.failure(error))` represents an error from the underlying
 *   object.
 *
 * This trait importantly means that the write method only works in the context
 * of a future's task. An object may fail if used outside of a task.
 */
public interface AsyncWrite {
    /**
     * Attempts to write bytes from `buf` into the object.
     */
    public fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>>

    /**
     * Attempts to flush the object, ensuring that buffered data reaches its
     * destination.
     */
    public fun pollFlush(cx: Context): Poll<Result<Unit>>

    /**
     * Initiates or attempts to shut down this writer, returning success when
     * the I/O connection has completely shut down.
     */
    public fun pollShutdown(cx: Context): Poll<Result<Unit>>

    /**
     * Like `pollWrite`, except that it writes from a slice of buffers.
     *
     * The default implementation calls `pollWrite` with the first nonempty
     * buffer, or an empty one if none exists.
     */
    public fun pollWriteVectored(cx: Context, bufs: List<IoSlice>): Poll<Result<Int>> {
        val buf = bufs.firstOrNull { !it.isEmpty() }?.asSlice() ?: UByteArray(0)
        return pollWrite(cx, buf)
    }

    /**
     * Determines if this writer has an efficient `pollWriteVectored`
     * implementation.
     */
    public fun isWriteVectored(): Boolean = false
}

/**
 * Borrowed byte buffer used for vectored writes.
 */
public class IoSlice(
    private val bytes: UByteArray,
) {
    public val size: Int = bytes.size

    public fun isEmpty(): Boolean = bytes.isEmpty()

    public fun asSlice(): UByteArray = bytes.copyOf()
}

/**
 * Asynchronous writer backed by an in-memory vector.
 */
public class VecAsyncWrite(
    initialBytes: UByteArray = UByteArray(0),
) : AsyncWrite {
    private val bytes: MutableList<UByte> = initialBytes.toMutableList()

    override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> {
        for (byte in buf) {
            bytes.add(byte)
        }
        return Poll.ready(Result.success(buf.size))
    }

    override fun pollWriteVectored(cx: Context, bufs: List<IoSlice>): Poll<Result<Int>> {
        var written = 0
        for (buf in bufs) {
            val slice = buf.asSlice()
            for (byte in slice) {
                bytes.add(byte)
            }
            written += slice.size
        }
        return Poll.ready(Result.success(written))
    }

    override fun isWriteVectored(): Boolean = true

    override fun pollFlush(cx: Context): Poll<Result<Unit>> =
        Poll.ready(Result.success(Unit))

    override fun pollShutdown(cx: Context): Poll<Result<Unit>> = pollFlush(cx)

    public fun asSlice(): UByteArray = UByteArray(bytes.size) { index -> bytes[index] }
}

/**
 * Asynchronous writer over a cursor into a fixed byte buffer.
 */
public class CursorAsyncWrite(
    private val bytes: UByteArray,
    private var position: Int = 0,
) : AsyncWrite {
    override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> {
        if (position >= bytes.size || buf.isEmpty()) {
            return Poll.ready(Result.success(0))
        }

        val amount = minOf(buf.size, bytes.size - position)
        buf.copyInto(bytes, destinationOffset = position, endIndex = amount)
        position += amount
        return Poll.ready(Result.success(amount))
    }

    override fun pollWriteVectored(cx: Context, bufs: List<IoSlice>): Poll<Result<Int>> {
        var written = 0
        for (buf in bufs) {
            val result = pollWrite(cx, buf.asSlice())
            when (result) {
                Poll.Pending -> return result
                is Poll.Ready -> {
                    val amount = result.value.getOrElse { return Poll.ready(Result.failure(it)) }
                    written += amount
                    if (amount < buf.size) {
                        return Poll.ready(Result.success(written))
                    }
                }
            }
        }
        return Poll.ready(Result.success(written))
    }

    override fun isWriteVectored(): Boolean = true

    override fun pollFlush(cx: Context): Poll<Result<Unit>> =
        Poll.ready(Result.success(Unit))

    override fun pollShutdown(cx: Context): Poll<Result<Unit>> = pollFlush(cx)

    public fun position(): Int = position

    public fun asSlice(): UByteArray = bytes.copyOf()
}
