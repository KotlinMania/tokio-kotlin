// port-lint: source util/memchr.rs
package io.github.kotlinmania.tokio.util

import kotlin.test.Test
import kotlin.test.assertEquals

class MemchrTest {
    @Test
    fun memchrTest() {
        val haystack = ubyteArrayOf(
            '1'.code.toUByte(),
            '2'.code.toUByte(),
            '3'.code.toUByte(),
            'a'.code.toUByte(),
            'b'.code.toUByte(),
            'c'.code.toUByte(),
            '4'.code.toUByte(),
            '5'.code.toUByte(),
            '6'.code.toUByte(),
            0u,
            0xffu,
            'a'.code.toUByte(),
            'b'.code.toUByte(),
            'c'.code.toUByte(),
            '\n'.code.toUByte(),
        )

        assertEquals(0, memchr('1'.code.toUByte(), haystack))
        assertEquals(1, memchr('2'.code.toUByte(), haystack))
        assertEquals(2, memchr('3'.code.toUByte(), haystack))
        assertEquals(6, memchr('4'.code.toUByte(), haystack))
        assertEquals(7, memchr('5'.code.toUByte(), haystack))
        assertEquals(8, memchr('6'.code.toUByte(), haystack))
        assertEquals(null, memchr('7'.code.toUByte(), haystack))
        assertEquals(3, memchr('a'.code.toUByte(), haystack))
        assertEquals(4, memchr('b'.code.toUByte(), haystack))
        assertEquals(5, memchr('c'.code.toUByte(), haystack))
        assertEquals(null, memchr('d'.code.toUByte(), haystack))
        assertEquals(null, memchr('A'.code.toUByte(), haystack))
        assertEquals(9, memchr(0u, haystack))
        assertEquals(10, memchr(0xffu, haystack))
        assertEquals(null, memchr(0xfeu, haystack))
        assertEquals(null, memchr(1u, haystack))
        assertEquals(14, memchr('\n'.code.toUByte(), haystack))
        assertEquals(null, memchr('\r'.code.toUByte(), haystack))
    }

    @Test
    fun memchrAll() {
        val arr = UByteArray(256) { it.toUByte() }
        for (byte in 0..255) {
            assertEquals(byte, memchr(byte.toUByte(), arr))
        }

        val reversed = UByteArray(256) { (255 - it).toUByte() }
        for (byte in 0..255) {
            assertEquals(255 - byte, memchr(byte.toUByte(), reversed))
        }
    }

    @Test
    fun memchrEmpty() {
        val empty = UByteArray(0)
        for (byte in 0..255) {
            assertEquals(null, memchr(byte.toUByte(), empty))
        }
    }
}
