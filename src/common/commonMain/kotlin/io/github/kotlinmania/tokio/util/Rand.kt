// port-lint: source util/rand.rs
package io.github.kotlinmania.tokio.util

import kotlin.random.Random

/**
 * A seed for random number generation.
 *
 * In order to make certain functions within a runtime deterministic, a seed
 * can be specified at the time of creation.
 */
public class RngSeed(public val s: UInt, public val r: UInt) {
    override fun equals(other: Any?): Boolean = other is RngSeed && s == other.s && r == other.r

    override fun hashCode(): Int = 31 * s.hashCode() + r.hashCode()

    override fun toString(): String = "RngSeed(s=$s, r=$r)"

    public companion object {
        /**
         * Creates a random seed using loom internally.
         */
        internal fun new(): RngSeed {
            return RngSeed.fromU64(Random.nextLong().toULong())
        }

        public fun fromU64(seed: ULong): RngSeed {
            val one = (seed shr 32).toUInt()
            var two = seed.toUInt()
            if (two == 0u) {
                // This value cannot be zero
                two = 1u
            }
            return RngSeed.fromPair(one, two)
        }

        public fun random(): RngSeed = new()

        public fun fromPair(s: UInt, r: UInt): RngSeed {
            return RngSeed(s = s, r = r)
        }
    }
}

/**
 * Fast random number generate.
 *
 * Implement `xorshift64 +`: 2 32-bit `xorshift` sequences added together.
 * Shift triplet `[17, 7, 16]` was calculated as indicated in Marsaglia's
 * `Xorshift` paper: <https://www.jstatsoft.org/article/view/v008i14/xorshift.pdf>
 * This generator passes the SmallCrush suite, part of TestU01 framework:
 * <http://simul.iro.umontreal.ca/testu01/tu01.html>
 */
public class FastRand(private var one: UInt, private var two: UInt) {
    public constructor(seed: RngSeed) : this(seed.s, seed.r)

    public constructor() : this(RngSeed.random())

    public fun fastrandN(n: UInt): UInt {
        // This is similar to fastrand() % n, but faster.
        // See https://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        val mul = this.fastrand().toULong() * n.toULong()
        return (mul shr 32).toUInt()
    }

    public fun fastrand(): UInt {
        var s1 = this.one
        val s0 = this.two
        s1 = s1 xor (s1 shl 17)
        s1 = s1 xor s0 xor (s1 shr 7) xor (s0 shr 16)
        this.one = s0
        this.two = s1
        return s0 + s1
    }

    public companion object {
        /**
         * Initialize a new fast random number generator using the default source of entropy.
         */
        internal fun new(): FastRand {
            return FastRand.fromSeed(RngSeed.new())
        }

        /**
         * Initializes a new, thread-local, fast random number generator.
         */
        internal fun fromSeed(seed: RngSeed): FastRand {
            return FastRand(one = seed.s, two = seed.r)
        }
    }
}
