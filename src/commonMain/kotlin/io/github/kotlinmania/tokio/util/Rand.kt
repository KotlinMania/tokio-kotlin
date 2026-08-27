// port-lint: source util/rand.rs
package io.github.kotlinmania.tokio.util

import kotlin.random.Random

/**
 * A seed for random number generation.
 *
 * In order to make certain functions within a runtime deterministic, a seed
 * can be specified at the time of creation.
 */
public class RngSeed(
    public val s: UInt,
    public val r: UInt,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RngSeed) return false
        return s == other.s && r == other.r
    }

    override fun hashCode(): Int = 31 * s.hashCode() + r.hashCode()

    override fun toString(): String = "RngSeed(s=$s, r=$r)"

    public companion object {
        public fun fromU64(seed: ULong): RngSeed {
            val one = (seed shr 32).toUInt()
            var two = seed.toUInt()
            if (two == 0u) {
                two = 1u
            }
            return RngSeed(one, two)
        }

        public fun random(): RngSeed {
            val seed = Random.nextLong().toULong()
            return fromU64(seed)
        }
    }
}

/**
 * Fast random number generator implementing `xorshift64+`.
 */
public class FastRand(
    private var one: UInt,
    private var two: UInt,
) {
    public constructor(seed: RngSeed) : this(seed.s, seed.r)

    public constructor() : this(RngSeed.random())

    public fun fastrand(): UInt {
        var s1 = one
        val s0 = two

        s1 = s1 xor (s1 shl 17)
        s1 = s1 xor s0 xor (s1 shr 7) xor (s0 shr 16)

        one = s0
        two = s1

        return s0 + s1
    }

    public fun fastrandN(n: UInt): UInt {
        if (n == 0u) return 0u
        val mul = fastrand().toULong() * n.toULong()
        return (mul shr 32).toUInt()
    }
}
