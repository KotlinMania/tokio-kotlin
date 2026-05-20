// port-lint: source io/read_buf.rs
package io.github.kotlinmania.tokio.io

import kotlin.math.min

/**
 * A wrapper around a byte buffer that is incrementally filled and initialized.
 *
 * This type is a sort of double cursor. It tracks three regions in the buffer:
 * a region at the beginning that has been logically filled with data, a region
 * that has been initialized at some point but not yet logically filled, and a
 * region at the end that may be uninitialized. The filled region is guaranteed
 * to be a subset of the initialized region.
 *
 * In summary, the contents of the buffer can be visualized as:
 *
 * ```
 * [             capacity              ]
 * [ filled |         unfilled         ]
 * [    initialized    | uninitialized ]
 * ```
 *
 * Do not de-initialize bytes from the uninitialized region. That region may
 * contain bytes that have been initialized outside this wrapper, and once a byte
 * is initialized it must stay initialized.
 */
public class ReadBuf private constructor(
    private val buffer: UByteArray,
    private val start: Int,
    private val end: Int,
    private var filled: Int,
    private var initialized: Int,
) {
    init {
        require(start in 0..end) { "start must be within the buffer" }
        require(end <= buffer.size) { "end must be within the buffer" }
        require(filled in 0..initialized) { "filled must not be larger than initialized" }
        require(initialized <= capacity()) { "initialized must not be larger than capacity" }
    }

    /**
     * Creates a new `ReadBuf` from a fully initialized buffer.
     */
    public constructor(buffer: UByteArray) : this(
        buffer = buffer,
        start = 0,
        end = buffer.size,
        filled = 0,
        initialized = buffer.size,
    )

    /**
     * Returns the total capacity of the buffer.
     */
    public fun capacity(): Int = end - start

    /**
     * Returns a copy of the filled portion of the buffer.
     */
    public fun filled(): UByteArray = buffer.copyOfRange(start, start + filled)

    /**
     * Returns a mutable view of the filled portion of the buffer.
     */
    public fun filledMut(): UByteSlice = UByteSlice(buffer, start, filled)

    /**
     * Returns a new `ReadBuf` comprised of the unfilled section up to `n`.
     */
    public fun take(n: Int): ReadBuf {
        val max = min(remaining(), n)
        return uninit(buffer, start + filled, start + filled + max)
    }

    /**
     * Returns a copy of the initialized portion of the buffer.
     *
     * This includes the filled portion.
     */
    public fun initialized(): UByteArray = buffer.copyOfRange(start, start + initialized)

    /**
     * Returns a mutable view of the initialized portion of the buffer.
     *
     * This includes the filled portion.
     */
    public fun initializedMut(): UByteSlice = UByteSlice(buffer, start, initialized)

    /**
     * Returns a mutable view of the entire buffer without ensuring that it has
     * been fully initialized.
     *
     * The caller must uphold the filled and initialized cursor invariants. If
     * the caller initializes part of the uninitialized section, it must call
     * `assumeInit` with the number of bytes initialized.
     */
    public fun innerMut(): UByteSlice = UByteSlice(buffer, start, capacity())

    /**
     * Returns a mutable view of the unfilled part of the buffer without
     * ensuring that it has been fully initialized.
     *
     * The caller must not de-initialize portions of the buffer that have already
     * been initialized.
     */
    public fun unfilledMut(): UByteSlice =
        UByteSlice(buffer, start + filled, remaining())

    /**
     * Returns a mutable view of the unfilled part of the buffer, ensuring it is
     * fully initialized.
     *
     * Since `ReadBuf` tracks the region that has been initialized, this is
     * effectively free after the first use.
     */
    public fun initializeUnfilled(): UByteSlice = initializeUnfilledTo(remaining())

    /**
     * Returns a mutable view of the first `n` bytes of the unfilled part of the
     * buffer, ensuring it is fully initialized.
     */
    public fun initializeUnfilledTo(n: Int): UByteSlice {
        require(remaining() >= n) { "n overflows remaining" }

        val endOfInitialized = filled + n
        if (initialized < endOfInitialized) {
            buffer.fill(0u, start + initialized, start + endOfInitialized)
            initialized = endOfInitialized
        }

        return UByteSlice(buffer, start + filled, n)
    }

    /**
     * Returns the number of bytes at the end of the buffer that have not yet
     * been filled.
     */
    public fun remaining(): Int = capacity() - filled

    /**
     * Clears the buffer, resetting the filled region to empty.
     *
     * The number of initialized bytes is not changed, and the contents of the
     * buffer are not modified.
     */
    public fun clear() {
        filled = 0
    }

    /**
     * Advances the size of the filled region of the buffer.
     *
     * The number of initialized bytes is not changed.
     */
    public fun advance(n: Int) {
        require(n <= Int.MAX_VALUE - filled) { "filled overflow" }
        setFilled(filled + n)
    }

    /**
     * Sets the size of the filled region of the buffer.
     *
     * The number of initialized bytes is not changed. This can shrink the
     * filled region in addition to growing it.
     */
    public fun setFilled(n: Int) {
        require(n <= initialized) { "filled must not become larger than initialized" }
        filled = n
    }

    /**
     * Asserts that the first `n` unfilled bytes of the buffer are initialized.
     *
     * `ReadBuf` assumes that bytes are never de-initialized, so this method does
     * nothing when called with fewer bytes than are already known to be
     * initialized.
     */
    public fun assumeInit(n: Int) {
        require(n <= remaining()) { "n overflows remaining" }
        val new = filled + n
        if (new > initialized) {
            initialized = new
        }
    }

    /**
     * Appends data to the buffer, advancing the written position and possibly
     * also the initialized position.
     */
    public fun putSlice(bytes: UByteArray) {
        require(remaining() >= bytes.size) {
            "buf.size must fit in remaining(); buf.size = ${bytes.size}, remaining() = ${remaining()}"
        }

        val amount = bytes.size
        val writeEnd = filled + amount
        bytes.copyInto(buffer, destinationOffset = start + filled)

        if (initialized < writeEnd) {
            initialized = writeEnd
        }
        filled = writeEnd
    }

    /**
     * Appends data to the buffer from a signed byte array.
     */
    public fun putSlice(bytes: ByteArray) {
        putSlice(UByteArray(bytes.size) { index -> bytes[index].toUByte() })
    }

    public fun remainingMut(): Int = remaining()

    public fun advanceMut(count: Int) {
        assumeInit(count)
        advance(count)
    }

    public fun chunkMut(): UByteSlice = unfilledMut()

    public fun fmt(): String = toString()

    override fun toString(): String =
        "ReadBuf(filled=$filled, initialized=$initialized, capacity=${capacity()})"

    public companion object {
        public fun new(buffer: UByteArray): ReadBuf = ReadBuf(buffer)

        /**
         * Creates a new `ReadBuf` from a buffer that may be uninitialized.
         *
         * The internal cursor marks the entire buffer as uninitialized. If the
         * buffer is known to be partially initialized, call `assumeInit` to move
         * the internal cursor.
         */
        public fun uninit(buffer: UByteArray): ReadBuf =
            uninit(buffer, start = 0, end = buffer.size)

        public fun uninit(capacity: Int): ReadBuf = uninit(UByteArray(capacity))

        private fun uninit(buffer: UByteArray, start: Int, end: Int): ReadBuf =
            ReadBuf(
                buffer = buffer,
                start = start,
                end = end,
                filled = 0,
                initialized = 0,
            )
    }
}

