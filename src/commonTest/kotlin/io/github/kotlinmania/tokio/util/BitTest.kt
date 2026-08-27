package io.github.kotlinmania.tokio.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BitTest {
    @Test
    fun testMaskFor() {
        assertEquals(0uL, maskFor(0))
        assertEquals(1uL, maskFor(1))
        assertEquals(3uL, maskFor(2))
        assertEquals(7uL, maskFor(3))
        assertEquals(15uL, maskFor(4))
        assertEquals(0xFFuL, maskFor(8))
        assertEquals(0xFFFFuL, maskFor(16))
    }

    @Test
    fun testPackAndUnpack() {
        val pack1 = Pack.leastSignificant(4)
        assertEquals(4, pack1.width())
        assertEquals(15uL, pack1.maxValue())

        val pack2 = pack1.then(8)
        assertEquals(8, pack2.width())
        assertEquals(255uL, pack2.maxValue())

        var base = 0uL
        base = pack1.pack(9uL, base)
        base = pack2.pack(42uL, base)

        assertEquals(9uL, pack1.unpack(base))
        assertEquals(42uL, pack2.unpack(base))
    }
}
