// port-lint: source util/metric_atomics.rs
package io.github.kotlinmania.tokio.util

import kotlin.concurrent.atomics.AtomicLong

/**
 * Atomic ULong that is a no-op on platforms without 64-bit atomics.
 *
 * When used on platforms without 64-bit atomics, writes to this are no-ops.
 * The load method is only defined when 64-bit atomics are available.
 */
internal class MetricAtomicU64(
    initialValue: ULong = 0u,
) {
    private val value = AtomicLong(initialValue.toLong())

    fun load(): ULong = value.load().toULong()

    fun store(value: ULong) {
        this.value.store(value.toLong())
    }

    fun add(value: ULong) {
        this.value.fetchAndAdd(value.toLong())
    }

    companion object {
        fun new(value: ULong): MetricAtomicU64 = MetricAtomicU64(value)
    }
}

/**
 * Atomic ULong for use in metrics.
 *
 * This exposes simplified APIs for use in metrics and uses the platform atomic
 * implementation directly to keep metric updates separate from loom tracing.
 */
internal class MetricAtomicUsize(
    initialValue: ULong = 0u,
) {
    private val value = AtomicLong(initialValue.toLong())

    fun load(): ULong = value.load().toULong()

    fun store(value: ULong) {
        this.value.store(value.toLong())
    }

    fun increment(): ULong = value.fetchAndAdd(1L).toULong()

    fun decrement(): ULong = value.fetchAndAdd(-1L).toULong()

    companion object {
        fun new(value: ULong): MetricAtomicUsize = MetricAtomicUsize(value)
    }
}
