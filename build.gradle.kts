@file:OptIn(
    ExperimentalKotlinGradlePluginApi::class,
    org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl::class,
)

import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.android.build.api.withAndroid
import dev.detekt.gradle.Detekt
import groovy.json.JsonSlurper
import io.github.kotlinmania.build.AndroidSdkSupport
import io.github.kotlinmania.build.CentralPortalSupport
import io.github.kotlinmania.build.FfiBuildSupport
import io.github.kotlinmania.build.IdeSourceSetSupport
import io.github.kotlinmania.build.SwiftExportSupport
import io.github.kotlinmania.build.VerificationSupport
import org.gradle.api.GradleException
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportedModuleMetadata
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.web.nodejs.BaseNodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.web.yarn.BaseYarnRootExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlinx.benchmark)
    alias(libs.plugins.kotlin.allopen)
    `maven-publish`
    signing
}

group = providers.gradleProperty("project.group").getOrElse("io.github.kotlinmania")
version = providers.gradleProperty("project.version").getOrElse("0.1.0-SNAPSHOT")
val publishProjectName = providers.gradleProperty("project.name").getOrElse("unnamed-project")
val frameworkName = providers.gradleProperty("project.frameworkName").getOrElse("Unnamed")
val projectNamespace = providers.gradleProperty("project.namespace").getOrElse("io.github.kotlinmania")
val projectCompileSdk = providers.gradleProperty("android.compileSdk").getOrElse("34")
val jvmToolchainVersion = providers.gradleProperty("jvm.toolchain").getOrElse("21").toInt()

val versionCatalog =
    extensions
        .getByType(VersionCatalogsExtension::class.java)
        .named("libs")

fun csvProperty(name: String): Set<String> =
    providers
        .gradleProperty(name)
        .map { value ->
            value
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }.getOrElse(emptySet())

fun optionalTrimmedProperty(name: String): String? =
    providers
        .gradleProperty(name)
        .map { it.trim() }
        .orNull
        ?.takeIf { it.isNotEmpty() }

fun boolProperty(name: String, default: Boolean = false): Boolean =
    providers.gradleProperty(name).map { it.trim().lowercase() == "true" }.getOrElse(default)

// kotlinmania:common-api-bundle:v1 begin
val commonMainApiDependencyBundle =
    optionalTrimmedProperty("project.dependencies.commonMainApiBundle")
        ?.let { bundleName ->
            versionCatalog
                .findBundle(bundleName)
                .orElseThrow { GradleException("Missing public API bundle '$bundleName'") }
        }
// kotlinmania:common-api-bundle:v1 end

val commonMainDependencyBundle =
    optionalTrimmedProperty("project.dependencies.commonMainBundle")
        ?.let { bundleName ->
            versionCatalog
                .findBundle(bundleName)
                .orElseThrow { GradleException("Missing libs bundle '$bundleName'") }
        }
val commonBenchmarkDependencyBundle =
    optionalTrimmedProperty("project.dependencies.commonBenchmarkBundle")
        ?.let { bundleName ->
            versionCatalog
                .findBundle(bundleName)
                .orElseThrow { GradleException("Missing libs bundle '$bundleName'") }
        }
val commonTestDependencyBundle =
    optionalTrimmedProperty("project.dependencies.commonTestBundle")
        ?.let { bundleName ->
            versionCatalog
                .findBundle(bundleName)
                .orElseThrow { GradleException("Missing libs bundle '$bundleName'") }
        }

val enabledFeatureNames = csvProperty("project.features")
val benchmarkEnabled = "benchmark" in enabledFeatureNames
val benchmarkTargetNames = csvProperty("project.benchmark.targets")
if (benchmarkEnabled && commonBenchmarkDependencyBundle == null) {
    throw GradleException("Feature 'benchmark' requires project.dependencies.commonBenchmarkBundle")
}
val benchmarkWarmups = providers.gradleProperty("project.benchmark.warmups").map { it.toInt() }.getOrElse(3)
val benchmarkIterations = providers.gradleProperty("project.benchmark.iterations").map { it.toInt() }.getOrElse(5)
val benchmarkIterationTime = providers.gradleProperty("project.benchmark.iterationTime").map { it.toLong() }.getOrElse(1L)
val benchmarkIterationTimeUnit = providers.gradleProperty("project.benchmark.iterationTimeUnit").getOrElse("s")

val nativeFfiEnabled = boolProperty("project.ffi.native")
val nativeFfiTargetNames = csvProperty("project.ffi.native.targets")
val nativeFfiCompileWrapper = boolProperty("project.ffi.native.compileWrapper", true)
val jvmFfiEnabled = boolProperty("project.ffi.jvm")
val napiFfiEnabled = boolProperty("project.ffi.napi")

val ffiNameRaw = providers.gradleProperty("project.ffi.name").getOrElse("Ffi").trim()
val ffiName = ffiNameRaw.replaceFirstChar { it.uppercase() }
val ffiNameLower = ffiNameRaw.lowercase()
val ffiDefFile = "src/native/interop/cinterop/$ffiName.def"
val ffiWrapperBase = "${ffiNameLower}_wrapper"
val ffiNapiDir = "src/js/node-$ffiNameLower/dist"
val ffiNpmPackage = "@kotlinmania/$ffiNameLower-native-bindings"

if (nativeFfiEnabled || jvmFfiEnabled || napiFfiEnabled) {
    println("FFI channels enabled: native=$nativeFfiEnabled jvm=$jvmFfiEnabled napi=$napiFfiEnabled name=$ffiName")
}

