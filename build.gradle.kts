import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

plugins {
    kotlin("multiplatform") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("com.android.kotlin.multiplatform.library") version "9.2.1"
    id("com.vanniktech.maven.publish") version "0.36.0"
}

group = "io.github.kotlinmania"
version = "0.1.0"

// The Android Gradle plugin resolves the SDK location while Gradle builds the
// task graph — before any task executes — so a project-local Android SDK must
// already be installed by the time configuration runs. setup-android-sdk.sh
// installs the SDK into this repo's own .android-sdk/ and writes
// local.properties to point there. It runs unconditionally on every
// configuration: the script itself is idempotent (an already-installed SDK is
// a fast no-op), but there is deliberately no Gradle-side condition that could
// skip the install, and no fallback to a sibling repo's SDK.
serviceOf<ExecOperations>().exec { commandLine("bash", "./setup-android-sdk.sh") }

kotlin {
    applyDefaultHierarchyTemplate()

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
        languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
    }

    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    val xcf = XCFramework("Tokio")

    macosArm64 {
        binaries.framework { baseName = "Tokio"; xcf.add(this) }
    }
    iosArm64 {
        binaries.framework { baseName = "Tokio"; xcf.add(this) }
    }
    iosSimulatorArm64 {
        binaries.framework { baseName = "Tokio"; xcf.add(this) }
    }
    iosX64 {
        binaries.framework { baseName = "Tokio"; xcf.add(this) }
    }

    tvosArm64 {
        binaries.framework { baseName = "Tokio"; xcf.add(this) }
    }
    tvosSimulatorArm64 {
        binaries.framework { baseName = "Tokio"; xcf.add(this) }
    }

    watchosArm32 {
        binaries.framework { baseName = "Tokio"; xcf.add(this) }
    }
    watchosArm64 {
        binaries.framework { baseName = "Tokio"; xcf.add(this) }
    }
    watchosDeviceArm64 {
        binaries.framework { baseName = "Tokio"; xcf.add(this) }
    }
    watchosSimulatorArm64 {
        binaries.framework { baseName = "Tokio"; xcf.add(this) }
    }

    linuxX64()
    linuxArm64()
    mingwX64()

    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()

    js {
        browser()
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    swiftExport {
        moduleName = "Tokio"
        flattenPackage = "io.github.kotlinmania.tokio"
    }

    android {
        namespace = "io.github.kotlinmania.tokio"
        compileSdk = 34
        minSdk = 24
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
    }

    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

    }
    jvmToolchain(21)
}

