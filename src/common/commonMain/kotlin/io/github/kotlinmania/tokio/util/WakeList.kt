// port-lint: source util/wake_list.rs
package io.github.kotlinmania.tokio.util

import io.github.kotlinmania.futures.Waker
import io.github.kotlinmania.futures.wake

private const val NUM_WAKERS: Int = 32

/** A list of wakers to be woken. */
internal class WakeList private constructor() {
    private val inner = arrayOfNulls<Waker>(NUM_WAKERS)
    private var curr = 0

    internal fun canPush(): Boolean = curr < NUM_WAKERS

    internal fun push(value: Waker) {
        check(canPush())
        inner[curr] = value
        curr += 1
    }

    internal fun wakeAll() {
        val count = curr
        curr = 0
        var next = 0
        try {
            while (next < count) {
                val waker = checkNotNull(inner[next])
                inner[next] = null
                next += 1
                waker.wake()
            }
        } finally {
            while (next < count) {
                inner[next] = null
                next += 1
            }
        }
    }

    internal fun drop() {
        inner.fill(null, 0, curr)
        curr = 0
    }

    internal companion object {
        internal fun new(): WakeList = WakeList()
    }
}