// ============================================================================
// Swift Export & Native Binary Configuration (gradle.properties)
// ----------------------------------------------------------------------------
// Kotlin 2.4.x (2.4.21) vs. Kotlin 2.5.0 (`Kotlin/swift-export-sample`):
// - KGP 2.4.21 uses `kotlin { swiftExport {} }` (`SwiftExportedModuleMetadata.flattenPackage`,
//   forwarded by `SwiftExportAction` as `SwiftModuleConfig.rootPackage`).
// - KGP 2.5.0 renames `flattenPackage` to `rootPackage` under `kotlin { export { swift {} } }`.
// - We alias `SwiftExportedModuleMetadata.rootPackage` to `flattenPackage` below.
// ============================================================================
val swiftExportModuleName = optionalTrimmedProperty("project.swiftExport.moduleName") ?: frameworkName
val swiftExportRootPackage =
    optionalTrimmedProperty("project.swiftExport.rootPackage")
        ?: optionalTrimmedProperty("project.swiftExport.flattenPackage")
        ?: projectNamespace

val SwiftExportedModuleMetadata.rootPackage: org.gradle.api.provider.Property<String>
    get() = flattenPackage

val swiftExportEnableCoroutinesSupport =
    providers
        .gradleProperty("project.swiftExport.enableCoroutinesSupport")
        .getOrElse("true")
        .trim()
val swiftExportSettings =
    providers
        .gradlePropertiesPrefixedBy("project.swiftExport.settings.")
        .getOrElse(emptyMap())
val swiftExportFreeCompilerArgs = csvProperty("project.swiftExport.freeCompilerArgs")
val swiftExportLinkFreeCompilerArgs = csvProperty("project.swiftExport.linkTask.freeCompilerArgs")
val swiftExportExportApiBundle = boolProperty("project.swiftExport.exportApiBundle", false)
val swiftExportHiddenDependencies = csvProperty("project.swiftExport.hidden")
val appleFrameworkStatic = boolProperty("project.apple.framework.static", false)
val nativeSharedLibEnabled = boolProperty("project.native.sharedLib", true)

enum class SwiftExportModuleVisibility {
    EXPOSED,
    HIDDEN,
}

data class SwiftExportModuleSpec(
    val key: String,
    val dependency: Any,
    val moduleName: String,
    val rootPackage: String,
    val visibility: SwiftExportModuleVisibility,
)

fun deriveSwiftModuleName(rawName: String): String =
    rawName
        .removePrefix(":")
        .substringAfterLast(":")
        .removeSuffix("-kotlin")
        .split('-', '_', '.')
        .filter { it.isNotEmpty() }
        .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }

fun parseSwiftExportModuleSpec(rawEntry: String): SwiftExportModuleSpec {
    val parts = rawEntry.split("|").map { it.trim() }
    val rawDep = parts.firstOrNull().orEmpty()
    if (rawDep.isEmpty()) {
        throw GradleException("Malformed project.swiftExport.exports entry: '$rawEntry'")
    }
    val resolvedDep: Any =
        when {
            rawDep.startsWith(":") -> {
                project(rawDep)
            }

            ":" in rawDep -> {
                rawDep
            }

            else -> {
                val dep =
                    versionCatalog
                        .findLibrary(rawDep)
                        .orElseThrow {
                            GradleException(
                                "Unknown Swift Export library alias '$rawDep' in libs.versions.toml " +
                                    "(use a catalog alias, 'group:artifact:version', or ':subproject')",
                            )
                        }.get()
                listOfNotNull(
                    dep.module.group,
                    dep.module.name,
                    dep.versionConstraint.requiredVersion.takeIf { it.isNotEmpty() },
                ).joinToString(":")
            }
        }
    val explicitVisibility =
        when (val rawVis = parts.getOrNull(3)?.uppercase().orEmpty()) {
            "", "EXPOSED" -> SwiftExportModuleVisibility.EXPOSED
            "HIDDEN" -> SwiftExportModuleVisibility.HIDDEN
            else -> throw GradleException("Unknown Swift Export visibility '$rawVis' in '$rawEntry' (expected EXPOSED or HIDDEN)")
        }
    val visibility =
        if (rawDep in swiftExportHiddenDependencies) {
            SwiftExportModuleVisibility.HIDDEN
        } else {
            explicitVisibility
        }
    val moduleName = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: deriveSwiftModuleName(rawDep)
    return SwiftExportModuleSpec(
        key = rawDep,
        dependency = resolvedDep,
        moduleName = moduleName,
        rootPackage = parts.getOrNull(2).orEmpty(),
        visibility = visibility,
    )
}

val swiftExportModuleExports: List<SwiftExportModuleSpec> =
    buildList {
        val explicitSpecs = LinkedHashMap<String, SwiftExportModuleSpec>()
        csvProperty("project.swiftExport.exports").forEach { entry ->
            val spec = parseSwiftExportModuleSpec(entry)
            explicitSpecs[spec.key] = spec
        }
        providers
            .gradlePropertiesPrefixedBy("project.swiftExport.export.")
            .getOrElse(emptyMap())
            .toSortedMap()
            .forEach { (_, entry) ->
                val trimmed = entry.trim()
                if (trimmed.isNotEmpty()) {
                    val spec = parseSwiftExportModuleSpec(trimmed)
                    explicitSpecs[spec.key] = spec
                }
            }
        if (swiftExportExportApiBundle && commonMainApiDependencyBundle != null) {
            commonMainApiDependencyBundle.get().forEach { dep ->
                val coordKey = "${dep.module.group}:${dep.module.name}"
                val artifactKey = dep.module.name
                if (coordKey !in explicitSpecs && artifactKey !in explicitSpecs) {
                    val isHidden = coordKey in swiftExportHiddenDependencies || artifactKey in swiftExportHiddenDependencies
                    val derivedModule = deriveSwiftModuleName(artifactKey)
                    val derivedPackage = "${dep.module.group}.${artifactKey.removeSuffix("-kotlin").replace("-", "").replace("_", "")}"
                    val coordinate =
                        listOfNotNull(
                            dep.module.group,
                            dep.module.name,
                            dep.versionConstraint.requiredVersion.takeIf { it.isNotEmpty() },
                        ).joinToString(":")
                    explicitSpecs[coordKey] =
                        SwiftExportModuleSpec(
                            key = coordKey,
                            dependency = coordinate,
                            moduleName = derivedModule,
                            rootPackage = derivedPackage,
                            visibility = if (isHidden) SwiftExportModuleVisibility.HIDDEN else SwiftExportModuleVisibility.EXPOSED,
                        )
                }
            }
        }
        addAll(explicitSpecs.values)
    }

