// port-lint: source io/util/copy.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncRead
import io.github.kotlinmania.tokio.io.AsyncWrite
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf

internal class CopyBuffer(
    bufSize: Int,
) {
    private var readDone: Boolean = false
    private var needFlush: Boolean = false
    private var pos: Int = 0
    private var cap: Int = 0
    private var amt: ULong = 0uL
    private val buf: UByteArray = UByteArray(bufSize)

    private fun pollFillBuf(
        cx: Context,
        reader: AsyncRead,
    ): Poll<Result<Unit>> {
        val readBuf = ReadBuf.new(buf)
        readBuf.setFilled(cap)

        val res = reader.pollRead(cx, readBuf)
        if (!res.isPending) {
            val result = (res as Poll.Ready).value
            if (result.isSuccess) {
                val filledLen = readBuf.filled().size
                readDone = cap == filledLen
                cap = filledLen
            }
        }
        return res
    }

    private fun pollWriteBuf(
        cx: Context,
        reader: AsyncRead,
        writer: AsyncWrite,
    ): Poll<Result<Int>> {
        val slice = buf.copyOfRange(pos, cap)
        val res = writer.pollWrite(cx, slice)
        if (res.isPending) {
            // Top up the buffer towards full if we can read a bit more data,
            // improving the chances of a larger subsequent write.
            if (!readDone && cap < buf.size) {
                val fillPoll = pollFillBuf(cx, reader)
                if (fillPoll.isPending) {
                    return Poll.pending()
                }
                val fillResult = (fillPoll as Poll.Ready).value
                val err = fillResult.exceptionOrNull()
                if (err != null) {
                    return Poll.ready(Result.failure(err))
                }
            }
            return Poll.pending()
        }
        return res
    }

    internal fun pollCopy(
        cx: Context,
        reader: AsyncRead,
        writer: AsyncWrite,
    ): Poll<Result<ULong>> {
        while (true) {
            // If there is space left in our buffer, try reading more data first
            // to maximize the chances of a large write.
            if (cap < buf.size && !readDone) {
                val fillPoll = pollFillBuf(cx, reader)
                if (fillPoll.isPending) {
                    // Ignore pending reads when our buffer is not empty, because
                    // we can try to write data immediately.
                    if (pos == cap) {
                        // Try flushing when the reader has no progress to avoid deadlock
                        // when the reader depends on a buffered writer.
                        if (needFlush) {
                            val flushPoll = writer.pollFlush(cx)
                            if (flushPoll.isPending) {
                                return Poll.pending()
                            }
                            val flushResult = (flushPoll as Poll.Ready).value
                            val flushErr = flushResult.exceptionOrNull()
                            if (flushErr != null) {
                                return Poll.ready(Result.failure(flushErr))
                            }
                            needFlush = false
                        }
                        return Poll.pending()
                    }
                } else {
                    val fillResult = (fillPoll as Poll.Ready).value
                    val fillErr = fillResult.exceptionOrNull()
                    if (fillErr != null) {
                        return Poll.ready(Result.failure(fillErr))
                    }
                }
            }

            // If our buffer has data, write it out.
            while (pos < cap) {
                val writePoll = pollWriteBuf(cx, reader, writer)
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
                    return Poll.ready(
                        Result.failure(IllegalStateException("write zero byte into writer")),
                    )
                }
                pos += i
                amt += i.toULong()
                needFlush = true
            }

            check(pos <= cap) { "writer returned length larger than input slice" }

            // All data has been written, so the buffer can be considered empty again.
            pos = 0
            cap = 0

            // If we have written all data and reached EOF, flush and finish.
            if (readDone) {
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
        }
    }
}

/**
 * Future state machine that asynchronously copies the entire contents of an [AsyncRead]
 * into an [AsyncWrite].
 */
public class Copy<R : AsyncRead, W : AsyncWrite> internal constructor(
    private val reader: R,
    private val writer: W,
    private val buf: CopyBuffer,
) {
    /**
     * Advances the copy operation until completion, error, or [Poll.Pending].
     */
    public fun poll(cx: Context): Poll<Result<ULong>> = buf.pollCopy(cx, reader, writer)

    public fun fmt(): String = toString()

    override fun toString(): String = "Copy(reader=$reader, writer=$writer)"
}

/**
 * Creates a [Copy] operation that asynchronously copies all bytes from [reader] to [writer].
 */
public fun <R : AsyncRead, W : AsyncWrite> copy(reader: R, writer: W): Copy<R, W> =
    Copy(reader = reader, writer = writer, buf = CopyBuffer(DEFAULT_BUF_SIZE))
