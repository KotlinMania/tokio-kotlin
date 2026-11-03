// port-lint: source runtime/metrics/scheduler.rs
package io.github.kotlinmania.tokio.runtime.metrics

import io.github.kotlinmania.tokio.util.MetricAtomicU64

/**
 * Retrieves metrics from the Tokio runtime.
 *
 * Note: this is an unstable API. The public API of this type may break in 1.x
 * releases. See the documentation on unstable features for details.
 */
internal class SchedulerMetrics(
    /**
     * Number of tasks that are scheduled from outside the runtime.
     */
    internal val remoteScheduleCount: MetricAtomicU64,
    internal val budgetForcedYieldCount: MetricAtomicU64,
) {
    /**
     * Increment the number of tasks scheduled externally.
     */
    internal fun incRemoteScheduleCount() {
        remoteScheduleCount.add(1u)
    }

    /**
     * Increment the number of tasks forced to yield due to budget exhaustion.
     */
    internal fun incBudgetForcedYieldCount() {
        budgetForcedYieldCount.add(1u)
    }

    companion object {
        internal fun new(): SchedulerMetrics =
            SchedulerMetrics(
                remoteScheduleCount = MetricAtomicU64.new(0u),
                budgetForcedYieldCount = MetricAtomicU64.new(0u),
            )
    }
}
