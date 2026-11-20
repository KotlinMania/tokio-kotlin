// port-lint: source runtime/metrics/io.rs
package io.github.kotlinmania.tokio.runtime.metrics

import io.github.kotlinmania.tokio.util.MetricAtomicU64

internal class IoDriverMetrics(
    internal val fdRegisteredCount: MetricAtomicU64 = MetricAtomicU64(),
    internal val fdDeregisteredCount: MetricAtomicU64 = MetricAtomicU64(),
    internal val readyCount: MetricAtomicU64 = MetricAtomicU64(),
) {
    internal fun incrFdCount() {
        this.fdRegisteredCount.add(1u)
    }

    internal fun decFdCount() {
        this.fdDeregisteredCount.add(1u)
    }

    internal fun incrReadyCountBy(amt: ULong) {
        this.readyCount.add(amt)
    }
}
