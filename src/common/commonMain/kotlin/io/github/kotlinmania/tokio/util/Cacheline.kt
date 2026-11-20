// port-lint: source util/cacheline.rs
package io.github.kotlinmania.tokio.util

/**
 * Wraps a value in a cache-padded holder to prevent false sharing across threads.
 */
public class CachePadded<T>(
    public var value: T,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachePadded<*>) return false
        return value == other.value
    }

    override fun hashCode(): Int = value?.hashCode() ?: 0

    override fun toString(): String = "CachePadded($value)"
}
