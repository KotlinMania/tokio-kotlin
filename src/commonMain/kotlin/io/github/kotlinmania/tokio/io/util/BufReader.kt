// port-lint: source io/util/buf_reader.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncBufRead
import io.github.kotlinmania.tokio.io.AsyncRead
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf
import kotlin.math.min

public const val DEFAULT_BUF_SIZE: Int = 8 * 1024

/**
 * The `BufReader` class adds buffering to any reader.
 *
 * It can be excessively inefficient to work directly with an [AsyncRead] instance.
 * A `BufReader` performs large, infrequent reads on the underlying [AsyncRead] and
 * maintains an in-memory buffer of the results.
 */
public class BufReader<R : AsyncRead>(
    private val inner: R,
    capacity: Int = DEFAULT_BUF_SIZE,
) : AsyncBufRead {
    private val buf: UByteArray = UByteArray(capacity)
    private var pos: Int = 0
    private var cap: Int = 0

    public companion object {
        public fun <R : AsyncRead> new(inner: R): BufReader<R> =
            BufReader(inner, DEFAULT_BUF_SIZE)

        public fun <R : AsyncRead> withCapacity(capacity: Int, inner: R): BufReader<R> =
            BufReader(inner, capacity)
    }

    public fun getRef(): R = inner

    public fun getMut(): R = inner

    public fun intoInner(): R = inner

    public fun buffer(): UByteArray = buf.copyOfRange(pos, cap)

    public fun discardBuffer() {
        pos = 0
        cap = 0
    }

    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
        // If we don't have any buffered data and we're doing a massive read
        // (larger than our internal buffer), bypass our internal buffer entirely.
        if (pos == cap && buf.remaining() >= this.buf.size) {
            val res = inner.pollRead(cx, buf)
            discardBuffer()
            return res
        }

        val fillPoll = pollFillBuf(cx)
        if (fillPoll.isPending) {
            return Poll.pending()
        }
        val readyResult = (fillPoll as Poll.Ready).value
        if (readyResult.isFailure) {
            return Poll.ready(Result.failure(readyResult.exceptionOrNull()!!))
        }
        val rem = readyResult.getOrThrow()
        val amt = min(rem.size, buf.remaining())
        buf.putSlice(rem.copyOfRange(0, amt))
        consume(amt)
        return Poll.ready(Result.success(Unit))
    }

    override fun pollFillBuf(cx: Context): Poll<Result<UByteArray>> {
        if (pos >= cap) {
            val readBuf = ReadBuf(buf)
            val readPoll = inner.pollRead(cx, readBuf)
            if (readPoll.isPending) {
                return Poll.pending()
            }
            val readyResult = (readPoll as Poll.Ready).value
            if (readyResult.isFailure) {
                return Poll.ready(Result.failure(readyResult.exceptionOrNull()!!))
            }
            cap = readBuf.filled().size
            pos = 0
        }
        return Poll.ready(Result.success(buf.copyOfRange(pos, cap)))
    }

    override fun consume(amt: Int) {
        pos = min(pos + amt, cap)
    }
}
