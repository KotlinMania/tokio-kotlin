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
 *
 * It helps with small, repeated writes to a file or network socket. It does not
 * help with very large writes, one or a few writes, or destinations already in
 * memory, such as [io.github.kotlinmania.tokio.io.VecAsyncWrite].
 *
 * Buffered data is discarded when this writer is no longer used. Multiple
 * buffered writers on the same stream can cause data loss. Poll [pollFlush] to
 * completion before releasing this writer if the buffered data must be written.
 */
public class BufWriter<W : AsyncWrite>(
    private val inner: W,
    private val capacity: Int = DEFAULT_BUF_SIZE,
) : AsyncWrite {
    private val buf: MutableList<UByte> = ArrayList(capacity)
    private var written: Int = 0

    public companion object {
        /** Creates a writer with the default capacity, currently 8 KB, which may change. */
        public fun <W : AsyncWrite> new(inner: W): BufWriter<W> =
            BufWriter(inner, DEFAULT_BUF_SIZE)

        /** Creates a writer with the specified buffer capacity. */
        public fun <W : AsyncWrite> withCapacity(capacity: Int, inner: W): BufWriter<W> =
            BufWriter(inner, capacity)
    }

    /** Gets a reference to the underlying writer. */
    public fun getRef(): W = inner

    /**
     * Gets a mutable reference to the underlying writer.
     *
     * It is inadvisable to write directly to the underlying writer.
     */
    public fun getMut(): W = inner

    /**
     * Gets the underlying writer for a poll operation.
     *
     * Kotlin references do not require Rust's pin projection. It is inadvisable
     * to write directly to the underlying writer.
     */
    public fun getPinMut(): W = inner

    /**
     * Returns the underlying writer, ending this wrapper's use.
     *
     * Any leftover data in the internal buffer is lost.
     */
    public fun intoInner(): W = inner

    /** Returns a snapshot of the internally buffered data. */
    public fun buffer(): UByteArray = UByteArray(buf.size) { buf[it] }

    public fun capacity(): Int = capacity

    private fun flushBuf(cx: Context): Poll<Result<Unit>> {
        val len = buf.size
        var result = Result.success(Unit)
        while (written < len) {
            val toWrite = UByteArray(len - written) { buf[written + it] }
            val writePoll = inner.pollWrite(cx, toWrite)
            if (writePoll.isPending) {
                return Poll.pending()
            }
            val res = (writePoll as Poll.Ready).value
            val failure = res.exceptionOrNull()
            if (failure != null) {
                result = Result.failure(failure)
                break
            }
            val n = res.getOrThrow()
            if (n == 0) {
                result = Result.failure(IllegalStateException("failed to write the buffered data"))
                break
            }
            written += n
        }
        if (written > 0) {
            buf.subList(0, written).clear()
        }
        written = 0
        return Poll.ready(result)
    }

    override fun pollWrite(cx: Context, buf: UByteArray): Poll<Result<Int>> {
        if (buf.size > capacity - this.buf.size) {
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
            val totalLen = bufs.fold(0) { acc, b -> acc + minOf(b.size, Int.MAX_VALUE - acc) }
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
                // Passing large slices directly to the writer avoids buffering them.
                // This also handles a total length that saturated at Int.MAX_VALUE.
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
            // Remove empty buffers at the beginning of bufs.
            var index = 0
            while (index < bufs.size && bufs[index].isEmpty()) {
                index++
            }
            if (index == bufs.size) {
                return Poll.ready(Result.success(0))
            }
            val first = bufs[index]
            // Flush if the first buffer does not fit.
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
                // A slice at least as large as the capacity bypasses the buffer.
                return inner.pollWrite(cx, first.asSlice())
            } else {
                val slice = first.asSlice()
                for (byte in slice) {
                    buf.add(byte)
                }
                var totalWritten = firstLen
                // Append the buffers that fit in the internal buffer.
                for (i in index + 1 until bufs.size) {
                    val nextBuf = bufs[i]
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
