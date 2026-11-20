// port-lint: source io/util/copy_buf.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncBufRead
import io.github.kotlinmania.tokio.io.AsyncWrite
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll

/**
 * Future state machine that asynchronously copies the entire contents of an [AsyncBufRead]
 * into an [AsyncWrite].
 */
public class CopyBuf<R : AsyncBufRead, W : AsyncWrite> internal constructor(
    private val reader: R,
    private val writer: W,
    private var amt: ULong = 0uL,
) {
    /**
     * Advances the copy operation until completion, error, or [Poll.Pending].
     */
    public fun poll(cx: Context): Poll<Result<ULong>> {
        while (true) {
            val fillPoll = reader.pollFillBuf(cx)
            if (fillPoll.isPending) {
                return Poll.pending()
            }
            val fillResult = (fillPoll as Poll.Ready).value
            val fillErr = fillResult.exceptionOrNull()
            if (fillErr != null) {
                return Poll.ready(Result.failure(fillErr))
            }
            val buffer = fillResult.getOrThrow()
            if (buffer.isEmpty()) {
                val flushPoll = writer.pollFlush(cx)
                if (flushPoll.isPending) {
                    return Poll.pending()
                }
                val flushResult = (flushPoll as Poll.Ready).value
                val flushErr = flushResult.exceptionOrNull()
                if (flushErr != null) {
                    return Poll.ready(Result.failure(flushErr))
                }
                return Poll.ready(Result.success(amt))
            }

            val writePoll = writer.pollWrite(cx, buffer)
            if (writePoll.isPending) {
                return Poll.pending()
            }
            val writeResult = (writePoll as Poll.Ready).value
            val writeErr = writeResult.exceptionOrNull()
            if (writeErr != null) {
                return Poll.ready(Result.failure(writeErr))
            }
            val i = writeResult.getOrThrow()
            if (i == 0) {
                return Poll.ready(Result.failure(IllegalStateException("write zero bytes into writer")))
            }
            amt += i.toULong()
            reader.consume(i)
        }
    }

    public fun fmt(): String = toString()

    override fun toString(): String = "CopyBuf(reader=$reader, writer=$writer, amt=$amt)"
}

/**
 * Creates a [CopyBuf] operation that asynchronously copies all bytes from [reader] to [writer]
 * without allocating an intermediate buffer.
 */
public fun <R : AsyncBufRead, W : AsyncWrite> copyBuf(reader: R, writer: W): CopyBuf<R, W> =
    CopyBuf(reader = reader, writer = writer, amt = 0uL)
