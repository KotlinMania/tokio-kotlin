import org.gradle.api.artifacts.VersionCatalogsExtension
import java.security.MessageDigest

// Kotlin's AsVoid/AsOptionalNothing bridges and bundled coroutine runtime emit
// warnings under the same compiler that generated them. Keep the corrections
// local to generated build outputs; compiler distributions stay immutable.
// https://github.com/JetBrains/kotlin/blob/v2.4.20/native/swift/sir-providers/src/org/jetbrains/kotlin/sir/providers/impl/BridgeProvider/TypeBridging.kt
// https://github.com/JetBrains/kotlin/blob/v2.4.20/native/swift/swift-export-standalone/resources/swift/KotlinCoroutineSupport.kt
object SwiftExportCompatibility {
    private const val originalRuntimeHash = "e63496593d0ae8e63c7d7180c0bfa0a07c23f63d998e06459e8b6878eded26fe"
    private const val correctedRuntimeHash = "3d1cf71c3dfc552210d7678c6b1e02e859762904e1b83aa847445e52f0c8ccb8"

    fun checkVersion(version: String) {
        check(version in setOf("2.4.20", "2.4.21")) {
            "Review Swift Export compatibility corrections for Kotlin $version before building Swift exports."
        }
    }

    fun bridges(source: String): String =
        // Consume the already evaluated result while preserving the Boolean ABI.
        // Unlike a bare expression statement, let also accepts a nullable result.
        source
            .replace("run { _result; true }", "_result.let { true }")
            .replace("run<Unit> { _result }", "_result.let { }")

    fun coroutineRuntime(source: String): String {
        val hash = sha256(source)
        if (hash == correctedRuntimeHash) return source
        check(hash == originalRuntimeHash) {
            "Swift Export coroutine runtime changed ($hash); review the compatibility corrections."
        }
        val corrected =
            source
                .replace(
                    "@OptIn(InternalCoroutinesApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)",
                    "@OptIn(InternalCoroutinesApi::class, InternalForInheritanceCoroutinesApi::class, " +
                        "kotlin.concurrent.atomics.ExperimentalAtomicApi::class)",
                ).replace(
                    "is State.Completed -> return state.error?.let { throw it } ?: null",
                    """
                    is State.Completed -> {
                                        val failure = state.error
                                        if (failure != null) throw failure
                                        return null
                                    }
                    """.trimIndent(),
                ).replace(
                    "as SwiftFlowIterator<kotlin.Any?>",
                    "as SwiftFlowIterator<*>",
                ).replace(
                    "arg0: SwiftFlowIterator<kotlin.Any?>.Value?",
                    "arg0: SwiftFlowIterator<*>.Value?",
                )
        check(sha256(corrected) == correctedRuntimeHash) {
            "Swift Export coroutine compatibility correction did not produce the reviewed runtime."
        }
        return corrected
    }

    private fun sha256(source: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

val swiftExportVersion =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion("kotlin")
        .get()
        .requiredVersion
val swiftExportRunnerVersion = providers.gradleProperty("versions.kotlin")
val swiftExportProducers = tasks.matching { Regex(".+(Debug|Release)SwiftExport").matches(it.name) }
val swiftExportRoot = layout.buildDirectory.dir("SwiftExport")

val correctSwiftExportBridges =
    tasks.register("correctSwiftExportBridges") {
        group = "build"
        description = "Corrects reviewed Kotlin 2.4 Swift bridge defects while retaining warnings as errors."
        // A separate dependency waits for export workers to finish before reading
        // their sources. An export doLast action can race asynchronous workers.
        dependsOn(swiftExportProducers)
        doLast {
            SwiftExportCompatibility.checkVersion(swiftExportVersion)
            check(swiftExportRunnerVersion.get() == swiftExportVersion) {
                "Swift Export runner and Kotlin plugin versions must match."
            }
            val outputRoot = swiftExportRoot.get().asFile.canonicalFile
            val generatedRoots =
                swiftExportProducers.flatMap { producer ->
                    producer.outputs.files.files.filter { it.name == "files" }.map { it.parentFile.canonicalFile }
                }
            check(generatedRoots.isNotEmpty()) { "No Swift Export producer outputs were found." }
            generatedRoots.distinct().forEach { root ->
                check(root.toPath().startsWith(outputRoot.toPath())) { "Unexpected Swift Export output: $root" }
                check(root.resolve("files").isDirectory) { "Swift Export did not generate $root/files" }
                val runtime = root.resolve("KotlinCoroutineSupport/KotlinCoroutineSupport/KotlinCoroutineSupport.kt")
                if (runtime.isFile) {
                    val source = runtime.readText()
                    val corrected = SwiftExportCompatibility.coroutineRuntime(source)
                    if (source != corrected) runtime.writeText(corrected)
                }
                root.resolve("files").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                    val source = file.readText()
                    val corrected = SwiftExportCompatibility.bridges(source)
                    if (source != corrected) file.writeText(corrected)
                }
            }
        }
    }

tasks.matching { it.name.startsWith("compileSwiftExportMainKotlin") }.configureEach {
    dependsOn(correctSwiftExportBridges)
}