val projectKonanDataDirectory =
    providers
        .environmentVariable("KONAN_DATA_DIR")
        .map {
            require(it.isNotBlank()) { "KONAN_DATA_DIR must not be blank" }
            File(it).absoluteFile
        }.orElse(providers.systemProperty("user.home").map { File(it, ".konan") })

val projectDependencyHandler = project.dependencies
configurations.configureEach {
    if (name.contains("androidDeviceTest", ignoreCase = true)) {
        exclude(group = "org.jetbrains", module = "annotations")
    }
    if (name == "swiftExportClasspath") {
        dependencies.add(projectDependencyHandler.create("io.github.kotlinmania:swift-export-embeddable:2.4.20-km.4"))
    }
}

val browserTestExcludes =
    providers
        .gradleProperty("project.js.browserTestExcludes")
        .getOrElse("")
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

val extraKotlinCompilerArgs =
    providers
        .gradleProperty("project.kotlin.freeCompilerArgs")
        .getOrElse("")
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

val commonOptIns =
    listOf(
        "kotlin.time.ExperimentalTime",
        "kotlin.concurrent.atomics.ExperimentalAtomicApi",
        "kotlin.ExperimentalUnsignedTypes",
    )

// Install and configure project-local Android SDK before evaluating `kotlin { android {} }`.
AndroidSdkSupport.configure(project)

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions.allWarningsAsErrors.set(true)
    doFirst {
        check(compilerOptions.allWarningsAsErrors.get()) { "Warnings-as-errors disabled for $path" }
    }
}

