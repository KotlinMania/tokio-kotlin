// port-lint: source io/ready.rs
package io.github.kotlinmania.tokio.io

private const val READY_READABLE: Int = 0b0_01
private const val READY_WRITABLE: Int = 0b0_10
private const val READY_READ_CLOSED: Int = 0b0_0100
private const val READY_WRITE_CLOSED: Int = 0b0_1000
private const val READY_PRIORITY: Int = 0b1_0000
private const val READY_ERROR: Int = 0b10_0000

/**
 * Describes the readiness state of an I/O resource.
 *
 * `Ready` tracks which operation an I/O resource is ready to perform.
 */
public class Ready private constructor(
    private val value: Int,
) : Comparable<Ready> {
    /**
     * Returns true if `Ready` is the empty set.
     *
     * # Examples
     *
     * ```
     * check(Ready.EMPTY.isEmpty())
     * check(!Ready.READABLE.isEmpty())
     * ```
     */
    public fun isEmpty(): Boolean = this == EMPTY

    /**
     * Returns true if the value includes readable readiness.
     *
     * # Examples
     *
     * ```
     * check(!Ready.EMPTY.isReadable())
     * check(Ready.READABLE.isReadable())
     * check(Ready.READ_CLOSED.isReadable())
     * check(!Ready.WRITABLE.isReadable())
     * ```
     */
    public fun isReadable(): Boolean =
        contains(READABLE) || isReadClosed()

    /**
     * Returns true if the value includes writable readiness.
     *
     * # Examples
     *
     * ```
     * check(!Ready.EMPTY.isWritable())
     * check(!Ready.READABLE.isWritable())
     * check(Ready.WRITABLE.isWritable())
     * check(Ready.WRITE_CLOSED.isWritable())
     * ```
     */
    public fun isWritable(): Boolean =
        contains(WRITABLE) || isWriteClosed()

    /**
     * Returns true if the value includes read-closed readiness.
     *
     * # Examples
     *
     * ```
     * check(!Ready.EMPTY.isReadClosed())
     * check(!Ready.READABLE.isReadClosed())
     * check(Ready.READ_CLOSED.isReadClosed())
     * ```
     */
    public fun isReadClosed(): Boolean = contains(READ_CLOSED)

    /**
     * Returns true if the value includes write-closed readiness.
     *
     * # Examples
     *
     * ```
     * check(!Ready.EMPTY.isWriteClosed())
     * check(!Ready.WRITABLE.isWriteClosed())
     * check(Ready.WRITE_CLOSED.isWriteClosed())
     * ```
     */
    public fun isWriteClosed(): Boolean = contains(WRITE_CLOSED)

    /**
     * Returns true if the value includes priority readiness.
     *
     * # Examples
     *
     * ```
     * check(!Ready.EMPTY.isPriority())
     * check(!Ready.WRITABLE.isPriority())
     * check(Ready.PRIORITY.isPriority())
     * ```
     */
    public fun isPriority(): Boolean = contains(PRIORITY)

    /**
     * Returns true if the value includes error readiness.
     *
     * # Examples
     *
     * ```
     * check(!Ready.EMPTY.isError())
     * check(!Ready.WRITABLE.isError())
     * check(Ready.ERROR.isError())
     * ```
     */
    public fun isError(): Boolean = contains(ERROR)

    /**
     * Returns true if this is a superset of `other`.
     *
     * `other` may represent more than one readiness operation, in which case
     * the function only returns true if this contains all readiness specified in
     * `other`.
     */
    internal fun contains(other: Ready): Boolean =
        this.and(other) == other

    /**
     * Returns an integer representation of the `Ready` value.
     *
     * This function is mainly provided to allow the caller to store a readiness
     * value in an atomic integer.
     */
    internal fun asUsize(): Int = value

    internal fun intersection(interest: Interest): Ready =
        Ready(value and fromInterest(interest).value)

    internal fun satisfies(interest: Interest): Boolean =
        value and fromInterest(interest).value != 0

    public fun or(other: Ready): Ready = Ready(value or other.value)

    public fun and(other: Ready): Ready = Ready(value and other.value)

    public fun minus(other: Ready): Ready = Ready(value and other.value.inv())

    public fun bitOr(other: Ready): Ready = or(other)

    public fun bitOrAssign(other: Ready): Ready = or(other)

    public fun bitAnd(other: Ready): Ready = and(other)

    public fun sub(other: Ready): Ready = minus(other)

    public fun fmt(): String = toString()

    override fun compareTo(other: Ready): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean =
        other is Ready && value == other.value

    override fun hashCode(): Int = value

    override fun toString(): String =
        "Ready(" +
            "isReadable=${isReadable()}, " +
            "isWritable=${isWritable()}, " +
            "isReadClosed=${isReadClosed()}, " +
            "isWriteClosed=${isWriteClosed()}, " +
            "isError=${isError()}, " +
            "isPriority=${isPriority()}" +
            ")"

    public companion object {
        /**
         * Returns the empty `Ready` set.
         */
        public val EMPTY: Ready = Ready(0)

        /**
         * Returns a `Ready` representing readable readiness.
         */
        public val READABLE: Ready = Ready(READY_READABLE)

        /**
         * Returns a `Ready` representing writable readiness.
         */
        public val WRITABLE: Ready = Ready(READY_WRITABLE)

        /**
         * Returns a `Ready` representing read-closed readiness.
         */
        public val READ_CLOSED: Ready = Ready(READY_READ_CLOSED)

        /**
         * Returns a `Ready` representing write-closed readiness.
         */
        public val WRITE_CLOSED: Ready = Ready(READY_WRITE_CLOSED)

        /**
         * Returns a `Ready` representing priority readiness.
         */
        public val PRIORITY: Ready = Ready(READY_PRIORITY)

        /**
         * Returns a `Ready` representing error readiness.
         */
        public val ERROR: Ready = Ready(READY_ERROR)

        /**
         * Returns a `Ready` representing readiness for all operations.
         */
        public val ALL: Ready =
            Ready(READY_READABLE or READY_WRITABLE or READY_READ_CLOSED or READY_WRITE_CLOSED or READY_ERROR or READY_PRIORITY)

        internal fun fromMio(event: MioEvent): Ready {
            var ready = EMPTY

            if (event.isAio()) {
                ready = ready.or(READABLE)
            }

            if (event.isLio()) {
                ready = ready.or(READABLE)
            }

            if (event.isReadable()) {
                ready = ready.or(READABLE)
            }

            if (event.isWritable()) {
                ready = ready.or(WRITABLE)
            }

            if (event.isReadClosed()) {
                ready = ready.or(READ_CLOSED)
            }

            if (event.isWriteClosed()) {
                ready = ready.or(WRITE_CLOSED)
            }

            if (event.isError()) {
                ready = ready.or(ERROR)
            }

            if (event.isPriority()) {
                ready = ready.or(PRIORITY)
            }

            return ready
        }

        /**
         * Creates a `Ready` instance using the given integer representation.
         *
         * The integer representation must have been obtained from a call to
         * `asUsize`.
         *
         * This function is mainly provided to allow the caller to get a
         * readiness value from an atomic integer.
         */
        internal fun fromUsize(value: Int): Ready =
            Ready(value and ALL.asUsize())

        internal fun fromInterest(interest: Interest): Ready {
            var ready = EMPTY

            if (interest.isReadable()) {
                ready = ready.or(READABLE)
                ready = ready.or(READ_CLOSED)
            }

            if (interest.isWritable()) {
                ready = ready.or(WRITABLE)
                ready = ready.or(WRITE_CLOSED)
            }

            if (interest.isPriority()) {
                ready = ready.or(PRIORITY)
                ready = ready.or(READ_CLOSED)
            }

            if (interest.isError()) {
                ready = ready.or(ERROR)
            }

            return ready
        }
    }
}

internal interface MioEvent {
    fun isAio(): Boolean = false

    fun isLio(): Boolean = false

    fun isReadable(): Boolean

    fun isWritable(): Boolean

    fun isReadClosed(): Boolean

    fun isWriteClosed(): Boolean

    fun isError(): Boolean

    fun isPriority(): Boolean = false
}
