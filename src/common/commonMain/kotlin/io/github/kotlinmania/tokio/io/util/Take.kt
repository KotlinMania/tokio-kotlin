// port-lint: source io/util/take.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncBufRead
import io.github.kotlinmania.tokio.io.AsyncRead
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf

/**
 * Stream for the [take] helper.
 */
public class Take<R : AsyncRead> internal constructor(
    internal val inner: R,
    internal var remainingLimit: ULong,
) : AsyncRead {
    /**
     * Returns the remaining number of bytes that can be read before this instance
     * will return EOF.
     */
    public fun limit(): ULong = remainingLimit

    /**
     * Sets the number of bytes that can be read before this instance will return EOF.
     *
     * This is the same as constructing a new [Take] instance, so the amount of bytes
     * read and the previous limit value do not matter when calling this method.
     */
    public fun setLimit(limit: ULong) {
        remainingLimit = limit
    }

    /**
     * Gets a reference to the underlying reader.
     */
    public fun getRef(): R = inner

    /**
     * Gets a mutable reference to the underlying reader.
     *
     * Care should be taken to avoid modifying the internal I/O state of the
     * underlying reader as doing so may corrupt the internal limit of this [Take].
     */
    public fun getMut(): R = inner

    /**
     * Gets a pinned mutable reference to the underlying reader.
     *
     * Care should be taken to avoid modifying the internal I/O state of the
     * underlying reader as doing so may corrupt the internal limit of this [Take].
     */
    public fun getPinMut(): R = inner

    /**
     * Consumes the [Take], returning the wrapped reader.
     */
    public fun intoInner(): R = inner

    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
        if (remainingLimit == 0uL) {
            return Poll.ready(Result.success(Unit))
        }

        val max = minOf(remainingLimit, Int.MAX_VALUE.toULong()).toInt()
        val subBuf = buf.take(max)

        val readPoll = inner.pollRead(cx, subBuf)
        if (readPoll.isPending) {
            return Poll.pending()
        }
        val result = (readPoll as Poll.Ready).value
        val err = result.exceptionOrNull()
        if (err != null) {
            return Poll.ready(Result.failure(err))
        }

        val n = subBuf.filled().size
        buf.assumeInit(n)
        buf.advance(n)
        remainingLimit -= n.toULong()
        return Poll.ready(Result.success(Unit))
    }

    public fun fmt(): String = toString()

    override fun toString(): String = "Take(inner=$inner, limit=$remainingLimit)"
}

/**
 * Fills the buffer from the underlying buffered reader up to the remaining limit.
 */
public fun <R : AsyncBufRead> Take<R>.pollFillBuf(cx: Context): Poll<Result<UByteArray>> {
    // Do not call into inner reader at all at EOF because it may still block.
    if (remainingLimit == 0uL) {
        return Poll.ready(Result.success(UByteArray(0)))
    }

    val fillPoll = inner.pollFillBuf(cx)
    if (fillPoll.isPending) {
        return Poll.pending()
    }
    val result = (fillPoll as Poll.Ready).value
    val err = result.exceptionOrNull()
    if (err != null) {
        return Poll.ready(Result.failure(err))
    }
    val buf = result.getOrThrow()
    val cap = minOf(buf.size.toULong(), remainingLimit).toInt()
    return Poll.ready(Result.success(buf.copyOf(cap)))
}

/**
 * Consumes at most the remaining limit of bytes from the underlying buffered reader.
 */
public fun <R : AsyncBufRead> Take<R>.consume(amt: Int) {
    // Prevent callers from resetting the limit by passing a negative or overlarge value.
    val clamped = minOf(amt.coerceAtLeast(0).toULong(), remainingLimit)
    remainingLimit -= clamped
    inner.consume(clamped.toInt())
}

/**
 * Creates an adapter which will read at most [limit] bytes from [inner].
 */
public fun <R : AsyncRead> take(inner: R, limit: ULong): Take<R> =
    Take(inner = inner, remainingLimit = limit)