// ============================================================================
// kotlin { … } — Modeled on Kotlin/multiplatform-library-template
// ============================================================================
kotlin {
    jvmToolchain(jvmToolchainVersion)

    // Default KMP hierarchy template augmented with custom intermediate groups:
    //   common -> hostSystem -> jvm / android / web (js, wasmJs) / native (mingw, posix -> apple / linux / androidNative)
    //   common -> wasmWasi
    applyDefaultHierarchyTemplate {
        common {
            group("hostSystem") {
                withJvm()
                withAndroid()
                group("web") {
                    withJs()
                    withWasmJs()
                }
                group("native") {
                    group("posix") {
                        group("apple")
                        group("linux")
                        group("androidNative")
                    }
                }
            }
        }
    }

    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_4)
        apiVersion.set(KotlinVersion.KOTLIN_2_4)
        allWarningsAsErrors.set(true)
        optIn.addAll(commonOptIns)
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xsuppress-version-warnings")
        freeCompilerArgs.addAll(extraKotlinCompilerArgs)
    }

    val xcf = XCFramework(frameworkName)
    val frameworkBundleId = projectNamespace

    fun KotlinNativeTarget.addToXcf(static: Boolean = appleFrameworkStatic) {
        binaries.framework {
            baseName = frameworkName
            if (static) isStatic = true
            xcf.add(this)
            binaryOption("bundleId", frameworkBundleId)
        }
    }

    fun KotlinNativeTarget.addSharedLib() {
        if (!nativeSharedLibEnabled) return
        binaries.sharedLib {
            baseName = frameworkName
        }
    }

    fun KotlinTarget.configureBenchmarkCompilation() {
        if (!benchmarkEnabled || name !in benchmarkTargetNames) return
        val mainCompilation = compilations.getByName("main")
        compilations.create("benchmark") {
            associateWith(mainCompilation)
            defaultSourceSet.dependencies {
                implementation(commonBenchmarkDependencyBundle!!)
            }
        }
    }

    // JVM
    jvm {
        configureBenchmarkCompilation()
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(jvmToolchainVersion.toString()))
        }
    }

    // Android KMP library
    android {
        namespace = projectNamespace
        compileSdk = projectCompileSdk.toInt()
        minSdk = providers.gradleProperty("android.minSdk").getOrElse("24").toInt()
        withHostTest {}
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {}
    }

    // Apple — Tier 1/2 targets
    macosArm64 {
        configureBenchmarkCompilation()
        addToXcf()
        addSharedLib()
    }
    iosArm64 {
        configureBenchmarkCompilation()
        addToXcf()
    }
    iosSimulatorArm64 {
        configureBenchmarkCompilation()
        addToXcf()
    }
    tvosArm64 {
        configureBenchmarkCompilation()
        addToXcf()
    }
    tvosSimulatorArm64 {
        configureBenchmarkCompilation()
        addToXcf()
    }
    watchosDeviceArm64 {
        configureBenchmarkCompilation()
        addToXcf()
    }
    watchosSimulatorArm64 {
        configureBenchmarkCompilation()
        addToXcf()
    }

    // Other native — Tier 1/2
    linuxX64 {
        configureBenchmarkCompilation()
        addSharedLib()
    }
    linuxArm64 {
        configureBenchmarkCompilation()
        addSharedLib()
    }
    mingwX64 {
        configureBenchmarkCompilation()
        addSharedLib()
    }

    // Android NDK — 64-bit only
    androidNativeArm64 {
        configureBenchmarkCompilation()
        addSharedLib()
    }
    androidNativeX64 {
        configureBenchmarkCompilation()
        addSharedLib()
    }

    // Web (Development + Production library distributions)
    js {
        configureBenchmarkCompilation()
        binaries.library()
        browser {
            testTask {
                browserTestExcludes.forEach { filter.excludeTestsMatching(it) }
            }
        }
        nodejs {
            testTask {
                useMocha {
                    timeout = "120s"
                }
            }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        configureBenchmarkCompilation()
        binaries.library()
        browser {
            testTask {
                browserTestExcludes.forEach { filter.excludeTestsMatching(it) }
            }
        }
        nodejs()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        configureBenchmarkCompilation()
        binaries.library()
        nodejs()
    }

    swiftExport {
        moduleName = swiftExportModuleName
        rootPackage = swiftExportRootPackage
        configure {
            settings.put("enableCoroutinesSupport", swiftExportEnableCoroutinesSupport)
            swiftExportSettings.forEach { (key, value) ->
                settings.put(key, value)
            }
            if (swiftExportFreeCompilerArgs.isNotEmpty()) {
                freeCompilerArgs.addAll(swiftExportFreeCompilerArgs)
            }
        }
        linkTask {
            if (swiftExportLinkFreeCompilerArgs.isNotEmpty()) {
                toolOptions.freeCompilerArgs.addAll(swiftExportLinkFreeCompilerArgs)
            }
        }
        swiftExportModuleExports
            .filter { it.visibility == SwiftExportModuleVisibility.EXPOSED }
            .forEach { exported ->
                export(exported.dependency) {
                    if (exported.moduleName.isNotEmpty()) {
                        moduleName = exported.moduleName
                    }
                    if (exported.rootPackage.isNotEmpty()) {
                        rootPackage = exported.rootPackage
                    }
                }
            }
    }

    tasks
        .withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>()
        .matching { it.name.startsWith("compileSwiftExportMain") }
        .configureEach {
            compilerOptions.freeCompilerArgs.add("-Xwarning-level=UNCHECKED_CAST:disabled")
        }

    // Native FFI — Kotlin/Native cinterop + per-target static wrapper library
    if (nativeFfiEnabled) {
        val configuredNativeTargets = targets.withType<KotlinNativeTarget>().map { it.name }.toSet()
        check(nativeFfiTargetNames.all { it in configuredNativeTargets }) {
            "Unknown project.ffi.native.targets: ${nativeFfiTargetNames - configuredNativeTargets}"
        }
        targets
            .withType<KotlinNativeTarget>()
            .matching {
                nativeFfiTargetNames.isEmpty() || it.name in nativeFfiTargetNames
            }.configureEach {
                val konanTargetName = konanTarget.name
                val targetCapitalized = name.replaceFirstChar { it.uppercase() }
                val wrapperTask =
                    if (nativeFfiCompileWrapper) {
                        FfiBuildSupport.registerNativeWrapperTask(
                            project,
                            ffiName,
                            ffiWrapperBase,
                            konanTargetName,
                            targetCapitalized,
                            projectKonanDataDirectory.get(),
                        )
                    } else {
                        null
                    }

                compilations.getByName("main") {
                    cinterops {
                        create(ffiNameLower) {
                            defFile(project.file(ffiDefFile))
                            packageName("$ffiNameLower.cinterop")
                            if (wrapperTask != null) {
                                extraOpts(
                                    "-libraryPath",
                                    layout.buildDirectory
                                        .dir("cinterop-targets/$konanTargetName")
                                        .get()
                                        .asFile.absolutePath,
                                )
                            }
                        }
                    }
                }
                if (wrapperTask != null) {
                    tasks.matching { it.name == "cinterop${ffiName}$targetCapitalized" }.configureEach {
                        dependsOn(wrapperTask)
                        inputs.files(wrapperTask)
                    }
                }
            }
    }

    // Source set hierarchy and per-source-set dependency declarations aligned with official Kotlin docs:
    //   https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html
    //   https://kotlinlang.org/docs/multiplatform/multiplatform-advanced-project-structure.html#dependson-and-source-set-hierarchies
    sourceSets {
        configureEach {
            val relPath = IdeSourceSetSupport.sourceSetDir(name)
            kotlin.setSrcDirs(listOf("$relPath/kotlin"))
            resources.setSrcDirs(listOf("$relPath/resources"))
        }
        val projectNsPath = projectNamespace.replace(".", "/")
        commonMain.dependencies {
            commonMainDependencyBundle?.let { implementation(it) }
            commonMainApiDependencyBundle?.let { api(it) }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            commonTestDependencyBundle?.let { bundle ->
                bundle.get().forEach { dep ->
                    val coordinate =
                        listOfNotNull(
                            dep.module.group,
                            dep.module.name,
                            dep.versionConstraint.requiredVersion.takeIf { it.isNotEmpty() },
                        ).joinToString(":")
                    implementation(coordinate) {
                        exclude(group = project.group.toString(), module = publishProjectName)
                    }
                }
            }
        }

        named("hostSystemMain").dependencies {}
        named("hostSystemTest").dependencies {}

        jvmMain {
            if (!jvmFfiEnabled) {
                csvProperty("project.ffi.jvm.kotlinExcludes").forEach { kotlin.exclude(it) }
                kotlin.exclude("$projectNsPath/internal/FfiJni.kt")
            }
            dependencies {}
        }
        jvmTest.dependencies {}

        androidMain.dependencies {}
        named("androidHostTest").dependencies {}
        named("androidDeviceTest").dependencies {}

        webMain.dependencies {}
        webTest.dependencies {}
        jsMain {
            if (!napiFfiEnabled) {
                csvProperty("project.ffi.napi.kotlinExcludes").forEach { kotlin.exclude(it) }
                val bindingSources = csvProperty("project.ffi.napi.kotlinSources")
                if (bindingSources.isEmpty()) {
                    kotlin.exclude("$projectNsPath/${ffiName}NativeBindings.kt")
                } else {
                    bindingSources.forEach { kotlin.exclude(it) }
                }
            }
            dependencies {}
        }
        jsTest.dependencies {}
        wasmJsMain.dependencies {}
        wasmJsTest.dependencies {}
        wasmWasiMain.dependencies {}
        wasmWasiTest.dependencies {}

        nativeMain.dependencies {}
        nativeTest.dependencies {}
        named("posixMain").dependencies {}
        named("posixTest").dependencies {}

        appleMain.dependencies {}
        appleTest.dependencies {}
        iosMain.dependencies {}
        iosTest.dependencies {}
        iosArm64Main.dependencies {}
        iosArm64Test.dependencies {}
        iosSimulatorArm64Main.dependencies {}
        iosSimulatorArm64Test.dependencies {}
        macosMain.dependencies {}
        macosTest.dependencies {}
        macosArm64Main.dependencies {}
        macosArm64Test.dependencies {}
        tvosMain.dependencies {}
        tvosTest.dependencies {}
        tvosArm64Main.dependencies {}
        tvosArm64Test.dependencies {}
        tvosSimulatorArm64Main.dependencies {}
        tvosSimulatorArm64Test.dependencies {}
        watchosMain.dependencies {}
        watchosTest.dependencies {}
        watchosDeviceArm64Main.dependencies {}
        watchosDeviceArm64Test.dependencies {}
        watchosSimulatorArm64Main.dependencies {}
        watchosSimulatorArm64Test.dependencies {}

        linuxMain.dependencies {}
        linuxTest.dependencies {}
        linuxX64Main.dependencies {}
        linuxX64Test.dependencies {}
        linuxArm64Main.dependencies {}
        linuxArm64Test.dependencies {}

        androidNativeMain.dependencies {}
        androidNativeTest.dependencies {}
        androidNativeArm64Main.dependencies {}
        androidNativeArm64Test.dependencies {}
        androidNativeX64Main.dependencies {}
        androidNativeX64Test.dependencies {}

        mingwMain.dependencies {}
        mingwTest.dependencies {}
        mingwX64Main.dependencies {}
        mingwX64Test.dependencies {}

        if (benchmarkEnabled) {
            val commonBenchmark = maybeCreate("commonBenchmark")
            commonBenchmark.dependencies {
                implementation(commonBenchmarkDependencyBundle!!)
            }
            benchmarkTargetNames.forEach { targetName ->
                findByName("${targetName}Benchmark")?.dependsOn(commonBenchmark)
            }
        }
    }
}

