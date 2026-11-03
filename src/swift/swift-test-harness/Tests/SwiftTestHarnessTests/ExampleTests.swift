#if canImport(Testing)
import Testing
import Template

@Suite("Template Swift Export Tests")
struct TemplateExportTests {
    @Test("Template swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        #expect(Bool(true), "Template swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import Template

final class TemplateExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "Template swift module imported cleanly")
    }
}
#endif
