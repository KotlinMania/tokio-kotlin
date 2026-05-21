// port-lint: source io/interest.rs
package io.github.kotlinmania.tokio.io

private const val INTEREST_READABLE: Int = 0b0001
private const val INTEREST_WRITABLE: Int = 0b0010
private const val INTEREST_AIO: Int = 0b0100
private const val INTEREST_LIO: Int = 0b1000
private const val INTEREST_PRIORITY: Int = 0b0001_0000
private const val INTEREST_ERROR: Int = 0b0010_0000

/**
 * Readiness event interest.
 *
 * Specifies the readiness events the caller is interested in when awaiting on
 * I/O resource readiness states.
 */
public class Interest private constructor(
    private val value: Int,
) {
    /**
     * Returns true if the value includes readable interest.
     *
     * # Examples
     *
     * ```
     * check(Interest.READABLE.isReadable())
     * check(!Interest.WRITABLE.isReadable())
     *
     * val both = Interest.READABLE.add(Interest.WRITABLE)
     * check(both.isReadable())
     * ```
     */
    public fun isReadable(): Boolean = value and INTEREST_READABLE != 0

    /**
     * Returns true if the value includes writable interest.
     *
     * # Examples
     *
     * ```
     * check(!Interest.READABLE.isWritable())
     * check(Interest.WRITABLE.isWritable())
     *
     * val both = Interest.READABLE.add(Interest.WRITABLE)
     * check(both.isWritable())
     * ```
     */
    public fun isWritable(): Boolean = value and INTEREST_WRITABLE != 0

    /**
     * Returns true if the value includes error interest.
     *
     * # Examples
     *
     * ```
     * check(Interest.ERROR.isError())
     * check(!Interest.WRITABLE.isError())
     *
     * val combined = Interest.READABLE.add(Interest.ERROR)
     * check(combined.isError())
     * ```
     */
    public fun isError(): Boolean = value and INTEREST_ERROR != 0

    private fun isAio(): Boolean = value and INTEREST_AIO != 0

    private fun isLio(): Boolean = value and INTEREST_LIO != 0

    /**
     * Returns true if the value includes priority interest.
     *
     * # Examples
     *
     * ```
     * check(!Interest.READABLE.isPriority())
     * check(Interest.PRIORITY.isPriority())
     *
     * val both = Interest.READABLE.add(Interest.PRIORITY)
     * check(both.isPriority())
     * ```
     */
    public fun isPriority(): Boolean = value and INTEREST_PRIORITY != 0

    /**
     * Add together two `Interest` values.
     *
     * This function returns the result of the operation without modifying the
     * original value.
     *
     * # Examples
     *
     * ```
     * val both = Interest.READABLE.add(Interest.WRITABLE)
     *
     * check(both.isReadable())
     * check(both.isWritable())
     * ```
     */
    public fun add(other: Interest): Interest = Interest(value or other.value)

    /**
     * Remove `Interest` from `this`.
     *
     * Interests present in `other` but not in `this` are ignored.
     *
     * Returns null if the set would be empty after removing `Interest`.
     *
     * # Examples
     *
     * ```
     * val readWriteInterest = Interest.READABLE.add(Interest.WRITABLE)
     *
     * val writeInterest = readWriteInterest.remove(Interest.READABLE)!!
     * check(!writeInterest.isReadable())
     * check(writeInterest.isWritable())
     *
     * check(writeInterest.remove(Interest.WRITABLE) == null)
     * check(readWriteInterest.remove(readWriteInterest) == null)
     * ```
     */
    public fun remove(other: Interest): Interest? {
        val newValue = value and other.value.inv()
        return if (newValue != 0) {
            Interest(newValue)
        } else {
            null
        }
    }

    internal fun toMio(): MioInterest {
        fun mioAdd(wrapped: MioInterest?, add: MioInterest): MioInterest =
            wrapped?.add(add) ?: add

        var mio: MioInterest? = null

        if (isReadable()) {
            mio = mioAdd(mio, MioInterest.READABLE)
        }

        if (isWritable()) {
            mio = mioAdd(mio, MioInterest.WRITABLE)
        }

        if (isPriority()) {
            mio = mioAdd(mio, MioInterest.PRIORITY)
        }

        if (isAio()) {
            mio = mioAdd(mio, MioInterest.AIO)
        }

        if (isLio()) {
            mio = mioAdd(mio, MioInterest.LIO)
        }

        if (isError()) {
            mio = mioAdd(mio, MioInterest.READABLE)
        }

        return mio ?: MioInterest.READABLE
    }

    internal fun mask(): Ready =
        when (this) {
            READABLE -> Ready.READABLE.or(Ready.READ_CLOSED)
            WRITABLE -> Ready.WRITABLE.or(Ready.WRITE_CLOSED)
            PRIORITY -> Ready.PRIORITY.or(Ready.READ_CLOSED)
            ERROR -> Ready.ERROR
            else -> Ready.EMPTY
        }

    internal fun asInt(): Int = value

    public fun or(other: Interest): Interest = add(other)

    public fun bitOr(other: Interest): Interest = add(other)

    public fun bitOrAssign(other: Interest): Interest = add(other)

    public fun fmt(): String = toString()

    override fun equals(other: Any?): Boolean =
        other is Interest && value == other.value

    override fun hashCode(): Int = value

    override fun toString(): String {
        val parts = mutableListOf<String>()

        if (isReadable()) {
            parts += "READABLE"
        }

        if (isWritable()) {
            parts += "WRITABLE"
        }

        if (isPriority()) {
            parts += "PRIORITY"
        }

        if (isAio()) {
            parts += "AIO"
        }

        if (isLio()) {
            parts += "LIO"
        }

        if (isError()) {
            parts += "ERROR"
        }

        return parts.joinToString(" | ")
    }

    public companion object {
        /**
         * Interest for POSIX AIO.
         */
        public val AIO: Interest = Interest(INTEREST_AIO)

        /**
         * Interest for POSIX AIO list-I/O events.
         */
        public val LIO: Interest = Interest(INTEREST_LIO)

        /**
         * Interest in all readable events.
         *
         * Readable interest includes read-closed events.
         */
        public val READABLE: Interest = Interest(INTEREST_READABLE)

        /**
         * Interest in all writable events.
         *
         * Writable interest includes write-closed events.
         */
        public val WRITABLE: Interest = Interest(INTEREST_WRITABLE)

        /**
         * Interest in error events.
         *
         * Passes error interest to the underlying OS selector. Behavior is
         * platform-specific; read your platform's documentation.
         */
        public val ERROR: Interest = Interest(INTEREST_ERROR)

        /**
         * Returns an `Interest` set representing priority completion interests.
         */
        public val PRIORITY: Interest = Interest(INTEREST_PRIORITY)

        internal fun fromInt(value: Int): Interest = Interest(value)
    }
}

internal class MioInterest private constructor(
    private val value: Int,
) {
    internal fun add(other: MioInterest): MioInterest = MioInterest(value or other.value)

    internal companion object {
        internal val READABLE: MioInterest = MioInterest(INTEREST_READABLE)
        internal val WRITABLE: MioInterest = MioInterest(INTEREST_WRITABLE)
        internal val AIO: MioInterest = MioInterest(INTEREST_AIO)
        internal val LIO: MioInterest = MioInterest(INTEREST_LIO)
        internal val PRIORITY: MioInterest = MioInterest(INTEREST_PRIORITY)
    }
}
