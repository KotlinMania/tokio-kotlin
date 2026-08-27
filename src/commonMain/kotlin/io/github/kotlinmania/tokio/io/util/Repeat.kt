// port-lint: source io/util/repeat.rs
package io.github.kotlinmania.tokio.io.util

import io.github.kotlinmania.tokio.io.AsyncRead
import io.github.kotlinmania.tokio.io.Context
import io.github.kotlinmania.tokio.io.Poll
import io.github.kotlinmania.tokio.io.ReadBuf

/**
 * An async reader which yields one byte over and over and over and over and
 * over and...
 *
 * This class is generally created by calling [repeat].
 */
public class Repeat internal constructor(
    private val byte: UByte,
) : AsyncRead {
    override fun pollRead(cx: Context, buf: ReadBuf): Poll<Result<Unit>> {
        val count = buf.remaining()
        if (count > 0) {
            val bytes = UByteArray(count) { byte }
            buf.putSlice(bytes)
        }
        return Poll.ready(Result.success(Unit))
    }

    override fun toString(): String = "Repeat(byte=$byte)"
}

/**
 * Creates an instance of an async reader that infinitely repeats one byte.
 *
 * All reads from this reader will succeed by filling the specified buffer with
 * the given byte.
 */
public fun repeat(byte: UByte): Repeat = Repeat(byte)

public fun repeat(byte: Byte): Repeat = Repeat(byte.toUByte())
