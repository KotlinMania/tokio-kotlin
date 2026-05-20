// port-lint: source loom/std/atomic_usize.rs
package io.github.kotlinmania.tokio.loom.std

import kotlin.concurrent.atomics.AtomicLong

internal typealias Target = AtomicLong

/**
 * `AtomicUsize` providing an additional `unsyncLoad` function.
 */
internal class AtomicUsize internal constructor(
    private val inner: AtomicLong,
) {
    /**
     * Performs an unsynchronized load.
     *
     * Safety: all mutations must have happened before the unsynchronized load.
     * Additionally, there must be no concurrent mutations.
     */
    internal fun unsyncLoad(): ULong = inner.load().toULong()

    internal fun <R> withMut(f: (AtomicUsize) -> R): R {
        // Safety: this method gives the callback mutable access to the wrapper.
        return f(this)
    }

    internal fun deref(): Target {
        // Safety: immutable receiver functions on the inner value are always safe
        // because this wrapper never performs unchecked mutations.
        return inner
    }

    internal fun derefMut(): Target {
        // Safety: this method represents mutable receiver access to the wrapper.
        return inner
    }

    internal fun fmt(): String = unsyncLoad().toString()

    internal fun load(order: Any? = null): ULong = inner.load().toULong()

    internal fun store(value: ULong, order: Any? = null) {
        inner.store(value.toLong())
    }

    internal fun swap(value: ULong, order: Any? = null): ULong =
        inner.exchange(value.toLong()).toULong()

    internal fun compareExchange(
        current: ULong,
        new: ULong,
        success: Any? = null,
        failure: Any? = null,
    ): ULong = inner.compareAndExchange(current.toLong(), new.toLong()).toULong()

    internal fun compareExchangeWeak(
        current: ULong,
        new: ULong,
        success: Any? = null,
        failure: Any? = null,
    ): ULong = compareExchange(current, new, success, failure)

    internal fun fetchAdd(value: ULong, order: Any? = null): ULong =
        inner.fetchAndAdd(value.toLong()).toULong()

    internal fun fetchSub(value: ULong, order: Any? = null): ULong =
        fetchUpdate { old -> old - value.toLong() }.toULong()

    internal fun fetchOr(value: ULong, order: Any? = null): ULong =
        fetchUpdate { old -> old or value.toLong() }.toULong()

    internal fun fetchAnd(value: ULong, order: Any? = null): ULong =
        fetchUpdate { old -> old and value.toLong() }.toULong()

    internal fun fetchXor(value: ULong, order: Any? = null): ULong =
        fetchUpdate { old -> old xor value.toLong() }.toULong()

    override fun toString(): String = fmt()

    private fun fetchUpdate(transform: (Long) -> Long): Long {
        while (true) {
            val old = inner.load()
            val new = transform(old)
            if (inner.compareAndSet(old, new)) {
                return old
            }
        }
    }

    companion object {
        internal fun new(value: ULong): AtomicUsize = AtomicUsize(AtomicLong(value.toLong()))
    }
}
