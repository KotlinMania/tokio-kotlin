// port-lint: source io/util/chain.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncBufRead
import io.github.kotlinmania.tokio.io.AsyncRead
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf

/**
 * Concrete pair of the two readers owned by a [Chain].
 */
public class ChainReaders<out T, out U>(
    public val first: T,
    public val second: U,
)

/**
 * Stream for the [chain] helper.
 */
public class Chain<T : AsyncRead, U : AsyncRead> internal constructor(
    internal val first: T,
    internal val second: U,
    internal var doneFirst: Boolean = false,
) : AsyncRead {
    /**
     * Gets references to the underlying readers in this [Chain].
     */
    public fun getRef(): ChainReaders<T, U> = ChainReaders(first, second)

    /**
     * Gets mutable references to the underlying readers in this [Chain].
     *
     * Care should be taken to avoid modifying the internal I/O state of the
     * underlying readers as doing so may corrupt the internal state of this [Chain].
     */
    public fun getMut(): ChainReaders<T, U> = ChainReaders(first, second)

    /**
     * Gets pinned mutable references to the underlying readers in this [Chain].
     *
     * Care should be taken to avoid modifying the internal I/O state of the
     * underlying readers as doing so may corrupt the internal state of this [Chain].
     */
    public fun getPinMut(): ChainReaders<T, U> = ChainReaders(first, second)

    /**
     * Consumes the [Chain], returning the wrapped readers.
     */
    public fun intoInner(): ChainReaders<T, U> = ChainReaders(first, second)

    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
        if (!doneFirst) {
            val rem = buf.remaining()
            val firstPoll = first.pollRead(cx, buf)
            if (firstPoll.isPending) {
                return Poll.pending()
            }
            val result = (firstPoll as Poll.Ready).value
            val err = result.exceptionOrNull()
            if (err != null) {
                return Poll.ready(Result.failure(err))
            }
            if (buf.remaining() == rem) {
                doneFirst = true
            } else {
                return Poll.ready(Result.success(Unit))
            }
        }
        return second.pollRead(cx, buf)
    }

    public fun fmt(): String = toString()

    override fun toString(): String = "Chain(t=$first, u=$second)"
}

/**
 * Polls the active buffered reader in this [Chain] for available bytes.
 */
public fun <T : AsyncBufRead, U : AsyncBufRead> Chain<T, U>.pollFillBuf(cx: Context): Poll<Result<UByteArray>> {
    if (!doneFirst) {
        val fillPoll = first.pollFillBuf(cx)
        if (fillPoll.isPending) {
            return Poll.pending()
        }
        val result = (fillPoll as Poll.Ready).value
        val err = result.exceptionOrNull()
        if (err != null) {
            return Poll.ready(Result.failure(err))
        }
        val buf = result.getOrThrow()
        if (buf.isEmpty()) {
            doneFirst = true
        } else {
            return Poll.ready(Result.success(buf))
        }
    }
    return second.pollFillBuf(cx)
}

/**
 * Consumes [amt] bytes from whichever underlying buffered reader is active in this [Chain].
 */
public fun <T : AsyncBufRead, U : AsyncBufRead> Chain<T, U>.consume(amt: Int) {
    if (!doneFirst) {
        first.consume(amt)
    } else {
        second.consume(amt)
    }
}

/**
 * Creates an adapter which will chain the [first] stream with the [second] one.
 */
public fun <T : AsyncRead, U : AsyncRead> chain(first: T, second: U): Chain<T, U> =
    Chain(first = first, second = second, doneFirst = false)
