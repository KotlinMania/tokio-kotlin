// port-lint: source io/stdin.rs
package io.github.kotlinmania.tokio.io

/**
 * A handle to the standard input stream of a process.
 *
 * The handle implements the [AsyncRead] trait.
 */
public class Stdin internal constructor(
    private val inner: AsyncRead,
) : AsyncRead {
    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> =
        inner.pollRead(cx, buf)

    public companion object {
        public fun create(inner: AsyncRead = ByteSliceAsyncRead(UByteArray(0))): Stdin = Stdin(inner)
    }
}

/**
 * Constructs a new handle to the standard input of the current process.
 */
public fun stdin(): Stdin = Stdin.create()
