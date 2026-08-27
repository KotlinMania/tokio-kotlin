// port-lint: source io/util/empty.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncRead
import io.github.kotlinmania.tokio.io.AsyncWrite
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.IoSlice
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf

/**
 * `Empty` ignores any data written via [AsyncWrite], and will always be empty
 * (returning zero bytes) when read via [AsyncRead].
 *
 * This class is generally created by calling [empty].
 */
public class Empty internal constructor() : AsyncRead, AsyncWrite {
    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> =
        Poll.ready(Result.success(Unit))

    override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> =
        Poll.ready(Result.success(buf.size))

    override fun pollFlush(cx: Context): Poll<Result<Unit>> =
        Poll.ready(Result.success(Unit))

    override fun pollShutdown(cx: Context): Poll<Result<Unit>> =
        Poll.ready(Result.success(Unit))

    override fun isWriteVectored(): Boolean = true

    override fun pollWriteVectored(cx: Context, bufs: List<IoSlice>): Poll<Result<Int>> {
        val total = bufs.sumOf { it.size }
        return Poll.ready(Result.success(total))
    }

    override fun toString(): String = "Empty"
}

/**
 * Creates a value that is always at EOF for reads, and ignores all data written.
 */
public fun empty(): Empty = Empty()
