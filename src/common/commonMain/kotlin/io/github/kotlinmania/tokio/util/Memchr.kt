// port-lint: source util/memchr.rs
package io.github.kotlinmania.tokio.util

/**
 * Search for a byte in a byte array.
 *
 * When no platform accelerator is in use, use a trivial implementation. This
 * port uses the portable implementation for all Kotlin Multiplatform targets.
 */
internal fun memchr(needle: UByte, haystack: UByteArray): Int? {
    for (index in haystack.indices) {
        if (needle == haystack[index]) {
            return index
        }
    }
    return null
}
