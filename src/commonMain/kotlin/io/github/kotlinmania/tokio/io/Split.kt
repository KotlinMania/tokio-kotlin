// port-lint: source io/split.rs
package io.github.kotlinmania.tokio.io

/**
 * Split a single value implementing `AsyncRead` and `AsyncWrite` into separate
 * `AsyncRead` and `AsyncWrite` handles.
 *
 * To restore this read/write object from its `ReadHalf` and `WriteHalf`, use
 * `unsplit`.
 */

/**
 * The readable half of a value returned from `split`.
 */
public class ReadHalf<T> internal constructor(
    internal val inner: Inner<T>,
) : AsyncRead where T : AsyncRead, T : AsyncWrite {
    /**
     * Checks if this `ReadHalf` and some `WriteHalf` were split from the same
     * stream.
     */
    public fun isPairOf(other: WriteHalf<T>): Boolean = other.isPairOf(this)

    /**
     * Reunites with a previously split `WriteHalf`.
     *
     * This method fails if this `ReadHalf` and the given `WriteHalf` do not
     * originate from the same `split` operation. This can be checked ahead of
     * time by calling `isPairOf`.
     */
    public fun unsplit(wr: WriteHalf<T>): T {
        check(isPairOf(wr)) {
            "Unrelated split.WriteHalf passed to split.ReadHalf.unsplit."
        }
        return inner.take()
    }

    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> =
        inner.withLock { stream -> stream.pollRead(cx, buf) }

    public fun fmt(): String = toString()

    override fun toString(): String = "split.ReadHalf"
}

/**
 * The writable half of a value returned from `split`.
 */
public class WriteHalf<T> internal constructor(
    internal val inner: Inner<T>,
) : AsyncWrite where T : AsyncRead, T : AsyncWrite {
    /**
     * Checks if this `WriteHalf` and some `ReadHalf` were split from the same
     * stream.
     */
    public fun isPairOf(other: ReadHalf<T>): Boolean = inner === other.inner

    override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> =
        inner.withLock { stream -> stream.pollWrite(cx, buf) }

    override fun pollFlush(cx: Context): Poll<Result<Unit>> =
        inner.withLock { stream -> stream.pollFlush(cx) }

    override fun pollShutdown(cx: Context): Poll<Result<Unit>> =
        inner.withLock { stream -> stream.pollShutdown(cx) }

    override fun pollWriteVectored(cx: Context, bufs: List<IoSlice>): Poll<Result<Int>> =
        inner.withLock { stream -> stream.pollWriteVectored(cx, bufs) }

    override fun isWriteVectored(): Boolean = inner.isWriteVectored

    public fun fmt(): String = toString()

    override fun toString(): String = "split.WriteHalf"
}

/**
 * Splits a single value implementing `AsyncRead` and `AsyncWrite` into separate
 * `AsyncRead` and `AsyncWrite` handles.
 *
 * To restore this read/write object from its `ReadHalf` and `WriteHalf`, use
 * `ReadHalf.unsplit`.
 */
public fun <T> split(stream: T): Pair<ReadHalf<T>, WriteHalf<T>>
    where T : AsyncRead, T : AsyncWrite {
    val isWriteVectored = stream.isWriteVectored()
    val inner = Inner(stream, isWriteVectored)
    val rd = ReadHalf(inner)
    val wr = WriteHalf(inner)
    return Pair(rd, wr)
}

internal class Inner<T>(
    stream: T,
    val isWriteVectored: Boolean,
) where T : AsyncRead, T : AsyncWrite {
    private var stream: T? = stream

    fun <R> withLock(f: (T) -> R): R {
        val current = checkNotNull(stream) { "split stream has already been unsplit" }
        return f(current)
    }

    fun take(): T {
        val current = checkNotNull(stream) { "split stream has already been unsplit" }
        stream = null
        return current
    }
}
