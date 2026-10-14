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
}
