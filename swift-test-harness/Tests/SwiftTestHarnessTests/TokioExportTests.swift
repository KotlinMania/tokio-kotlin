import Testing
@_spi(kotlin$ExperimentalUnsignedTypes) import Tokio

@Suite("Tokio Swift Export Tests")
struct TokioExportTests {
    @Test("ReadBuf preserves initialized data when its filled cursor is cleared")
    func readBufMutationAndClear() {
        let buf = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 4)
        let writable = buf.chunkMut()
        #expect(writable.len() == 4)
        writable.writeByte(index: 0, byte: -128)
        writable.writeByte(index: 1, byte: -1)
        buf.advanceMut(cnt: 2)
        #expect(buf.chunkMut().len() == 2)
        #expect(buf.capacity() == 4)
        #expect(buf.remaining() == 2)
        #expect(buf.filledMut().size == 2)
        #expect(buf.filledMut()[0] == 128)
        #expect(buf.filledMut()[1] == 255)
        buf.clear()
        #expect(buf.remaining() == 4)
        #expect(buf.filledMut().isEmpty())
        #expect(buf.initializedMut().size == 2)
        #expect(buf.initializedMut()[0] == 128)
        #expect(buf.initializedMut()[1] == 255)
    }

    @Test("ReadBuf take writes through only its unfilled window")
    func takenBufferSharesStorage() {
        let parent = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 6)
        let prefix = parent.initializeUnfilledTo(n: 2)
        prefix[0] = 7
        prefix[1] = 8
        parent.advance(n: 2)
        let child = parent.take(n: 2)
        let writable = child.initializeUnfilledTo(n: 2)
        writable[0] = 9
        writable[1] = 10
        child.advance(n: 2)
        #expect(child.remaining() == 0)
        #expect(parent.filledMut().size == 2)
        parent.assumeInit(n: 2)
        parent.advance(n: 2)
        #expect(parent.filledMut().size == 4)
        #expect(parent.filledMut()[0] == 7)
        #expect(parent.filledMut()[1] == 8)
        #expect(parent.filledMut()[2] == 9)
        #expect(parent.filledMut()[3] == 10)
        #expect(parent.remaining() == 2)
    }

    @Test("SimplexStream applies backpressure, wakes writers, and drains before EOF")
    func simplexBackpressureAndShutdown() {
        let stream = Tokio.io.util.SimplexStream.Companion.shared.newUnsplit(maxBufSize: 2)
        var wakes = 0
        let cx = Tokio.io.Context(wakeup: { wakes += 1 })
        let input = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 2)
        let bytes = input.initializeUnfilled()
        bytes[0] = 128
        bytes[1] = 255
        input.advance(n: 2)
        #expect(stream.pollWrite(cx: cx, buf: input.filled()).isReady)
        #expect(stream.pollWrite(cx: cx, buf: input.filled()).isPending)
        let output = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 1)
        #expect(stream.pollRead(cx: cx, buf: output).isReady)
        #expect(output.filledMut().size == 1)
        #expect(output.filledMut()[0] == 128)
        #expect(wakes == 1)
        #expect(stream.pollShutdown(cx: cx).isReady)
        output.clear()
        #expect(stream.pollRead(cx: cx, buf: output).isReady)
        #expect(output.filledMut().size == 1)
        #expect(output.filledMut()[0] == 255)
        output.clear()
        #expect(stream.pollRead(cx: cx, buf: output).isReady)
        #expect(output.filledMut().isEmpty())
    }

    @Test("BufWriter shutdown resumes partial writes before closing the stream")
    func bufferedWriterBackpressureAndShutdown() {
        let stream = Tokio.io.util.SimplexStream.Companion.shared.newUnsplit(maxBufSize: 2)
        let writer = Tokio.io.util.BufWriter.Companion.shared.withCapacity(capacity: 8, inner: stream)
        let input = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 4)
        let bytes = input.initializeUnfilled()
        bytes[0] = 128
        bytes[1] = 255
        bytes[2] = 7
        bytes[3] = 8
        input.advance(n: 4)
        var wakes = 0
        let cx = Tokio.io.Context(wakeup: { wakes += 1 })
        let readCx = Tokio.io.Context(wakeup: {})
        #expect(writer.getPinMut() === stream)
        #expect(writer.pollWrite(cx: cx, buf: input.filled()).isReady)
        #expect(Tokio.io.ReadBuf.Companion.shared.new(buffer: writer.buffer()).capacity() == 4)
        let output = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 2)
        #expect(stream.pollRead(cx: readCx, buf: output).isPending)
        #expect(writer.pollShutdown(cx: cx).isPending)
        #expect(stream.pollRead(cx: readCx, buf: output).isReady)
        #expect(output.filledMut().size == 2)
        #expect(output.filledMut()[0] == 128)
        #expect(output.filledMut()[1] == 255)
        #expect(wakes == 1)
        #expect(writer.pollShutdown(cx: cx).isReady)
        #expect(Tokio.io.ReadBuf.Companion.shared.new(buffer: writer.buffer()).capacity() == 0)
        output.clear()
        #expect(stream.pollRead(cx: readCx, buf: output).isReady)
        #expect(output.filledMut().size == 2)
        #expect(output.filledMut()[0] == 7)
        #expect(output.filledMut()[1] == 8)
        output.clear()
        #expect(stream.pollRead(cx: readCx, buf: output).isReady)
        #expect(output.filledMut().isEmpty())
    }

    @Test("BufWriter removes the sent prefix when a fixed destination becomes full")
    func bufferedWriterZeroWriteRetainsSuffix() {
        let storage = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 2)
        _ = storage.initializeUnfilled()
        storage.advance(n: 2)
        let inner = Tokio.io.CursorAsyncWrite(bytes: storage.filled(), position: 0)
        let writer = Tokio.io.util.BufWriter.Companion.shared.withCapacity(capacity: 8, inner: inner)
        let input = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 4)
        let bytes = input.initializeUnfilled()
        bytes[0] = 0
        bytes[1] = 1
        bytes[2] = 2
        bytes[3] = 3
        input.advance(n: 4)
        let cx = Tokio.io.Context(wakeup: {})
        #expect(writer.pollWrite(cx: cx, buf: input.filled()).isReady)
        #expect(writer.pollFlush(cx: cx).isReady)
        #expect(inner.position() == 2)
        let suffix = Tokio.io.ReadBuf.Companion.shared.new(buffer: writer.buffer()).initializedMut()
        #expect(suffix.size == 2)
        #expect(suffix[0] == 2)
        #expect(suffix[1] == 3)
        let sent = Tokio.io.ReadBuf.Companion.shared.new(buffer: inner.asSlice()).initializedMut()
        #expect(sent[0] == 0)
        #expect(sent[1] == 1)
    }

    @Test("Cursor buffered reads preserve full-width positions beyond EOF")
    func cursorBeyondEofPreservesItsPosition() {
        let input = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 3)
        let bytes = input.initializeUnfilled()
        bytes[0] = 10
        bytes[1] = 20
        bytes[2] = 30
        input.advance(n: 3)
        let positions: [UInt64] = [UInt64(Int32.max) + 1, UInt64(UInt32.max) + 1, UInt64.max]
        let cx = Tokio.io.Context(wakeup: {})
        for position in positions {
            let reader = Tokio.io.CursorAsyncBufRead(bytes: input.filled(), position: position)
            #expect(reader.pollFillBuf(cx: cx).isReady)
            reader.consume(amt: 0)
            #expect(reader.position() == position)
            let output = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 2)
            output.initializeUnfilledTo(n: 1)[0] = 99
            output.advance(n: 1)
            #expect(reader.pollRead(cx: cx, buf: output).isReady)
            #expect(output.filledMut().size == 1)
            #expect(output.filledMut()[0] == 99)
            #expect(reader.position() == position)
        }
    }

    @Test("BufReader clamps large consume counts without replaying data")
    func bufferedReaderConsumeDoesNotOverflow() {
        let input = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 6)
        let bytes = input.initializeUnfilled()
        for index in 0..<6 {
            bytes[Int32(index)] = UInt8(index)
        }
        input.advance(n: 6)
        let inner = Tokio.io.ByteSliceAsyncRead(bytes: input.filled())
        let reader = Tokio.io.util.BufReader.Companion.shared.withCapacity(capacity: 4, inner: inner)
        let cx = Tokio.io.Context(wakeup: {})
        #expect(reader.getPinMut() === inner)
        #expect(reader.pollFillBuf(cx: cx).isReady)
        reader.consume(amt: 1)
        reader.consume(amt: Int32.max)
        #expect(Tokio.io.ReadBuf.Companion.shared.new(buffer: reader.buffer()).capacity() == 0)
        let output = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 4)
        #expect(reader.pollRead(cx: cx, buf: output).isReady)
        #expect(output.filledMut().size == 2)
        #expect(output.filledMut()[0] == 4)
        #expect(output.filledMut()[1] == 5)
    }

    @Test("BufReader resumes a pending fill and drains buffered data before EOF")
    func bufferedReaderPendingFillAndShutdown() {
        let stream = Tokio.io.util.SimplexStream.Companion.shared.newUnsplit(maxBufSize: 4)
        let reader = Tokio.io.util.BufReader.Companion.shared.withCapacity(capacity: 4, inner: stream)
        var wakes = 0
        let cx = Tokio.io.Context(wakeup: { wakes += 1 })
        #expect(reader.pollFillBuf(cx: cx).isPending)
        let input = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 2)
        let bytes = input.initializeUnfilled()
        bytes[0] = 128
        bytes[1] = 255
        input.advance(n: 2)
        #expect(stream.pollWrite(cx: cx, buf: input.filled()).isReady)
        #expect(wakes == 1)
        #expect(reader.pollFillBuf(cx: cx).isReady)
        #expect(Tokio.io.ReadBuf.Companion.shared.new(buffer: reader.buffer()).capacity() == 2)
        #expect(stream.pollShutdown(cx: cx).isReady)
        let output = Tokio.io.ReadBuf.Companion.shared.uninit(capacity: 1)
        #expect(reader.pollRead(cx: cx, buf: output).isReady)
        #expect(output.filledMut()[0] == 128)
        output.clear()
        #expect(reader.pollRead(cx: cx, buf: output).isReady)
        #expect(output.filledMut()[0] == 255)
        output.clear()
        #expect(reader.pollRead(cx: cx, buf: output).isReady)
        #expect(output.filledMut().isEmpty())
    }
}
