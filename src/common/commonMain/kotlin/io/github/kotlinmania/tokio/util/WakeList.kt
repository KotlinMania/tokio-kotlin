// port-lint: source util/wake_list.rs
package io.github.kotlinmania.tokio.util

import io.github.kotlinmania.futures.Waker
import io.github.kotlinmania.futures.wake

private const val NUM_WAKERS: Int = 32

/**
 * A list of wakers to be woken.
 *
 * # Invariants
 *
 * The first `curr` elements of `inner` are initialized.
 */
internal class WakeList private constructor(private val inner: Array<Waker?>, private var curr: Int) {
    internal fun canPush(): Boolean {
        return this.curr < NUM_WAKERS
    }

    internal fun push(value: Waker) {
        check(this.canPush())
        this.inner[this.curr] = value
        this.curr += 1
    }

    internal fun wakeAll() {
        check(this.curr <= NUM_WAKERS)
        val end = this.curr
        // Transfer ownership of the wakers before waking them.
        this.curr = 0
        var start = 0
        try {
            while (start < end) {
                val waker = checkNotNull(this.inner[start])
                this.inner[start] = null
                start += 1
                // If this throws, the remaining wakers are released.
                waker.wake()
            }
        } finally {
            while (start < end) {
                this.inner[start] = null
                start += 1
            }
        }
    }

    internal fun drop() {
        this.inner.fill(null, 0, this.curr)
        this.curr = 0
    }

    public companion object {
        internal fun new(): WakeList {
            return WakeList(inner = arrayOfNulls(NUM_WAKERS), curr = 0)
        }
    }
}
