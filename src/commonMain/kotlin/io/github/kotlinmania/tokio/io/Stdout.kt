// port-lint: source io/stdout.rs
package io.github.kotlinmania.tokio.io

/**
 * A handle to the standard output stream of a process.
 *
 * The handle implements the [AsyncWrite] trait.
 */
public class Stdout internal constructor(
    private val inner: AsyncWrite,
) : AsyncWrite {
    override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> =
        inner.pollWrite(cx, buf)

    override fun pollFlush(cx: Context): Poll<Result<Unit>> =
        inner.pollFlush(cx)

    override fun pollShutdown(cx: Context): Poll<Result<Unit>> =
        inner.pollShutdown(cx)

    override fun pollWriteVectored(cx: Context, bufs: List<IoSlice>): Poll<Result<Int>> =
        inner.pollWriteVectored(cx, bufs)

    override fun isWriteVectored(): Boolean = inner.isWriteVectored()

    public companion object {
        public fun create(inner: AsyncWrite = VecAsyncWrite()): Stdout = Stdout(inner)
    }
}

/**
 * Constructs a new handle to the standard output of the current process.
 */
public fun stdout(): Stdout = Stdout.create()
