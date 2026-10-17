// port-lint: source io/util/buf_reader.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncBufRead
import io.github.kotlinmania.tokio.io.AsyncRead
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import kotlin.math.min

public const val DEFAULT_BUF_SIZE: Int = 8 * 1024

/**
 * The `BufReader` class adds buffering to any reader.
 *
 * It can be excessively inefficient to work directly with an [AsyncRead] instance.
 * A `BufReader` performs large, infrequent reads on the underlying [AsyncRead] and
 * maintains an in-memory buffer of the results.
 *
 * It can improve the speed of small, repeated reads from the same file or
 * network socket. It does not help with large reads, with only a few reads,
 * or with sources that are already in memory.
 *
 * Discarding a `BufReader` also discards its buffered data. Creating multiple
 * buffered readers over the same stream can therefore cause data loss.
 */
public class BufReader<R : AsyncRead>(
    private val inner: R,
    capacity: Int = DEFAULT_BUF_SIZE,
) : AsyncBufRead {
    private val buf: UByteArray = UByteArray(capacity)
    private var pos: Int = 0
    private var cap: Int = 0

    public companion object {
        /**
         * Creates a buffered reader with the default capacity, currently 8 KB.
         * The default may change in the future.
         */
        public fun <R : AsyncRead> new(inner: R): BufReader<R> =
            BufReader(inner, DEFAULT_BUF_SIZE)

        /** Creates a buffered reader with the specified capacity. */
        public fun <R : AsyncRead> withCapacity(capacity: Int, inner: R): BufReader<R> =
            BufReader(inner, capacity)
    }

    /**
     * Gets a reference to the underlying reader.
     * Reading directly from it is inadvisable because this reader may hold buffered data.
     */
    public fun getRef(): R = inner

    /**
     * Gets a mutable reference to the underlying reader.
     * Reading directly from it is inadvisable because this reader may hold buffered data.
     */
    public fun getMut(): R = inner

    /**
     * Gets the underlying reader for a pinned mutable access in Rust.
     * Kotlin uses the same object reference for this form of access.
     * Reading directly from it is inadvisable because this reader may hold buffered data.
     */
    public fun getPinMut(): R = inner

    /**
     * Returns the underlying reader. Any unread bytes held by this buffer are
     * lost if reading continues directly from the returned reader.
     */
    public fun intoInner(): R = inner

    /** Returns the buffered data without attempting to fill an empty buffer. */
    public fun buffer(): UByteArray = buf.copyOfRange(pos, cap)

    /** Invalidates all data in the internal buffer. */
    public fun discardBuffer() {
        pos = 0
        cap = 0
    }

    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
        // If we don't have any buffered data and we're doing a massive read
        // (larger than our internal buffer), bypass our internal buffer entirely.
        if (pos == cap && buf.remaining() >= this.buf.size) {
            val res = inner.pollRead(cx, buf)
            if (res.isPending) {
                return Poll.pending()
            }
            discardBuffer()
            return res
        }

        val fillPoll = pollFillBuf(cx)
        if (fillPoll.isPending) {
            return Poll.pending()
        }
        val readyResult = (fillPoll as Poll.Ready).value
        if (readyResult.isFailure) {
            return Poll.ready(Result.failure(readyResult.exceptionOrNull()!!))
        }
        val rem = readyResult.getOrThrow()
        val amt = min(rem.size, buf.remaining())
        buf.putSlice(rem.copyOfRange(0, amt))
        consume(amt)
        return Poll.ready(Result.success(Unit))
    }

    override fun pollFillBuf(cx: Context): Poll<Result<UByteArray>> {
        // At the end of the buffered data, fetch more from the underlying reader.
        // Use >= instead of == to make the bounds of the pos..cap slice explicit.
        if (pos >= cap) {
            val readBuf = ReadBuf(buf)
            val readPoll = inner.pollRead(cx, readBuf)
            if (readPoll.isPending) {
                return Poll.pending()
            }
            val readyResult = (readPoll as Poll.Ready).value
            if (readyResult.isFailure) {
                return Poll.ready(Result.failure(readyResult.exceptionOrNull()!!))
            }
            cap = readBuf.filled().size
            pos = 0
        }
        return Poll.ready(Result.success(buf.copyOfRange(pos, cap)))
    }

    override fun consume(amt: Int) {
        require(amt >= 0) { "cannot consume a negative number of bytes" }
        // Clamp the increment before adding it so Kotlin's signed index cannot overflow.
        pos += min(amt, cap - pos)
    }
}
