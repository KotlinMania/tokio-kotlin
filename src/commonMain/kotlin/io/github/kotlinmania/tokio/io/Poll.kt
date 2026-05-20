// port-lint: ignore
// Kotlin task poll model used by async_read.rs and async_write.rs translations.
package io.github.kotlinmania.tokio.io

/**
 * Result of polling an asynchronous operation.
 */
public sealed interface Poll<out T> {
    /**
     * The operation has completed and produced a value.
     */
    public data class Ready<T>(public val value: T) : Poll<T>

    /**
     * The operation cannot complete yet; the current task should be woken when
     * progress may be possible.
     */
    public data object Pending : Poll<Nothing>

    public companion object {
        public fun <T> ready(value: T): Poll<T> = Ready(value)

        public fun pending(): Poll<Nothing> = Pending
    }
}

/**
 * Task context supplied to asynchronous I/O poll methods.
 */
public class Context(
    private val wakeup: () -> Unit = {},
) {
    public fun waker(): () -> Unit = wakeup
}
