// port-lint: source util/bit.rs
package io.github.kotlinmania.tokio.util

/**
 * Packs multiple values into a single integer word using bit masks and shifts.
 */
public class Pack internal constructor(
    public val mask: ULong,
    public val shift: Int,
) {
    public fun then(width: Int): Pack {
        val leadingZeros = mask.countLeadingZeroBits()
        val nextShift = 64 - leadingZeros
        val nextMask = maskFor(width) shl nextShift
        return Pack(nextMask, nextShift)
    }

    public fun width(): Int {
        val shifted = mask shr shift
        return 64 - shifted.countLeadingZeroBits()
    }

    public fun maxValue(): ULong = (1uL shl width()) - 1uL

    public fun pack(value: ULong, base: ULong): ULong {
        require(value <= maxValue()) { "value $value exceeds max value ${maxValue()}" }
        return (base and mask.inv()) or (value shl shift)
    }

    public fun unpack(src: ULong): ULong = unpackValue(src, mask, shift)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Pack) return false
        return mask == other.mask && shift == other.shift
    }

    override fun hashCode(): Int = 31 * mask.hashCode() + shift.hashCode()

    override fun toString(): String = "Pack(mask=${mask.toString(2)}, shift=$shift)"

    public companion object {
        public fun leastSignificant(width: Int): Pack {
            val mask = maskFor(width)
            return Pack(mask, 0)
        }
    }
}

/**
 * Returns a `ULong` with the right-most `n` bits set.
 */
public fun maskFor(n: Int): ULong {
    if (n <= 0) return 0uL
    if (n >= 64) return ULong.MAX_VALUE
    val shift = 1uL shl (n - 1)
    return shift or (shift - 1uL)
}

/**
 * Unpacks a value using a mask and shift.
 */
public fun unpackValue(src: ULong, mask: ULong, shift: Int): ULong = (src and mask) shr shift
