// port-lint: source io/util/sink.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncWrite
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.IoSlice
import io.github.kotlinmania.tokio.io.Poll

/**
 * An async writer which will move data into the void.
 *
 * This class is generally created by calling [sink].
 */
public class Sink internal constructor() : AsyncWrite {
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

    override fun toString(): String = "Sink"
}

/**
 * Creates an instance of an async writer which will successfully consume all data.
 */
public fun sink(): Sink = Sink()
