// Kotlin task poll model used by async_read.rs and async_write.rs translations.
package io.github.kotlinmania.tokio.io

/**
 * Result of polling an asynchronous operation.
 */
public open class Poll<out T> protected constructor(
    public val isReady: Boolean,
) {
    public val isPending: Boolean get() = !isReady

    /**
     * The operation has completed and produced a value.
     */
    public data class Ready<T>(
        public val value: T,
    ) : Poll<T>(true)

    /**
     * The operation cannot complete yet; the current task should be woken when
     * progress may be possible.
     */
    public data object Pending : Poll<Nothing>(false)

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
