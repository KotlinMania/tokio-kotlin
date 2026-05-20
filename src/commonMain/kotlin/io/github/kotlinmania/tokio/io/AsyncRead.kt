// port-lint: source io/async_read.rs
package io.github.kotlinmania.tokio.io

import kotlin.math.min

/**
 * Reads bytes from a source.
 *
 * This trait is analogous to a blocking byte reader, but integrates with the
 * asynchronous task system. In particular, `pollRead` queues the current task
 * for wakeup and returns when data is not yet available, rather than blocking
 * the calling thread.
 *
 * Specifically, `pollRead` returns one of the following:
 *
 * - `Poll.Ready(Result.success(Unit))` means that data was immediately read and
 *   placed into the output buffer. The amount read can be determined by the
 *   increase in the length of `ReadBuf.filled()`. If the difference is zero,
 *   either EOF has been reached, or the output buffer had zero capacity.
 * - `Poll.Pending` means that no data was read into the buffer provided. The
 *   I/O object is not currently readable but may become readable in the future.
 * - `Poll.Ready(Result.failure(error))` represents an error from the underlying
 *   object.
 *
 * This trait importantly means that the read method only works in the context
 * of a future's task. An object may fail if used outside of a task.
 */
public interface AsyncRead {
    /**
     * Attempts to read from the `AsyncRead` into `buf`.
     */
    public fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>>
}

/**
 * Asynchronous reader over an in-memory byte slice.
 */
public class ByteSliceAsyncRead(
    bytes: UByteArray,
) : AsyncRead {
    private var remainingBytes: UByteArray = bytes.copyOf()

    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
        val amount = min(remainingBytes.size, buf.remaining())
        val read = remainingBytes.copyOfRange(0, amount)
        val rest = remainingBytes.copyOfRange(amount, remainingBytes.size)
        buf.putSlice(read)
        remainingBytes = rest
        return Poll.ready(Result.success(Unit))
    }

    public fun remaining(): UByteArray = remainingBytes.copyOf()
}

/**
 * Asynchronous reader over a cursor into an immutable byte slice.
 */
public class CursorAsyncRead(
    private val bytes: UByteArray,
    private var position: ULong = 0u,
) : AsyncRead {
    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
        if (position > bytes.size.toULong()) {
            return Poll.ready(Result.success(Unit))
        }

        val start = position.toInt()
        val amount = min(bytes.size - start, buf.remaining())
        val end = start + amount
        buf.putSlice(bytes.copyOfRange(start, end))
        position = end.toULong()

        return Poll.ready(Result.success(Unit))
    }

    public fun position(): ULong = position
}
