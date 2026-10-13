// port-lint: source io/util/buf_writer.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncWrite
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.IoSlice
import io.github.kotlinmania.tokio.io.Poll

/**
 * Wraps a writer and buffers its output.
 *
 * It can be excessively inefficient to work directly with something that implements [AsyncWrite].
 * A `BufWriter` keeps an in-memory buffer of data and writes it to an underlying writer in
 * large, infrequent batches.
 */
public class BufWriter<W : AsyncWrite>(
    private val inner: W,
    private val capacity: Int = DEFAULT_BUF_SIZE,
) : AsyncWrite {
    private val buf: MutableList<UByte> = ArrayList(capacity)
    private var written: Int = 0

    public companion object {
        public fun <W : AsyncWrite> new(inner: W): BufWriter<W> =
            BufWriter(inner, DEFAULT_BUF_SIZE)

        public fun <W : AsyncWrite> withCapacity(capacity: Int, inner: W): BufWriter<W> =
            BufWriter(inner, capacity)
    }

    public fun getRef(): W = inner

    public fun getMut(): W = inner

    public fun intoInner(): W = inner

    public fun buffer(): UByteArray = UByteArray(buf.size) { buf[it] }

    public fun capacity(): Int = capacity

    private fun flushBuf(cx: Context): Poll<Result<Unit>> {
        val len = buf.size
        while (written < len) {
            val toWrite = UByteArray(len - written) { buf[written + it] }
            val writePoll = inner.pollWrite(cx, toWrite)
            if (writePoll.isPending) {
                return Poll.pending()
            }
            val res = (writePoll as Poll.Ready).value
            if (res.isFailure) {
                return Poll.ready(Result.failure(res.exceptionOrNull()!!))
            }
            val n = res.getOrThrow()
            if (n == 0) {
                return Poll.ready(
                    Result.failure(
                        IllegalStateException("failed to write the buffered data (zero bytes written)"),
                    ),
                )
            }
            written += n
        }
        if (written > 0) {
            buf.subList(0, written).clear()
        }
        written = 0
        return Poll.ready(Result.success(Unit))
    }

    override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> {
        if (this.buf.size + buf.size > capacity) {
            val flushPoll = flushBuf(cx)
            if (flushPoll.isPending) {
                return Poll.pending()
            }
            val res = (flushPoll as Poll.Ready).value
            if (res.isFailure) {
                return Poll.ready(Result.failure(res.exceptionOrNull()!!))
            }
        }

        if (buf.size >= capacity) {
            return inner.pollWrite(cx, buf)
        } else {
            for (byte in buf) {
                this.buf.add(byte)
            }
            return Poll.ready(Result.success(buf.size))
        }
    }

    override fun pollWriteVectored(cx: Context, bufs: List<IoSlice>): Poll<Result<Int>> {
        if (inner.isWriteVectored()) {
            val totalLen = bufs.fold(0) { acc, b -> acc + b.size }
            if (totalLen > capacity - buf.size) {
                val flushPoll = flushBuf(cx)
                if (flushPoll.isPending) {
                    return Poll.pending()
                }
                val res = (flushPoll as Poll.Ready).value
                if (res.isFailure) {
                    return Poll.ready(Result.failure(res.exceptionOrNull()!!))
                }
            }
            if (totalLen >= capacity) {
                return inner.pollWriteVectored(cx, bufs)
            } else {
                for (b in bufs) {
                    val slice = b.asSlice()
                    for (byte in slice) {
                        buf.add(byte)
                    }
                }
                return Poll.ready(Result.success(totalLen))
            }
        } else {
            val nonEmpty = bufs.filter { !it.isEmpty() }
            if (nonEmpty.isEmpty()) {
                return Poll.ready(Result.success(0))
            }
            val first = nonEmpty[0]
            val firstLen = first.size
            if (firstLen > capacity - buf.size) {
                val flushPoll = flushBuf(cx)
                if (flushPoll.isPending) {
                    return Poll.pending()
                }
                val res = (flushPoll as Poll.Ready).value
                if (res.isFailure) {
                    return Poll.ready(Result.failure(res.exceptionOrNull()!!))
                }
            }
            if (firstLen >= capacity) {
                return inner.pollWrite(cx, first.asSlice())
            } else {
                val slice = first.asSlice()
                for (byte in slice) {
                    buf.add(byte)
                }
                var totalWritten = firstLen
                for (i in 1 until nonEmpty.size) {
                    val nextBuf = nonEmpty[i]
                    if (nextBuf.size > capacity - buf.size) {
                        break
                    } else {
                        val nextSlice = nextBuf.asSlice()
                        for (byte in nextSlice) {
                            buf.add(byte)
                        }
                        totalWritten += nextBuf.size
                    }
                }
                return Poll.ready(Result.success(totalWritten))
            }
        }
    }

    override fun isWriteVectored(): Boolean = true

    override fun pollFlush(cx: Context): Poll<Result<Unit>> {
        val flushPoll = flushBuf(cx)
        if (flushPoll.isPending) {
            return Poll.pending()
        }
        val res = (flushPoll as Poll.Ready).value
        if (res.isFailure) {
            return Poll.ready(res)
        }
        return inner.pollFlush(cx)
    }

    override fun pollShutdown(cx: Context): Poll<Result<Unit>> {
        val flushPoll = flushBuf(cx)
        if (flushPoll.isPending) {
            return Poll.pending()
        }
        val res = (flushPoll as Poll.Ready).value
        if (res.isFailure) {
            return Poll.ready(res)
        }
        return inner.pollShutdown(cx)
    }
}