IdeSourceSetSupport.configure(project)

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
    annotation("kotlinx.benchmark.State")
}

if (benchmarkEnabled) {
    benchmark {
        targets {
            benchmarkTargetNames.forEach { targetName ->
                register("${targetName}Benchmark")
            }
        }
        configurations {
            named("main") {
                warmups = benchmarkWarmups
                iterations = benchmarkIterations
                iterationTime = benchmarkIterationTime
                iterationTimeUnit = benchmarkIterationTimeUnit
            }
        }
    }
}

// ============================================================================
// Test logging, Detekt & Ktlint
// ============================================================================
tasks.withType<AbstractTestTask>().configureEach {
    ignoreFailures = false
    testLogging {
        events(
            TestLogEvent.STARTED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.FAILED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR,
        )
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
        showStandardStreams = true
    }
}

detekt {
    buildUponDefaultConfig.set(true)
    allRules.set(false)
    autoCorrect.set(false)
    source.setFrom(files("src"))
    config.setFrom(files("detekt.yml"))
    parallel.set(true)
}

tasks.withType<Detekt>().configureEach {
    mustRunAfter(tasks.withType<KotlinCompilationTask<*>>())
    reports {
        html.required.set(true)
        sarif.required.set(true)
        checkstyle.required.set(false)
    }
}

tasks
    .matching { it.name.startsWith("runKtlint") || it.name.startsWith("ktlint") }
    .configureEach {
        mustRunAfter(tasks.withType<KotlinCompilationTask<*>>())
    }

ktlint {
    version.set(libs.versions.ktlintEngine.get())
    debug.set(false)
    verbose.set(false)
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(ReporterType.CHECKSTYLE)
        reporter(ReporterType.SARIF)
    }
    filter {
        exclude("**/build/**")
        include("**/src/**/kotlin/**")
    }
}

if (benchmarkEnabled) {
    tasks
        .withType<Detekt>()
        .matching {
            it.name.contains("BenchmarkBenchmark")
        }.configureEach {
            enabled = false
        }

    tasks
        .matching {
            it.name.startsWith("runKtlintCheckOver") && it.name.endsWith("BenchmarkBenchmarkSourceSet")
        }.configureEach {
            enabled = false
        }
}

// ============================================================================
// JS / Wasm / WASI Toolchain Pins & Test Runners
// ============================================================================
val nodeMaxOldSpaceSizeMb = providers.gradleProperty("node.maxOldSpaceSizeMb").getOrElse("4096")
val nodeHeapArg = "--max-old-space-size=$nodeMaxOldSpaceSizeMb"
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>().configureEach {
    nodeJsArgs += nodeHeapArg
}
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack>().configureEach {
    nodeArgs += nodeHeapArg
}

val nodeVersion = providers.gradleProperty("node.version").getOrElse("24.22.0")
val wasmNodeVersion = providers.gradleProperty("wasm.node.version").getOrElse(nodeVersion)
val yarnVersion = providers.gradleProperty("yarn.version").getOrElse("1.22.22")
val wasmYarnVersion = providers.gradleProperty("wasm.yarn.version").getOrElse(yarnVersion)

@Suppress("UNCHECKED_CAST")
val webpackVersion: String =
    (JsonSlurper().parse(rootProject.file("kotlin-js-store/package.json")) as Map<String, Any>)
        .let { it["dependencies"] as Map<String, Any> }["webpack"] as String

rootProject.extensions.configure<NodeJsEnvSpec>("kotlinNodeJsSpec") { version.set(nodeVersion) }
rootProject.extensions.configure<WasmNodeJsEnvSpec>("kotlinWasmNodeJsSpec") { version.set(wasmNodeVersion) }
rootProject.extensions.configure<YarnRootEnvSpec>("kotlinYarnSpec") {
    version.set(yarnVersion)
    yarnLockMismatchReport.set(org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport.WARNING)
    yarnLockAutoReplace.set(true)
}
rootProject.extensions.configure<WasmYarnRootEnvSpec>("kotlinWasmYarnSpec") {
    version.set(wasmYarnVersion)
    yarnLockMismatchReport.set(org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport.WARNING)
    yarnLockAutoReplace.set(true)
}