private fun sliceToUninitMut(slice: UByteArray): UByteSlice =
    UByteSlice(slice, 0, slice.size)

private fun sliceAssumeInit(slice: UByteSlice): UByteArray = slice.copy()

private fun sliceAssumeInitMut(slice: UByteSlice): UByteSlice = slice

/**
 * Mutable view over a contiguous region of a byte buffer.
 */
public class UByteSlice public constructor(
    private val buffer: UByteArray,
    private val start: Int,
    public val size: Int,
) {
    init {
        require(start >= 0) { "start must be non-negative" }
        require(size >= 0) { "size must be non-negative" }
        require(start + size <= buffer.size) { "slice must fit inside the buffer" }
    }

    public fun isEmpty(): Boolean = size == 0

    public operator fun get(index: Int): UByte {
        checkIndex(index)
        return buffer[start + index]
    }

    public operator fun set(index: Int, value: UByte) {
        checkIndex(index)
        buffer[start + index] = value
    }

    public fun copy(): UByteArray = buffer.copyOfRange(start, start + size)

    public fun writeFrom(bytes: UByteArray) {
        require(bytes.size <= size) { "source must fit inside the slice" }
        bytes.copyInto(buffer, destinationOffset = start)
    }

    private fun checkIndex(index: Int) {
        require(index in 0..<size) { "index out of bounds" }
    }
}
