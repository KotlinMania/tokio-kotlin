// port-lint: source util/sync_wrapper.rs
package io.github.kotlinmania.tokio.util

/**
 * A wrapper that enables sending a value across thread boundaries by restricting access.
 */
public class SyncWrapper<T>(
    private var value: T,
) {
    public fun intoInner(): T = value

    public fun get(): T = value

    public fun set(newValue: T) {
        value = newValue
    }

    override fun toString(): String = "SyncWrapper($value)"
}