val wasmNpmVersionPins =
    (JsonSlurper().parse(rootProject.file("kotlin-js-store/wasm/package.json")) as Map<*, *>)
        .let { it["dependencies"] as Map<*, *> }

listOf("kotlinYarn", "kotlinWasmYarn").forEach { extensionName ->
    rootProject.extensions.configure<BaseYarnRootExtension>(extensionName) {
        yarnLockMismatchReportProperty.set(org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport.WARNING)
        yarnLockAutoReplaceProperty.set(true)
        providers
            .gradlePropertiesPrefixedBy("yarn.resolution.")
            .getOrElse(emptyMap())
            .forEach { (key, ver) ->
                val pkg = key.removePrefix("yarn.resolution.")
                resolution(pkg, ver)
                resolution("**/$pkg", ver)
            }
        resolution("webpack", webpackVersion)
        resolution("**/webpack", webpackVersion)
        if (extensionName == "kotlinWasmYarn") {
            wasmNpmVersionPins.forEach { (pkg, version) ->
                resolution(pkg as String, version as String)
                resolution("**/$pkg", version)
            }
        }
    }
}

listOf("rootPackageJson", "wasmRootPackageJson").forEach { taskName ->
    rootProject.tasks.named(taskName) {
        inputs.property("yarnResolutions", providers.gradlePropertiesPrefixedBy("yarn.resolution."))
        inputs.property("webpackVersion", webpackVersion)
    }
}
rootProject.tasks.named("wasmRootPackageJson") {
    inputs.file(rootProject.file("kotlin-js-store/wasm/package.json"))
}

// Disable npm package-lock tasks since this project uses Yarn; prevents IntelliJ IDEA's
// post-sync lockfile update hook from failing on missing build/js/package-lock.json.
tasks
    .matching {
        it.name == "kotlinUpgradePackageLock" ||
            it.name == "kotlinStorePackageLock" ||
            it.name == "kotlinWasmUpgradePackageLock" ||
            it.name == "kotlinWasmStorePackageLock"
    }.configureEach {
        enabled = false
    }

val patchedKarmaWebpackPackage =
    rootProject.layout.projectDirectory
        .dir("gradle/npm/karma-webpack")
        .asFile.absolutePath
        .replace("\\", "/")

listOf("kotlinNodeJs", "kotlinWasmNodeJs").forEach { extensionName ->
    rootProject.extensions.configure<BaseNodeJsRootExtension>(extensionName) {
        versions.webpack.version = webpackVersion
        versions.webpackCli.version = providers.gradleProperty("node.webpackCli.version").getOrElse("7.2.3")
        versions.karma.version = providers.gradleProperty("node.karma.version").getOrElse("npm:karma-maintained@6.4.8")
        versions.karmaWebpack.version = "file:$patchedKarmaWebpackPackage"
        versions.mocha.version = providers.gradleProperty("node.mocha.version").getOrElse("12.0.0-beta-10")
        versions.kotlinWebHelpers.version = providers.gradleProperty("node.kotlinWebHelpers.version").getOrElse("3.1.0")
    }
}

val jsTasksNeedingYarnLock =
    setOf(
        "compileKotlinJs",
        "compileTestKotlinJs",
        "compileDevelopmentLibraryKotlinJs",
        "compileProductionLibraryKotlinJs",
        "jsProcessResources",
        "jsTestProcessResources",
        "jsNodeTest",
        "jsBrowserTest",
    )

tasks.matching { it.name in jsTasksNeedingYarnLock }.configureEach {
    dependsOn(rootProject.tasks.named("kotlinUpgradeYarnLock"))
}
rootProject.tasks.matching { it.name == "kotlinStoreYarnLock" }.configureEach {
    dependsOn(rootProject.tasks.named("kotlinUpgradeYarnLock"))
}

val wasmTasksNeedingYarnLock =
    setOf(
        "compileKotlinWasmJs",
        "compileTestKotlinWasmJs",
        "compileDevelopmentLibraryKotlinWasmJs",
        "compileProductionLibraryKotlinWasmJs",
        "wasmJsProcessResources",
        "wasmJsTestProcessResources",
        "wasmJsNodeTest",
        "wasmJsBrowserTest",
        "compileKotlinWasmWasi",
        "compileTestKotlinWasmWasi",
        "compileDevelopmentLibraryKotlinWasmWasi",
        "compileProductionLibraryKotlinWasmWasi",
        "wasmWasiProcessResources",
        "wasmWasiTestProcessResources",
        "wasmWasiNodeTest",
    )

tasks.matching { it.name in wasmTasksNeedingYarnLock }.configureEach {
    dependsOn(rootProject.tasks.named("kotlinWasmUpgradeYarnLock"))
}
rootProject.tasks.matching { it.name == "kotlinWasmStoreYarnLock" }.configureEach {
    dependsOn(rootProject.tasks.named("kotlinWasmUpgradeYarnLock"))
}

// KGP's DevelopmentLibrary and ProductionLibrary sync into the same package directory;
// order ProductionLibrary tasks after DevelopmentLibrary tasks to satisfy Gradle 9 task validation.
listOf("js", "wasmJs", "wasmWasi").forEach { targetPrefix ->
    val devSyncTasks =
        tasks.matching {
            it.name == "${targetPrefix}DevelopmentLibraryCompileSync" ||
                it.name.startsWith("${targetPrefix}NodeDevelopmentLibrary") ||
                it.name.startsWith("${targetPrefix}BrowserDevelopmentLibrary")
        }
    tasks
        .matching {
            it.name == "${targetPrefix}ProductionLibraryCompileSync" ||
                it.name.startsWith("${targetPrefix}NodeProductionLibrary") ||
                it.name.startsWith("${targetPrefix}BrowserProductionLibrary")
        }.configureEach {
            mustRunAfter(devSyncTasks)
        }
}

