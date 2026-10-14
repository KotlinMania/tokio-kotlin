// port-lint: source io/async_buf_read.rs
package io.github.kotlinmania.tokio.io

import kotlin.math.min

/**
 * Reads bytes asynchronously from a buffered source.
 *
 * This trait is analogous to a blocking buffered byte reader, but integrates with the
 * asynchronous task system. In particular, the [pollFillBuf] method, unlike a blocking
 * equivalent, will automatically queue the current task for wakeup and return if data
 * is not yet available, rather than blocking the calling thread.
 */
public interface AsyncBufRead : AsyncRead {
    /**
     * Attempts to return the contents of the internal buffer, filling it with more data
     * from the inner reader if it is empty.
     *
     * On success, returns `Poll.Ready(Result.success(buf))`.
     *
     * If no data is available, returns [Poll.Pending] and arranges for the
     * current task, through [Context.waker], to be notified when the source
     * becomes readable or is closed.
     *
     * This call must be paired with [consume]. Filling the buffer does not
     * consume its contents: a later [pollRead] can return the same bytes.
     * Call [consume] with the number of bytes used to avoid returning them twice.
     *
     * An empty buffer returned indicates that the stream has reached EOF.
     */
    public fun pollFillBuf(cx: Context): Poll<Result<UByteArray>>

    /**
     * Tells this buffer that [amt] bytes have been consumed from the buffer,
     * so they should no longer be returned in calls to [pollRead].
     *
     * This call must be paired with [pollFillBuf]. It performs no I/O; it only
     * marks bytes from the most recently returned buffer as consumed. Calling
     * it without first filling the buffer may have unexpected results.
     *
     * [amt] must be nonnegative and no greater than the number of bytes returned
     * by [pollFillBuf].
     */
    public fun consume(amt: Int)
}

/**
 * Asynchronous buffered reader over an in-memory byte slice.
 */
public class ByteSliceAsyncBufRead(
    bytes: UByteArray,
) : AsyncBufRead {
    private var remainingBytes: UByteArray = bytes.copyOf()

    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
        val amount = min(remainingBytes.size, buf.remaining())
        val read = remainingBytes.copyOfRange(0, amount)
        val rest = remainingBytes.copyOfRange(amount, remainingBytes.size)
        buf.putSlice(read)
        remainingBytes = rest
        return Poll.ready(Result.success(Unit))
    }

    override fun pollFillBuf(cx: Context): Poll<Result<UByteArray>> =
        Poll.ready(Result.success(remainingBytes.copyOf()))

    override fun consume(amt: Int) {
        require(amt in 0..remainingBytes.size) { "cannot consume more bytes than remaining" }
        remainingBytes = remainingBytes.copyOfRange(amt, remainingBytes.size)
    }

    public fun remaining(): UByteArray = remainingBytes.copyOf()
}

/**
 * Asynchronous buffered reader over a cursor into an immutable byte slice.
 */
public class CursorAsyncBufRead(
    private val bytes: UByteArray,
    private var position: ULong = 0u,
) : AsyncBufRead {
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

    override fun pollFillBuf(cx: Context): Poll<Result<UByteArray>> {
        // Clamp in the cursor's width before converting to a Kotlin array index.
        val start = minOf(position, bytes.size.toULong()).toInt()
        return Poll.ready(Result.success(bytes.copyOfRange(start, bytes.size)))
    }

    override fun consume(amt: Int) {
        val current = minOf(position, bytes.size.toULong()).toInt()
        val available = bytes.size - current
        require(amt in 0..available) { "cannot consume more bytes than available" }
        position += amt.toULong()
    }

    public fun position(): ULong = position
}