tasks.withType<AbstractTestTask>().configureEach {
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

rootProject.extensions.configure<NodeJsEnvSpec>("kotlinNodeJsSpec") {
    version.set("24.15.0")
}

rootProject.extensions.configure<WasmNodeJsEnvSpec>("kotlinWasmNodeJsSpec") {
    version.set("24.15.0")
}

rootProject.extensions.configure<YarnRootEnvSpec>("kotlinYarnSpec") {
    version.set("1.22.22")
}

rootProject.extensions.configure<WasmYarnRootEnvSpec>("kotlinWasmYarnSpec") {
    version.set("1.22.22")
}

rootProject.extensions.configure<YarnRootExtension>("kotlinYarn") {
    resolution("diff", "8.0.3")
    resolution("**/diff", "8.0.3")
    resolution("fast-uri", "3.1.2")
    resolution("**/fast-uri", "3.1.2")
    resolution("serialize-javascript", "7.0.5")
    resolution("**/serialize-javascript", "7.0.5")
    resolution("webpack", "5.106.2")
    resolution("**/webpack", "5.106.2")
    resolution("follow-redirects", "1.16.0")
    resolution("**/follow-redirects", "1.16.0")
    resolution("lodash", "4.18.1")
    resolution("**/lodash", "4.18.1")
    resolution("ajv", "8.20.0")
    resolution("**/ajv", "8.20.0")
    resolution("brace-expansion", "5.0.6")
    resolution("**/brace-expansion", "5.0.6")
    resolution("flatted", "3.4.2")
    resolution("**/flatted", "3.4.2")
    resolution("minimatch", "10.2.5")
    resolution("**/minimatch", "10.2.5")
    resolution("picomatch", "4.0.4")
    resolution("**/picomatch", "4.0.4")
    resolution("qs", "6.15.1")
    resolution("**/qs", "6.15.1")
    resolution("socket.io-parser", "4.2.6")
    resolution("**/socket.io-parser", "4.2.6")
    resolution("ws", "8.20.1")
    resolution("**/ws", "8.20.1")
}


val patchedKarmaWebpackPackage = rootProject.layout.projectDirectory.dir("gradle/npm/karma-webpack").asFile.absolutePath.replace("\\", "/")

rootProject.extensions.configure<NodeJsRootExtension>("kotlinNodeJs") {
    versions.webpack.version = "5.106.2"
    versions.webpackCli.version = "7.0.2"
    versions.karma.version = "npm:karma-maintained@6.4.7"
    versions.karmaWebpack.version = "file:$patchedKarmaWebpackPackage"
    versions.mocha.version = "12.0.0-beta-10"
    versions.kotlinWebHelpers.version = "3.1.0"
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "tokio-kotlin", version.toString())

    pom {
        name.set("tokio-kotlin")
        description.set("Kotlin Multiplatform port of tokio-rs/tokio - An event-driven, non-blocking I/O platform for writing asynchronous I/O backed applications")
        inceptionYear.set("2026")
        url.set("https://github.com/KotlinMania/tokio-kotlin")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("sydneyrenee")
                name.set("Sydney Renee")
                email.set("sydney@solace.ofharmony.ai")
                url.set("https://github.com/sydneyrenee")
            }
        }

        scm {
            url.set("https://github.com/KotlinMania/tokio-kotlin")
            connection.set("scm:git:git://github.com/KotlinMania/tokio-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com/KotlinMania/tokio-kotlin.git")
        }
    }
}

tasks.register<Exec>("setupAndroidSdk") {
    group = "setup"
    description = "Downloads and configures the project-local Android SDK."
    commandLine("./setup-android-sdk.sh")
}

tasks.register("test") {
    group = "verification"
    description =
        "Runs the host-portable test suite (macOS + JS + WasmJS + Android unit). " +
        "Non-host native targets (mingwX64, linuxX64) only run on their own host."

    val defaultTestTasks = listOf(
        "macosArm64Test",
        "jvmTest",
        "jsNodeTest",
        "wasmJsNodeTest",
        "compileAndroidMain",
        "assembleUnitTest",
    )

    dependsOn(defaultTestTasks.mapNotNull { taskName -> tasks.findByName(taskName) })
}

// The generated Wasm-WASI Node test runner cannot see the filesystem unless
// the project directory is preopened. Patch the runner before wasmWasiNodeTest.
val patchWasmWasiNodePreopens = tasks.register("patchWasmWasiNodePreopens") {
    description = "Preopen the project directory for the generated Wasm-WASI Node test runner."
    group = "verification"
    dependsOn("compileTestDevelopmentExecutableKotlinWasmWasi")
    outputs.upToDateWhen { false }

    doLast {
        val runnerFile = layout.buildDirectory.file(
            "compileSync/wasmWasi/test/testDevelopmentExecutable/kotlin/${rootProject.name}-test.mjs",
        ).get().asFile
        if (!runnerFile.exists()) {
            // No Wasm-WASI test runner was generated (the repo has no
            // wasmWasi test sources), so there is nothing to preopen.
            return@doLast
        }
        val text = runnerFile.readText()
        val withCwdImport = text.replace(
            "import { argv, env } from 'node:process';",
            "import { argv, env, cwd } from 'node:process';",
        )
        val patched = withCwdImport.replace(
            "const wasi = new WASI({ version: 'preview1', args: argv, env, });",
            "const wasi = new WASI({ version: 'preview1', args: argv, env, preopens: { '/': cwd() }, });",
        )
        runnerFile.writeText(patched)
    }
}

tasks.named("wasmWasiNodeTest") {
    dependsOn(patchWasmWasiNodePreopens)
}