// ============================================================================
// JNI & N-API FFI Tasks
// ============================================================================
if (jvmFfiEnabled) {
    val packageFfiJni = FfiBuildSupport.registerJniTasks(project, ffiName, ffiNameLower, projectNamespace)
    kotlin.sourceSets.named("jvmMain") { resources.srcDir(packageFfiJni) }

    tasks.withType<Test>().matching { it.name == "jvmTest" || it.name == "testAndroidHostTest" }.configureEach {
        dependsOn("compile${ffiName}Jni")
        systemProperty(
            "java.library.path",
            layout.buildDirectory
                .dir("natives")
                .get()
                .asFile.absolutePath,
        )
    }

    val packagedJniTest =
        tasks.register<Test>("packagedJniTest") {
            dependsOn("jvmJar", "jvmTestClasses")
            val jvmTest = tasks.named<Test>("jvmTest")
            testClassesDirs = jvmTest.get().testClassesDirs
            val mainOutput =
                kotlin.targets
                    .getByName("jvm")
                    .compilations
                    .getByName("main")
                    .output.allOutputs
            classpath = files(tasks.named<Jar>("jvmJar").flatMap { it.archiveFile }) + jvmTest.get().classpath.minus(mainOutput)
            systemProperty(
                "java.library.path",
                layout.buildDirectory
                    .dir("empty-native-search-path")
                    .get()
                    .asFile.absolutePath,
            )
            filter.isFailOnNoMatchingTests = false
            filter.includeTestsMatching("*.HostIoTest")
        }
    tasks.named("check") { dependsOn(packagedJniTest) }

    val compileAndroidJni =
        FfiBuildSupport.registerAndroidJniTask(
            project,
            ffiName,
            ffiNameLower,
            nativeFfiEnabled,
            nativeFfiCompileWrapper,
            projectKonanDataDirectory.get(),
        )
    extensions.getByType<KotlinMultiplatformAndroidComponentsExtension>().onVariants { variant ->
        requireNotNull(variant.sources.jniLibs).addGeneratedSourceDirectory(compileAndroidJni) { it.nativeDirectory }
    }
}

if (napiFfiEnabled) {
    val nodeExecutable = rootProject.extensions.getByType<NodeJsEnvSpec>().executable
    FfiBuildSupport.registerNapiTasks(
        project,
        ffiName,
        ffiNameLower,
        ffiNapiDir,
        ffiNpmPackage,
        nodeVersion,
        nodeExecutable,
    )
}

FfiBuildSupport.configureIncludedBuildsFfi(project, jvmFfiEnabled)

// ============================================================================
// Swift Export & Maven Central Portal Support
// ============================================================================
SwiftExportSupport.configure(project, swiftExportModuleName)

val hasAppleTargets =
    kotlin.targets
        .withType<KotlinNativeTarget>()
        .any { it.konanTarget.family.isAppleFamily }
CentralPortalSupport.configure(project, publishProjectName, hasAppleTargets)

// ============================================================================
// Verification, Build Aggregates & Runtime Validation
// ============================================================================
val rootYarnLockTasks =
    listOf(
        rootProject.tasks.named("kotlinUpgradeYarnLock"),
        rootProject.tasks.named("kotlinStoreYarnLock"),
        rootProject.tasks.named("kotlinWasmUpgradeYarnLock"),
        rootProject.tasks.named("kotlinWasmStoreYarnLock"),
    )

val nativeTargetNames =
    listOf(
        "androidNativeArm64",
        "androidNativeX64",
        "iosArm64",
        "iosSimulatorArm64",
        "linuxArm64",
        "linuxX64",
        "macosArm64",
        "mingwX64",
        "tvosArm64",
        "tvosSimulatorArm64",
        "watchosDeviceArm64",
        "watchosSimulatorArm64",
    )

val fullTargetBuildTaskNames =
    buildSet {
        addAll(
            listOf(
                "compileAndroidMain",
                "compileAndroidHostTest",
                "compileAndroidDeviceTest",
                "assembleAndroidMain",
                "assembleAndroidHostTest",
                "assembleAndroidDeviceTest",
                "assembleUnitTest",
                "assembleAndroidTest",
                "jvmMainClasses",
                "jvmTestClasses",
                "jvmJar",
                "compileDevelopmentLibraryKotlinJs",
                "compileProductionLibraryKotlinJs",
                "jsMainClasses",
                "jsTestClasses",
                "compileDevelopmentLibraryKotlinWasmJs",
                "compileProductionLibraryKotlinWasmJs",
                "wasmJsMainClasses",
                "wasmJsTestClasses",
                "compileDevelopmentLibraryKotlinWasmWasi",
                "compileProductionLibraryKotlinWasmWasi",
                "wasmWasiMainClasses",
                "wasmWasiTestClasses",
                "swiftExportSmokeTest",
                "assemble${frameworkName}XCFramework",
            ),
        )
        for (target in nativeTargetNames) {
            add("${target}Binaries")
            add("${target}TestBinaries")
        }
        if (jvmFfiEnabled) add("compile${ffiName}Jni")
        if (napiFfiEnabled) add("buildNode$ffiName")
    }

tasks.register("hostTests") {
    group = "verification"
    description = "Runs every enabled target's tests that this host can execute, plus Android host tests."
    dependsOn(
        rootProject.tasks.named("kotlinUpgradeYarnLock"),
        rootProject.tasks.named("kotlinWasmUpgradeYarnLock"),
        "allTests",
        "testAndroidHostTest",
    )
}

