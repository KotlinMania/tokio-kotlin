package io.github.kotlinmania.tokio.util

import io.github.kotlinmania.futures.Waker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WakeListTest {
    @Test
    fun wakesInInsertionOrderAndReusesCapacity() {
        val list = WakeList.new()
        val seen = mutableListOf<Int>()
        repeat(32) { i -> list.push(Waker { seen.add(i) }) }
        assertFalse(list.canPush())
        assertFailsWith<IllegalStateException> { list.push(Waker {}) }
        list.wakeAll()
        assertEquals((0 until 32).toList(), seen)
        assertTrue(list.canPush())
        list.wakeAll()
        assertEquals(32, seen.size)
    }

    @Test
    fun failureClearsRemainingWakersWithoutWakingThem() {
        val list = WakeList.new()
        var wakes = 0
        list.push(Waker { throw IllegalStateException("wake failed") })
        list.push(Waker { wakes++ })
        assertFailsWith<IllegalStateException> { list.wakeAll() }
        list.wakeAll()
        assertEquals(0, wakes)
        list.push(Waker { wakes++ })
        list.wakeAll()
        assertEquals(1, wakes)
    }

    @Test
    fun droppingClearsWithoutWaking() {
        val list = WakeList.new()
        var wakes = 0
        list.push(Waker { wakes++ })
        list.drop()
        list.wakeAll()
        assertEquals(0, wakes)
        assertTrue(list.canPush())
    }
}
