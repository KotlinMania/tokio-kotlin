package io.github.kotlinmania.tokio.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RandTest {
    @Test
    fun testRngSeedDeterminism() {
        val seed = RngSeed(12345u, 67890u)
        val rng1 = FastRand(seed)
        val rng2 = FastRand(seed)

        val values1 = List(10) { rng1.fastrand() }
        val values2 = List(10) { rng2.fastrand() }
        assertEquals(values1, values2)
    }

    @Test
    fun testFastrandN() {
        val rng = FastRand(RngSeed(111u, 222u))
        for (i in 0 until 50) {
            val v = rng.fastrandN(10u)
            assertTrue(v < 10u)
        }
    }
}
