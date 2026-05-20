// port-lint: source loom/std/unsafe_cell.rs
package io.github.kotlinmania.tokio.loom.std

internal class UnsafeCell<T> internal constructor(
    private var data: T,
) {
    internal fun <R> with(f: (T) -> R): R = f(data)

    internal fun <R> withMut(f: (UnsafeCell<T>) -> R): R = f(this)

    internal fun get(): T = data

    internal fun set(data: T) {
        this.data = data
    }

    companion object {
        internal fun <T> new(data: T): UnsafeCell<T> = UnsafeCell(data)
    }
}