tasks.register("test") {
    group = "verification"
    description = "Runs tests for every enabled target this host can run, Android host tests, and the Swift Export smoke test."
    dependsOn("hostTests")
    dependsOn("swiftExportSmokeTest")
}

tasks.named("check") {
    dependsOn(tasks.withType<Detekt>())
    dependsOn(tasks.named("ktlintCheck"))
    dependsOn("test")
}

tasks.named("build") {
    dependsOn(rootYarnLockTasks)
    dependsOn(fullTargetBuildTaskNames)
}

val macroLayoutCSource = rootProject.file("verification/macro-layout-wasm.c")
if (macroLayoutCSource.isFile) {
    val macroLayoutDirectory = layout.buildDirectory.dir("macro-layout-wasm")
    val macroLayoutWasm = macroLayoutDirectory.map { it.file("macro-layout.wasm") }
    val macroLayoutScript = macroLayoutDirectory.map { it.file("macro-layout.js") }
    val macroFixtureLlvmBin =
        providers.gradleProperty("project.macroFixture.llvmBin").orElse(
            projectKonanDataDirectory.map { VerificationSupport.resolveKonanLlvmBin(it) },
        )
    val cinteropIncludeDir = file("src/native/interop/cinterop")
    val compileMacroLayoutWasm =
        tasks.register("compileMacroLayoutWasm") {
            inputs.files(macroLayoutCSource, cinteropIncludeDir.resolve("macro_parity_fixture.h"))
            inputs.property("llvmBin", macroFixtureLlvmBin)
            outputs.dir(macroLayoutDirectory)
            doLast {
                VerificationSupport.compileMacroLayoutWasm(
                    project,
                    macroLayoutCSource,
                    cinteropIncludeDir,
                    macroLayoutDirectory.get().asFile,
                    macroLayoutWasm.get().asFile,
                    macroLayoutScript.get().asFile,
                    macroFixtureLlvmBin.get(),
                )
            }
        }
    tasks
        .withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>()
        .matching {
            it.name == "jsBrowserTest" || it.name == "wasmJsBrowserTest"
        }.configureEach {
            dependsOn(compileMacroLayoutWasm)
            inputs.file(macroLayoutScript)
            environment("LIBC_MACRO_LAYOUT_SCRIPT", macroLayoutScript.get().asFile.absolutePath)
        }
    val macroWasiWasm = objects.fileProperty()
    val prepareMacroWasiTestLoader =
        tasks.register<Copy>("prepareMacroWasiTestLoader") {
            from(rootProject.file("verification/macro-wasi-test-loader.mjs"))
            into(layout.buildDirectory.dir("macro-layout-wasi"))
        }
    tasks.named<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>("wasmWasiNodeTest") {
        @OptIn(ExperimentalWasmDsl::class)
        val binary =
            compilation.binaries
                .filterIsInstance<org.jetbrains.kotlin.gradle.targets.js.ir.ExecutableWasm>()
                .single { it.mode == org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode.DEVELOPMENT }

        @OptIn(ExperimentalWasmDsl::class)
        val kotlinWasmFile = binary.mainWasmFile

        @OptIn(ExperimentalWasmDsl::class)
        val kotlinWasmLinkTask = binary.linkTask
        macroWasiWasm.set(kotlinWasmFile)
        dependsOn(compileMacroLayoutWasm, prepareMacroWasiTestLoader, kotlinWasmLinkTask)
        inputs.file(macroLayoutWasm)
        inputs.file(kotlinWasmFile)
        inputFileProperty.set(layout.buildDirectory.file("macro-layout-wasi/macro-wasi-test-loader.mjs"))
        doFirst {
            environment("LIBC_WASI_TEST_MODULE", kotlinWasmFile.get().asFile.absolutePath)
        }
        environment("LIBC_MACRO_WASM", macroLayoutWasm.get().asFile.absolutePath)
    }

    val wasmWasiPreopenTest =
        tasks.register<Exec>("wasmWasiPreopenTest") {
            group = "verification"
            description = "Executes public WASI FILE IO against a real preopened host directory."
            dependsOn("wasmWasiNodeTest")
            inputs.file(macroWasiWasm)
            inputs.file(macroLayoutWasm)
            doFirst {
                commandLine(
                    rootProject.extensions
                        .getByType<WasmNodeJsEnvSpec>()
                        .executable
                        .get(),
                    rootProject.file("verification/wasi-preopen-test.mjs").absolutePath,
                    macroWasiWasm.get().asFile,
                    layout.buildDirectory
                        .dir("test-results/wasmWasiPreopenTest")
                        .get()
                        .asFile,
                    macroLayoutWasm.get().asFile,
                )
            }
        }
    tasks.named("test") { dependsOn(wasmWasiPreopenTest) }
    tasks.named("check") { dependsOn(wasmWasiPreopenTest) }
}

tasks.register("classes") {
    dependsOn(
        "compileAndroidMain",
        "jvmMainClasses",
        "jsMainClasses",
        "wasmJsMainClasses",
        "wasmWasiMainClasses",
    )
}

tasks.register("testClasses") {
    dependsOn(
        "compileAndroidHostTest",
        "jvmTestClasses",
        "jsTestClasses",
        "wasmJsTestClasses",
        "wasmWasiTestClasses",
    )
}

val wasmWasiTestPreopens =
    providers
        .gradleProperty("project.wasmWasi.testPreopens")
        .getOrElse("")
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

if (wasmWasiTestPreopens.isNotEmpty()) {
    tasks.named("wasmWasiNodeTest") {
        val driverDir = layout.buildDirectory.dir("compileSync/wasmWasi/test/testDevelopmentExecutable/kotlin")
        val hostRoot = layout.buildDirectory.dir("tmp/wasmWasiTestPreopens")
        doFirst {
            VerificationSupport.patchWasmWasiTestPreopens(
                driverDir.get().asFile,
                hostRoot.get().asFile,
                wasmWasiTestPreopens,
            )
        }
    }
}
