# Swift Export Rollout — Recipe for the Claude GitHub Agent

> **Distributed source of truth:** `template/SWIFT.md` is the canonical copy
> distributed to projects. Keep the KotlinMania-root `SWIFT.md` byte-for-byte
> synchronized with it. Update these copies through one coordinated writer.
> For a standalone, task-oriented technical reference with linked table of
> contents, stable section anchors, command recipes, and exact evidence tables,
> see the companion guide [`template/V2_SWIFT.md`](file:///Volumes/stuff/Projects/kotlinmania/template/V2_SWIFT.md).

> **Self-contained instruction set.** This file lives at the root of every
> repository that has the canonical KotlinMania Swift Export rollout
> committed to its `automation/swift-export-rollout` branch. The Claude
> GitHub Agent will be assigned a per-repo issue that asks it to walk this
> recipe top-to-bottom and finish the rollout by pushing the branch and
> opening the pull request. Nothing in this recipe depends on context from
> outside the current repository.

> **IMPORTANT — Historical Rollout Log vs. Current Normative Rules (`2026-10-21`):**
> Sections from the initial May 2026 rollout below are preserved for historical provenance, but **several early patterns are now retired workspace-wide and must NEVER be applied**:
> 1. **Retired Apple `x86_64` & 32-bit targets:** `iosX64`, `macosX64`, `tvosX64`, `watchosX64`, `watchosArm32`, `watchosArm64` (`arm64_32`), `androidNativeArm32`, `androidNativeX86`, `linuxArm32Hfp`, and `mingwX86` are retired workspace-wide (`AGENTS.md` §5.5). Only `iosSimulatorArm64` (`isStatic = true`) is used for iOS Simulator; never add `iosX64` or fat simulator framework tasks.
> 2. **Import-only `XCTAssertTrue(true)` smoke tests are rejected:** Every Swift test harness must instantiate exported types, call real methods, and assert concrete behavioral outcomes with positive executed test counts.
> 3. **No automatic PR-triggered CI:** The top-level workflow (`ci.yml`) uses manual `workflow_dispatch`, reusable platform workflows (`swift.yml`, `macos.yml`, `ios.yml`, `linux.yml`, etc.) use `workflow_call`, and `publish.yml` uses `release` (`published` / `released`) plus `workflow_dispatch` (`AGENTS.md` §7) — none use `push` or `pull_request` triggers.
> 4. **Never hide public generic APIs or claim "no API change" when erasing return types to `Iterator<Pair<A, B>>`:** De-generify into concrete named types/records (`V2_SWIFT.md` §6), and never leak `kotlin.Pair` on public surfaces.
> 5. **API-preserving `KotlinError.wrapped` (`32f6c1ac` diagnostic & `e1b871c` published `io.github.kotlinmania:swift-export-embeddable:2.4.20-km.1` + `KotlinBase.h` `99186d1d`) is the validated mixed-mode reference:** Accepting removal of `KotlinError.wrapped` (the historical `11da4d91` snapshot candidate) is **not** mandatory. All three upstream producer releases (`io.github.kotlinmania:swift-export-embeddable:2.4.20-km.1`, `io.github.kotlinmania:proc-macro2-kotlin:0.1.7`, and `io.github.kotlinmania:syn-kotlin:0.4.0`) are now **PUBLISHED and independently verified on public Maven Central**; downstream repositories may adopt them **only along legitimate project dependency and tool edges** and must still pass their own unchanged behavioral acceptance. Always read [`template/V2_SWIFT.md`](file:///Volumes/stuff/Projects/kotlinmania/template/V2_SWIFT.md) first for current normative recipes.

## TL;DR (Historical Initial Rollout — See Note Above)

A workspace-wide rollout already produced a local branch named
`automation/swift-export-rollout` with a single canonical commit (the
"rollout commit") that wires the Kotlin → Swift Export → SPM → swift-test
loop into the repository. **Your job, Claude, is to:**

1. **Push** the local `automation/swift-export-rollout` branch to `origin`.
2. **Open a pull request** against the base branch the rollout was made
   from (NOT necessarily `main`), using the title and body template in
   [§ PR template](#pr-template) below.
3. **Verify** the PR landed cleanly *(Historical note: CI workflows now use manual `workflow_dispatch` per `AGENTS.md` §7 and do not auto-trigger on PR creation)*.
4. **Comment on the assigned issue** linking the new PR and close the
   issue with a one-line summary.

Do **NOT** modify any source files. Do **NOT** rebase, squash, or amend
the rollout commit. Do **NOT** change the branch base. If anything in
the precondition checks fails, [stop and report](#what-to-do-when-the-recipe-can-not-be-followed)
rather than improvising.

---

## Background — what's already on this branch

The rollout commit applies a strict, idempotent set of five changes
that are byte-identical across every KotlinMania repository it touched.
These were produced by `automation-artifacts/swift-export-rollout/apply.sh`
in the workspace, mirroring the canonical pattern that landed in
[`libc-kotlin`](https://github.com/KotlinMania/libc-kotlin) and was
proven end-to-end with a working `swift test` invocation against the
`embedSwiftExportForXcode`-produced Swift Package Manager package.

### The five changes (Historical Initial Rollout)

1. **`build.gradle.kts`** — **[HISTORICAL / `iosX64` RETIRED]** In the initial May 2026 rollout, both iOS Simulator framework binaries (`iosSimulatorArm64` and the now-retired `iosX64`) were set to `isStatic = true`. **Current rule (`AGENTS.md` §5.5):** `iosX64` (along with all Apple `x86_64` and 32-bit targets) is **retired workspace-wide** and must never be configured. Only `iosSimulatorArm64` (`isStatic = true`) remains for iOS Simulator.

   > **Historical context only (retired `iosX64`):** When `iosX64` still existed, mixing static `iosSimulatorArm64` and dynamic `iosX64` failed `assembleDebugIosSimulatorFatFrameworkFor<Name>XCFramework` with:
   > ```
   > Cannot create a fat framework from:
   >   <Name> - arm64 - static
   >   <Name> - x64 - dynamic
   >   All input frameworks must be either static or dynamic
   > ```
   > Because `iosX64` is now removed across the workspace, only `iosSimulatorArm64` is configured (`isStatic = true`), while `iosArm64`, `tvosArm64`, `tvosSimulatorArm64`, `watchosDeviceArm64`, `watchosSimulatorArm64`, and `macosArm64` stay dynamic.

2. **`.github/workflows/swift.yml`** — a new platform workflow with a
   `workflow_call:` trigger, matching the shape of the existing
   `macos.yml` / `ios.yml` / `tvos.yml` / etc. workflows. It:
     - Sets every Xcode-style environment variable that the Kotlin
       Multiplatform plugin's `BuildSPMSwiftExportPackage` task reads
       as input. The list includes `BUILT_PRODUCTS_DIR`,
       `TARGET_BUILD_DIR`, `SDK_NAME=macosx`, `CONFIGURATION=Debug`,
       `ARCHS=arm64`, `FRAMEWORKS_FOLDER_PATH=Frameworks`,
       `MACOSX_DEPLOYMENT_TARGET=14.0`, and crucially
       `DEPLOYMENT_TARGET_SETTING_NAME=MACOSX_DEPLOYMENT_TARGET` —
       which Xcode normally injects per-target but the Kotlin plugin
       has no default for. Without it,
       `BuildSPMSwiftExportPackage` fails its property validation
       with `property 'deploymentTargetSettingName' doesn't have a
       configured value`.
     - Runs `./gradlew embedSwiftExportForXcode --no-configuration-cache`
       to populate `build/swift-test/` with a `lib<Module>.a` static
       archive containing every `@ExportedBridge`-annotated function
       generated from the upstream Kotlin source, plus four
       `.swiftmodule/` bundles named `<Module>`, `KotlinRuntimeSupport`,
       `ExportedKotlinPackages`, and `KotlinRuntime`.
     - Changes directory into `src/swift/swift-test-harness/` and runs
       `swift test`, which compiles and runs the smoke test against
       the just-produced Swift module.
     - Uploads the static archive, swiftmodule bundles, and the full
       generated SPM package tree as a `swift-export-artifacts` build
       artifact on both success and failure so a broken run is
       debuggable from the GitHub Actions run summary.

3. **`.github/workflows/ci.yml`** — a four-line `swift:` job is wired
   in next to the existing `wasm:` and `js:` orchestrator entries with
   the same `permissions: contents: read` ceiling. This makes the
   `swift.yml` workflow run as part of the regular CI orchestration.

4. **`src/swift/swift-test-harness/`** — a new top-level directory containing:
     - `Package.swift` — a Swift Package Manager package depending on
       the Kotlin-generated SPM at
       `../build/SPMPackage/macosArm64/Debug` with one `testTarget`
       (`SwiftTestHarnessTests`). The testTarget carries a
       `linkerSettings` block with
       `unsafeFlags(["-L", "../build/swift-test", "-l<Module>"])` so
       that `swift test` (run outside an `xcodebuild`-driven
       invocation) can resolve the `__root____*` and KotlinError
       symbols from the static archive that `embedSwiftExportForXcode`
       drops next to the Sources/ tree. This linker hint is necessary
       because the Kotlin plugin's generated `Package.swift` does NOT
       reference its own `lib<Module>.a` as a binary target (one of
       the two upstream gaps noted in [§ Known upstream gaps](#known-upstream-gaps)
       below).
     - `Tests/SwiftTestHarnessTests/<Module>ExportTests.swift` — **[HISTORICAL SCAFFOLD — SUPERSEDED]** The initial rollout generated a placeholder smoke test that did `import <Module>` and asserted a single `XCTAssertTrue(true, ...)`. **Current normative rule:** An import-only `XCTAssertTrue(true)` test is **never accepted** as proof that Swift Export works (see [§ Annotation hiding is not an API repair](#annotation-hiding-is-not-an-api-repair) and [`template/V2_SWIFT.md` §1](file:///Volumes/stuff/Projects/kotlinmania/template/V2_SWIFT.md#overview-and-core-invariants)). Every repository's Swift test suite must instantiate primary exported types, call representative methods, and assert concrete behavioral results with positive executed test counts.
     - `.gitignore` — ignores the SPM `.build/` output directory.

5. **`.gitignore`** — appended with a Swift/Xcode workspace state
   section: `.swiftpm/`, `DerivedData/`, `*.xcuserstate`,
   `*.xcworkspace/xcuserdata/`, `*.xcodeproj/xcuserdata/`. The
   Kotlin Swift Export staging directory `build/swift-test/` is
   already covered by the existing `build/` rule.

### Background — what Swift Export does

Kotlin 2.2+ ships an experimental Swift Export feature
(`kotlin.experimental.swift-export.enabled`) that emits idiomatic
Swift bindings for a Kotlin Multiplatform module's exported API.
Instead of consuming the Kotlin output through Objective-C interop
(the legacy approach that produces `KotlinInt`, mangled names, and
`underscore_prefixed_calls`), Swift consumers get a real
`.swiftmodule` they can `import <Module>` against. Kotlin typealiases
are exported too; generic type parameters are erased to their upper
bounds rather than preserved as concrete Swift types.

The KotlinMania workspace standardized on Swift Export across every
repository that ships an XCFramework so that downstream Swift / Xcode
consumers don't have to opt into the legacy interop. The rollout
this recipe describes is the mechanical part — wiring up the build,
the test harness, and the CI workflow.

### Direct-export contracts and generated evidence

Use the [official direct Swift export guide](https://kotlinlang.org/docs/native-swift-export.html)
to assess the source API, then read the generated Swift declarations and
Kotlin bridges to verify what the installed toolchain actually exports.
Emission alone does not prove that an API satisfies the documented contract.

- Ordinary exported classes must be final and directly inherit from
  `Any`. The guide separately documents sealed hierarchies and
  cross-language inheritance through a Kotlin interface plus an open
  Kotlin base; those are not general permission to export exception
  subclasses.
- Generic parameters erase to their upper bounds, generic types are
  generally unsupported, and operator support is limited. A Kotlin
  `SynResult<T>` whose generated `getOrThrow()` returns
  `(any KotlinRuntimeSupport._KotlinBridgeable)?` is not a concrete,
  typed Swift result.
- `Unit` maps to `Void`, and constructors map to initializers. Diagnose
  generated conversion warnings separately from source API violations.
- Declarations requiring opt-in need the explicit module compiler
  `optIn` required by the guide. Opting in does not repair unrelated
  unchecked casts or redundant expressions.

For diagnostic APIs, follow
[the concrete SynError repair and verification rules](#diagnostic-apis-repair-the-exported-types-not-their-aliases)
below. A new name alone does not remove inherited or erased Kotlin APIs.

---

## Preconditions to verify before acting

Run these checks in order. If any of them fails, stop and report —
**do NOT attempt to fix the repository state**; the workspace-level
rollout will be re-run instead.

### 1. The rollout branch exists locally

```sh
git rev-parse --verify automation/swift-export-rollout
```

Must print a commit SHA. If this errors with "unknown revision", the
rollout never landed in this repo and there's nothing to push.

### 2. The rollout commit is the tip of the branch

```sh
git log -1 --format='%s' automation/swift-export-rollout
```

Must print exactly:

```
Wire Kotlin → Swift Export → swift test loop into CI
```

If the tip commit's subject is something else, someone has amended,
rebased, or added commits on top — stop and report.

### 3. The rollout files are present at the tip

```sh
git show --stat automation/swift-export-rollout
```

Must list at minimum:

- `build.gradle.kts` modified (1 insertion if multi-line iosSimulatorArm64 form, 4 insertions and 1 deletion if single-line form)
- `.github/workflows/swift.yml` new
- `.github/workflows/ci.yml` modified (4-line addition)
- `src/swift/swift-test-harness/Package.swift` new
- `src/swift/swift-test-harness/.gitignore` new
- `src/swift/swift-test-harness/Tests/SwiftTestHarnessTests/<Module>ExportTests.swift` new
- `.gitignore` modified

It may also include this `SWIFT_EXPORT_ROLLOUT.md` file at the repo
root — that's the recipe being committed alongside the rollout.

If any of the seven (or eight, counting this recipe) files are missing
from the diff, stop and report.

### 4. The base branch is known

The rollout branch was created off the repo's HEAD at the time of the
rollout — that's the **base branch**, and that's where the PR should
target. Determine it by walking back through the local reflog:

```sh
git reflog show automation/swift-export-rollout | tail -5
```

The earliest entry's "from" branch is the base. In most repos this
will be `main`. In a small handful it will be a feature branch like
`setup-ci`, `automation/kotlinmania-ci-h14-4`, or
`port/drop-bignum-add-build-gate` (these are repos that had
in-progress work at rollout time; the rollout was branched off the
feature branch to keep scopes clean).

If you cannot determine the base from the reflog, default to `main`
**but** flag this in your final summary so the human reviewer can
verify.

### 5. Origin remote exists and points at GitHub

```sh
git remote get-url origin
```

Must be a `github.com/KotlinMania/<repo>` URL (HTTPS or SSH).

---

## The work

### Step 1 — push the rollout branch

```sh
git push -u origin automation/swift-export-rollout
```

Expected outcome: the remote ref is created (or fast-forwarded if
it already existed from a previous attempt). If the push is rejected
because the remote ref exists and has diverged, **do not force-push**;
stop and report.

### Step 2 — open the pull request

Use `gh pr create` with the title and body from
[§ PR template](#pr-template) below. The base branch is the one you
determined in precondition #4.

```sh
gh pr create \
    --base "<base-branch>" \
    --head "automation/swift-export-rollout" \
    --title "Wire Kotlin → Swift Export → swift test loop into CI" \
    --body-file <(cat <<'BODY'
... see § PR template ...
BODY
)
```

Capture the returned PR URL — you'll need it for the issue comment.

### Step 3 — verify PR state (Historical Note: CI is now manual `workflow_dispatch`)

> **Historical note (`AGENTS.md` §7):** At the time of the initial rollout, some repositories still had PR-triggered checks. Across KotlinMania, the top-level workflow (`ci.yml`) now uses manual `workflow_dispatch`, reusable platform workflows (`swift.yml`, `macos.yml`, `ios.yml`, `linux.yml`, etc.) use `workflow_call`, and `publish.yml` uses `release` (`published` / `released`) plus `workflow_dispatch` — none run automatically on `push` or `pull_request`. Never add `push` or `pull_request` triggers; trigger CI deliberately via `gh workflow run ci.yml --ref <branch>` only after all local build and test gates pass.

```sh
gh pr view <pr-number> --json statusCheckRollup
```

If the field is empty, that is expected under the workspace-wide `workflow_dispatch`-only CI policy.

### Step 4 — close the assigned issue

Post a comment on the assigned issue (the one that asked you to do
this work) with the format below, then close the issue.

```
Pushed `automation/swift-export-rollout` and opened PR:
<pr-url>

PR base: <base-branch>
CI status: <queued | in_progress | n/a — no PR triggers on base>
Rollout commit: <commit-sha>

Done.
```

---

## PR template

### Title

```
Wire Kotlin → Swift Export → swift test loop into CI
```

### Body

```markdown
## Summary

This PR wires the Kotlin → Swift Export → SPM → `swift test` loop into
this repository's CI, mirroring the canonical pattern established by
[libc-kotlin](https://github.com/KotlinMania/libc-kotlin) and proven
end-to-end with a passing `swift test` invocation against the
`embedSwiftExportForXcode`-produced Swift Package Manager package.

The full recipe for the rollout — including the rationale for each
change, the two upstream Kotlin Multiplatform plugin gaps it works
around, and the precondition / verification checks the Claude agent
ran before opening this PR — is checked in at the repo root in
[`SWIFT.md` (§ The five changes)](#the-five-changes-historical-initial-rollout)
*(historically named `SWIFT_EXPORT_ROLLOUT.md` before workspace consolidation)*.

## What this PR changes

- `build.gradle.kts` — `iosSimulatorArm64` framework binary now has
  `isStatic = true` so the Swift Export SPM bridge can link against
  it.
- `.github/workflows/swift.yml` (new) — `workflow_call:` platform
  workflow that sets the full Xcode-style environment, runs
  `./gradlew embedSwiftExportForXcode`, then runs `swift test` from
  `src/swift/swift-test-harness/`. Uploads the static archive and `.swiftmodule`
  bundles as artifacts on success-or-failure.
- `.github/workflows/ci.yml` — adds the `swift:` job alongside
  `wasm:` and `js:`.
- `src/swift/swift-test-harness/` (new) — a Swift Package Manager package with
  one smoke test that asserts the Swift module imports cleanly.
- `.gitignore` — Swift/SPM workspace state section.

## Test plan

- [ ] The `Build (Swift)` job in CI lands BUILD SUCCESSFUL.
- [ ] The `Run swift test against Kotlin-exported module` step prints
      1 test executed, 0 failures.
- [ ] The uploaded `swift-export-artifacts` archive contains
      `lib<Module>.a` and the four `.swiftmodule/` directories.

## Known upstream gaps

Two issues live in the upstream Kotlin Multiplatform plugin and are
worked around per-repo in this PR. Both would, if fixed upstream,
allow every Kotlin Swift Export consumer to drop the local workaround
at once:

1. **`Package.swift` doesn't reference its own static archive.** The
   Kotlin plugin emits Swift source files for the SPM package but
   does not declare `lib<Module>.a` as a binary target or expose its
   link path. `src/swift/swift-test-harness/Package.swift` compensates with
   `unsafeFlags(["-L", "../build/swift-test", "-l<Module>"])`.
2. **`BuildSPMSwiftExportPackage` validates
   `deploymentTargetSettingName` as a non-optional input** but
   provides no default. Outside Xcode the env var
   `DEPLOYMENT_TARGET_SETTING_NAME` isn't set, so the task fails
   property validation. `swift.yml` sets it explicitly.

🤖 Opened by the Claude GitHub Agent following
[`SWIFT.md` (§ The five changes)](#the-five-changes-historical-initial-rollout)
*(historically `SWIFT_EXPORT_ROLLOUT.md`)*.
```

---

## What to do when the recipe can NOT be followed

If any precondition check fails, or if any step errors out in a way
not anticipated by the recipe, **do NOT improvise**. Specifically:

- Do NOT force-push.
- Do NOT amend, rebase, or squash the rollout commit.
- Do NOT modify any source files.
- Do NOT change the PR base.
- Do NOT close the issue without a PR link, unless the rollout truly
  doesn't apply to this repo (e.g., no XCFramework declaration, no
  Apple targets).

Instead, post a comment on the assigned issue with the format:

```
Cannot complete rollout — <one-line summary>.

What I observed:
<copy/paste the relevant `git`, `gh`, or shell output>

What this implies:
<short interpretation>

Leaving the issue open for a human to follow up. The local
`automation/swift-export-rollout` branch (if it exists) has not
been modified.
```

Then stop. A human will pick it up.

---

## Known upstream gaps

1. **Kotlin-plugin-generated `Package.swift` doesn't reference its
   static archive.** Workaround in `src/swift/swift-test-harness/Package.swift`:
   `unsafeFlags(["-L", "../build/swift-test", "-l<Module>"])`.

2. **`BuildSPMSwiftExportPackage.deploymentTargetSettingName` has no
   default.** Workaround in `.github/workflows/swift.yml`:
   `DEPLOYMENT_TARGET_SETTING_NAME=MACOSX_DEPLOYMENT_TARGET` in the
   env block for the macOS build.

3. **`KotlinStdlib.kt` unchecked-cast bridge** (the big one). When a
   declaration that reaches the Swift boundary exposes
   `kotlin.Result<T>` or `kotlin.Throwable` / `kotlin.Exception`, the
   Kotlin plugin generates
   `build/SwiftExport/<target>/<config>/files/KotlinStdlib/KotlinStdlib.kt`
   containing `Any?` → `Array<Any?>` unchecked casts. Under the
   workspace-canonical `allWarningsAsErrors.set(true)` these become
   compile errors.

   **First-choice fix:** keep the Apple-facing public API usable. Replace
   `kotlin.Result<T>` / `Throwable` surfaces with Swift-exportable outcome
   and error types, or keep the Swift-hostile implementation internal and
   expose a concrete facade.

   ```kotlin
   package io.github.kotlinmania.example

   public sealed class ParseOutcome {
       public data class Ok(public val value: Value) : ParseOutcome()
       public data class Err(public val error: ParseError) : ParseOutcome()
   }

   public data class ParseError(public val message: String)

   public class Parser {
       public fun tryParse(input: String): ParseOutcome =
           when (val parsed = parseValue(input)) {
               is InternalParseResult.Ok -> ParseOutcome.Ok(parsed.value)
               is InternalParseResult.Err -> ParseOutcome.Err(ParseError(parsed.message))
           }
   }
   ```

   `@HiddenFromObjC` is not a fix. It only removes the declaration from
   the Objective-C / Swift framework surface; Kotlin callers still see it,
   but Apple consumers do not. Repair the public API by renaming colliding
   declarations and replacing Swift-hostile shapes with strongly-typed,
   bridgeable Kotlin declarations. See
   [§ The four hazard classes (what makes Swift Export fail under `-Werror`)](#the-four-hazard-classes-what-makes-swift-export-fail-under--werror)
   *(into which the historical `triage-kotlin-stdlib-in-public-api.md` notes were merged)*
   for the workspace-wide taxonomy.

4. **Swift Export does NOT preserve Kotlin sealed-subclass casts.**
   Discovered empirically while making http-kotlin's `swift test`
   actually link. A `sealed class Foo { class Ok(...) : Foo(); class
   Err(...) : Foo() }` does not expose `Http.FooOk` / `Http.FooErr`
   as Swift types that consumers can reach with `as? Http.FooOk`.
   The Swift consumer can `import` `Foo` but cannot pattern-match on
   its sealed subclasses. **Use a flat class with nullable fields
   and boolean predicates instead** — see "Recipe for replacing
   `kotlin.Result<T>` in a public API" below.

5. **Swift accesses Kotlin companion objects as `Type.Companion.shared.member`**,
   not `Type.companion.member`. Common gotcha when writing the smoke
   test in `src/swift/swift-test-harness/`. The Swift binding generated by the
   plugin uses a capital `Companion` struct type with a `shared`
   singleton accessor, not a lowercase `companion` property.

6. **Mutable generic collections in public APIs crash the bridging code.**
   `MutableList<T>` / `MutableSet<T>` / `MutableMap<K, V>` as public
   property types or return types cause the Swift Export compiler
   plugin to crash while emitting Swift-to-Kotlin bridging code,
   typically with type mismatch errors between `MutableList<Any?>`
   and `MutableList<T>`. The plugin's generic erasure to upper bound
   (`Any?`) collides with the mutability covariance constraints.
   **Per-repo workaround**: change public properties and return types
   to their immutable equivalents (`List<T>`, `Set<T>`, `Map<K, V>`).
   Where mutability is genuinely needed internally, hide the mutable
   collection with `internal` and expose the read-only view publicly.

7. **`macos-latest` ships an SDK older than the Kotlin/Native platform
   caches reference.** The Kotlin/Native 2.3.x distribution embeds
   prebuilt caches —
   `liborg.jetbrains.kotlin.native.platform.{CoreFoundation,Foundation,darwin}-cache.a`
   — that reference macOS 26 / iOS 26 SDK symbols
   (`_NSCalendarIdentifier{Vikram,Bangla,Dangi,Gujarati,…}`,
   `_NSURLUbiquitousItem{IsSyncPaused,SupportedSyncControls}Key`,
   `_mach_vm_reclaim_update_kernel_accounting_trap`,
   `_xpc_{connection,session,listener}_set_peer_requirement`,
   `_kCF{Bangla,Dangi,…}Calendar`). On GitHub Actions, `macos-latest`
   currently resolves to `macos-15` with Xcode 16 / macOS 15 SDK, so
   `swift test` link fails with "Undefined symbols for architecture
   arm64" on every symbol the cache references but the SDK doesn't
   carry. A consumer whose Kotlin code never reaches into those APIs
   may not notice (libc-kotlin happens to be in this lucky bucket),
   but any consumer that does (schemars-kotlin et al.) link-fails.
   **Workaround**: in `.github/workflows/swift.yml` set
   `runs-on: macos-26`. The macos-26 image's default Xcode is in the
   26.x line (carries 26.0.1 / 26.1.1 / 26.2 / 26.3 / 26.4.1 / 26.5;
   26.4.1 is the image default at the time of writing) and supplies
   the SDK stubs the platform cache expects.

   > **Do NOT add a `maxim-lobanov/setup-xcode@v1` step.** Earlier
   > revisions of this recipe pinned the Xcode version explicitly via
   > that third-party action. KotlinMania's enterprise GitHub Actions
   > policy does not whitelist third-party actions, and any workflow
   > that references one returns `startup_failure` at dispatch time
   > with zero jobs spawned — confirmed across two consecutive
   > dispatches on itertools-kotlin/automation/swift-export-rollout
   > (runs `26326086457`, `26326121639`). The pin step was redundant
   > anyway: `swift --version` from a working run on macos-26 prints
   > `Target: arm64-apple-macosx26.0` and `Xcode 26.4.1` straight from
   > the image default. Keep `runs-on: macos-26`; omit the pin step.

These first seven gaps are filable upstream against the Kotlin Multiplatform plugin
(or, for #7, against the Kotlin/Native distribution's platform-cache
build pipeline). When they're fixed, the workarounds in this recipe
become deletable.

8. **The Kotlin Swift Export bridge file casts `Any` / `Any?` to typed
   Kotlin values under `allWarningsAsErrors=true`.** The plugin-generated
   bridge at `build/SwiftExport/<target>/<config>/files/<Module>/<Module>.kt`
   pushes every typed Kotlin value through an untyped `Any` channel
   and then casts back to the concrete type on the way out. Under the
   workspace-canonical `allWarningsAsErrors.set(true)` every such cast
   trips `Unchecked cast` and `compileSwiftExportMainKotlin<Target>`
   fails. There are **two distinct API shapes** that provoke this; the
   bridge file looks similar in both cases but the source-side fix is
   different.

   **Trigger 8a — unconstrained generic public classes.** When a
   public Kotlin API exposes a generic class whose type parameter is
   unconstrained (`WithPosition<T>`, `ZipEq<L, R>`, `Unfold<St, T>`,
   etc.), the plugin erases `T` to `kotlin.Any?` in the bridge and
   emits:

   ```kotlin
   val ____self = dereferenceExternalRCRef(self) as io.github.kotlinmania.<pkg>.ZipEq<kotlin.Any?, kotlin.Any?>
   // w: Unchecked cast: 'Any?' to 'ZipEq<Any?, Any?>'.
   ```

   Same shape as gap #3 but for generic public types rather than
   `kotlin.Result`. The flat-class workaround in [§ Recipe for replacing
   `kotlin.Result<T>` in a public API](#recipe-for-replacing-kotlinresultt-in-a-public-api)
   does NOT apply — these are iterator/wrapper types that cannot be
   flattened without unfaithful translation.

   *Per-repo fix (HISTORICAL — see warning below):* stop exposing the
   unconstrained generic classes. Swift Export only emits bridge code for
   symbols visible in the public API surface — internal types are skipped,
   so no `as <UserClass><Any?>` cast gets generated for them. The historical
   pattern from itertools-kotlin commit `40513a5`:

   > [!WARNING]
   > **Historical instruction — do NOT hide public upstream Rust types or expose `Pair<A, B>` claiming "no API change":**
   > 1. Hiding a public upstream Rust struct (such as `pub struct ZipEq<I, J>`) as `internal class` and widening the return type to `Iterator<T>` removes the concrete public type and any type-specific methods from downstream consumers (violating [§ Annotation hiding is not an API repair](#annotation-hiding-is-not-an-api-repair)). Only mark a declaration `internal` when the upstream Rust item is genuinely crate-private (`pub(crate)` / private, as verified for `IsolateAnnex.slots` in `v8-kotlin`).
   > 2. Returning `Iterator<Pair<A, B>>` on a public API surface introduces `kotlin.Pair<A, B>`, which itself triggers the `KotlinStdlib` `Unchecked cast of 'Any?' to 'Pair<Any?, Any?>'` failure under `-Werror` (see [§ The four hazard classes](#the-four-hazard-classes-what-makes-swift-export-fail-under--werror)). Use a concrete named record class instead of `Pair<A, B>`.

   - Mark genuinely crate-internal unconstrained generic iterator/wrapper classes
     `internal class` (the class itself, its companion factories, and
     any nested sealed subclasses).
   - Public factory functions keep their generic type parameters but
     declare their return type as a stdlib interface
     (`Iterator<T>`, `Sequence<T>`, `Iterable<T>`, `List<T>`, etc.)
     instead of the concrete internal class **(note: this is an explicit public return-type change, not "no API change")**.
   - Tests in `commonTest` of the same module retain visibility of the
     internal classes via Kotlin's module-private rules, so
     type-specific assertions (`sizeHint`, internal `state`, `putBack`,
     etc.) keep working by constructing the internal class directly.
     Iteration-shape assertions can continue to use the public
     factory.

   **Trigger 8b — Kotlin function types in public API positions.** A
   public field, parameter, or return type of `() -> R` / `(A) -> R` /
   `(A, B) -> R` triggers the same `Unchecked cast` shape, even when
   the *enclosing* class has no generic parameter. The Kotlin source
   has no `<T>` in sight, but Kotlin function types compile to
   `Function0<R>` / `Function1<A, R>` / ... under the hood — same
   erasure surface, same untyped bridge channel. The bridge emits:

   ```kotlin
   val ____self = dereferenceExternalRCRef(self) as kotlin.Function0<kotlin.Long>
   // w: Unchecked cast of 'Any' to '() -> Long'.
   ```

   Live in-the-wild example from tree-sitter-language-kotlin CI on
   2026-05-23 (`compileSwiftExportMainKotlinMacosArm64` failed on
   `build/SwiftExport/macosArm64/Debug/files/TreeSitterLanguage/TreeSitterLanguage.kt:12:37`
   with `Unchecked cast of 'Any' to '() -> Long'.`):

   ```kotlin
   // Source (Language.kt) — no visible generic, but () -> Long erases
   class LanguageFn private constructor(private val raw: () -> Long) {
       companion object { fun fromRaw(f: () -> Long): LanguageFn = LanguageFn(f) }
       fun intoRaw(): () -> Long = raw
   }
   ```

   *Per-repo fix:* replace the public function-type with a named
   `fun interface` (single-abstract-method interface). SAM conversion
   lets every existing lambda call site stay unchanged
   (`LanguageFn.fromRaw { 42L }` still compiles), but the bridge sees a
   stable nominal type instead of an erased `FunctionN<...>`. The
   pattern:

   ```kotlin
   // Named SAM interface — bridge-friendly, lambda-friendly
   fun interface LanguageProvider {
       fun call(): Long
   }

   class LanguageFn private constructor(private val raw: LanguageProvider) {
       companion object {
           fun fromRaw(f: LanguageProvider): LanguageFn = LanguageFn(f)
       }
       fun intoRaw(): LanguageProvider = raw
   }
   ```

   `fun interface` is the right knob — not a plain `interface` (loses
   SAM conversion at call sites) and not `Function0<Long>` (same
   erasure problem in the bridge). If the function-type value is
   internal-only, marking the field `internal` is also valid.

   **Primary solution when Kotlin compatibility must stay public.** Keep
   the public Kotlin API source-compatible by changing the surface to
   bridgeable nominal types, not by hiding declarations. For trigger 8a,
   public factories keep their Kotlin generic parameters but return
   stdlib interfaces (`Iterator<T>`, `Sequence<T>`, `Iterable<T>`,
   `List<T>`, etc.) while the concrete implementation class becomes
   internal. For trigger 8b, replace public function-type positions with
   named `fun interface` SAMs so existing lambda call sites still compile
   and Swift Export sees a stable type name. For Swift and Java emitted
   name collisions, rename the Kotlin declaration or file itself and
   migrate callers; do not preserve the old colliding spelling with a
   typealias or platform naming annotation. The compatibility path is a
   real bridgeable API, not an annotation tag.

   **DO NOT** scope `allWarningsAsErrors=false` to the
   `compileSwiftExportMain*` task family for either trigger. That was
   tried first (itertools-kotlin PR #22 for trigger 8a) and is symptom
   suppression: it leaves the unchecked casts in the bridge and only
   mutes the compiler. The `tasks.matching { ... }.withType<KotlinCompilationTask<*>>()`
   block (and any `KotlinCompilationTask` import that accompanies it)
   should be deleted whenever found.

   With either fix in place, `allWarningsAsErrors=true` applies
   uniformly. The bridge surface shrinks accordingly — in itertools-kotlin
   it went from ~20 `@ExportedBridge` functions and 12 generic
   `@file:BindClassToObjCName(... :: class, ...)` annotations down to
   6 `@ExportedBridge` functions and zero generic class bindings,
   leaving only the genuinely-public concrete types (e.g. `Position`)
   bridged to Swift. itertools-kotlin commit `40513a5` is the canonical
   reference for trigger 8a; tree-sitter-language-kotlin (the
   `LanguageFn` / `LanguageProvider` rewrite) is the canonical reference
   for trigger 8b. tree-sitter-kotlin's batch SAM-ification of seven
   public function-type surfaces (preemptive, not reactive — done from
   the bridge-clean source before CI surfaced the warnings) is the
   canonical reference for the audit-and-sweep pattern below.

   *Audit grep (run once per repo after a fresh `embedSwiftExportForXcode`):*

   ```sh
   # Combined audit — both triggers, both shapes the bridge writes
   grep -nE "as kotlin\\.Function[0-9]+<|as io\\.github\\.kotlinmania\\.[^<]+<kotlin\\.Any" \
       build/SwiftExport/macosArm64/Debug/files/*/*.kt
   ```

   Every match in a `kotlin.FunctionN<...>` is trigger 8b (function
   type in public surface); every match in
   `io.github.kotlinmania.<pkg>.<Type><kotlin.Any` is trigger 8a
   (unconstrained generic). Both are fixable per-repo via the
   recipes below. No matches rule out only those searched patterns;
   still check the documented source contracts and the complete
   generated compilation diagnostics.

9. **Audit coroutine export against the installed toolchain and strict
   generated compilation.** The historical `crossterm-kotlin` rollout
   (public `Flow<InternalEvent>` event stream) encountered the following
   configuration, runtime, cache and package issues. Diagnose each from
   actual output; they are not an unconditional four-part recipe.
   One observed symptom was that the generated
   `OrgJetbrainsKotlinxKotlinxCoroutinesCore.swift` references a
   `KotlinCoroutineSupport` module that is neither imported nor emitted
   (`cannot find type 'KotlinCoroutineSupport' in scope`).

   **9a — Verify configuration rather than inferring runtime inclusion
   from a setting name.** In the inspected Kotlin 2.4.20 KGP action,
   module `settings` become `SwiftModuleConfig.experimentalFeatures`;
   the global `SwiftExportConfig` coroutine-support Boolean is separate.
   Removing `settings["enableCoroutinesSupport"] = "true"` from Syn did
   not remove `KotlinCoroutineSupport` from freshly generated output.
   Do not claim that removing an unused module setting fixes runtime
   warnings. Check the resolved plugin, actual configuration and emitted
   module inputs. Use documented configuration supported by that
   installed version; do not invent settings or mutate cached tools.

   **9b — Keep `allWarningsAsErrors=true` for generated bridge and runtime
   compilation. There is no coroutine-runtime exception.** This replaces
   the former recipe that disabled strict warnings on `compileSwiftExport*`.
   A warning in generated source still fails the gate.

   Classify the cause before changing code. Source API shapes can pull
   unsupported stdlib bridges into the output; repair those shapes as
   described under Class Stdlib. The documented explicit compiler opt-in
   resolved Syn's inheritance opt-in diagnostic in the inspected 2.4.20
   run with strict warnings retained. It did not fix the runtime's two
   iterator casts or redundant Elvis expression.

   Concrete constructor bridges also emitted
   `return run { _result; true }` after initialization and produced
   `UNUSED_EXPRESSION`. A nominal callback fixed its separate Function1
   cast without fixing that constructor conversion. Preserve these
   distinctions in the evidence. Verify any generator/runtime repair
   through regeneration and strict compilation; a proposed rewrite or
   successful source compilation is not a completed bridge repair.
   Do not patch generated bridge sources, suppress diagnostics, or edit
   installed compiler/cache JARs to manufacture a passing gate.

   **9c — Disable the Kotlin/Native incremental cache.** Linking the
   Swift Export binary builds a K/N cache for `…_swiftExportMain.klib`,
   and the generated `SwiftFlowIterator` (a `NativePtr` continuation)
   hits an internal compiler error in C-bridge lowering
   (`doesn't correspond to any C type: kotlin.native.internal.NativePtr`,
   in `CBridgeGen.convertBlockPtrToKotlinFunction`). The compiler's own
   message recommends the workaround; set it in `gradle.properties`:

   ```properties
   kotlin.incremental.native=false
   ```

   Correctness-neutral (cache only). Revisit when the K/N
   Swift-Export-plus-coroutines cache path is fixed upstream.

   **9d — Inject a `platforms` declaration into the generated SPM
   `Package.swift`.** The Kotlin-generated `Package.swift` omits a
   `platforms:` clause, so a standalone `swift test` builds it below
   macOS 10.15 and every Swift Concurrency symbol in the coroutine
   bridge (`Task`, `AsyncSequence`, `withUnsafeThrowingContinuation`, …)
   fails with *"is only available in macOS 10.15 or newer."* Setting
   `MACOSX_DEPLOYMENT_TARGET` in the embed env (gap-#7 / the workflow)
   does NOT propagate into the generated manifest. Patch it in the
   `swiftExportSmokeTest` task, between `embedSwiftExportForXcode` and
   `swift test` (`name:` must precede `platforms:` in the initializer):

   ```kotlin
   val generatedPackageSwift =
       layout.buildDirectory.file("SPMPackage/macosArm64/Debug/Package.swift").get().asFile
   if (generatedPackageSwift.exists()) {
       val text = generatedPackageSwift.readText()
       if (!text.contains("platforms:")) {
           generatedPackageSwift.writeText(
               text.replaceFirst(
                   Regex("(name:\\s*\"[^\"]*\",)"),
                   "$1\n    platforms: [.macOS(.v14)],",
               ),
           )
       }
   }
   ```

   Also give `src/swift/swift-test-harness/Package.swift` the same
   `platforms: [.macOS(.v14)]`. If a prior failed run cached a bad
   manifest, clear `src/swift/swift-test-harness/.build` once.

   **Where Flow-exporting symbols live in the generated package.**
   Inspect the emitted SPM targets and dependencies, including
   `KotlinCoroutineSupport` and the exported coroutine library. The
   historical crossterm smoke result under the former warning exception
   does not establish that current strict compilation and tests pass.
   Record fresh results for the exact source, plugin and configuration.

## Recipe for replacing `kotlin.Result<T>` in a public API

The canonical pattern from http-kotlin commits `a179143` and the
follow-up that removed `!!`. Two principles guide the shape:

  * **The class invariant ("exactly one of value/error is non-null")
    is encoded explicitly with `init { require(...) }`.** That makes
    the invariant a real fact the type-checker can rely on after
    construction.
  * **No `!!` and no `@Suppress("UNCHECKED_CAST")`.** Both are
    workspace-forbidden per every repo's `CLAUDE.md`. `!!` is the
    runtime cousin of `@Suppress`: it suppresses the null-safety
    type-check at the cost of silencing a real bug class. The
    `when { value != null -> ...; error != null -> ...; else ->
    error("...") }` form below reads only the field whose
    non-null-ness is guaranteed in that branch, with the unreachable
    `else` as a defensive belt-and-suspenders for invariant
    violations.

```kotlin
/**
 * Result of [SomeApi.someFactoryThatCanFail].
 *
 * Flat-class shape (rather than sealed Ok / Err variants) — the
 * Kotlin Swift Export plugin does not currently emit Swift bindings
 * that let consumers pattern-match on sealed subclasses. A flat
 * class with `isSuccess()` / `isFailure()` predicates and nullable
 * `value` / `error` accessors bridges cleanly to Swift, where the
 * consumer uses `if result.isSuccess() { result.value?.thing() }`.
 *
 * The class invariant — exactly one of [value] / [error] is non-null —
 * is enforced at construction by [init]. That makes the otherwise
 * unreachable branch in [getOrThrow] expressible without `!!`.
 */
class SomeResult internal constructor(
    val value: SomeType?,
    val error: SomeConcreteError?,
) {
    init {
        require((value == null) != (error == null)) {
            "SomeResult must carry exactly one of value or error (got value=$value, error=$error)"
        }
    }

    companion object {
        internal fun ok(value: SomeType) = SomeResult(value, null)
        internal fun err(error: SomeConcreteError) = SomeResult(null, error)
    }

    fun getOrThrow(): SomeType = when {
        value != null -> value
        error != null -> throw SomeException(error)
        else -> error("SomeResult class invariant violated: both value and error are null")
    }

    fun getOrNull(): SomeType? = value

    fun isSuccess(): Boolean = value != null
    fun isFailure(): Boolean = error != null
}
```

### What NOT to do

These patterns look like they work but quietly suppress bugs:

```kotlin
// WRONG: !! suppresses the null check, hiding any future bug where
// the class invariant is violated. The `error` field "should never"
// be null when `value` is null, but the type system can't see that.
fun getOrThrow(): SomeType = value ?: throw SomeException(error!!)

// WRONG: `internal val error` hides the field from the public Swift
// surface to dodge an export problem, but Swift consumers legitimately
// need to inspect the failure. The fix is to make the Swift Export
// boundary handle the type, not to hide the API.
class SomeResult internal constructor(
    val value: SomeType?,
    internal val error: SomeConcreteError?,
)

// WRONG: sealed Ok/Err variants don't survive Swift Export — see
// upstream gap #4 above. Compiles in Kotlin but the Swift consumer
// can't do `as? SomeResultOk`.
sealed class SomeResult {
    class Ok(val value: SomeType) : SomeResult()
    class Err(val error: SomeConcreteError) : SomeResult()
}
```

---

## Recipe for hiding unconstrained generic iterator classes

> [!WARNING]
> **Historical section (`itertools-kotlin` `40513a5`) — do NOT apply as a blanket rule:**
> Only mark a generic helper class `internal` when the upstream Rust declaration in `tmp/` is genuinely crate-private (`pub(crate)` or private), and verify that no public subclass inherits erased generic member bridges. Hiding a public upstream Rust struct (such as `pub struct ZipEq<I, J>`) behind `Iterator<T>` removes its concrete type and type-specific methods from public consumers (violating [§ Annotation hiding is not an API repair](#annotation-hiding-is-not-an-api-repair)), and returning `Iterator<Pair<A, B>>` exposes `kotlin.Pair<A, B>`, which triggers `Unchecked cast of 'Any?' to 'Pair<Any?, Any?>'` in `KotlinStdlib.kt` under `-Werror`. See [`template/V2_SWIFT.md` §6.B](file:///Volumes/stuff/Projects/kotlinmania/template/V2_SWIFT.md#kotlin-vs-swift-defect-taxonomy) for current normative API rules.

The historical pattern from itertools-kotlin commit `40513a5` was written
when a public Kotlin API exposed a generic class whose type
parameter was unconstrained (`class Foo<T>`, `class Bar<L, R>`, etc.)
and the class was reachable through the module's exported surface.

The historical rationale was: Swift
Export only emits bridge code for symbols that are part of the
public API — `internal` types are skipped, so the unchecked
`Any?` casts on that class disappeared when the concrete class was made `internal` (at the cost of changing the public return type).

### Before

```kotlin
package io.github.kotlinmania.itertools

class ZipEq<A, B>(
    private val left: Iterator<A>,
    private val right: Iterator<B>,
) : Iterator<Pair<A, B>> {
    override fun hasNext(): Boolean = TODO()
    override fun next(): Pair<A, B> = TODO()
}

fun <A, B> zipEq(left: Iterator<A>, right: Iterator<B>): ZipEq<A, B> =
    ZipEq(left, right)
```

Plugin-generated bridge file (`build/SwiftExport/.../Itertools.kt`) ends up with:

```kotlin
@ExportedBridge
fun __root___ZipEq_next(self: kotlin.native.internal.NativePtr): kotlin.native.internal.NativePtr {
    val ____self = dereferenceExternalRCRef(self) as io.github.kotlinmania.itertools.ZipEq<kotlin.Any?, kotlin.Any?>
    // ^^^ Unchecked cast: 'Any?' to 'ZipEq<Any?, Any?>'
    ...
}
```

Under `allWarningsAsErrors.set(true)` that warning becomes an error
and `compileSwiftExportMainKotlin<Target>` fails.

### After (Historical `itertools-kotlin` `40513a5` snapshot — see warning)

> [!WARNING]
> **Historical example only — superseded in two respects:**
> 1. **Visibility parity:** Changing a public class (`ZipEq<A, B>`) to `internal class` and widening `zipEq(...)` to return `Iterator<...>` **is a public API change** (callers lose the concrete `ZipEq` type and its non-`Iterator` methods such as `sizeHint()`). Per [§ Annotation hiding is not an API repair](#annotation-hiding-is-not-an-api-repair), do not hide public upstream Rust types merely to silence Swift Export; only scope declarations `internal` when upstream Rust visibility is `pub(crate)` or private.
> 2. **No public `Pair<A, B>`:** Returning `Iterator<Pair<A, B>>` exposes `kotlin.Pair<A, B>` in the public API, which triggers `Unchecked cast of 'Any?' to 'Pair<Any?, Any?>'` in generated `KotlinStdlib.kt` under `-Werror` (see [§ The four hazard classes](#the-four-hazard-classes-what-makes-swift-export-fail-under--werror)). Replace public `Pair<A, B>` with a named record class.

```kotlin
package io.github.kotlinmania.itertools

internal class ZipEq<A, B>(
    private val left: Iterator<A>,
    private val right: Iterator<B>,
) : Iterator<Pair<A, B>> {
    override fun hasNext(): Boolean = TODO()
    override fun next(): Pair<A, B> = TODO()
}

fun <A, B> zipEq(left: Iterator<A>, right: Iterator<B>): Iterator<Pair<A, B>> =
    ZipEq(left, right)
```

In this historical `40513a5` snapshot, making the class `internal` omitted it from the Swift Export bridge and changed the factory's return type from `ZipEq<A, B>` to `Iterator<Pair<A, B>>`. Note that this **does** alter the public API surface (removing concrete `ZipEq` members from external callers) and still leaves `Pair<A, B>` exposed to the stdlib bridge hazard.

### Tests

Tests in `commonTest` of the same module retain visibility of
`internal` classes (Kotlin's module-private rules). When a test
asserts on a type-specific member that isn't part of `Iterator<T>` —
e.g. a custom `sizeHint`, `state`, `putBack`, internal counter —
construct the internal class directly instead of going through the
factory:

```kotlin
// commonTest can still see ZipEq's internal members
@Test
fun zipEqSizeHint() {
    val iter = ZipEq(listOf(1).iterator(), listOf("a", "b").iterator())
    assertEquals(1, iter.sizeHint())
}

// Iteration-shape assertions can still use the public factory
@Test
fun zipEqIterationShape() {
    val seq = zipEq(listOf(1, 2).iterator(), listOf("a", "b").iterator()).asSequence().toList()
    assertEquals(listOf(1 to "a", 2 to "b"), seq)
}
```

### What NOT to do

```kotlin
// WRONG: silencing the warning leaves the unchecked Any? cast in the
// bridge file. The plugin's runtime cast remains and the type-erased
// shape of the bridge stays the same — only the compiler is muted.
// Itertools-kotlin PR #22 tried this and was reverted by PR after.
tasks.matching { it.name.startsWith("compileSwiftExportMain") }
    .withType<KotlinCompilationTask<*>>()
    .configureEach {
        compilerOptions.allWarningsAsErrors.set(false)
    }

// WRONG: making the class `public abstract` and exposing only a
// public interface doesn't help — Swift Export still bridges the
// abstract class and emits the same `Any?` cast.
abstract class ZipEq<A, B> : Iterator<Pair<A, B>>

// WRONG: a `typealias` to a stdlib interface doesn't hide the
// underlying class from Swift Export. The original class still
// participates in bridging.
typealias ZipEqAlias<A, B> = ZipEq<A, B>
```

### Scope check

For each repo running Swift Export, grep the bridge file once after
a fresh `embedSwiftExportForXcode` run:

```sh
grep -nE "as io\\.github\\.kotlinmania\\.<pkg>\\.[A-Z][A-Za-z0-9_]+<kotlin\\.Any" \
    build/SwiftExport/macosArm64/Debug/files/*/*.kt
```

Every match must be evaluated against upstream Rust visibility first: only genuinely crate-private (`pub(crate)` / private) helper classes should be made `internal`, whereas public Rust types must retain their public operations via concrete Swift-bridgeable façades rather than being hidden (see [§ Annotation hiding is not an API repair](#annotation-hiding-is-not-an-api-repair)). If grep returns no results, this trigger (8a) doesn't apply to this repo.

---

## Recipe for hiding Kotlin function types behind a `fun interface`

The canonical pattern from the tree-sitter-language-kotlin
`LanguageFn` / `LanguageProvider` rewrite (2026-05-23). Applies
whenever a public Kotlin API uses a Kotlin function type
(`() -> R`, `(A) -> R`, `(A, B) -> R`, `suspend (A) -> R`, etc.) in a
**public** field, parameter, or return position.

The principle: **named SAM interface, not anonymous function type.**
Swift Export bridges named types as stable nominal symbols; it bridges
function types by erasing them through `Function0<R>` / `Function1<A, R>`
/ ... which collide with the `Any` channel in the bridge. A `fun interface`
gives the bridge a real symbol, and Kotlin's SAM conversion keeps lambda
call sites unchanged.

### Before

```kotlin
package io.github.kotlinmania.treesitterlanguage

class LanguageFn private constructor(private val raw: () -> Long) {
    companion object {
        fun fromRaw(f: () -> Long): LanguageFn = LanguageFn(f)
    }
    fun intoRaw(): () -> Long = raw
}
```

Plugin-generated bridge file (`build/SwiftExport/.../TreeSitterLanguage.kt`)
ends up with:

```kotlin
@ExportedBridge
fun __root___LanguageFn_intoRaw(self: kotlin.native.internal.NativePtr): kotlin.native.internal.NativePtr {
    val ____self = dereferenceExternalRCRef(self) as kotlin.Function0<kotlin.Long>
    // ^^^ Unchecked cast of 'Any' to '() -> Long'
    ...
}
```

Under `allWarningsAsErrors.set(true)` the warning becomes an error
and `compileSwiftExportMainKotlin<Target>` fails.

### After

```kotlin
package io.github.kotlinmania.treesitterlanguage

/**
 * Single-abstract-method interface that stands in for the upstream
 * Rust `unsafe extern "C" fn() -> *const c_void` function pointer.
 * Named so Swift Export can bridge a nominal symbol instead of an
 * erased `FunctionN<...>`.
 */
fun interface LanguageProvider {
    fun call(): Long
}

class LanguageFn private constructor(private val raw: LanguageProvider) {
    companion object {
        fun fromRaw(f: LanguageProvider): LanguageFn = LanguageFn(f)
    }
    fun intoRaw(): LanguageProvider = raw
}
```

`LanguageProvider` is `fun interface`, so Kotlin's SAM conversion still
accepts a lambda at the call site:

```kotlin
val fn = LanguageFn.fromRaw { tree_sitter_language() }
// Equivalent to: LanguageFn.fromRaw(LanguageProvider { tree_sitter_language() })
```

The bridge file's exported function now references `LanguageProvider`
(a nominal symbol Swift Export emits a binding for) instead of the
erased `Function0<Long>`. No `Any` cast.

### After (migration variant: name the SAM method `invoke`)

The recipe above uses `fun call()` as the SAM method, which is right
for new APIs. For repos *migrating* from a public typealias whose
call sites already use call-syntax (`callback(arg)` instead of
`callback.call(arg)`), name the SAM method `operator fun invoke(...)`
so Kotlin's invoke-operator convention keeps every existing call site
working without an edit:

```kotlin
// Before (typealias):
typealias ParseProgressCallback = (currentByteOffset: UInt, hasError: Boolean) -> Boolean

// After (fun interface with invoke):
fun interface ParseProgressCallback {
    operator fun invoke(currentByteOffset: UInt, hasError: Boolean): Boolean
}
```

Every prior caller of `callback(byte, hasError)` continues to compile
unchanged — `callback(...)` is now `callback.invoke(...)` via the
operator convention. `StableRef<ParseProgressCallback>` references in
nativeMain actuals also compile unchanged because the type name is
the same; only its kind changed (typealias → fun interface). Evidence:
tree-sitter-kotlin batch SAM-ification of seven public typealiases
and inline callback parameters, where this naming choice meant the
`jvmMain`, `androidMain`, and `nativeMain` actuals needed zero edits.

### Kotlin compatibility through named SAMs

The `fun interface` rewrite preserves the normal Kotlin call shape:
callers that pass lambdas keep writing the same lambda expression.
Callers that wrote out `(UInt, Boolean) -> Boolean` as an explicit type
must move to the named SAM type. That is the intended compatibility
boundary because the published API is now a bridgeable nominal type
instead of an erased `FunctionN`.

```kotlin
package io.github.kotlinmania.treesitter

fun interface ParseProgressCallback {
    fun invoke(
        byteOffset: UInt,
        hasError: Boolean,
    ): Boolean
}

public class Parser {
    public fun parseWithProgress(callback: ParseProgressCallback): Tree =
        parseInternal { offset, hasError -> callback.invoke(offset, hasError) }
}
```

When most or all of a subpackage is implementation runtime code that
should never reach Swift or Java consumers, make it `internal` and keep
public entry points in a small strongly-typed API layer. Do not ship a
public API and then hide it from one platform.

### Tests

SAM-convertible interfaces work the same as function types in tests
— pass a lambda or an explicit implementation, your choice:

```kotlin
@Test
fun fromRawAcceptsLambda() {
    val fn = LanguageFn.fromRaw { 42L }
    assertEquals(42L, fn.intoRaw().call())
}

@Test
fun fromRawAcceptsExplicitImpl() {
    val provider = object : LanguageProvider {
        override fun call(): Long = 99L
    }
    val fn = LanguageFn.fromRaw(provider)
    assertSame(provider, fn.intoRaw())
}
```

### What NOT to do

```kotlin
// WRONG: regular `interface` loses SAM conversion. Every existing
// `LanguageFn.fromRaw { ... }` call site breaks because Kotlin won't
// auto-wrap the lambda. Use `fun interface` instead.
interface LanguageProvider {
    fun call(): Long
}

// WRONG: switching to Function0<Long> is the same erasure problem.
// The bridge still writes `as kotlin.Function0<kotlin.Long>` because
// Function0 IS the underlying type of `() -> Long`.
class LanguageFn private constructor(private val raw: Function0<Long>)

// WRONG: silencing the warning leaves the unchecked cast in the
// bridge file. The plugin's runtime cast remains and the bridge
// shape is unchanged — only the compiler is muted.
tasks.matching { it.name.startsWith("compileSwiftExportMain") }
    .withType<KotlinCompilationTask<*>>()
    .configureEach {
        compilerOptions.allWarningsAsErrors.set(false)
    }

// WRONG: typealias to a Function type does NOT hide it from the
// bridge. The underlying function type still participates.
typealias LanguageProvider = () -> Long
```

### Scope check

For each repo running Swift Export, grep the bridge file once after
a fresh `embedSwiftExportForXcode` run:

```sh
grep -nE "as kotlin\\.Function[0-9]+<" \
    build/SwiftExport/macosArm64/Debug/files/*/*.kt
```

Every match is a candidate for the `fun interface` treatment. As a
source-side audit (preferred — it also catches surfaces the bridge
hasn't been generated for yet, e.g. new ports or new public
typealiases added to existing ports), grep for public function-type
signatures:

```sh
grep -nE "^(public |internal |private )?(class |object |val |var |fun )[^=]*: \\(.*\\) -> " \
    src/common/commonMain/kotlin/**/*.kt
grep -nE "^(public |internal |private )?(class |object |val |var |fun )[^=]*\\(.* \\(.*\\) -> " \
    src/common/commonMain/kotlin/**/*.kt
grep -rnE "^typealias [A-Z][A-Za-z0-9_]+ = \\(.*\\) -> " \
    src/common/commonMain/kotlin/
```

If all three passes return no results, trigger 8b doesn't apply and
no API change is needed.

### Preemptive sweep (run on every fresh port)

A new repo's public function-type surfaces should be SAM-ified
**before** Swift Export ever runs against them, not after CI surfaces
the warning. The audit greps above are cheap to run from a clean
checkout. The pattern from tree-sitter-kotlin (seven SAMs landed in
a single PR ahead of CI evidence):

1. Run the three source-side greps. Catalog every public
   function-type surface.
2. For each surface, decide:
   - **typealias whose call sites use `callback(arg)` syntax** →
     replace with `fun interface X { operator fun invoke(...) }`
     so call sites compile unchanged.
   - **new SAM, no existing call-syntax constraint** → name the SAM
     method semantically (`fun call()`, `fun read(byte: UInt)`, etc.).
   - **inline `((State) -> Boolean)?` parameter on a builder class**
     → introduce a co-located named SAM and replace the parameter
     type.
   - **subpackage that is internal-by-convention runtime port** →
     make it `internal` and expose only the strongly-typed public entry
     points that Swift and Java consumers should actually use.
3. Compile the three highest-coverage targets (`macosArm64`, `jvm`,
   `androidMain`) locally to confirm the SAM conversion didn't break
   any call site. Lambda call sites at invocation positions
   SAM-convert automatically; `StableRef<TypeName>` references stay
   the same because the type name didn't change, only its kind.

---

## Gaps discovered during the crossterm-kotlin port (2026-05-26)

Three previously undocumented issues surfaced during the crossterm-kotlin
Swift Export rollout that are general enough to hit other repos. All
three produced hard link failures (`ld: symbol(s) not found for
architecture arm64`) rather than compiler warnings, making them
impossible to miss — but also impossible to diagnose from the Swift
side alone.

### 9. Public `internal expect fun` symbols leak into the Swift Export bridge

**Symptom.** `swift test` link fails with `Undefined symbols for
architecture arm64: _io_github_kotlinmania_<pkg>_<symbol>` for a
function that *is* declared `internal` in commonMain but is also the
`actual` implementation of an `expect fun` that was itself `internal`.

**Root cause.** When commonMain declares `internal expect fun foo()` and
a platform source set provides `internal actual fun foo()`, the Kotlin
Swift Export plugin may still generate an `@ExportedBridge` entry for
the symbol because the `expect`/`actual` mechanism creates a public
entry point during metadata compilation even though the original
declaration is `internal`. The macOS native binary then doesn't export
the symbol (it's truly internal), and the bridge reference dangles.

**Fix.** Make the function truly invisible to the bridge by ensuring it
doesn't participate in the exported surface. For utility functions that
only exist as internal implementation details (like `getTtyFd()` which
returns a raw fd for termios calls), the fix was to remove `expect`
entirely and make the function a plain `internal fun` in the platform
source set that needs it. The function was never part of the public API
— it was only `expect` so that multiple platform source sets could share
the signature, but since each platform that calls it already has its
own copy, the `expect` was unnecessary overhead that leaked into the
bridge.

```kotlin
// BEFORE (leaks into bridge):
// commonMain:
internal expect fun getTtyFd(): Int

// posixMain:
internal actual fun getTtyFd(): Int { ... }

// AFTER (no bridge leak, no expect needed):
// posixMain only (no commonMain declaration):
internal fun getTtyFd(): Int { ... }
```

**When to use this fix.** Only for `internal expect fun` declarations
where every platform actual is already in a source set that's a
dependency of the Apple target. If the `expect` exists so that
`otherMain` / `jvmMain` / `wasmJsMain` can provide a no-op or
JVM-specific actual, you still need the `expect`/`actual` pair. For
Apple export, keep it `internal` and expose the public operation through
a strongly-typed bridgeable declaration.

### 10. JVM class name clashes between commonMain and platform source sets

**Symptom.** `compileAndroidMain` (or `jvmMainClasses`) fails with:

```
Duplicate JVM class name 'io/github/kotlinmania/<pkg>/FooKt'
generated from: FooKt, FooKt
```

**Root cause.** Kotlin compiles each source set's top-level functions
into a class named after the source file (`Foo.kt` → `FooKt`). When
`commonMain/src/Foo.kt` defines `expect fun bar()` and
`jvmMain/src/Foo.kt` defines `actual fun bar()`, both compile to
`FooKt.class` on JVM targets. The JVM class loader can't resolve which
one to use.

This is a JVM-only issue — Kotlin/Native and Kotlin/JS don't have this
problem because they don't use the same class-file naming scheme. But
androidMain, jvmMain, and any JVM-based source set all hit it.

**Fix.** Rename the Kotlin file or declaration that emits the duplicate
JVM class. The actual-implementation file gets a descriptive name that
reflects what it contains, not the same name as the commonMain expect
file:

```kotlin
// commonMain/src/AnsiSupport.kt -> defines expect fun enableVtProcessing()
// jvmMain/src/VtProcessing.kt   -> defines actual fun enableVtProcessing()
// androidMain/src/VtProcessing.kt -> defines actual fun enableVtProcessing()
```

The JVM class names become `AnsiSupportKt` (commonMain) and
`VtProcessingKt` (jvmMain + androidMain) — no clash.

Do not use `@file:JvmName` or `@file:JvmMultifileClass` to paper over
the collision. Those annotations change the Java interop surface and
keep the underlying naming mistake in place. The project rule for Java
matches the Swift rule: rename the Kotlin source element that emits the
bad name.

**Prevention.** After porting `expect`/`actual` declarations, check
for JVM class name clashes by compiling `compileAndroidMain` or
`jvmMainClasses` before declaring victory.

### 11. Display name constants (`internal const val`) referenced as
     function calls in test files

**Symptom.** `compileTestKotlin<Platform>` fails with `Unresolved
reference 'keyCodeBackspaceDisplayName'` (or similar).

**Root cause.** When `expect fun keyCodeBackspaceDisplayName(): String`
 declarations across all platform source sets are consolidated into
 `internal const val KEY_CODE_BACKSPACE_DISPLAY_NAME: String = "Backspace"`
 in commonMain (part of reducing the expect surface), the test files
 still reference them as function calls: `keyCodeBackspaceDisplayName()`.
 The `const val` form is not callable — it's a value, not a function.

**Fix.** Update test files to reference the constant directly:

```kotlin
// BEFORE (function call syntax from the expect fun era):
assertEquals(keyCodeBackspaceDisplayName(), KeyCode.Backspace.toString())

// AFTER (constant reference):
assertEquals(KEY_CODE_BACKSPACE_DISPLAY_NAME, KeyCode.Backspace.toString())
```

Since these are `internal` constants in the same module, `commonTest`
can access them. The naming convention stays SCREAMING_SNAKE_CASE for
constants per Kotlin convention.

## Gaps discovered during the build-gate / test-wiring pass (2026-05-30)

Two general defects in how `build`, `check`, and the per-platform CI
workflows wire test *execution* vs. *compilation*. Neither is a Swift
Export bridge bug — they decide whether each platform's `actual`s ever
actually run, including the Swift smoke test.

### 12. The build gate must BUILD every target but must not single out one platform's tests to RUN

**Symptom.** `fullTargetBuildTaskNames` (the all-target build gate) lists
`testAndroidHostTest` — an actual test *run* — alongside the compile/link
tasks, while every other target contributes only `*MainClasses` /
`*TestClasses` / `${target}Binaries` / `${target}TestBinaries` (compile
and link, no execution). So `./gradlew build` *runs* the Android unit
tests but for every other target merely links the `test.kexe` without
ever executing it. The gate looks symmetric but silently tests exactly
one platform.

**Root cause.** A "build must compile every target" contract and "run the
tests" are two different jobs. The build gate is about *compilation
coverage*; test *execution* is host-dependent (you can't run a Linux or
mingw test on a macOS runner, and device / Android-Native targets have no
host-runnable test task at all). Burying one runnable test in the build
set conflates the two.

**Fix.** Keep `fullTargetBuildTaskNames` pure-build (compile + link every
target, including each target's test binary — that proves the test code
*compiles*). Move test *execution* to `check`, where KMP's `allTests`
already runs every host-runnable test (`jvmTest`, `macosArm64Test`, the
Apple simulator tests, `jsNodeTest`, `wasmJsNodeTest`, ...). Add the
Android host test and the Swift smoke test there too:

```kotlin
tasks.named("check") {
    dependsOn(tasks.withType<io.gitlab.arturbosch.detekt.Detekt>())
    dependsOn("ktlintCheck")
    dependsOn("testAndroidHostTest")     // not in the build set
    dependsOn("swiftExportSmokeTest")
}
```

Do **not** try to make tests depend on the `build` lifecycle task to
force this ordering: `build → check → allTests` already exists, so
`anyTest.dependsOn("build")` forms the cycle
`build → check → allTests → build` and Gradle rejects it at
configuration time. Depend on the underlying compile/link tasks (or just
let `check` own execution) instead.

### 13. Every per-platform CI workflow must RUN its platform's test, not just compile it

**Symptom.** A platform's reusable workflow compiles and assembles but
never invokes a test-*run* task, so that platform's `actual`s are never
exercised in CI. Observed: `android.yml` ran
`compileAndroidMain assembleUnitTest assembleAndroidTest` with **no**
`testAndroidHostTest` — Android built green forever while its tests
never ran. (`watchos.yml` separately still listed the retired
`compileKotlinWatchosArm32`, which fails "task not found" once the target
is scrubbed — audit for retired targets in the same pass.)

**Fix.** Each `<platform>.yml` runs the test task that executes on its
runner: `macosArm64Test`, `linuxX64Test`, `mingwX64Test`,
`iosSimulatorArm64Test`, `tvosSimulatorArm64Test`,
`watchosSimulatorArm64Test`, `jsNodeTest`/`jsBrowserTest`,
`wasmJsNodeTest`/`wasmJsBrowserTest`/`wasmWasiNodeTest`,
`testAndroidHostTest`, and `swift test` (swift.yml). Audit rule: a
workflow whose Gradle task list contains only `compile*` / `assemble*`
and no `*Test` run is a platform building-but-not-testing.

**Honest limits (compile-only, by physics — not laziness).**
`androidNative*` (ELF for Android ABIs, needs an emulator per ABI),
`linuxArm64` on an x64 runner, and the device Apple slices (`iosArm64`,
`tvosArm64`, `watchosArm64`, `watchosDeviceArm64`, which have no
host-runnable test task) can only be compiled/linked. Their `actual`s
share the `appleMain`/`iosMain`/etc. source sets that the corresponding
**simulator** test exercises, so the same code is covered — state this
in the workflow rather than implying the binary was tested.

The local `swiftExportSmokeTest` (kasuari-kotlin is the reference shape)
runs `swift test` against the `embedSwiftExportForXcode` output and must
be wired into `check` so Swift Export breakage surfaces on
`./gradlew check` locally, not only in `swift.yml`. When aligning a repo,
confirm `project.frameworkName` (→ `swiftExport.moduleName`) matches the
harness's `import <Module>` — a mismatched module name fails `swift test`
with "no such module" even though the bridge compiled cleanly.

---

## Why the recipe is in every repo

Two reasons:

1. **The Claude agent operates per-repo** in the GitHub App context.
   Each repo's agent invocation only sees the repo it's working in.
   Pulling instructions from a central source would require the agent
   to make cross-repo API calls; embedding the recipe in the repo
   makes the work fully self-contained.

2. **The recipe ages with the rollout commit.** When the workarounds
   in [§ Known upstream gaps](#known-upstream-gaps) eventually become
   unnecessary, the recipe's removal (or amendment) will land in the
   same commit as the workaround removal. Future spelunkers reading
   `git log -- SWIFT_EXPORT_ROLLOUT.md` will see the full history of
   what was tried and why.

— Sydney Renee, KotlinMania

---

## Additional Workspace Guidelines and Hazard Classes

### The mandatory infrastructure pins

- **`swift.yml` runs on `runs-on: macos-26`** — not `macos-latest`. The Kotlin/Native 2.3.x SDK cache requires it.
- **Never add `maxim-lobanov/setup-xcode@v1`** or any other third-party Xcode setup action. Third-party actions cause `startup_failure` on the KotlinMania allowlist.
- **`swift test` is wired into the Kotlin `test` / `check` lifecycle.** A Swift Export failure must surface locally. Do not push and let remote CI find it — by that point you've burned a CI run.
- **iOS Simulator framework static rule (HISTORICAL `iosX64` note).**
  > [!NOTE]
  > **Historical target retirement:** `iosX64` (along with all Apple `x86_64` and 32-bit targets) is **RETIRED** workspace-wide per `AGENTS.md` §5.5. Only `iosSimulatorArm64` (with `baseName = frameworkName` and `isStatic = true`) is configured in active builds. The fat-simulator diagnostic below is preserved solely as historical context for pre-retirement commits.
  Historically, when both `iosSimulatorArm64` and `iosX64` existed, both had to set `isStatic = true`; mixing one static / one dynamic failed `assembleDebugIosSimulatorFatFramework<Name>` with:
  ```
  Cannot create a fat framework from:
    <Name> - arm64 - static
    <Name> - x64 - dynamic
    All input frameworks must be either static or dynamic
  ```
  This was **gap #7** in the early rollout before `iosX64` was retired.

### The four hazard classes (what makes Swift Export fail under `-Werror`)

Swift Export's generated bridge fails `allWarningsAsErrors=true` in four distinct ways. **Do not scope `allWarningsAsErrors=false` to `compileSwiftExportMain*` to silence them — that's symptom suppression and a forbidden fix.** Apply the structural remedy below.

**Class A — Unchecked cast `Any?` → `Foo<T>`.** Public generic types with unconstrained type parameters force the bridge to erase to `Any?` and cast back. Fix: wrap the generic with an `internal` implementation + a non-generic façade for Swift, or use a stdlib type the bridge already handles.

**Class B — `Cannot infer T`.** Public generic functions Swift can't project a single Swift type for. Fix: `internal` factory that fixes the type parameter at the boundary; the public Kotlin function stays generic for Kotlin callers.

**Class C — `Array`/`List`/`Iterator` mismatch.** Public collection surfaces that Swift would bridge to non-equivalent types. Fix: non-generic collection at the boundary, or wrap in a thin Swift-friendly type.

**Class Stdlib (the fourth class) — warnings in *auto-generated* `KotlinStdlib.kt`.** Public `kotlin.Result<X>`, classes extending `RuntimeException` / `Exception` / any `Throwable` subclass, public `MutableList<X>`, public `MutableMap<K, V>` each drag the `Throwable.getStackTrace()` → `Array` bridge or the `MutableList`/`MutableMap` bridge into the generated `KotlinStdlib.kt`, which then fails Unchecked-cast under `-Werror`. Fixes:

- **Public `kotlin.Result<T>`** → a named, concrete success/failure API for the exported value type, with a non-Throwable diagnostic. Merely replacing it with another generic result does not restore concrete Swift typing.
- **`class FooError : RuntimeException(...)`** (or `Exception`) → data class / sealed class that does NOT extend any `Throwable`. Kotlin callers that need to throw it wrap in their own throw site.
- **Public `MutableList<X>` / `MutableMap<K, V>`** → internal-backed `List<X>` / a custom read-only Map wrapper; internal helpers do copy-and-replace to keep the read-only public surface.
- **Public `Pair<A, B>`** → named record class.
- **Public `() -> X` / `(A) -> B`** function types → `fun interface` SAM type. Swift Export can't bridge raw Kotlin function types.

### Diagnostic APIs: repair the exported types, not their aliases

`Swift.Error` name collisions and unsupported exception inheritance are
separate problems. Rename the actual Kotlin declaration to a distinct
name where needed, and repair its actual superclass and parameter types.
Renaming `Error.kt`, adding `typealias SynError = Error`, or renaming an
exception subclass to `SynError` does not stop re-exporting its raw
Kotlin/Native functionality. Keeping `typealias Error = SynError` also
retains the colliding exported name. Generated namespace aliases must be
traced to their underlying declaration; they are not evidence that its
API has been repaired.

The Syn bridge audit exposed this concrete chain before the diagnostic
redesign:

```text
SynError : IllegalArgumentException
  → exported Throwable constructors and getStackTrace()
  → Array<String> stack trace
  → erased Array<Any?> receivers in Array get / iterator / set / size
  → four unchecked-cast diagnostics in KotlinStdlib.kt
```

Its `new`, `newAt` and `newSpanned` message factories also accepted
Kotlin `Any`. Their generated Swift parameters were
`any KotlinRuntimeSupport._KotlinBridgeable`, even though callers needed
text. Renaming the class cannot repair those factory signatures.
Warnings in `KotlinStdlib.kt` therefore do not, by themselves, establish
that the only remedy is an upstream compiler change.

The required source repair is:

1. Make `SynError` an actual non-Throwable diagnostic, with no exception
   superclass. Preserve its message, span and compile-error operations.
   Preserve the same diagnostic object when propagating a failure result.
2. Use concrete, non-null Kotlin `String` messages in the diagnostic
   property and every exported message factory or constructor. Format
   other values into text at the caller. Do not keep an `Any` overload
   that reintroduces the erased exported parameter.
3. Migrate `catch (error: SynError)` and `throw diagnostic` sites
   deliberately. Prefer result-based propagation in the original parse
   order. A Kotlin `OrThrow` boundary can throw an internal exception
   carrying the original diagnostic; do not publish another Throwable
   subclass as its replacement. Record source and binary compatibility
   changes and rebuild consumers. A generated function named `getOrThrow`
   is not evidence of a Swift `throws` contract.
4. Provide concrete result types at Swift-facing boundaries that need
   typed success values. A non-Throwable diagnostic does not by itself
   solve the generic `SynResult<T>` erasure described above.

**Verify the repair in regenerated output, not just the source diff.**
Record the source revision, resolved plugin version and configuration,
target/build type, generated compilation inputs and their hashes.
Inspect both the module's `.swift` declarations and its Kotlin bridges,
then follow dependencies into `KotlinStdlib.kt`:

- `SynError` must no longer inherit the exported Kotlin exception class;
  its message property and message parameters must emit `Swift.String`.
- The Throwable constructors, `getStackTrace()` and four Array receiver
  casts brought in by that diagnostic must disappear. If they remain,
  identify the other exported declaration keeping them reachable.
  Inspect newly exposed stdlib shapes as well; removal of one chain
  does not prove the entire export surface is clean.
- Retain identity, message/span and failure-path regression tests and
  exercise the concrete diagnostic/result API from the Swift harness.
  Keep `allWarningsAsErrors=true` through bridge/runtime compilation
  and run the actual Swift tests. Generation, linking, skipped tests and
  earlier results for a different revision are not current test passes.

The non-Throwable/String redesign addresses this source API chain.
Bundled coroutine warnings and concrete Unit-conversion warnings require
their own evidence and validated repairs; neither disappears merely
because the diagnostic was renamed or redesigned.

### Result APIs: retain concrete success and diagnostic types

Apply the same source-level repair to `SynResult` at public operations
whose success type is known. In the inspected generated Swift,
`SynResult.success`, `Success.value`, `getOrThrow`, `getOrElse` and
`fold` use optional `KotlinRuntimeSupport._KotlinBridgeable` values.
The absence of an unchecked-cast diagnostic does not restore the lost
success type. A sealed-case wrapper around an erased payload does not
restore it either.

Use an actual, non-generic result class for each concrete public
contract: `FileParseResult` for `parseFile`, Syn's `TokenStreamParseResult`
for token transformations, and the consumer's `ArgsParseResult` for
async-trait arguments. Each class has a concrete nullable `value`, a
nullable non-Throwable `SynError`, and exactly one populated field.
Private constructors and concrete success/failure factories enforce
that invariant. Conversion must retain the original value or diagnostic
object.

Change the operation's actual return type. Do not use
`typealias TokenStreamParseResult = SynResult<TokenStream>`, inherit
from `SynResult<TokenStream>`, substitute `Any` for the payload, or
hide the operation while leaving no equivalent concrete API. Generic
results can serve algorithms whose parser callbacks genuinely choose
arbitrary types, but that does not justify exposing an erased result
from an operation with a known output. Public generic `fold`/`map`
callbacks must not reintroduce the same erased result surface.

Record the source and binary signature changes, migrate callers and
rebuild compiled consumers. Regenerate the bridge and inspect the
operation return, factory arguments, result properties and accessors.
Swift consumer tests must assign the success payload to its concrete
type and use the diagnostic's `String` message without forced casts.
Run those tests through the full strict KMP gate. Neither a Kotlin-only
test nor a warning-free but erased declaration proves this contract.

### Annotation hiding is not an API repair

`@HiddenFromObjC` hides a declaration from Objective-C / Swift export.
That is not a repair for a published Apple framework. If a declaration
should not be exported, make it `internal`. If a declaration is part of
the public API, make it bridgeable by construction.

For Swift name collisions, the fix is to rename the Kotlin type itself.
Do not hide the declaration, do not rely on `@ObjCName`, and do not keep a
backward-compatible `typealias` with the old colliding name. Swift Export
emits the Kotlin declaration surface, including typealiases, so the old
name keeps colliding.

The concrete reference is `syn-kotlin`: commit `e34775e` renamed
`Type` / `Error` / `Result` to `SynType` / `SynError` / `SynResult`, and
commit `afbebfe` removed the aliases after CI showed
`typealias Type = SynType` was still exported and rejected by Swift.

```kotlin
// Before: collides with Swift's `foo.Type` metatype expression.
public sealed class Type

// Correct: rename the Kotlin API and migrate callers.
public sealed class SynType

public data class BareFnArg(
    public val ty: SynType,
)

// Wrong: aliases are exported too, so this keeps the Swift collision.
public typealias Type = SynType
```

The reviewed API-hiding failure case is `lru-kotlin` PR #18. The PR
annotated the main cache type:

```kotlin
class LruCache<K : Any, V : Any> private constructor(...)
```

The generated framework could still import, but Swift / Objective-C
consumers could not construct or use the cache. The correct direction is
one of:

- keep `LruCache` exported and make its public surface bridgeable;
- when the failure is a Swift name collision, rename the Kotlin type to a
  Swift-safe name and update Kotlin callers to that name;
- when the failure is generic bridge shape, keep a generic implementation
  internal and provide exported concrete types with Swift-safe names;
- make unsupported iterator/view/helper types `internal` while the
  primary cache API remains exported and usable.

Do not accept an import-only smoke test as proof that Swift Export is
healthy. The Swift harness must instantiate at least one primary exported
type and call a representative method.

**Fallback** (when faithfulness isn't a constraint): mark `internal` + expose a factory returning the public stdlib interface.

### Project goal: strongly-typed public APIs — generics only where design requires

After a Swift Export compatibility pass lands and a repo's gate is green,
the **follow-up pass is to de-generify**. The project goal across the
workspace is **strongly-typed public APIs**: no unconstrained generics, no
`<T : Any>` exposed to Swift, no `<T, E>` Result-style wrappers — unless
the design genuinely requires the type parameter (parser combinators,
typed builders, container types where the element type carries real
meaning).

Why this is a real rule and not a style preference:
- Annotation hiding only removes the Swift bridge problem from view. The Swift side sees no API for that surface at all, which is worse than a non-generic Swift API. Swift callers either cannot use the type or have to drop into Objective-C interop.
- The Class A "unchecked cast `Any?` → `Foo<T>`" failure is generated by the same erased-bridge code path no matter what you do; hiding it means the next type the bridge tries to express through that path hits the same wall.
- Strongly-typed public APIs survive Kotlin/Native compiler upgrades. The generic-bridge handling in Swift Export is the most volatile surface of the K/N toolchain and keeps changing between 2.3.x patches; non-generic APIs don't care.

**The strong-typing checklist:**
1. Is the generic parameter actually doing structural work for callers?
   If **no**, replace it with the concrete type or a small set of
   concrete named subtypes (`IntFoo`, `StringFoo`, `DoubleFoo`).
2. If the parameter is doing structural work but public use only
   instantiates one or two concrete types, hoist those instantiations
   into named non-generic subclasses (`class IntCell : Cell<Int>()`),
   expose those publicly, and make the generic base `internal`.
3. If the parameter is genuinely required by design (for example, a
   typed builder DSL or a generic container callers instantiate at
   arbitrary types), leave the generic and document why in a one-line
   `// generic by design:` comment so the next sweep does not mistake it
   for unfinished work.
4. `Pair<A, B>` -> named record class. Always. There is no design
   justification for leaking `kotlin.Pair` into a public API.
5. `Result<T, E>` / generic Outcome wrappers -> per-error-domain sealed
   classes. `AddConstraintOutcome` not `Outcome<Unit, AddConstraintError>`.
6. Function-type surfaces (`(A) -> B`) -> `fun interface` SAM with a
   meaningful name.

**Forbidden phrasing** in commits and PR descriptions:
- "Hide `Foo` from Swift" as the *only* change for that API. → Not acceptable when `Foo` is the only useful Apple-facing API; provide an exported facade or redesign the surface.
- "Generic API kept for flexibility." → Specify *which caller* needs the flexibility, or de-generify.

Memory hook: `feedback_swift_export_three_patterns.md`,
`feedback_swift_export_throwable_result_array.md`, and
`feedback_swift_export_gap8_internal_generics.md` predate the stricter
public-API rule. Treat them as bridge-failure diagnostics, not as
permission to hide the only Swift-facing API.

### The 5-class sweep (per-repo, run before every Swift release)

Inspect commonMain for **all** of these, not just whatever last tripped CI:
1. `runs-on:` on `swift.yml` is `macos-26`; no `setup-xcode` action.
2. Public mutable collection surfaces (`MutableList`, `MutableMap`, `MutableSet`) → read-only Kotlin types + internal copy-and-replace helpers.
3. Public generic types / SAMs / function-type surfaces -> **first choice: de-generify** (concrete named subtypes, `fun interface` SAM with a real name, internal generic base). See "Project goal: strongly-typed public APIs" above.
4. Public `kotlin.Result<X>` / `Throwable` subclasses → concrete outcome APIs and non-Throwable diagnostic classes; use `String` messages, migrate catches deliberately, and verify that regenerated output removes the inherited Throwable/Array chain and erased message parameters.
5. Public `Pair<A, B>` helpers → named record class.

Run the gate after every repair, not just at the end:
```bash
./gradlew embedSwiftExportForXcode --no-daemon
./gradlew test --no-daemon          # must include swift test
```

### 12. Kotlin names that conflict with Swift or Java emitted names

**Symptom.** `compileSwiftExportMainKotlinMacosArm64` or the
`xcodebuild` step inside `macosArm64DebugBuildSPMPackage` fails with:

```
error: type member must not be named 'Type', since it would conflict with the 'foo.Type' expression
error: type member must not be named 'Error'
error: type member must not be named 'Result'
```

**Root cause.** Swift Export emits Kotlin declaration names into the
generated Swift module, and JVM compilation emits Kotlin files and
declarations as Java-visible class and method names. If a Kotlin public
type or typealias is named like a Swift keyword, metatype expression,
protocol, or built-in type (`Type`, `Error`, `Result`, etc.), the Swift
compiler rejects the module. If common/platform Kotlin files or
declarations emit the same JVM class or method signature, Java/JVM
compilation fails.

`@HiddenFromObjC` only removes the API from Apple consumers. `@ObjCName`,
`@JvmName`, and `@JvmMultifileClass` are not fixes. A Kotlin `typealias`
does not preserve compatibility either because Swift Export emits aliases
too: `typealias Type = SynType` still exports `Type` and still collides.

**Fix.** Rename the Kotlin declaration or file itself to an emitted-safe
name and migrate every Kotlin caller to that new name. Remove any
compatibility typealias or platform naming annotation that preserves the
old colliding name. If downstream `*-kotlin` repos use the old name,
update them in the same compatibility pass; there is no bridge that
preserves the old source spelling without reintroducing the collision.

Concrete pattern from `syn-kotlin`:

```kotlin
// Before
public sealed class Type {
    public data class Tuple(
        val elems: Punctuated<Type, Comma>,
    ) : Type()
}

// After
public sealed class SynType {
    public data class Tuple(
        val elems: Punctuated<SynType, Comma>,
    ) : SynType()
}

public data class BareFnArg(
    public val ty: SynType,
)

// Forbidden: this is exported and collides in Swift.
public typealias Type = SynType
```

**Evidence.** `syn-kotlin` commit `e34775e` renamed
`Type` / `Error` / `Result` to `SynType` / `SynError` / `SynResult`.
Commit `afbebfe` then removed `typealias Type = SynType`,
`typealias Error = SynError`, and `typealias Result<T> = SynResult<T>`
after CI confirmed the aliases were exported and rejected by Swift.

### 13. `NoClassDefFoundError: kotlinx/coroutines/internal/intellij/IntellijCoroutines` during Swift Export (and related Gradle 9.8.1 / KGP 2.4.x wiring)

**Symptom.** The `macosArm64DebugSwiftExport` Gradle task fails with:

```
ERROR: Worker exited due to exception
java.lang.NoClassDefFoundError: kotlinx/coroutines/internal/intellij/IntellijCoroutines
```

**Root cause.** KGP runs Swift Export in an isolated worker process whose
classpath is resolved from the `swiftExportClasspath` Gradle configuration
(not from `buildscript.dependencies`). The embedded IntelliJ platform used by
the worker requires `kotlinx.coroutines.internal.intellij.IntellijCoroutines`
(`org.jetbrains.intellij.deps.kotlinx:kotlinx-coroutines-core-jvm`), which is
omitted from KGP's default `swiftExportClasspath`. Because adding any explicit
dependency to `swiftExportClasspath` disables KGP's `defaultDependencies`
callback for that configuration, `org.jetbrains.kotlin:swift-export-embeddable`
(pinned to the project's Kotlin version) must be added explicitly alongside the
IntelliJ coroutines artifact.

**Impact (measured outcome — reconciles earlier stale note).** Contrary to the
earlier note in this section, this error **is fatal**: when the worker throws
`NoClassDefFoundError: kotlinx/coroutines/internal/intellij/IntellijCoroutines`,
`macosArm64DebugSwiftExport` aborts before emitting the generated Swift/Kotlin
bridge sources and `SPMPackage`.

**Verified fix (already present in `template/build.gradle.kts`).** Populate
`swiftExportClasspath` with both `swift-export-embeddable` and
`kotlinx-coroutines-core-jvm`:

```kotlin
val projectDependencyHandler = project.dependencies
configurations.configureEach {
    if (name == "swiftExportClasspath") {
        dependencies.add(
            projectDependencyHandler.create("org.jetbrains.kotlin:swift-export-embeddable:$kotlinVersion"),
        )
        dependencies.add(
            projectDependencyHandler.create(
                "org.jetbrains.intellij.deps.kotlinx:kotlinx-coroutines-core-jvm:$intellijCoroutinesVersion",
            ),
        )
    }
}
```

#### Related Gradle 9.8.1 / KGP 2.4.x build-graph and strict-gate rules

1. **Gradle 9.8.1 sourceSet DSL (`create` / `getByName`).** Property-delegate
   helpers (`by creating`, `by getting`) are deprecated in Gradle 9.8.1 and
   flagged as incompatible with Gradle 10. Use explicit
   `create("<sourceSetName>") { ... }` and `getByName("<sourceSetName>") { ... }`
   calls as in `template/build.gradle.kts`.
2. **JS / WasmJS `test` binary `CompileSync` vs `KotlinJsTest` implicit
   dependency in Gradle 9.8.1.** In KGP 2.4.x, `KotlinJsCompilation` registers
   both `DEVELOPMENT` and `PRODUCTION` executables on the `test` compilation,
   and their `CompileSync` tasks (`jsTestTestDevelopmentExecutableCompileSync`
   and `jsTestTestProductionExecutableCompileSync`, plus the `wasmJs`
   equivalents) write into the same npm workspace directory
   (`build/js/packages/<pkg>-test/kotlin` and
   `build/wasm/packages/<pkg>-test/kotlin`). In addition,
   `compileTestProductionExecutableKotlinJs` is `finalizedBy`
   `jsTestTestProductionExecutableCompileSync`. When `:library:build` and
   `:library:allTests` run in the same invocation, Gradle 9.8.1 fails
   `:library:jsBrowserTest`, `:library:jsNodeTest`, `:library:wasmJsBrowserTest`,
   and `:library:wasmJsNodeTest` with an undeclared implicit dependency on
   `*TestTestProductionExecutableCompileSync`. Keep `linkTask` and
   `optimizeTask` wired into `assemble` (so production test linking and
   Binaryen `wasm-opt` still run), but for
   `isTestCompilation && mode == KotlinJsBinaryMode.PRODUCTION` omit
   `linkSyncTask` from `assemble` and set `linkSyncTask.configure { enabled = false }`
   so only the `DEVELOPMENT` test executable syncs into the shared runner package.
3. **Keep generated Kotlin bridge warning policy distinct from required strict
   Swift 6 / `-warnings-as-errors`, and separate compiler, language, and tools
   versions.**
   - Generated Kotlin bridge compilation (`compileSwiftExportMainKotlinMacosArm64`
     under `allWarningsAsErrors = true`) and Swift compilation
     (`-swift-version 6 -warnings-as-errors`) are distinct gates; neither
     substitutes for the other.
   - **Compiler version, Swift language mode (`-swift-version`), and SwiftPM
     `swift-tools-version` are three independent settings.** A
     `swift-tools-version: 6.0` test harness does **not** force a generated
     `swift-tools-version: 5.9` dependency package into Swift 6 language mode.
     Unless the generated dependency manifest or `XCODE_XCCONFIG_FILE`
     (`verification/strict-swift.xcconfig` + `verification/enforce_swift6.py`)
     enforces Swift 6 on the dependency targets, generated modules such as
     `KotlinRuntimeSupport` and `KotlinCoroutineSupport` compile under
     `-swift-version 5` while the test target compiles under `-swift-version 6`.
     Any mixed-mode execution must be explicitly labeled as mixed-mode
     compatibility rather than full strict Swift 6 dependency compilation.
   - **Do not copy `oauth2-kotlin`'s generated-Kotlin `-Werror` disable or
     `swift-tools-version: 5.9` rewrite as a strict fix.** Scoping
     `allWarningsAsErrors = false` onto `compileSwiftExportMain*` or rewriting
     manifests to tools 5.9 / Swift 5 mode masks diagnostics rather than
     repairing the exported contract.
   - **Zero executed tests and green CI job labels are not acceptance.** Every
     validation report must inspect the exact final replacement head and record
     actual executed test counts across all KMP targets and Swift
     (`Test run with <N> tests`).

#### Controlled 2x2 Swift 5 vs Swift 6 dependency language-mode diagnostic across `async-trait-kotlin` (Kotlin 2.4.21) and `openssl-sys-kotlin` (Kotlin 2.4.20) under Swift 6.3.3

Controlled diagnostics in `async-trait-kotlin` (`automation-artifacts/2026-10-20-async-trait-swift-mode-diagnostic/`, frozen head `685c3dacb8afaa2999f49cc2711ca82408df5540`, KGP/exporter `2.4.21`) and `openssl-sys-kotlin` (`automation-artifacts/2026-10-20-openssl-sys-swift-mode-diagnostic/`, frozen head `23623166c6e007b66ff4d3615abed86ae4d54657`, KGP/exporter `2.4.20`) tested whether stock Kotlin Swift Export emits dependency bridge sources compatible with Swift 5 language mode that fail under Swift 6 while consumer test targets remain in strict Swift 6 (`-swift-version 6 -warnings-as-errors`):

1. **Generation is invariant to Swift language mode (`javap` + `2.4.20` vs `2.4.21` bytecode audit).**
   - `SwiftExportTask` -> `SwiftExportAction` -> `SwiftExportRunnerKt.runSwiftExport` (`SwiftExportConfig` + `SwiftModuleConfig`) has no parameter, branch, or gate for Swift compiler version, `swift-tools-version`, or `-swift-version`. Neither Kotlin `2.4.20` nor Kotlin `2.4.21` restricts generation to Swift `< 6`.
   - Entry-by-entry SHA256 inspection (`javap-2.4.20-vs-2.4.21.json`, SHA256 `c428af050d775f702c4147803e60183da1be7b2d482b2d8cc3e2842b1017e1ea`) between `swift-export-embeddable-2.4.20.jar` (`24c6159bcf0087ecf86bfc9bad01a975e192abcf75005b248292d85a139c2055`) and `swift-export-embeddable-2.4.21.jar` (`615e8a032f47baa15bc6126cbffe707c87069445b215ecce7c4830d13b079180`) proves that **`6,262` of `6,263` JAR entries are 100% byte-for-byte identical** (only `META-INF/MANIFEST.MF` differs; **all `681` `swiftexport` + `sir` class and resource entries are byte-identical**). Likewise, between `kotlin-gradle-plugin-2.4.20-gradle96.jar` (`6f49688f2c6715d60f5dc52d4c1ada992ce4bed6053d1bd6e579c9392d7ba0ed`) and `kotlin-gradle-plugin-2.4.21-gradle96.jar` (`d0a11912936c8801f1773a47e8fc51835a6ab568d73e4e1d188281955a7f575b`), **all `144` `org/jetbrains/kotlin/gradle/plugin/mpp/apple/swiftexport/*` class entries are 100% byte-for-byte identical**.
   - Consequently, shared generated runtime files are byte-identical across both projects and both Kotlin versions (`KotlinRuntimeSupport.swift`: `3cf1d7fb7b1177a62400397dc94f8207ada3b26a955c09594def8ab8ee620560`, `KotlinCoroutineSupport.swift`: `883cec935c34af1380ca1e59a5e78af498349cb152c79aeb6878216fc097dc04`, `KotlinRuntimeSupport.kt`: `caf6da985fe941dbd1b0dcdfcf2c572f7ffe63cea6d91c8b9cabaaf248804f1f`, `KotlinCoroutineSupport.kt`: `e63496593d0ae8e63c7d7180c0bfa0a07c23f63d998e06459e8b6878eded26fe`, `KotlinBase.h`: `14390b9a40bcd8f924909692249ca95468cd3de36157be762371b1673dab86f3`).
   - `SPMManifestGenerator.generateManifest` (`GenerateSPMPackageFromSwiftExport.kt:167`) hardcodes `// swift-tools-version: 5.9` and omits `platforms:` and `swiftLanguageModes`. Under Swift 6.3 (`swiftlang-6.3.3.1.3`), SwiftPM defaults every target in a `swift-tools-version: 5.9` dependency package to `-swift-version 5` even when consumed by a `swift-tools-version: 6.0` test harness.
   - `BuildSPMSwiftExportPackage` (`BuildSPMSwiftExportPackage.kt:103-154`) invokes `xcodebuild` without `SWIFT_VERSION=6` or `SWIFT_TREAT_WARNINGS_AS_ERRORS=YES`, so KGP's built-in `macosArm64DebugBuildSPMPackage` compiles the generated `SPMPackage` in Swift 5 language mode without `-warnings-as-errors`.
2. **Generated Kotlin bridge compilation (`compileSwiftExportMainKotlinMacosArm64` with `allWarningsAsErrors = true`).**
   - Stock `2.4.20`/`2.4.21` `TypeBridging.kt:337` (`Bridge$AsVoid$inKotlinSources$1.kotlinToSwift`) emits `run { _result; true }` for `Unit`-returning bridges while `KotlinBridgePrinter` emits only `@file:kotlin.Suppress("DEPRECATION_ERROR")`.
   - **Stage 2a (stock unmodified `.kt` bridges under `-Werror`)**: fails in `async-trait-kotlin` with `0` errors and `1,404` warnings (`1,396` `UNUSED_EXPRESSION`, `4` `UNCHECKED_CAST` on `Array<Any?>` in `KotlinStdlib.kt`, and `4` warnings in `KotlinCoroutineSupport.kt`) and fails in `openssl-sys-kotlin` with `0` errors and `193` warnings (`189` `UNUSED_EXPRESSION`, `0` `Array<Any?>` casts because only primitive `ByteArray`/`IntArray`/`UIntArray`/`ULongArray` bridges are emitted, and `4` warnings in `KotlinCoroutineSupport.kt`).
   - **Stage 2b (separate Kotlin warning-suppression intervention — NOT an unchanged-source or unchanged-warning-policy mode-only result)**: To link `libSwiftExportBinary.a` (`fc5cf8f9...` in `async-trait-kotlin` -> merged `libAsyncTrait.a` `524239bd00a82d37222a0a2f6febc172be8fe155de922f39f0bc92ea279b39ac`; `62a59116...` in `openssl-sys-kotlin`) for the downstream Swift 5 vs Swift 6 dependency language-mode comparison, line 1 of the generated `.kt` files was extended with `"UNUSED_EXPRESSION", "UNCHECKED_CAST", "USELESS_ELVIS_RIGHT_IS_NULL", "OPT_IN_USAGE", "OPT_IN_TO_INHERITANCE"` (after an initial 4-key attempt in `async-trait-kotlin` failed on `KotlinCoroutineSupport.kt:42:34` `USELESS_ELVIS_RIGHT_IS_NULL`; see `command-log-manifest.json` SHA256 `25aac62c34c40e11cd10c7d35c3ce9687453af01294e3bcee37b64f6ed3588c0` and `post-suppression-kt-sha256.txt` SHA256 `d3f332e5cf2a5eb84b6d37a0aeb1b90f571949a3f16bf409d1c844f791f26d04`). Because this modifies generated `.kt` source headers to suppress warnings, it is explicitly classified as a separate Kotlin warning-policy intervention.
3. **2x2 Factorial Swift Compilation & Execution Across Both Projects (identical stock `.swift`/`.h` files; consumer test targets always compiled with `-swift-version 6 -warnings-as-errors`):**
   - **Arm A1 (Dependency `-swift-version 5`, no dependency `-warnings-as-errors`; Consumer `-swift-version 6 -warnings-as-errors`):**
     - In `async-trait-kotlin`: all **8** generated dependency modules and both Swift 6 consumer test targets compile and link with **0 errors** (22 dependency warnings, 0 consumer warnings). Under `--no-parallel`, all **8 consumer tests execute** (`7/8` pass on unmodified `AsyncTraitTests.swift`; `exposesConcreteDiagnosticMessages` fails at line 85 only because `parseArgs(tokens: tokens)` at line 74 calls `proc-macro2-kotlin` `TokenStream.intoIter()` -> `Rcvec.makeOwned()`, which drains `tokens.inner` in-place to `[]` before line 84 reuses `tokens`, and passes **`8/8`** when given a fresh `TokenStream`; `--parallel` crashes with `ArrayIndexOutOfBoundsException` because `proc-macro2-kotlin` `Fallback.kt:266` `SourceMap` uses an unsynchronized process-wide `mutableListOf<FileInfo>()`).
     - In `openssl-sys-kotlin`: all **5** generated dependency modules compile with **0 errors** (15 dependency warnings). Unchanged `OpensslSysExportTests.swift` fails consumer compilation on `OpensslSys.NID_sha256` (renamed to `NID_SHA256` in commit `93a22e0`); **Supplementary Arm A1-S** (aligning `NID_SHA256` in the test while keeping all generated files and flags identical) compiles with 0 consumer warnings/errors and passes **`3/3` serial** and **`3/3` parallel** tests.
   - **Arm B1 (Dependency `-swift-version 6`, no dependency `-warnings-as-errors`; Consumer `-swift-version 6 -warnings-as-errors`):** In **both** `async-trait-kotlin` and `openssl-sys-kotlin` (including Supplementary Arm B1-S), switching dependency language mode from `5` to `6` fails compilation in `KotlinCoroutineSupport.swift:66:13` and `91:17` (`error: capture of 'cancellation' with non-Sendable type 'KotlinTask' in a '@Sendable' closure [#SendableClosureCaptures]`) and adds Swift 6 `Sendable` diagnostics in `KotlinRuntimeSupport.swift:5:16` (`stored property 'wrapped' of 'Sendable'-conforming struct 'KotlinError' has non-Sendable type 'KotlinBase'`). `0/8` (`async-trait-kotlin`) and `0/3` (`openssl-sys-kotlin`) consumer tests execute.
   - **Arms A2 & B2 (Dependency `-warnings-as-errors` enabled under `-swift-version 5` or `6`):** In **both** projects, passing global `-Xswiftc -warnings-as-errors` on the `swift test` CLI propagates `-warnings-as-errors` to all dependency targets, failing in `KotlinRuntimeSupport.swift` (`3` errors in Swift 5 mode; `5`–`8` errors across `KotlinRuntimeSupport.swift` and `KotlinCoroutineSupport.swift` in Swift 6 mode).
   - **Labeling and non-acceptance qualification:** Any run where dependency modules compile under `-swift-version 5` while consumer tests compile under `-swift-version 6` is **mixed-mode compatibility** and must never be reported as strict Swift 6 dependency compilation. **Neither diagnostic comparison is an accepted strict production gate:** Arm A1 disables dependency `-warnings-as-errors` and relies on the separate Stage 2b `.kt` `@file:kotlin.Suppress` intervention to link the Kotlin/Native static archive, while Arms B1, A2, and B2 execute `0` cases. See `automation-artifacts/2026-10-20-async-trait-swift-mode-diagnostic/report.json` (SHA256 `7902e419248e09a0868e0d989863a508c7d28a713e106a20e183d52aaec41ae3`), `provenance-and-two-project-matrix.json` (SHA256 `466eafc85f690576eb7e318ecab7b22177b667d38711c7dce7c128125a66c7bd`), `automation-artifacts/2026-10-20-openssl-sys-swift-mode-diagnostic/report.json` (SHA256 `2ff2e0380ba11b70839a31ab086537b0a8797970212ebefaf3e66605db135226`), and Syn's compatibility references below (`3364aa7a3ddb76aea2357f0329c367365bf796b1` and `db24b934e8db9b0d843a83f3902d9db057e65e67` in `syn-kotlin` PR #166).

### 14. Custom sealed generic result types in public API cause argument type mismatch in Swift Export bridge

**Symptom.** `compileSwiftExportMainKotlinMacosArm64` fails with:

```
Argument type mismatch: actual type is '(ParseNestedMeta) -> SynResult<Any?>',
  but '(ParseNestedMeta) -> SynResult<Unit>' was expected.
Cannot infer type for type parameter 'R'. Specify it explicitly.
```

**Root cause.** Swift Export generates bridge code for public API
declarations. When a function takes or returns a custom generic sealed
class (e.g. `SynResult<T>`), the bridge erases the type parameter to
`Any?` because it doesn't understand the sealed-class type hierarchy.
This produces Kotlin-level argument type mismatches in the generated
bridge file that fail compilation.

**Fix.** If these are Kotlin-internal parser APIs that Swift consumers do
not need, make them `internal`. If they are public API, replace the
custom generic sealed result with a Swift-exportable concrete outcome
type.

The flat-class pattern from [§ Recipe for replacing `kotlin.Result<T>`
in a public API](#recipe-for-replacing-kotlinresultt-in-a-public-api) is
the correct shape for exported result APIs. The flat class with
`value`/`error` nullable fields and `isSuccess()`/`isFailure()`
predicates bridges cleanly.

**Evidence.** syn-kotlin's durable fix was the type-rename pass:
`Type` / `Error` / `Result` became `SynType` / `SynError` / `SynResult`,
and the compatibility aliases were removed after CI proved aliases are
exported too.

<a id="15-backticked-kotlin-reserved-keywords-on-public-declarations-emit-unbackticked-calls-in-the-swift-export-kotlin-bridge"></a>
### 15. Backticked Kotlin reserved keywords on public declarations emit unbackticked calls in the Swift Export Kotlin bridge

**Symptom.** `compileSwiftExportMainKotlinMacosArm64` fails on generated
`build/SwiftExport/macosArm64/Debug/files/<Module>/<Module>.kt` with:

```
Cannot infer type for type parameter 'R'. Specify it explicitly.
Reference has a nullable type 'Nothing?'. Use explicit '?.invoke' to make a function-like call instead.
No receiver can be passed to this function or property.
```

**Root cause.** When a public Kotlin function or property is named with a
backticked Kotlin hard keyword to match upstream Rust identifiers (such as
`` fun `null`(): UnsafeRawIsolatePtr `` or `` fun `this`(): Object ``),
the Swift Export bridge generator emits unbackticked Kotlin call sites in
the generated `.kt` bridge file:

```kotlin
val _result = run { __self.null() }
// or:
val _result = run { __self.this() }
```

The Kotlin compiler parses `__self.null()` as attempting to invoke the literal
`null` (of type `Nothing?`) with `__self` as a receiver rather than calling the
method named `` `null` ``, and parses `__self.this()` as invalid `this` syntax.

**Fix.** Expose a non-keyword public name for Kotlin and Swift consumers
(e.g., `fun nullValue()` / `fun thisValue()`) and scope the backticked
keyword alias to `internal` (e.g., `` internal fun `null`() = nullValue() ``,
`` internal fun `this`() = thisValue() ``). Because Swift Export only bridges
`public` declarations, the invalid unbackticked bridge call is never emitted
while internal callers and `ast_distance` symbol parity still match the exact
upstream Rust identifier:

```kotlin
// Before (breaks compileSwiftExportMainKotlinMacosArm64)
public fun `null`(): UnsafeRawIsolatePtr = UnsafeRawIsolatePtr(null)

// After (bridges cleanly to Swift while preserving internal Rust parity)
public fun nullValue(): UnsafeRawIsolatePtr = UnsafeRawIsolatePtr(null)
internal fun `null`(): UnsafeRawIsolatePtr = nullValue()
```

**Evidence.** Discovered and independently verified in `v8-kotlin` ([PR #121](https://github.com/KotlinMania/v8-kotlin/pull/121), merged to `main` at `4be04116172bd955580511da3ab264eb69797c00`; retained step logs in `/Users/sydney/.gemini/antigravity/brain/035cfd35-895c-497a-927a-3af4da50a6b1/.system_generated/steps/`):
- **Retained bridge snippet & compiler failure (Step `1878` & [`steps/1874/output.txt`](file:///Users/sydney/.gemini/antigravity/brain/035cfd35-895c-497a-927a-3af4da50a6b1/.system_generated/steps/1874/output.txt), `SHA256: e197fb6d905f6d523b6d18145c57fa9ad519f200d5566aa04c61c565c0f8d22c`):** Generated `V8.kt:12310-12315` emitted `val _result = run { __self.null() }`, failing `compileSwiftExportMainKotlinMacosArm64` at `V8.kt:12313:19` (`Cannot infer type for type parameter 'R'`) and `V8.kt:12313:32` (`Reference has a nullable type 'Nothing?'` / `No receiver can be passed to this function or property`).
- **Source locations:**
  - `src/common/commonMain/kotlin/io/github/kotlinmania/v8/Isolate.kt` lines `83, 85`: `` internal fun `null`(): UnsafeRawIsolatePtr = nullValue() `` paired with `public fun nullValue(): UnsafeRawIsolatePtr = UnsafeRawIsolatePtr(null)`.
  - `src/common/commonMain/kotlin/io/github/kotlinmania/v8/Function.kt` lines `127, 129` (`PropertyCallbackArguments`) and lines `179, 181` (`FunctionCallbackArguments`): `` internal fun `this`(): Object = thisValue() `` paired with `public fun thisValue(): Object`.
- **Qualification:** This preserves the public operations for Kotlin and Swift consumers via an explicit **project-specific public spelling migration** (`nullValue()` / `thisValue()`); it is **not** a repair of the Swift Export bridge generator itself and must **not** be cited to justify hiding public APIs without a public replacement. The Syn producer owner received the exact regression evidence and recorded backticked-keyword escaping in [`kotlin-swift-export-tooling/verification/UPSTREAM_ISSUES.md`](file:///Volumes/stuff/Projects/kotlinmania/kotlin-swift-export-tooling/verification/UPSTREAM_ISSUES.md) (local documentation only; no external upstream issue was filed).

<a id="16-public-mutablemap--mutableset--mutablelist-properties-trigger-global-ktypewkotlincollectionsmutableset-already-exists-during-static-linking"></a>
### 16. Public `MutableMap` / `MutableSet` / `MutableList` properties trigger `Global 'ktypew:kotlin.collections.MutableSet' already exists` during static linking

**Symptom.** `linkSwiftExportBinaryDebugStaticMacosArm64` fails during LLVM
bitcode generation (`ObjCExportCodeGenerator.emitCollectionConverters`) with:

```
e: Compilation failed: Global 'ktypew:kotlin.collections.MutableSet' already exists
java.lang.IllegalArgumentException: Global 'ktypew:kotlin.collections.MutableSet' already exists
```

**Root cause.** Even when `MutableSet` never appears directly in your repo's
source code, exposing a public `MutableMap<K, V>` property on any public class
(for example, `val slots: MutableMap<K, V>`) causes Swift Export to bridge
`kotlin.collections.MutableMap` in `KotlinStdlib.kt`. That in turn transitively
bridges `MutableMap.entries` and `MutableMap.keys` as
`kotlin.collections.MutableSet` (`KotlinStdlib.kt:20` `@file:BindClassToObjCName(kotlin.collections.MutableSet::class, ...)` plus reverse `entries`/`keys` bridges at `KotlinStdlib.kt:341, 351`), which collides with Kotlin/Native's
`ObjCExport` writable type-info globals (`ktypew:kotlin.collections.MutableSet`)
when linking the static archive.

**Fix.** Inspect `build/SwiftExport/macosArm64/Debug/files/<Module>/<Module>.swift`
for `ExportedKotlinPackages.kotlin.collections.MutableMap` (or `MutableSet` /
`MutableList`) to pinpoint the declaration pulling mutable stdlib collections
into the bridge. Check upstream Rust visibility first: if the declaration is
genuinely crate-private (`pub(crate)` or private in `tmp/`), scope it to
`internal`; if it is genuinely part of the upstream public API, evaluate a reviewed migration to an immutable `Map<K, V>` / `Set<T>` / `List<T>` view plus explicit typed mutation methods rather than removing public mutation capabilities.

**Evidence.** Discovered and independently verified in `v8-kotlin` ([PR #121](https://github.com/KotlinMania/v8-kotlin/pull/121), merged to `main` at `4be04116172bd955580511da3ab264eb69797c00`):
- **Retained failure log ([`steps/1904/output.txt`](file:///Users/sydney/.gemini/antigravity/brain/035cfd35-895c-497a-927a-3af4da50a6b1/.system_generated/steps/1904/output.txt), `SHA256: 4eba8703b0e74c84a6b899d252aa2a2d6e05b3fbb722c9b0ed1334bf8767ceaf`):** `KotlinStdlib.kt:20` (`BindClassToObjCName` on `MutableSet`) + reverse `entries`/`keys` bridges at `KotlinStdlib.kt:341, 351` triggered `Global 'ktypew:kotlin.collections.MutableSet' already exists`.
- **Upstream Rust parity:** `src/common/commonMain/kotlin/io/github/kotlinmania/v8/Isolate.kt:97` (`internal val slots: MutableMap<String, Any> = mutableMapOf()`) on `class IsolateAnnex internal constructor(...)` corresponds to upstream Rust `tmp/v8/src/isolate.rs:1899` (`pub(crate) struct IsolateAnnex`) and its private field `slots: HashMap<TypeId, RawSlot, BuildTypeIdHasher>` at line `1901`. Making `slots` `internal` is therefore faithful crate-private visibility alignment, **not** hiding a public upstream Rust API.
- The Syn producer owner received the exact regression evidence and recorded the `ktypew:kotlin.collections.MutableSet` duplicate-global symbol collision in [`kotlin-swift-export-tooling/verification/UPSTREAM_ISSUES.md`](file:///Volumes/stuff/Projects/kotlinmania/kotlin-swift-export-tooling/verification/UPSTREAM_ISSUES.md) (local documentation only; no external upstream issue was filed).
- **Build-mode and test-count qualification on `v8-kotlin` (`4be04116172bd955580511da3ab264eb69797c00`):** Passing Step `1942` ([`steps/1942/output.txt`](file:///Users/sydney/.gemini/antigravity/brain/035cfd35-895c-497a-927a-3af4da50a6b1/.system_generated/steps/1942/output.txt), `SHA256: ee9995f8033abadf5fe5ee135617c0207ed80ec7370a6abab75a114871240228`) executed **1 Swift Testing test (`testSwiftModuleLoads`, passed)** exercising real `PropertyAttribute`, `PropertyFilter`, `HandlerFlags`, `MicrotasksPolicy`, and `version` operations (despite XCTest's separate `0` summary). However, retained `.build/arm64-apple-macosx/debug/description.json` (`SHA256: 8928e61883026d1a103582e9c599ba389f804abd5a1f077bea29b814da4c0d92`) and `v8-kotlin/build.gradle.kts` (`lines 370–374, 1389, 1474`) confirm that `v8-kotlin` disabled generated Kotlin bridge `-Werror`, rewrote `swift-tools-version: 6.0` $\to$ `5.9`, and compiled all Swift targets **without `-warnings-as-errors`** (see item 5 at the end of this document).

## Reproducing the isolated Kotlin 2.4.20 Swift bridge repair (2026-10-20)

This is a diagnostic reconstruction of the released exporter, **not an installed compiler replacement or a product rollout**. The generated Native and Swift code compiles strictly and **all twelve Swift/Flow tests pass under Swift 6 with warnings as errors**. Three earlier sendability errors were corrected in the fixture's ownership model, without further runtime/exporter changes. No Syn/Libc/Async/Image integration, full KMP gate, CI, release or Maven publication is established by this experiment.

### Inputs and isolation

Use the existing artifact directory, relative to the workspace root:

```
automation-artifacts/2026-10-20-syn-isolated-exporter-integration
```

The host was EmberHarmony.local, Xcode 26.6 build 17F113, Apple Swift 6.3.3 (`swiftlang-6.3.3.1.3`), with the fixture targeting macOS ARM64 15.0. KGP/compiler/exporter are **2.4.20**; coroutines is **1.11.0**. The original exporter JAR SHA256 is `24c6159bcf0087ecf86bfc9bad01a975e192abcf75005b248292d85a139c2055`; the isolated final exporter SHA256 is `170771a2b55387a94ab1adab9ecd259c3eed9d7622997150f259cb8069896a37`. ZIP timestamps can change a rebuilt archive's whole-file hash: verify its entry contents and class audit, rather than assuming archive-byte reproducibility.

All six upstream source files are pinned to official Kotlin revision **890ac1d94fdb80eb85f0eeb5be5e4352df987b2f**, resolved from v2.4.20:

- `native/swift/sir-providers/src/org/jetbrains/kotlin/sir/providers/impl/BridgeProvider/TypeBridging.kt`
- `native/swift/sir-providers/src/org/jetbrains/kotlin/sir/providers/impl/BridgeProvider/SirBridgeProviderImpl.kt`
- `native/swift/sir-printer/src/org/jetbrains/sir/printer/impl/SirAsSwiftSourcesPrinter.kt`
- `native/swift/swift-export-standalone/resources/swift/KotlinCoroutineSupport.kt`
- `native/swift/swift-export-standalone/resources/swift/KotlinRuntimeSupport.swift`
- `native/swift/swift-export-standalone/resources/swift/KotlinCoroutineSupport.swift`

`producer-input-manifest.json` records each upstream path, Git blob, SHA256 and the sixteen exact compiler/exporter dependency JAR paths/hashes. Original files are in `producer-inputs/`; changes are in `producer-src/`. The Kotlin coroutine resource SHA256 is `cc30c61170e4a5fee58fcdda09539f30781cbada4c59389d99da8fed2a6f6d6f`. Its identity was also observed in 2.4.21, but **this exporter integration was tested only on 2.4.20**.

`prepare-producer.rb` acquires the initial exact sources and hash-checks/copies the captured dependency inputs; it deliberately refuses to overwrite originals. `prepare-swift-strict-sources.rb` acquires the additional printer/Swift resources. The exact official reverse-provider API response is retained in `sir-provider-api.json`; `build-reverse-producer.rb` verifies/records its source. Do not use an unrelated local compiler source archive as the release source.

`seed-isolation.rb`, `copy-native-inputs.rb` and `restore-native-dependencies.rb` copied dependency inputs into `gradle-home/`, `native-sdk/` and `konan-data/`, materializing input symlink targets as ordinary files. The restored `.extracted` receipt names only the three copied LLVM/libffi/lldb dependencies. The fixture's `kotlin.native.home` points to the copied SDK. Temporary files, Gradle/Konan/compiler caches, SwiftPM build/package/module caches, Java user/IDE directories and product outputs are inside the artifact directory. Installed compiler/dependency inputs were read-only; `input-reverification.json` independently rechecks all sixteen original/copied JARs. No Maven-local or sibling project substitution is involved.

### Source changes and their purpose

1. In TypeBridging's Kotlin conversion templates, consume the expression as the result of `run`, then transform it with `let`: Unit uses `run { expression }.let { }`, Boolean sentinels use `.let { true }`, nullable Nothing uses `.let { null }`, and the impossible reverse Nothing fallback uses `.let { throw IllegalStateException() }`. This evaluates the expression once, retaining effects and exception propagation without unused expressions.
2. In Swift conversion templates, explicitly consume discarded bridge values with `_ = expression`. Give callback result locals their actual Swift type. For a Nothing-returning callback/reverse method, emit only the terminating call; do not create a Never local or append an unreachable return/fatalError. A forward noreturn C call already imports as Never and needs no extra fatalError. The failed explicit `return <Never>` to a Bool bridge attempt is preserved; the verified form is a terminating call statement.
3. In the Kotlin coroutine resource, replace the completed-error Elvis with an explicit local error check followed by `return null`. Match opaque iterator receiver casts with `SwiftFlowIterator<*>`/`<*>?` and the continuation payload with `SwiftFlowIterator<*>.Value?`, avoiding invariant unchecked casts while preserving the actual inner payload.
4. In the printer and both Swift runtime resources, replace deprecated implementation-only imports with `internal import`, following [Swift SE-0409](https://github.com/swiftlang/swift-evolution/blob/main/proposals/0409-access-level-on-imports.md). In KotlinRuntimeSupport.swift remove the same-type `[Any]` forced cast and replace the optional-only protocol-wrapper cast with its explicit unwrap. In KotlinCoroutineSupport.swift use `catch is CancellationError` instead of binding an unused error.

There are no generated-source rewrites, warning suppressions, diagnostic severity changes, library-evolution flag workaround, Objective-C API decorators or `@JvmName` changes. The fixture applies the documented module-level `InternalForInheritanceCoroutinesApi` opt-in required by the generated Job implementation; this is explicit API consent, not a warning waiver.

### Build and verification commands

Obtain an explicit shared local slot before any compiler/build/test command. A returned `execution-grant.json` is intentionally inactive: the coordinator must grant a new run. Iris is excluded and must not be started, resumed or messaged. Keep the completed inspection receipt; do not reinterpret it as permission to interact with Iris.

From the artifact directory, archive existing logs before a new experiment, then run sequentially:

```sh
ruby build-producer.rb
ruby prepare-swift-strict-sources.rb
ruby build-printer.rb
ruby build-reverse-producer.rb
ruby package-producer.rb
ruby run-fixture.rb baseline
ruby run-fixture.rb patched
ruby run-swift-tests.rb
ruby prepare-serial-tests.rb
ruby run-swift-serial-tests.rb
ruby compare-abi.rb
```

The baseline fixture is an **expected failure described below**; the corrected full Swift suite must succeed with twelve executed tests. Inspect each result before proceeding, rather than chaining with `&&` or treating every nonzero exit as the same failure. The serial subset is retained for historical comparison and is not a substitute for the full suite. Compiler args, environment, source hashes, exits and logs are persisted by each runner. The scripts contain the exact captured classpath and input selection; `exporter-selection.init.gradle` replaces only the exporter on the original sixteen-JAR worker classpath, including the IntelliJ coroutine dependency. An ordinary Gradle-resolved replacement classpath previously lost that dependency and was rejected.

The source-only producer reconstruction uses the copied 2.4.20 K2JVMCompiler with `-Werror`, module-specific friend path/name, JVM target 1.8 and no-compatibility default methods. This JVM invocation builds the exporter implementation; product validation is Native/Swift. Official sources contain newer square-bracket Pair destructuring syntax: the build scripts normalize only compiler inputs to equivalent parentheses in both controls (ten TypeBridging sites, three printer sites and four provider sites). Acceptance requires original versus rebuilt-baseline instruction equality for every packaged class, not merely successful compilation.

The JAR audit checks **6263 entries**, no additions/removals, exactly **nine classes plus three resources** changed. Nine original/baseline/patched `javap -p -s` signatures match; baseline `javap -p -c` instruction blocks match original after constant-pool index normalization and member-order-independent comparison. The initial printer mismatch was only method ordering; no instruction differences were waived.

`run-fixture.rb` invokes the read-only Gradle 9.8.0 distribution's `embedSwiftExportForXcode --continue --no-daemon --max-workers=2 --no-configuration-cache`, writes variant-separated output, and requires `allWarningsAsErrors=true` for actual generated Native compilation. `run-swift-tests.rb` uses SwiftPM with Swift 6 test sources and `-Xswiftc -warnings-as-errors`. Its explicit `-target arm64-apple-macosx15.0` aligns dependency compilation with the fixture's declared target: the generated package itself omits a deployment minimum. This proves macOS 15 compatibility, not support for older Apple OS versions.

### Verified results and remaining limits

- Final baseline session **12504** exited 1 with the expected **29 Native warnings**: 26 unused expressions, one redundant Elvis and two unchecked casts. Baseline generation and its Swift modules succeeded; strict Native compilation prevented a baseline linked binary.
- Final patched session **56059** exited 0: actual exporter generation, strict generated Native compilation, static linking, SPM generation and embedding passed. Earlier patched **95692** also exited 0, but was the initial Native-only candidate, not a Swift/Flow behavior pass.
- Historical full Swift session **18167** exited 1 after generated Swift compiled cleanly. Three fixture tests shared non-Sendable iterator/probe captures across Task boundaries: suspended producer/resumption, cancellation while waiting for the producer, and concurrent-next rejection. The source and failed diagnostics are preserved under `sendability-review/`. These are fixture ownership errors, resolved by the actor correction below; no unchecked Sendable conformance or relaxed language mode was introduced.
- Final full Swift session **48138** exited 0: **all twelve Swift Testing cases executed and passed, zero failures**, using Swift 6 and `-warnings-as-errors`. This includes all three formerly failing concurrency cases. The exporter JAR, generated product source and runtime resources were unchanged for this correction.
- Separate serial session **31865** exited 0: **nine actual Swift Testing cases passed**, preserving original test bodies. These exercise constructors/setters, both Unit callback directions, nullable Nothing, String order, full-width UInt, nullable object payloads, empty/repeated completion, exact original Kotlin error identity, iterator release before start and cancellation of a backpressured producer on release. `serial-subset.json` records the full/subset hashes and three omitted compile-blocked cases. This is explicitly a partial behavior result, not a green full-suite gate.
- The original repeated-error expectation was corrected against [Apple's AsyncIteratorProtocol contract](https://developer.apple.com/documentation/swift/asynciteratorprotocol): after an error, future next calls must return nil. The final test checks the first wrapped error against the producer's original object and then checks repeated terminal nil; no runtime behavior was changed to satisfy this correction. The fetched official text is retained in `asynciteratorprotocol.md`.
- Final ABI session **35990** exited 0. All **five generated C headers are byte-identical**, and all **37 declared Integration C exports** exist in the patched static library. Compiled Swift API dumps for Integration, KotlinRuntimeSupport and KotlinCoroutineSupport have identical declarations after excluding import nodes. Complete raw dumps differ by the expected ImplementationOnly import attributes; they are retained, not described as identical. This is not a cross-version binary compatibility guarantee: there is no strictly linked baseline binary.

Never callback signatures/generation compile, but no deliberately fatal Swift Never callback was executed. Other Apple targets, 2.4.21 exporter execution and all product KMP/Swift integration gates remain unverified. The twelve-case isolated full suite is green; the earlier nine-case subset remains historical evidence.

### Correct Swift 6 ownership in the concurrency fixture

All three diagnostics pointed to fixture Task initializers. A closure captured a non-Sendable iterator associated with a probe still used by the caller. Declaring those exported reference types Sendable would assert a broader concurrency contract and was not required. The verified correction is the private `SuspendedFlowReceiver` actor in `swift-harness/Tests/ExporterIntegrationTests/BridgeTests.swift`:

- Construct and retain both the FlowProbe and its iterator inside the actor. Tasks capture only that actor; probe and iterator references never cross its boundary.
- In its receive method, take a local reference to the iterator and use `try await iterator.next(isolation: self)`, the standard [isolation-aware AsyncIteratorProtocol API](https://developer.apple.com/documentation/swift/asynciteratorprotocol/next(isolation:)). It is available from macOS 15, matching this fixture. The installed SDK's protocol extension and Apple's exact API documentation were read and recorded in `sendability-review/next-isolation.md`.
- Return only Sendable String?/Int32/Bool observations. Read the Kotlin probe's existing thread-safe start-count/stopped state inside the actor, with a five-second deadline and suspending one-millisecond clock sleeps. The exported non-Sendable probe is not sent to a second executor merely to await a signal.
- Actor reentrancy while the first receive is suspended allows the second actor call to exercise the real Kotlin concurrent-receiver guard. The test still requires rejection, releases the producer, verifies the first receiver gets its value and verifies completion/finalization. Cancellation still cancels the actual pending receiver Task, requires CancellationError and checks the producer finally block.

No tests were removed, no unsafe Sendable declaration or suppression was added, and no runtime/exporter change was needed for sendability. After an explicit coordinator regrant, run the existing `ruby run-swift-tests.rb` once; final session 48138 built, linked and executed all twelve cases successfully. `sendability-review/classification-and-candidate.json`, `actor-full12.log` and `actor-full12-result.json` preserve exact source hash, diagnosis and result. No ABI rerun is needed for this fixture-only change; the audited exporter/runtime/native inputs and their ABI receipt remain unchanged.

### Return and cleanup boundary

All owned compiler/build/test commands exited and the local slot was explicitly returned in `local-slot-return.json`; the active grant is false. Preserve source manifests, input copies, scripts, full failed/passing logs and ABI receipts. Reverting this experiment requires no installed compiler or cache restoration because those were never changed. Do not delete shared inputs or other owners' artifacts. Any removal of this artifact directory should be a separately authorized cleanup after retaining its evidence. Product adoption, installed deployment, CI/publication and fleet rollout require separate approval and their own full gates.

### Actual syn-kotlin isolated validation (2026-10-20)

This follow-up used the existing shared `syn-kotlin` checkout at **3cfb17f796a13499be94019cd88947420ad36768**, with the same verified exporter SHA256 **170771a2b55387a94ab1adab9ecd259c3eed9d7622997150f259cb8069896a37** and official Kotlin 2.4.20 revision **890ac1d94fdb80eb85f0eeb5be5e4352df987b2f** described above. It is an actual-library validation attempt, **not a green integration gate**. No replacement exporter incorporating the later enum candidate has been compiled or verified.

All paths below are relative to the workspace root. The owned artifact directory is `automation-artifacts/2026-10-20-syn-isolated-library-validation`. Its `inputs.json` records the exact source/configuration hashes, original SDK and generated-output baseline, exporter hash and copied inputs. `prepare-inputs.rb` requires the recorded clean source revision and materializes dependency/SDK inputs as ordinary files in this directory. It copies input binaries, never a Syn source snapshot. Installed Gradle/Native inputs, the shared dependency caches and the existing project Android SDK are read-only; no Maven-local or sibling substitution is used.

The source preparation introduced two narrow changes: avoid rewriting an already identical `local.properties`, and let the real Swift harness's Package.swift read `KOTLINMANIA_SWIFT_EXPORT_BUILD` while retaining `../build` as the default. The six existing Swift test bodies were unchanged. The tested build-script formatting was subsequently corrected to put its multiline initializer on the next line; that correction has not been rebuilt and must not be described as the exact tested source.

After a fresh serialized LOCAL grant, the exact entry points are:

```sh
cd /Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-20-syn-isolated-library-validation
ruby prepare-inputs.rb
ruby run-library.rb preflight
ruby run-library.rb gate
ruby verify-published-metadata.rb
ruby correlate-consumed-dependencies.rb
ruby audit-generated-api.rb
```

Preparation refuses unexpected source state; do not overwrite a previous run or re-activate its grant to repeat this sequence. Use a newly allocated owned artifact directory and reviewed input manifest for a subsequent revision. The current grant is inactive. The gate expands to `CI=true test build --continue --no-daemon --max-workers=2 --no-configuration-cache`, with `--project-cache-dir` and an init script inside the artifact directory. It is a full KMP gate, not a JVM smoke test. `logs/gate-invocation.json` preserves every argument and environment value.

`isolation.init.gradle` sets layout.buildDirectory before project configuration, selects only the verified exporter on the original compiler worker classpath, redirects Node/Yarn locks and SwiftPM dependency checkout paths, and rejects declared task outputs outside the artifact root. Gradle, Konan, Kotlin persistent state, Java temporary/user/IDE directories, Android user/AVD state, Swift/Clang module caches, npm/Yarn caches and all products are isolated. `native-sdk/` and `konan-data/` are owned input copies. The init script removes only the known appended GenerateSPMPackage action that rewrites generated source/manifest; the real generation action remains. Preflight observed StandardTaskAction plus AbstractTask$TaskActionWrapper. The later action receipt captured a mutable action list after mutation, so its original_actions field is incomplete; the preflight inspection and removed-wrapper field are the evidence for the two-action starting state.

The strict Swift build uses `strict-swift.xcconfig` with warnings as errors. `run-real-swift-tests.rb` invokes the existing shared `src/swift/swift-test-harness` with artifact-local SwiftPM scratch/package/config/security/module paths, Swift 6 and `-Xswiftc -warnings-as-errors`, targeting macOS ARM64 15.0. It replaces the outer smoke runner's nested Gradle invocation, retaining the real embed dependency and six real test bodies. No generated output was patched to force a pass.

Observed results:

- Session **49789** performed actual Native compilation/static linking and the full KMP task graph. Fresh XML reports contain **3,896 passing cases, zero failures/errors/skips across eleven environments**: JVM 356; macOS ARM64, Android host, JS browser/node, Wasm JS browser/node, Wasm WASI node, iOS simulator ARM64, tvOS simulator ARM64 and watchOS simulator ARM64 each 354. `actual-test-results.json` records every XML hash. This is no claim of Windows/Linux runtime execution or Android device execution.
- **Zero of the six real Swift tests executed.** Strict dependency SPM compilation failed on six exhaustive `Delimiter`/`Spacing` switches with unreachable `default: fatalError()`, at generated ProcMacro2 Swift lines 21/32/71/83/92/125. `logs/current-spm-diagnostics.json` and the decoded Xcode activity log preserve the diagnostics.
- The fresh KGP report records four failed tasks: `macosArm64DebugBuildSPMPackage`, `detektMacosArm64SwiftExportMainSourceSet`, `ktlintKotlinScriptCheck`, and `runKtlintCheckOverMacosArm64SwiftExportMainSourceSet`. Detekt identifies the generated coroutine resource's unused private `Throw` class at line 127 and reports three analysis compiler errors. The source build-script lint error was at line 173; its formatting correction is source-only. The generated-source ktlint task's precise exception was not recovered before client loss; it remains an unclassified failure, not an assumed filter defect.
- The exec connection was lost before the outer runner could write `gate-result.json`. Its owned daemon log explicitly records client disconnection, build cancellation and daemon shutdown. The fresh KGP build report records the failures, but there is no reliable numeric process exit or final full-gate completion. `local-slot-return.json` records this distinction; no cancellation/restart was requested by this worker.
- `generated-api-abi.json` verifies **six byte-identical generated C headers** against the retained pre-existing baseline and identical selected public declaration lines in seven generated Swift files. All **7,300 forward Syn C exports** are present in the actual Native static library, SHA256 `2d39857573e887eee5e37a4beb4091f0d475c94f9ed53efb1d861e2b486e5fed`. The **774 reverse Swift declarations** and a complete compiled real-library Swift API/ABI comparison remain unverified because SPM failed. Declaration-line comparison is not a complete binary ABI proof.
- The corrected independent Central audit fetched metadata for 108 coordinates and matched 142 of 156 copied binary candidates. Fourteen unmatched older Quote cache candidates were preserved, not silently replaced. The fresh KGP compiler-argument report exposes four consumed JVM JAR paths; all four match Central SHA256 metadata, including Quote. This limited path coverage does **not** prove all consumed Native/Android artifacts. `published-metadata-verification.json` and `consumed-dependency-verification.json` record the scope. The older filename-based verifier used incorrect KMP URLs; its 404s are not evidence of an unpublished dependency.

Source-only producer analysis fetched and read `native/swift/sir-light-classes/src/org/jetbrains/sir/lightclasses/nodes/SirEnumFromKtSymbol.kt` at the same official revision (blob `b568e90314c57fbd493a1554a2bab395a753331d`). The candidate omits the fallback only in the three exhaustive nonempty switch-self bodies, retaining empty-enum and integer-input fallbacks and invalid-string validation. `enum-producer-analysis.json` and the original/candidate/diff record it. **No compiler, class ABI, packaging or behavior validation has run on that candidate.** It must not replace the proven artifact for another owner's build until separately validated.

The full real-library gate remains blocked by strict exporter diagnostics and generated-source lint, plus the interrupted aggregate and the unretested formatting correction. The earlier twelve-test fixture remains valid fixture evidence only. Installed tooling and shared caches require no rollback because they were not modified. Preserve raw receipts/logs and coordinate the next serialized build; do not delete inputs, deploy the exporter, publish a toolchain/library, or imply fleet adoption from this validation.

### Swift language-mode qualification and enum follow-up preparation

The retained Syn Xcode compiler activity confirms `KotlinRuntimeSupport` was compiled with **`-swift-version 5 -warnings-as-errors`**. A Swift tools 6 test harness does not force a generated tools 5.9 dependency package into Swift 6 mode. The twelve passing fixture test cases are real Swift 6 test-target evidence; they are **not** proof that every generated dependency module compiles in Swift 6 language mode. `automation-artifacts/2026-10-20-syn-isolated-library-validation/swift-language-mode-review.json` records the observed command and activity-log hash.

Libc's subsequent isolated run explicitly enabled Swift 6 for generated dependencies and reported that the exported `KotlinError: Error` value stores a non-Sendable `KotlinBase`. This is separately observed Libc runtime evidence, not a new Syn run, and no `@preconcurrency`, unsafe Sendable declaration or language downgrade has been applied as a repair. A complete Swift 6 generated-runtime gate remains unverified.

The parent-authorized enum follow-up is prepared under `automation-artifacts/2026-10-20-syn-isolated-library-validation/enum-repair/`. Its inactive grant, frozen input hashes, bounded allocation request, official baseline/candidate compiler and class/JAR audit scripts, strict generation runners, six Swift enum behaviors and lint-provenance plan are inspectable there. No producer compile, fixture generation or new test has executed in this follow-up. The enum tests cover empty/single/multiple cases, descriptions and ordinals, invalid/extreme inputs and actual Native reference round-trips. This diagnostic isolation of the enum family must record generated dependency language modes and cannot establish that the separate Swift 6 runtime issue is fixed. Preserve the earlier interrupted real-library result and 3,896 passes as historical exact-revision evidence; no new full KMP result is claimed.

### Bounded enum result and immutable runtime diagnostic candidate

The bounded follow-up used unchanged clean Syn **61290caba7ebe4d23ee5ef613d70634f08bd4434**. Both official baseline and candidate producer compilations passed `-Werror` in session **89168**. Packaging did not pass: audits 69252/7856/20102 rejected reconstruction differences. Constant-pool indices, `ldc`/`ldc_w` widths and instruction-label relocation explain some differences, but unchanged `SirEnumFromKtSymbol.attributes_delegate$lambda$0` still differs in local register allocation and instruction/control layout. No broader difference was waived. Raw disassemblies and method-graph differences remain under `enum-repair/logs/`. No candidate JAR was produced; zero enum embeds/Swift tests executed.

Lint dry run **4284** exited 0 but includes `compileKotlinMacosArm64` and `macosArm64DebugSwiftExport`, so no diagnostic execution was allowed or attempted. Initial sandboxed wrapper 39490 failed before Gradle initialization because its lock-contention socket was denied; the same dry run succeeded with reviewed socket access. `enum-repair/local-slot-return.json` records all terminal sessions and inactive allocation. Session 49789 was not restarted; no new full gate supersedes its historical 3,896 cases.

Sydney directed resetting expectations against Rust semantics rather than preserving current code as dogma. The full existing Syn `tmp/syn/src/error.rs` and `thread.rs` were read: diagnostic messages are owned Strings; span access is thread-bound and falls back to call-site on other threads. Libc's owner independently reviewed its raw status/errno API and found no original Throwable-object identity requirement. This supersedes the experiment's earlier requirement to preserve unrestricted Swift `KotlinError.wrapped` object access.

The uncompiled/unpackaged source candidate is `automation-artifacts/2026-10-20-syn-isolated-library-validation/runtime-snapshot-repair/producer-src/KotlinRuntimeSupport.swift`, SHA256 **69ce24e6cad0169a25a129d79cc597883c8359e23d6f3241a485c41f0c6fee25**. `KotlinError` stores only `public let message: String`; description returns it. `init(wrapped: KotlinBase)` retains its synchronous call signature, immediately captures the existing message/fallback expression, and stores no Kotlin object. `init(message: String)` constructs an owned diagnostic directly. Removing public mutable wrapped is intentional API change, not an ABI-neutral rename or unchecked assertion. The two confirmed synchronous/asynchronous producer construction sites retain their calls; strict compilation must verify the source-level compatibility.

`runtime-snapshot-repair/candidate-contract.json` pins the resource/Rust inputs with verified=false. Prepared fixture changes retain all twelve behavior scenarios, replace object identity with diagnostic stability after source-message mutation, and add a detached-task throw/catch test: thirteen candidate cases. MutableStateFlow protects the source-message mutation. No fixture case was deleted, no generated output edited, and no unsafe Sendable/preconcurrency/handle workaround or warning waiver introduced. Libc independently reviewed this source contract, not its compiler result.

Next runtime validation must explicitly compile generated dependencies in Swift 6 with warnings as errors. The earlier dependency-mode-5 fixture does not prove that coroutine Task captures or generic continuation transfers satisfy Swift 6. Fresh producer generation, actual untouched-runtime regression, all thirteen fixture cases and real-library exact-head gates remain unverified, and require fresh serialized allocations. The proven exporter SHA170771a2 remains unchanged and does not contain this candidate. No installed tooling, shared caches, rollout or publication changed.

### Resource-only snapshot validation under explicit Swift 6

The next authorized bounded allocation froze 31 input files in `runtime-snapshot-repair/frozen-inputs.json` (manifest SHA256 `5cb83fe08d538705177840f59e6ae86c30f1addde01459273ee7a31c5deb1142`) and retained product checkout **61290caba7ebe4d23ee5ef613d70634f08bd4434** unchanged. Exact runners in that artifact directory are `package-runtime.rb`, `run-runtime-fixture.rb baseline`, `run-runtime-typecheck.rb baseline`, `run-runtime-fixture.rb corrected`, then the remaining corrected typecheck/13-test runner only when authorized and module compilation is green. Do not execute this list blindly: inspect each result and stop on a new unexpected diagnostic.

`package-runtime.rb` session **79691** exited 0. The candidate JAR is `runtime-snapshot-repair/toolchain/runtime-snapshot-swift-export-embeddable-2.4.20.jar`, SHA256 **7b0007f8f93843a0bcf785cd6fc4d85f2540f14e808f07a62595cb53b61c8695**. The full 6,263-entry audit proves exactly `swift/KotlinRuntimeSupport.swift` changed, zero class changes, no additions/removals. The proven SHA170771a2 JAR is unchanged. No K2 recompile or enum-class packaging occurred.

Both tiny fixture builds use explicit `SWIFT_VERSION = 6.0` and warnings-as-errors in the owned Xcode config; recorded frontend commands verify language mode 6 for KotlinRuntimeSupport, KotlinCoroutineSupport, ExportedKotlinPackages and the exported coroutines module. All Gradle/Konan/product/cache writes stay in the existing owned artifact root; installed/shared caches remain read-only. The actual fixtures and thirteen candidate behaviors use the new owned-message contract described above.

- Baseline build **37556** exited 1 and reproduced the exact non-Sendable `KotlinError.wrapped` error. Native compilation and static linking passed. This is the expected negative, not a product pass.
- Pre-typecheck wrapper **47234** launched no Swift compiler: a strict resource-hash assertion discovered the exporter appends exactly one trailing LF. Full byte comparison proved that sole difference, and `input-manifest.json` pins the actual untouched generated runtime/C inputs. No generated output was changed. The generated baseline runtime SHA256 is `59c15fb0cf52f6002a4848b491593031fbcc021ed8902a132dca1691db12f8dd`, distinct from the raw JAR resource hash.
- Baseline focused regression **85939** returned wrapper exit 0 because the actual Swift compiler exited **1** with the exact expected Sendable diagnostic under `-swift-version 6 -warnings-as-errors`. `logs/baseline-result.json` preserves both statuses.
- Corrected build **40499** exited 1 on the next producer family: generated KotlinCoroutineSupport.swift lines **66 and 91** capture non-Sendable `KotlinTask` in `@Sendable` cancellation closures. Native compilation/static linking passed. KotlinRuntimeSupport's corrected Swift 6 module was emitted successfully; its module hash and actual compiler modes are in `corrected-output-evidence.json`. This validates that module's snapshot correction, not the whole runtime or its behavior suite.

The first new blocker stopped execution. The standalone corrected-output typecheck and **all thirteen behavior cases remain unexecuted**. `corrected-input-manifest.json` pins freshly generated untouched corrected runtime/C inputs for a future allocation. Four generated C headers are byte-identical between these two tiny fixture variants; this is not a real Syn/Libc binary ABI guarantee, and KotlinError's intentionally changed Swift public API is not ABI-neutral.

All owned commands are terminal, the grant is inactive, and `runtime-snapshot-repair/local-slot-return.json` records the explicit return. Cancellation follow-up must preserve native-job/Swift-task cancellation and lifetime semantics; simply asserting Sendable or holding a lock around a native call is not a correction, because callbacks may re-enter Swift. No additional compile, retry, suppression, unsafe Sendable, generated-output edit, installed deployment, adoption, CI, full KMP repeat or publication followed this blocker.

### Cancellation ownership follow-up: prepared, not executed

A source-only candidate for the two Swift 6 cancellation captures is recorded under `automation-artifacts/2026-10-20-syn-isolated-library-validation/runtime-cancellation-repair/`. Its `producer-src/KotlinCoroutineSupport.swift` SHA256 is **f8acaaeb7f4f42a3ea1948b9e89265c5e16274f66dd2b674969d9b804055664c**. The bound KotlinTask remains non-Sendable. A checked `KotlinTaskState` protects the current-task handle, and a checked `KotlinCancellation` owns the bound native Job wrapper through Mutex storage. Native callbacks capture the checked state; Sendable cancellation handlers capture the checked owner. Native calls occur after releasing the owner mutex, with explicit lifetime extension on direct calls. No unchecked Sendable conformance, preconcurrency conversion or new Objective-C annotation was added.

This is a candidate, not a proven ownership repair. `candidate-contract.json` explicitly requires compiler validation of sending parameters and Mutex-region transfers, review of inner externalRCRef lifetime at every generated conversion, and reentrancy checks for callbacks registered on an already-cancelled Job. The installed SDK explicitly marks UnsafeCurrentTask's Sendable conformance unavailable. Kotlin's [Job concurrency contract](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/) permits concurrent Job operations, but does not establish Swift wrapper lifetime. No lock may be held across a native callback-capable call merely to make a declaration compile.

`run-cancellation-regression.rb baseline` is prepared against actual unchanged coroutine source generated in session40499, its emitted Swift6 runtimeSupport module, and the original C module headers. `baseline-input-manifest.json` pins these real inputs; no fake KotlinBase or generated-output patch is used. The expected negative requires actual nonzero compiler exit and the precise KotlinTask Sendable-capture diagnostic. A corrected mode requires separately pinned fresh producer output and compiler exit0. Neither mode has run. `prepared-allocation-request.json` freezes the candidate/negative inputs and requests a new bounded slot; current grant is inactive. All thirteen real fixture behavior cases remain mandatory and unexecuted in this new contract.

Separately, the template owner received read-only actual-library KLIB receipts in `syn-local-klib-handoff.json` and `syn-native-dependency-path-handoff.json`. The existing macOS Syn KLIB is an unpacked77-file directory with content-tree SHA256 `e208fb3a4c249ff7f64f81d13cc9f40c0f55600820537eb8756f9d6f0c942786`; its source-root historical output is byte-identical to the isolated actual-library output. Actual source was compiled at3cfb17f; current61290ca changes only build-script formatting and all Kotlin source hashes match. Fixture Integration/EnumFixture KLIBs are not Syn library outputs. The retained report does not expose the Native compiler argument list; cached resolved-coordinate candidates are therefore qualified, not represented as proven consumed inputs. Atomicfu's main/cinterop files have distinct required identities; Quote has an older duplicate plus a current independently published-checksum match. No compilation/download/output recreation was performed for this handoff.

### Lifetime review correction and test-parity qualification

The cancellation candidate above at hashf8acaa is superseded by source SHA256 **374d75a6f978ba56cb3e56b55c822f5f10b15886e4f93d3dd25f13a06f881269** at the same candidate path. The prior source is retained, hash-checked, in `runtime-cancellation-repair/review-history/KotlinCoroutineSupport-f8acaa.swift`. Source review identified that the Swift forwarding getter does not itself inherit KotlinBase's NS_RETURNS_INNER_POINTER lifetime semantics. The candidate now explicitly encloses the entire forward `fn` invocation in `withExtendedLifetime(cancellation)`. Both official `swiftAsyncCall` generation and iterator.next execute their complete foreign call inside this nonescaping scope; direct setTask/cancel calls already extend self. This addresses the identified scope gap in source, not a compiler/runtime proof.

All thirteen candidate scenarios are retained. The existing deterministic cancellation case additionally prepares eight concurrent cancel/release races, permits only successful released-value delivery or CancellationError, waits for producer finalization, and issues late cancellation after the receiver result/clear. The unsafe current-task handle is not copied out of its mutex. Source/race hashes and unproved reverse-transfer/cache-identity/reentrancy requirements remain in `candidate-contract.json` and `prepared-allocation-request.json`. No new compiler, JAR or test execution followed this review; no green fixture claim is made.

The historical 3,896 XML cases also require a newly discovered qualification: **55 executions were empty SizeTest bodies**, five tests across eleven environments. Reading the entire matching Rust test file confirms exact full-value size assertions Expr176, Item352, Type224, Pat184 and Lit24, under compiler/pointer-width/span-location guards. They measure the inline enum value including payload and padding, not the discriminant alone. Current Kotlin AST roots are managed sealed classes without the asserted inline-value representation. Neither comments, counts, serialized lengths, fixed-size dummy buffers nor C structs unused by the actual parser would preserve those operations. The source-only statement mapping and required real-representation/actual-stride proposal are `automation-artifacts/2026-10-20-syn-dependency-repair/size-parity-review/statement-parity-review.json`. Those five false-green test bodies remain unrepaired; neither 3,896 XML cases nor the unaudited remainder establish Rust test parity. The owned historical JSON reports now carry this qualification while preserving their XML totals and raw evidence.

### Bounded cancellation validation: expected negative and pointer-contract failure

The next granted run used the frozen 57-input manifest `42bae75193cf8848add5eecf3bfd4a49c1fd97289db81fe9adec36a914b25b2e` under `runtime-cancellation-repair`. Packaging session71093 exited0: exactly `swift/KotlinCoroutineSupport.swift` changed atop snapshot JAR7b000, with zero class changes and no added/removed entries. The resulting experimental JAR SHA256 is `a44af057f5cfdf81b63a31f653605f377beeaa92558ced3c13a7831bc28fa193`.

`ruby run-cancellation-regression.rb baseline` ran in session87418 against the untouched40499 output: actual Swift6/Werror compiler exit1 reproduced the KotlinTask capture diagnostic; wrapper exit0 means the expected negative matched. `ruby run-runtime-fixture.rb corrected` session10502 exited1 after30seconds/16 executed tasks. Native compile/static link passed. Swift compilation rejected three uses at coroutine source lines66/76/329 because the newly introduced forwarding method returns `UnsafeMutableRawPointer?` while native bridge parameters require nonoptional pointers. The raw compiler log hash is `39315e00058bc46ec2b67143a0b87ed84313e023513b1963835d0ddf0dc7cf7f`. No corrected standalone typecheck or behavior runner followed: **zero of thirteen behavior cases executed**. The allocation was returned with no retry; `local-slot-return.json` records each actual exit separately.

The source-only follow-up `runtime-cancellation-repair/next-candidate/KotlinCoroutineSupport.swift` restores the forwarding getter's nonoptional result contract without casts, force unwraps, suppression or generated edits. Its hash is `b5597d8c475c7c470afb01b737051736dfc0b04ab06e849a0b9e2b719eeaa4f7`. It is uncompiled/unpackaged; the previous frozen candidate and actual generated failure output remain intact. Peer lifetime, cached-wrapper transfer and cancellation reentrancy obligations remain unproved. Run the same bounded audit/negative/fresh-generation/typecheck/all-thirteen sequence only after another allocation, with fresh manifests and output paths. This is not product integration, publication or toolchain adoption.

### Pointer baseline diagnostic parser correction

The separate `runtime-pointer-repair` run packaged source b5597d8c as experimental exporter SHA256 `b49fd36c8a0c7989db5a6a37b9ac91b8f117b1065afa02ca6399523772583877` in session18883; audit again found exactly one coroutine resource delta and zero class/add/remove changes. Baseline session31593 actual Swift6/Werror compiler exit1 reproduced the same three located optional-pointer errors at66:43,76:48,329:126. The original runner exited1 because it counted the three inline highlight renderings again. Raw log SHA256 `42dc07093a40d1e9d97f615b968b58ca4f3a466759fe3d03eb607cde143ff4f3` and original runner remain intact. Offline anchored source-location classification confirms three errors, with no additional family; the mismatch is a harness counter defect, not an inferred producer regression.

No corrected embed or behavior test started in that allocation. The slot was returned. `run-pointer-regression-v2.rb` corrects only diagnostic counting; the fresh87-input continuation manifest is `494aec801d4b52dd52088705c8a6baf669bf6593ad58456d3c367ac69f1d2bdb`. The remaining requested allocation reuses the existing verified JAR and baseline evidence, runs one corrected embed, and only after module success pins fresh output, typechecks it and runs all thirteen cases. No baseline rerun, repack or successful candidate claim is implied.

### Pointer continuation: actual Swift 6 ownership errors

After a fresh87-input check, corrected embed session65330 exited1 after30seconds/16tasks. Native compilation and static linking passed. The nonoptional pointer correction removed the previous three pointer errors. Actual dependency Swift6/Werror compilation then rejected five ownership operations in generated KotlinCoroutineSupport.swift: line37 inout-sending mutex state retained task isolation; lines85 and112 attempted to send the borrowed UnsafeCurrentTask; line89 transferred an unconstrained generic continuation value; line110 passed a task-isolated closure to Task's sending parameter. These are real compiler diagnostics, not diagnostic-counter duplication. The ownership design is not validated by using checked Sendable on the containing classes.

`runtime-pointer-repair/continuation-local-slot-return.json` records terminal exit1 and the five complete diagnostics. The raw log SHA256 is `a18ba4da5d4e6d9427c441cec3e35c364e9cb24a013925243b33dbd8f0bf8bbb`. The first unexpected diagnostic stopped the allocation; no corrected standalone typecheck or thirteen-case behavior runner executed. No baseline retry, repack or second generation occurred. Both execution grants are inactive and the coordinator received explicit slot return. Exporter b49 remains an experimental failed candidate, not an adoptable/published fix. A genuine ownership redesign must address borrowed current-task lifetime, continuation payload transfer and reverse Task closure isolation before new behavioral evidence can be claimed.

### Prepared owned-task and end-to-end callback repair (uncompiled)

The next source candidate is isolated in `runtime-task-ownership-repair`. Runtime SHA256 `22fc88554fa88fe96df572da918cd34a2863b650cdbc6a2ea62c432c601bca1e` removes stored borrowed current-task handles. Reverse calls retain the actual Task<Void,Never> handle with synchronized pre-install cancellation and early-completion state, and invoke Task.cancel outside locks. Forward calls create no child task: the native callback records cancellation, and every completion exit closes state and marks the resumed caller cancelled inside a synchronous withUnsafeCurrentTask scope. This covers successful fallback and non-cancellation failure after native cancellation; it does not claim identical instantaneous observer timing before resumption.

The repair also changes real generator sources: TypeBridging SHA256 `8f4912c4fb0f8e4fd5ecad3179461ab0bf070f68ac1cecd78d1e7ac2e486a58e`, SirBridgeProviderImpl `37313372163c70f365f10e2e075be7ff22b96c6790f6500f5599512a5c1cad5f`, and SirAsSwiftSourcesPrinter `5a80e47960b763c7f8ee0933f09d2567292eab8cfd7be763104ae5448024786b`. Forward continuation ownership survives its generated adapter; async closure APIs require checked @Sendable, preserve dynamic actor isolation, and transfer parameter/result regions. This intentionally changes closure source contracts. It adds no KotlinBase Sendable conformance, unchecked assertion, preconcurrency import or warning waiver. The design follows [SE-0430](https://github.com/swiftlang/swift-evolution/blob/main/proposals/0430-transferring-parameters-and-results.md) and [Mutex ownership](https://github.com/swiftlang/swift-evolution/blob/main/proposals/0433-mutex.md); those references do not substitute for actual compilation and alias/lifetime proof.

All original thirteen fixture cases are byte-preserved. Six added cases cover generated reverse suspend success/error/Kotlin-origin cancellation, forward native cancellation followed by fallback/other error with caller cancellation status, and concrete reverse Payload referent identity. An additional compile-negative must reject retaining a non-Sendable Payload alias in the asynchronous Sendable callback. All nineteen behavior cases and the new negative remain unexecuted. Existing thirteen alone never invoked withKotlinTask; fresh output must contain real reverse invocations before any both-direction claim.

The prepared120-input manifest is `5c9fc3c37bfc8e203a4745e6716b1e60c4be0cdde4dd1533608f359c6cfcf391`; request hash `3cce212c17da9857862031cbab9ce39772efd76c38bfa1acefc21b7c56563487`. After explicit serialized allocation, run `build-generator.rb baseline provider`, `candidate provider`, `baseline printer`, `candidate printer` (four K2 tool-generator calls). Then `audit-and-package.rb` requires exact official-baseline instructions/signatures before packaging four specified existing classes and one runtime resource atop b49. No widened normalization or enum patch is allowed. Next run the actual65330 five-error negative and one fresh strict corrected embed. Only after module success, pin fresh outputs, verify actual reverse callers/concrete ownership signatures and recovered Swift6/Werror frontend commands, run the corrected typecheck and retained-alias negative, and run all nineteen cases. Each command and stop rule is in prepared-allocation-request.json. No compiler call, package creation or adoption occurred during preparation. Cached foreign-wrapper aliases and adversarial scheduler interleavings remain explicit proof limits.

Async acceptance remains a separately published producer contract: public io.github.kotlinmania.syn.TokenStreamParseResult and public SynResult<TokenStream>.toTokenStreamParseResult(), plus the consumer KDoc import/reference, must resolve from the independently fetched release. Maintained Async PR168 and the tmp/async-trait-kotlin new build-template checkout are separate consumer revisions. Local source existence and an isolated exporter never establish publication; no new compatible version is claimed.

### Anyhow exact-head remote corroboration and current repair limits

Anyhow CI [39500059547](https://github.com/KotlinMania/anyhow-kotlin/actions/runs/39500059547) checked out **d8583d90a195e4a32367c27183ee3dce4615de50**. Its real `macosArm64DebugBuildSPMPackage` invoked xcodebuild on Xcode26.6 / Apple Swift6.3.3 (`swiftlang-6.3.3.1.3`); the log identifies Kotlin/Native2.4.20. The actual KotlinRuntimeSupport frontend has `-swift-version 6 -warnings-as-errors` and no `-enable-library-evolution`. This is independent real-library CI corroboration of the existing published-runtime blocker, not a new warning family or an isolated producer success.

Generated `build/SPMPackage/macosArm64/Debug/Sources/KotlinRuntimeSupport/KotlinRuntimeSupport.swift` reported:

- 1:22: `using '@_implementationOnly' without enabling library evolution for 'KotlinRuntimeSupport' may lead to instability during execution`
- 5:16: `stored property 'wrapped' of 'Sendable'-conforming struct 'KotlinError' has non-Sendable type 'KotlinBase'`
- 2:1: `add '@preconcurrency' to treat 'Sendable'-related errors from module 'KotlinRuntime' as warnings`

The last diagnostic is compiler output, not permission to apply that workaround. No preconcurrency import, weaker language mode, unsafe Sendable declaration or generated-output edit is part of this repair. The retained log is `automation-artifacts/2026-10-20-anyhow-ci-repair/ci39500059547-swift.log`, SHA256 **0db99357f7d75909ceff59a9df8d6a463adf40b852a170f9d8e1ee0266b3599b**. The owner reports zero of two real Swift harness cases executed; a module failure is not a runtime test pass. Only2.4.20 producer execution is established here; this is not verification of a2.4.21 fix.

| Evidence | Verified result | Limit |
| --- | --- | --- |
| Original twelve-case isolated fixture | Twelve behavior cases passed with Swift6 test sources | Retained dependency compiler command used Swift5/Werror. A Swift6 test target did **not** establish Swift6 dependency compilation. |
| Snapshot runtime candidate69ce24 / JAR7b000 | Actual Swift6/Werror KotlinRuntimeSupport module compiled with owned String diagnostic and internal import | Coroutine module then failed; zero new13-case behaviors executed. This is partial module evidence. |
| Corrected pointer embed65330 / JARb49 | Native compile/link passed; optional-pointer errors removed | Actual Swift6 coroutine compile reported five ownership-transfer errors; zero13-case behaviors executed. |
| Owned-task/end-to-end candidate22fc8855 and three generator sources | Four K2 tool-generator compilations and strict audit-v4/package67641 passed | Baseline31685 used typecheck only and unexpectedly produced no diagnostics. No corrected embed or nineteen-case behavior runner executed; no adopted/published repair is established. |

The strict audit stop preserves the official-baseline instruction comparison and raw output. Source-specific member deltas are recorded in `runtime-task-ownership-repair/compiled-member-delta-review.json`; they require explicit bounded audit review rather than silently broadening normalization. Full real Syn/Libc validation and a legitimate verified published promotion route remain separate from fixture/compiler evidence.

### Strict generator audit passed; object-stage negative still required

Audit-v4 session67641 exited0. For all four selected classes, the rebuilt unchanged official baseline matches original signatures, normalized instructions and complete class-file access flags. Candidate added/removed members match the frozen names, descriptors and complete flags, including static/final/synthetic bits. The removed printer lambda6 and replacement lambda7 have equal normalized bodies after the explicit declaration-name correspondence; instruction normalization was not widened. Earlier audit64439 remains a historical stop, superseded by this reviewed executable audit.

`runtime-task-ownership-repair/package-results-v4.json` records the resulting isolated `toolchain/owned-task-swift-export-embeddable-2.4.20.jar`, SHA256 **f7715a1c7a3ddf443853fd38e6939813c7c3b3fd093a5bc0b7db545f1673d0f3**. It retains6263 entries, adds/removes none, and changes exactly the four approved existing classes plus `swift/KotlinCoroutineSupport.swift` atop b49. This proves the bounded packaging audit; it does not prove generated Swift compilation, public Swift ABI compatibility or concurrency behavior. The intentional async closure source-contract changes still require actual generated signatures and behavior checks.

The next command, baseline31685, ran `swiftc -typecheck` on the untouched generated65330 source with Swift6/Werror. The compiler exited0 with an empty diagnostic log; the regression wrapper exited1 because it required the five previously observed ownership errors. Actual Xcode65330 used object compilation (`-c`) and failed those five operations. A compiler-stage gap is therefore a hypothesis to verify, not evidence that the old source was repaired. The first unexpected result stopped execution and the local slot was explicitly returned. No corrected embed, output pin, alias negative or nineteen-case runner followed.

The changed-stage recipe is frozen in `runtime-task-ownership-repair/object-stage-request.json` (SHA256 **03b5f56fe0dd0c450fcb10f6d592089df721d247c0e621f26105f4d41eb449fa**) and `frozen-object-stage-inputs.json` (148 inputs, SHA256 **76db766021ac9b5ed35e88c64314fbccf069badfe2711da2ac38a3d6df18ef85**). After a fresh serialized allocation and input verification, run from that directory:

1. `ruby run-ownership-object-regression.rb baseline`: compile the actual untouched65330 source with `-c`, Swift6/Werror and an artifact-local object output; require the exact five located errors.
2. Only after that expected negative matches, run the single fresh `ruby run-runtime-fixture.rb corrected` embed.
3. Only after module compilation succeeds, run `ruby pin-corrected-inputs.rb` to verify untouched outputs, actual reverse withKotlinTask callers, concrete Payload ownership signatures and recovered dependency Swift6/Werror frontend commands.
4. Run `ruby run-ownership-object-regression.rb corrected`, then `ruby run-alias-object-negative.rb`, then `ruby run-swift-tests-object-gated.rb` for all nineteen preserved/additional behaviors, each gated on its predecessor.

These object-stage runners are prepared, not executed. Preserve the previous scripts, raw logs and result receipts. Stop on the first unexpected diagnostic or provenance mismatch. This continuation needs no K2 rebuild, audit repetition or repack; it authorizes no second generation, warning waiver, generated edit, installed/shared-cache mutation, product adoption or publication. Cached wrapper aliases and deterministic cancellation race coverage remain explicit proof limits even if the declared cases pass.

### Object-stage baseline verified; corrected embed exposes Flow and Kotlin callback defects

Object-stage baseline40300 exited0 at the regression wrapper and1 at the actual Swift compiler. Untouched65330 source reproduced all five expected ownership diagnostic families at37/85/89/110/112: inout-sending task isolation, borrowed currentTask transfer, generic continuation transfer and Task closure transfer. `logs/object-baseline-family-verification.json` verifies the diagnostic families, not merely their number and locations. This confirms that the preceding typecheck-only command missed checks exercised by object compilation.

The single corrected embed87904 exited1 after26seconds. Actual Swift6/Werror object compilation reports `KotlinCoroutineSupport.swift:338:21: sending value of non-Sendable type 'Element?' risks causing data races` at the Flow iterator's `continuation(.some(element))`. The five baseline operations were not reported in this output; their absence is not a behavior pass. Separately, Native bridge compilation fails under Werror on generated `Integration.kt` lines78/86/94: unchecked casts from Any to callbacks `(String) -> Unit`, `(NSError?) -> Unit`, and `(Payload) -> Unit`. Both failed tasks concern producer output; no generated file was patched.

The Flow source creates a possibly cached KotlinBase wrapper before handing generic Element to a sending continuation. Its lifetime/alias and isolation contract needs repair; encoding a pointer as an integer or declaring blanket Sendable would not establish that contract. The Kotlin callback source chain is asyncContinuationBridges/renderKotlinSuspendSwiftCoroutine, then AsCovariantBlock.inKotlinSources retaining the function object, then AsCovariantBlock.helperBridges casting dereferenced Any to parameterized Function1. A concrete callback carrier could make the runtime cast checkable, but requires producer implementation and actual tests; it is not yet a verified fix.

`runtime-task-ownership-repair/object-stage-local-slot-return.json` records both terminal sessions and explicit slot return. Corrected embed log SHA256 is **6b0d9cc8a9a0776601179cb8101d844157c87779e4e5a5941f2648115097c772**. No corrected-output pin, standalone corrected object compile, retained-alias negative or nineteen-case runner executed. All nineteen behavior cases remain unexecuted. No retry, second generation, toolchain adoption or publication occurred.

### Compatible managed Flow handoff and concrete callback carrier (source only)

The Element:Sendable restriction was rejected before compilation: it would remove the retained `nullPayloadIsDistinctFromCompletion` behavior over Flow<Payload?>. The original nineteen cases remain byte-preserved. The replacement source under `runtime-task-ownership-repair/flow-handoff-candidate/` keeps the managed value in Kotlin until the caller resumes, then obtains the external reference synchronously and materializes the wrapper in the caller's isolation. Only Bool readiness crosses the continuation. Explicit isolated iterator overloads and caller-inheriting legacy methods follow [SE-0421](https://github.com/swiftlang/swift-evolution/blob/main/proposals/0421-generalize-async-sequence.md) and [SE-0461](https://github.com/swiftlang/swift-evolution/blob/main/proposals/0461-async-function-isolation.md); the latter syntax requires Swift6.2 or newer and remains uncompiled in this candidate.

A reviewed source interleaving showed that a second next could advance the producer between publication and take. The candidate now acquires native request ownership before advancing, retains it through value consumption, and rejects overlap without clearing the first request. EOS/error release ownership. The existing next C entry retains its payload-pointer callback semantics; a separate nextReady entry implements readiness-only delivery, and takeValue consumes the managed slot. This is an additive native interface, not a claim that new Swift can run with old Native. Pin/generate Kotlin runtime, Swift resource and C header coherently.

Generated Kotlin callbacks now have a proposed concrete private holder containing their exact typed function. The bridge retains that holder and checks its concrete class before invocation, replacing the unchecked Any-to-FunctionN cast. Existing required opt-ins apply to the holder declaration. This changes producer source; no generated output was rewritten. Three new prepared cases exercise caller-actor object identity, legacy iterator identity and the real C publication-to-take gap, including the old C entry's retained-pointer behavior. All original nineteen remain unchanged; twenty-two declared cases are unexecuted.

The source-only request is `flow-handoff-candidate/allocation-request.json`, SHA256 **f1efe46b752e730b3e9272d69c9e9829ebf9e8b04ea72802cb3de5d52608ad35**, with162 inputs in `frozen-inputs.json`, SHA256 **d49b8647e8e653e15099ea95f99c5033ee9730dcc6a6dce56b2126ad28898c5d**. Its ceiling is one candidate provider K2 tool-generator compilation and, only if green, unchanged-baseline signatures/instructions/full-flags review using existing frozen class files. It permits no packaging or Native/Swift build. Concrete member deltas require review before a later package. No new JAR, module pass, ABI pass or behavior pass is established. Arbitrary cached aliases on other actors, pending-value GC, callback-holder release and declaration deduplication remain proof obligations; same-actor identity alone does not establish cross-actor safety.

### Flow provider compilation and four-class baseline review

The one candidate K2 provider compile87850 exited0 with Werror. Read-only review73115 exited0: all four selected classes match their unchanged parent/reused baselines in signatures, normalized instructions and full access flags. `flow-handoff-candidate/provider-review.json` SHA256 **f7ba9c055f013cb12be8626db10798ac6ec27a2a5c214210a290df51e365e21f** records the candidate deltas; `reviewed-delta-mapping.json` maps them to source and freezes full changed-block hashes. They include the converter's captured owner/constructor, carrier-name capture, the new proxy registration method, descriptor metadata and typed-holder declaration. A read-only peer review found these intended changes consistent with source, without asserting generated lifetime or product ABI success. Adding an abstract proxy method does not establish compatibility with old third-party implementations.

Inspection then found a fifth required existing tool class: carrier opt-in emission calls `SirBridgeProviderImplKt.access$getOptInAnnotation(List):String`. A read-only member parser found the added accessor flags4121 and no removed members; the fifth class's full unchanged-baseline comparison has not run. `additional-class-dependency.json` records that finding. The next request is `fifth-review-request.json`, SHA256 **403d4067a49ff71652eb420f7c66a053e30448f32cecb0ffb03a8974240a6400**, with173 frozen inputs in `frozen-fifth-inputs.json`, SHA256 **57e3d3fd6e6d4833bca335d23ca25a010932708675c026f467352af3ea53e3bc**. It requests only read-only signatures/instructions/full-flags review using existing compiled output, with no compiler repeat or packaging. The four-class pass alone is not whole-bundle acceptance.

`local-slot-return.json` records both terminal exits and the returned allocation. No JAR was packaged, no Native/Swift/Gradle command ran, and none of the twenty-two behavior cases executed. An initial missing log-directory shell redirection failed before Ruby/compiler launch; the single actual K2 invocation count remained one. Keep these generator results separate from strict generated Kotlin/Swift and real-library gates.

### Fifth-class audit stop and dependency-contained source revision

Read-only fifth-class session91955 exited1: unchanged parent/reused-baseline signatures matched, but normalized instructions differed in SirBridgeProviderImplKt.isSupported. Retained `fifth-baseline-difference.json` identifies that one method; store/load versus dup/swap operations and branch/register layout differ. No equivalence is claimed, no normalization was widened, and the review stopped before candidate acceptance. `fifth-local-slot-return.json` records the inactive grant and terminal return. No package or compiler rerun followed that allocation.

The source-only revision `flow-handoff-candidate-v2` emits the carrier's opt-in declaration locally inside BridgeFunctionDescriptor using public ClassId methods. It removes the new call to the private file-facade getter. Any future bundle must retain the parent SirBridgeProviderImplKt entry byte-identically, rather than replace unrelated code whose baseline did not match. The next review explicitly rejects new file-facade members or method dependencies from the four selected classes; the prior mismatch is not waived. Runtime, header and all twenty-two test sources are unchanged from the previous candidate.

The v2 request is `allocation-request.json` SHA256 **4f3b2aa1d11aa983f0538d972bb0fb74b87a840a095b57a254499646707b806d**, with185 frozen inputs in `frozen-inputs.json` SHA256 **7fab198a1f5f07ee02038339e8d3d3ac93ca6f085ef3aa3ba3726307c66b2fc6**. Its scope is one materially changed provider K2 compilation and conditional read-only class/dependency review, with no baseline compile, packaging, Native/Swift/Gradle/tests, retry or adoption. At this checkpoint v2 is uncompiled and zero of twenty-two behavior cases have run.

### Coherent Flow/carrier v2 executed: runtime modules pass, generated call ownership fails

The v2 provider compile83345 and baseline/dependency review94630 exited0. Every selected-class file-facade reference resolves against the retained parent descriptor and full access flags; the parent facade remains byte-identical. Package55550 exited0 and produced `flow-handoff-candidate-v2/toolchain/flow-handoff-swift-export-embeddable-2.4.20.jar`, SHA256 **2fceea1c40c94e76928066b0fc6d2ddadf65bf8ff48150e15257053c2c19bfc2**. It changes exactly four reviewed existing classes and three coherent KotlinCoroutineSupport resources, retains6263 entries and adds/removes none. This supersedes the preceding v2-uncompiled checkpoint; it does not waive the historical fifth-class audit failure.

The combined recipe is frozen in `runtime-task-ownership-repair/flow-handoff-candidate-v2/validation-request.json`, SHA256 **20d5acce4be7848253fe574f7a0116e455bfa0a55de3f873c82f77eebe29af6d**, and250-input `validation-inputs.json`, SHA256 **bc07a75aa33e086b0ee94fbc26e13437d4991ae0661e3c8b5e79499c31e4b795**. Run its sequential package audit, actual baseline object regression and corrected embed under an explicitly serialized local allocation. Baseline51839 verified the actual untouched87904 Flow Element? error at338:21: regression wrapper0, actual Swift compiler1. It used object compilation, Swift6 and warnings as errors; typecheck alone is insufficient.

Corrected embed88568 exited1 after32seconds. Actual generated Native bridge compilation and static linking passed, and the previous unchecked Any-to-Function1 casts were absent. Both KotlinRuntimeSupport and KotlinCoroutineSupport compiled under actual dependency Swift6/Werror; the Flow Element? transfer error was absent. These are module/compiler results, not runtime behavior results.

Generated `Integration.swift` then reported25 unique ownership errors. At92:47, a Payload wrapper constructed from a foreign callback pointer cannot be passed to the sending continuation. Async functional adapters at505/528/551/585 cannot transfer their converted continuation, exception, cancellation wrapper and operation closure into withKotlinTask; Payload arguments also fail transfer. Reverse @_cdecl bridges at629/647 encounter the same transfer problem, including captured receiver/raw argument state. The source chain is AsContravariantBlock.inSwiftSources and createReverseAsyncSwiftBridge. Runtime helper signatures alone do not establish disconnected ownership for values materialized from foreign parameters or cached wrappers. The next repair must address those actual generated boundaries without unsafe Sendable assertions, pointer laundering, generated-output edits or a weaker language mode.

Retained evidence is `flow-handoff-candidate-v2/validation-local-slot-return.json` and `logs/fixture-corrected.log`, SHA256 **6543778483c2593ad2c951e2a65f9ce3f6ad06d23c4e87b59cf25eb1045d24cc**. All commands are terminal and the allocation was returned. Execution stopped before output pinning, corrected standalone object compilation, retained-alias negative and the verbose named behavior runner. **Zero of twenty-two behavior cases executed.** All original nineteen and three added cases remain preserved. No real-library pass, broad concurrency-safety proof, ABI compatibility, published producer repair or toolchain adoption follows from this partial result. The prior Swift5 dependency-mode qualification remains unchanged.

### Actor-isolation boundary probes and forward managed-result source

The next isolated probe imports the actual88568 KotlinRuntime headers and real non-Sendable KotlinBase. Session6584 made one object compile: the unconfined call was rejected because its KotlinBase result crosses from an opaque @isolated(any) operation to a nonisolated context. The original runner expected only the later `risks causing data races` wording and stopped; its source/log remain preserved. The coordinator accepted the exact retained semantic negative without rerunning it. Two remaining object compiles then completed in terminal tool chunk32c7e6: both compiler exits were1. Passing operation.isolation into a local isolated actor parameter still did not establish that the opaque operation result shares that isolation. The alternative `[isolated isolation]` capture syntax was rejected by this installed compiler. These are expressivity results under Swift6/Werror, not producer repairs or behavior passes. Evidence: `flow-handoff-candidate-v2/boundary-isolation-probe/{results.json,remaining-results.json,local-slot-return.json,remaining-local-slot-return.json}`. A proposal's future isolated-capture rules must not be assumed to compile.

The resulting source iteration is `flow-handoff-candidate-v2/forward-managed-result-source`. Native SwiftJob stores a managed per-request result before publishing Bool readiness; generated additive ready/take entries consume a concrete typed result carrier, and Swift performs the ordinary result conversion after resumption. A nonnull holder distinguishes a successful nullable value from no published result. The old C entries remain, and direct-dispatch generation has matching additive entries. This source addresses general forward object results rather than declaring cached KotlinBase wrappers transferable. It is **uncompiled** and does not repair the reverse callback/job/receiver transfers. The next bounded executable check is the changed provider's K2 Werror compilation described in compiler-request.json; no packaged replacement or positive Native/Swift behavior result exists for it. All twenty-two retained behavior cases remain unexecuted.

### Managed forward result verified with real actor-origin and Native behavior

Provider83205 and printer60631 each passed the one changed K2 tool-generator compilation with Werror. Their logs contain the JDK's four sun.misc.Unsafe::invokeCleaner notices from Kotlin FastJarFileSystem, not Kotlin source diagnostics; do not describe those logs as empty. New forward source is under `runtime-task-ownership-repair/flow-handoff-candidate-v2/forward-managed-result-source-v2`. Native notification failures discard the pending result before propagating failure; successful fallback after cancellation retains its value. The printer emits explicit nonisolated(nonsending) on every async SirFunction, including protocol requirements; this syntax requires Swift6.2 or newer. Accessors/initializers are not changed by that printer branch.

Audit15449 passed exact unchanged parent/reused class bytes, normalized instructions and complete member flags. Frozen source mapping covers eleven added Descriptor members, fourteen new/changed Descriptor bodies and the printer's one changed declaration body. An earlier audit stopped because the *unpackaged* recompiled facade no longer generated the unused access$swiftAsyncCall accessor. The refined guard accepts only that exact removed member/descriptor/flags4121, accepts no additions, resolves all nine used references against the retained parent and preserves the actual parent facade byte-identically. It does not widen instruction normalization. Package47935 passed the exact6263-entry, zero-add/remove audit: two existing classes and KotlinCoroutineSupport.kt/.swift change atop2fce. Candidate SHA256 is **49851dd9c565dec2c0aab8806690946706713f42f5115e491214b78f19ed313e**; retained facade SHA256 remains **64f6eba8e05f61e13fb1f06590175a499d6af5ee9a706df298094a1ab2756fdf**.

The323-input continuation manifest is `continuation-inputs.json`, SHA256 **bd2d5e97b8ce3d2c66cb43370090882ca6bb84a770b5c4cc448d7d6619414bac**; request SHA256 **bf39e690bfa1882fdc5db5e01c5aaadf6283e7f7dff50fc71d12c9315f215cdb**. After fresh serialized ownership and hash checks, the demonstrated sequence is `ruby review-forward-v2.rb`, `ruby package-forward.rb`, `ruby run-fixture.rb forward`, output pinning, then the strict SwiftPM test runner. Reuse the existing artifact-local Gradle home, Native SDK and Konan data paths recorded in run-fixture.rb; fresh project/output/module/SwiftPM caches remain under this candidate root. There is no installed compiler replacement or shared user-cache write. This is a focused fixture, not a product full-KMP build.

Actual embed21290 exited0 after35seconds: Native compilation/static linking, strict Swift package compilation, merge and embed all passed. The runtime keeps the concrete per-request result in Kotlin, sends Bool readiness and materializes the wrapper synchronously after resumption. Untouched emitted resources match the candidate plus the exporter-added newline; generated source contains the typed carrier and ready/take entries, while the old entry remains. The real @MainActor client awaits echoPayload with a retained Payload alias and asserts referential identity and its message/label after resumption.

SwiftPM9926 actually exited0. Both named Swift tests passed once, with zero skips: forwardObjectResultRemainsOnCallerActor and managedNativeResultCleanupIsExact. The second invokes the real compiled Native helper and executes four checks: nullable success versus absent/already consumed result, false payload versus readiness, exact throwing-notification failure with discarded result, and successful fallback after cancellation. These are actual runtime assertions, not declarations or simulated JVM tests.

The first wrapper nevertheless exited1 because its frontend selector also included SwiftPM's two Package.swift **configuration** compilations (harness mode6, generated manifest mode5, neither Werror). Preserve that raw result. Independent classification of the retained log proves that all thirteen actual product/test frontend commands across ExportedKotlinPackages, KotlinRuntimeSupport, KotlinCoroutineSupport, Integration, ForwardTests and ForwardTestsPackageTests used **Swift6 and warnings as errors**. This is direct dependency compilation evidence, not an inference from the test target. The actual log SHA256 is **80151c0fd210d33307040d5fddc3179c634af31457837652ec98085b5edb7cad**. `logs/actual-forward-result-classification.json` identifies the exact two manifest paths and all product commands. No test was rerun, no raw exit rewritten, no diagnostic suppressed and no language mode weakened. A future runner with the exact manifest/product distinction is prepared separately as run-forward-tests-v2.rb.

Successful Gradle console output did not expose frontend commands. The first pin guard stopped on missing evidence; no generated output was edited or rebuild performed. Retained Xcode activity data instead records four actual Swift driver commands, each mode6/Werror. pin-forward-output-v2.rb records those as **driver** evidence; the subsequent verbose SwiftPM run supplies the actual frontend proof above. Keep those two evidence types distinct. All commands are terminal and the local slot was explicitly returned; `forward-local-slot-return.json` records the sessions and limits.

This establishes only the focused forward path. The original twenty-two cases remain byte-preserved and **zero of those twenty-two ran**. Four newly authored actual protocol witness/override/reverse cases under witness-fixture also remain unexecuted; a top-level forward pass does not establish their acceptance. The opaque @isolated(any) reverse callback still lacks a proven compatible transfer for arbitrary cached non-Sendable receiver/payload aliases. Existing Swift6.3.3 probes reject the tested isolation connection and isolated-capture syntax. A separate owner is preparing an explicit actor-owned full-request adapter; that registration contract is different and must not be silently substituted for the existing API. No broad concurrency fix, real Syn/Libc pass, compatible Maven release or toolchain adoption/publication is claimed.

### Upstream source patch provenance and separate reverse registration experiment

The current forward candidate's seven unified source patches are preserved under `automation-artifacts/2026-10-20-syn-isolated-library-validation/runtime-task-ownership-repair/flow-handoff-candidate-v2/forward-managed-result-source-v2/upstream-source-patch/`. Its `provenance.json` SHA256 is **e72bdbaee2620e8ff7b0daec1d72cb8b2a9d2edf50768515478ab0f181e1bc5a**. It maps exact upstream paths, baseline hashes, six pinned Git blobs, current source hashes and patch hashes against JetBrains/kotlin revision890ac1d94fdb80eb85f0eeb5be5e4352df987b2f (2.4.20). The KotlinCoroutineSupport.h baseline is independently pinned from the official exporter resource; its Git blob is not independently verified. These inputs are artifact-local official source files. **There is no full Kotlin source Git checkout in this investigation**, and no upstream submission or official replacement release exists. The isolated JAR must not be published under JetBrains coordinates or silently installed as their compiler.

Separately, Anyhow's explicit MainActor-owned request adapter compiled in session95637 with actual `swiftc -c -swift-version 6 -warnings-as-errors`, producing two objects with no diagnostics. The real imported Payload/KotlinBase client retains aliases; this object check used retained88568 module declarations, whose earlier module emission was not an object or behavior pass. `automation-artifacts/2026-10-20-anyhow-ci-repair/reverse-call-adapter/object-check-result.json` records exact inputs and commands. The adapter requires the complete request and typed take to be formed in the actual lexical owner scope. It does not transparently adapt the existing opaque arbitrary-actor callback. Its six lifecycle cases have not yet been verified at this checkpoint. No runtime, foreign reverse ABI, original22 or public API compatibility result follows from object compilation alone.

### Reverse adapter lifecycle execution and actual generated witness blocker

The separate explicit MainActor request experiment subsequently passed runtime session83710: all six exact Payload lifecycle cases ran once, with zero failures/skips, process0, unchanged inputs, and four actual product frontend commands under Swift6/Werror/macOS15. The cases cover cached object identity, cancellation before dispatch, throwing-ready cleanup, cancellation during suspension, cancellation after readiness/repeated discard, and weak frame release. Its retained `runtime-v4-local-return.json` records log SHA256 **34d9145405bf2087aff08ba8a340e9499295f8145d3bbbdd90de82feb91c1160**. This supersedes the preceding unexecuted-six checkpoint, but remains a caller-side lexical MainActor registration contract; it does not repair an arbitrary opaque operation or generated reverse entry.

Actual generated witness embed37271 then exited1 after32seconds/16 executed tasks. Native compilation and static linking passed. Generated PayloadEcho protocol requirements and PayloadEchoBase open overrides contain explicit nonisolated(nonsending), establishing that the printer emits the intended declarations. Swift compilation fails in two actual reverse C entries at Integration.swift233/251: each reports transfers of __continuation, __exception, __cancellation and the operation closure into withKotlinTask, for eight unique located errors. They materialize cached receiver/callback/job wrappers at an unisolated foreign entry and then transfer them to a task; reference retention alone does not establish actor ownership. None of the four new witness cases ran. No original22 case ran, no output was patched and no successful protocol witness or reverse ABI behavior is inferred. The build stopped before conditional output pinning/testing and its separate allocation was returned.

Evidence is `forward-managed-result-source-v2/witness-local-slot-return.json` and `logs/fixture-witness.log`, SHA256 **a3fe2f586fdb800206bdd53c503497d1bc8bc72ae5b8c48854bcf52f0dd764cb**. Request SHA256 **a2cecf793e54e6f3b81c9fb47519fa28d77e290971f012dee23b47f1c9ca11bd** freezes328 inputs in witness-validation-inputs.json, SHA256 **17dd9d44c6a980ab86998c0961bbfcc1ca921d8db21f44e0de78dbf1ee876bf4**. A preliminary sandbox startup11128 failed before configuration/tasks because Gradle's local lock-coordination socket was denied; its raw log/invocation/result remain separately preserved with .sandbox-startup suffix. The same command then ran with execution permission in37271; that startup correction is not a second generated-candidate attempt.

### Ordinary runtime repair must include the resolved support closure

The two-file ordinary RuntimeSupport/import subset is preserved in Syn verification/swift-exporter-patches/ordinary-runtime (commit1a0f4ab). It does not contain the asynchronous ownership/declaration changes. It fixes internal bridge imports and replaces mutable KotlinError.wrapped storage with an immutable message snapshot, plus redundant-cast corrections. Reading or assigning the old wrapped property is source-incompatible, and old runtime layout/accessors are not a binary-compatibility promise. init(wrapped:) retains its label but captures the description immediately rather than exposing the original object or tracking later mutation.

ProcMacro2's absence of public async functions did not omit CoroutineSupport. Actual regeneration3211 (setting removed) and11525 (explicit false) each exited0 and kept root Swift/Kotlin/C headers byte-identical, with support still reexported through its legitimate published ProcMacro dependency graph. Their receipt SHA256 is **e3d7e42d97aee8e0a15405a25b7805882c55e189606a4ce8284410f6c1391b4e**. Existing kotlin-source references have no Git provenance proof; they are not used alone to establish exact-version execution semantics.

Read-only javap of the exact official2.4.20 exporter JAR, SHA256 **24c6159bcf0087ecf86bfc9bad01a975e192abcf75005b248292d85a139c2055**, independently confirms the chain. PatchConfigAndLogIssuesKt computes whether a module name contains KotlinxCoroutinesCore and passes that boolean to SwiftExportConfig.copy$default with mask503. Coroutine property bit8 is cleared in that mask, so the incoming boolean is not retained. SwiftExportRunnerKt invokes this patch before translation, then emits support according to the patched value. Exact class dumps are artifact-local under ordinary-runtime-minimal; Syn's runtime-closure.json records their hashes and consumer receipts, SHA256 **bc1de71bc203fca9a9078a8437c29e86c99a5ba7f773f25028461b299d740228** (commit a8ca3c3).

Consequently the two-file subset is not a whole-library repair claim. The real three-case ProcMacro2 gate needs either coherent emitted coroutine runtime corrections or a supported explicit producer opt-out with graph/configuration/API tests; dependency exclusion or generated-module deletion is not a substitute. No alternate component has been injected there. The outstanding scope decision is permission to select an artifact-local alternate exporter for that controlled actual-library diagnostic despite the current no-injection boundary. No tool rejected this proposed action; it has not been attempted. Publishing/adopting an experimental component is a separate boundary: an accepted official upstream release or an explicitly authorized immutable KotlinMania-owned producer route must precede consumer adoption. Do not fabricate a JetBrains release, overwrite installed caches, or treat the unresolved opaque reverse API as the cause of the independent ordinary RuntimeSupport defects.

### Correction: diagnostic gate withdrawn; target-specific proof remains mandatory

The preceding no-injection hold was coordinator caution, not Sydney's restriction and not a tool denial. It is withdrawn for one artifact-local ProcMacro2 native/Swift diagnostic and its three existing strict Swift cases under the existing repair-and-test authorization. Do not ask again for that routine diagnostic. Installed compiler replacement, silent production adoption and publication remain separate. The consumer owner runs the actual shared checkout; the producer owner supplies the checked component and does not launch a competing build.

The selected diagnostic component is the existing coherent49851 candidate, not the incomplete two-file ordinary subset, because CoroutineSupport is actually emitted. `ordinary-runtime-minimal/proc-macro2-component-handoff.json` SHA256 **f14afa8b49b35314081dd4be6ab85178a76e8e8a6a7f665c419f1bc4cfa901ab** pins its path, source provenance and known limitations. A fresh read-only full-entry audit against official24c615 retained6263 entries, changed17 entries and added/removed none; receipt SHA256 **63e57aa786598f0ce3fa4e675ec464935bac4ec85fbadfa28010623f58953c5d**. This does not waive prior class/facade checks or establish a new test result. Known enum-default and reverse-contract limitations remain explicit; no unverified enum producer was packaged.

Report the library per target: Kotlin/Native compilation produces native code and native archives; Swift export generates bindings consumed by the Swift compiler. Native compile/link, actual dependency Swift6/Werror compilation and three named runtime cases are separate acceptance stages. Compiler tool packaging does not turn that native library into a Java/JVM library. JVM, Android, JS and Wasm results establish only their respective target coverage. No JVM test result supplies native or Swift behavior proof.

### Actual ProcMacro2 diagnostic: native runtime objects pass; enum generation fails

The corrected artifact-local ProcMacro2 diagnostic2800 ran against clean source4287fb5e0b69eb3f758802792a9a2e8f70e90b3d with coherent exporter49851 and the real published ProcMacro0.1.4 graph. Main macosArm64 Kotlin/Native compilation and KLIB construction passed. Actual generated RuntimeSupport.o and CoroutineSupport.o are fresh Mach-O64 ARM64 objects, independently rehashed as **d60e1fe5383cd8294b294ca376551fe8766c42b94a8307c08de6701f0f5ab709** and **9a64247afc20c10909ac64ccd5999aefa7a9732d195786d60ec181781fd51aaa**. Xcode activity records actual dependency driver commands using Swift6/Werror. All six runtime source/header resources match the coherent tested component. These are native Swift dependency compilation results, not JVM evidence or library runtime-test passes.

ProcMacro2's actual Swift frontend used Swift6/Werror/arm64-apple-macos15.0 and failed six unreachable-default diagnostics at212/223/262/274/283/316. The previous RuntimeSupport implementationOnly/wrapped error families were absent. Generated Kotlin/Native bridge compilation and static linking were not reached because this diagnostic did not use continue; no libProcMacro2.a or three-case Swift pass is inferred. Zero of its three existing Swift tests executed. `automation-artifacts/2026-10-20-proc-macro2-publication-repair/strict-swift-4287/product-diagnostics-v2.json`, SHA256 **0805b2c9cb9b8151d74a648efe2927c6637b4399f0a97452c95ac513dcc3d323**, records exact paths/flags/resources and terminal return. The earlier41373 attempt stopped before task execution on SwiftPM lock-store isolation; the corrected init preserves task actions while redirecting public directory properties. Its one harness source link, containing three cases, was replaced with an exact regular file before actual compilation; no symlinks remain.

The public-declaration guard also remains failed:268 old baseline declarations versus282 fresh declarations, including sealed adapters. The old generated baseline lacks proven exporter/KLIB provenance, so neither a compatible API result nor attribution to the repaired component is established. Preserve both results and the failed guard; do not normalize additions away or silently establish a new baseline.

The enum correction is preserved in Syn verification/swift-exporter-patches/enum-switch (commit8c89c8b), source Git blob **b568e90314c57fbd493a1554a2bab395a753331d**, three-site patch SHA256 **bf9079e2d1661f2f428f0a78fe39de1249197edbc6aea0b99b42c88f13a83a34**. It preserves empty-enum and invalid-input guards. Retained producer compilations passed, but packaging is not accepted: read-only classification5753 across all twelve baseline classes found eleven exact matches and one differing private inline attributes method in SirEnumFromKtSymbol. Receipt SHA256 **808538b29ff2df2e862f0394e3bfe3fc586e3241ca545e090619ec51ffa7bacc** records both complete block hashes. No normalization change, baseline waiver, enum package, consumer retry, installed write or adoption follows from this classification. The next repair must resolve that exact integrity discrepancy before testing a coherent enum-corrected component.

### Verified enum repair, matching baseline and real ProcMacro2 Swift execution

The old ProcMacro2 268-versus-282 API comparison was cross-version: gradle.properties selected exporter 2.4.10 while the plugin and identical 42-file Native KLIB tree used 2.4.20. The retained task-history evidence is qualified as a strings extraction, with current artifact hashes rather than a historical runtime hash capture. Receipt `strict-swift-4287/baseline-provenance-v2.json` SHA256 **b186ff9ccb27e7b2437ad89e282b02aff4eaab3a789aea01c8ea46fac569cffb** preserves that distinction. Production source9d700f9 binds the exporter to the existing catalog version. Actual stock generation49207 then captured the unmodified official 2.4.20 classpath: its 282 public declaration records and C header exactly match the prior corrected diagnostic. Generation alone was not a runtime pass.

The enum reconstruction discrepancy is now resolved without weakening the instruction comparator. Upstream revision **890ac1d94fdb80eb85f0eeb5be5e4352df987b2f** specifies bootstrap **2.4.20-dev-7885**; its downloaded compiler SHA256 is **63fc90ac7bfbe40b045e7bd10f9328ab7dd63bba22a0a8d8c40927ebddfbfb11**, verified against both repository SHA256 and SHA1. Bootstrap alone reproduced the same private inline-method mismatch. The upstream module compiles the enum together with `utils/AnnotationUtils.kt` and `extensions/lazyWithSessions.kt`; consuming their already compiled inline bodies was not equivalent reconstruction. Supplying those exact source dependencies produced all twelve original enum class signatures and instruction sets under the unchanged normalizer in execution23522. Their Git blobs are **ff272bf43b1c15ee6586d332a1b42f8fccc6ad95** and **db11b0a32d2e8fe3223d658c84a5dd8c23472360**. All candidate member descriptors and full access flags remain equal.

The source fix removes defaults from three nonempty exhaustive self switches. Focused actual Swift compilation26426 also found an empty-enum integer initializer assigning an impossible value after its always-false bounds guard. Its corrected source returns nil directly for an empty enum, retaining nonempty bounds/indexing, string rejection, impossible-self fallbacks and the foreign unknown-ordinal trap. Candidate source SHA256 is **13fa738bb55a4ad7d8034809d01487d12fc3d219d76962dfafe4434389018778**. Exactly four method bodies differ. The separately observed real ProcMacro222305 then passed Swift module compilation but failed Native bridge Werror because SwiftJob lacked Job's required inheritance opt-in. Official coroutines1.11.0 Job is annotated with SubclassOptInRequired(InternalForInheritanceCoroutinesApi); the producer SwiftJob now adds that specific OptIn. It changes no cancellation implementation or compiler warning gate. Kotlin runtime source SHA256 is **bd00ccad0f54f495f923c2920ccfb294457a306d6bb8c5bf849f844dd24f12fc**.

The coherent component is `enum-repair/bootstrap-provenance/enum-runtime-swift-export-embeddable-2.4.20.jar`, SHA256 **57182d059e9c7f087206310aeaa6ccd56fa06ea552f31510ce58c8e7e2945e32**. Its package audit24059 preserves 6263 file entries, adding/removing none, and changes only the enum class and Kotlin coroutine resource atop49851. Archive directory entries are counted separately. Package receipt SHA256 is **199068c12381862ce4e70d001b607c2bc0a98849672aa69d06c9a4fe85d79da5**. This is compiler tooling packaging, not Java product validation.

For reproduction, use workspace artifact root `automation-artifacts/2026-10-20-syn-isolated-library-validation/enum-repair/bootstrap-provenance`. `downloaded-tools.json` pins the five bootstrap artifacts and official checksums; `baseline-inputs-v4.json` and `baseline-v4-invocation.json` pin the complete launcher, source and target dependency classpaths. Compile the unmodified enum with both inline source files, module `org.jetbrains.kotlin_sir-light-classes`, target1.8, `-jvm-default=no-compatibility`, no implicit stdlib/reflection, friend path to official2.4.20, both KaContextParameterApi and KaExperimentalApi opt-ins, and Werror. Keep tooling bootstrap inputs separate from the Native compiler. `check-baseline-v4.rb`, `check-empty.rb` and `package-empty.rb` contain the exact executed commands and unchanged comparator/full-flag gates. Use fresh owned output directories and freeze the resulting command/input manifest before execution; never erase a retained attempt or normalize another discrepancy to make it pass. No full product source copy is needed.

`run-fixture-v4.rb` executes the retained Empty/Single/Multiple fixture through embedSwiftExportForXcode, with artifact-local build/project/Gradle/Konan paths, workers2, no daemon reuse and strict task-output checks. `fixture-v4.init.gradle` selects only the pinned coherent exporter tooling and redirects SwiftPM import directories through their public directory properties. `strict.xcconfig` requests dependency Swift6 and Werror. Actual44274 passed main Native compilation, generated Native bridge compilation, native static linking and Swift dependency compilation. The fixture-level Job inheritance opt-in was removed so the producer must supply its own. `run-six-tests.rb` invokes verbose SwiftPM with mode6, Werror and macOS15. Actual64802 process0 ran all six retained named cases once with no failures/skips. Eight actual product/test frontend commands across EnumFixture, both runtime modules and EnumTests carry mode6/Werror. Raw wrapper1 is preserved: its summary matcher omitted the actual words “in0suites”. Independent classification SHA256 **bf47c0cefa075ff604cf00827a8e4bc28dfaf4947d409dc519ad216b7a1675a7** verifies the retained log; no runtime rerun was used to repair the receipt. Nonfatal debug module-cache path diagnostics remain recorded.

The real ProcMacro2 consumer then passed actual91639 at source **0e48b336c21fc4e1d7ed94d048d49d69ef0ef564**. Native embedding exited0 and all three existing Swift cases passed once, without failures/skips. Sixteen actual product/test frontends across eight modules used Swift6/Werror/macOS15; Package.swift configuration frontends were classified separately. Its native ARM64 archive SHA256 is **500b261a2824efd916b586dbb86c8d738c03acc8f9246c1f784e70275535006d**. All 42 Native KLIB files, 282 public declaration records and C header match the coherent stock2.4.20 baseline. Consumer raw wrapper1/resultacceptedfalse are preserved because its parser missed Swift Testing's display-name alias; seven offline parser checks and `strict-swift-runtime-0e48b33/actual-runtime-classification.json` SHA256 **0476374eeaa8b236521eaf66372d131fe485855b6f828323066707c1ddfbc673** establish actual runtime process0 without rerunning it. Raw Swift log SHA256 is **59ef4c637c956a1d93bee37401cc235e64d408e8f4df4afc8a4f8f075243c2c9**.

These results prove those concrete Native/Swift cases. They do not prove the original22 asynchronous cases, arbitrary cached-object reverse ownership, 2.4.21 compatibility or a published repaired toolchain. Removing KotlinError.wrapped remains a source/binary API break, despite retaining the initializer label. Installed compiler files and shared caches were unchanged. Coherent components must stay together; rollback selects a previously recorded immutable tool version and regenerates outputs, not mixed resources. Preserve source patches, manifests and raw failed/successful receipts before cleaning only owned artifact outputs.

The production-route comparison is in `syn-kotlin/verification/swift-exporter-patches/DELIVERY.md`: official upstream source submission and a verified matching official release, or an explicitly authorized immutable KotlinMania-owned tooling coordinate with licensing, reproducibility, CI and rollback gates. No official fixed published version has been verified. Do not publish modified tooling as org.jetbrains.kotlin, replace official cached artifacts, or silently adopt the isolated component. Selecting a production delivery route is distinct from permission for routine diagnostics.

### Additional boundary assertions and current hosted failure correlation

Actual enum execution80224 preserved all six cases and added explicit Empty(rawValue: Int32.min) and Empty(rawValue: 1) rejection checks, completing the requested min/-1/0/1/max boundary set. SwiftPM and the corrected wrapper both exited0; all six named cases passed once with no skips/failures, and all eight selected product/test frontends used Swift6/Werror. No Native regeneration occurred. Durable hand-authored fixture sources are in Syn verification/swift-exporter-patches/enum-switch/fixture. Result SHA256 **c69ba1f4be65ae8947c8aa2d8d79fd9e6f174f8ab00b29b059e2b51e6722cea4** and log SHA256 **7a6551a5445ea13d777a99b71692ded026854a177825bc5394d48726f45cd490** preserve this separate test advance; the original64802 raw parser history remains intact.

Read-only hosted correlation: OpenTelemetry Appender Tracing run39531269242, head9f454783dd88e0da231474c21197e15c50ea5623, Swift job118997464324 fails the known RuntimeSupport implementationOnly/library-evolution and wrapped:KotlinBase Sendable diagnostics. Its actual runtime frontend uses Swift6/Werror. The later IDERunDestination empty-platform/multiple-AnyMac warnings are separate from those compiler errors; this log does not establish a destination-selection defect or justify a template workaround. Diagnosis receipt SHA256 **77dcc44ca2bee956c656379b7ef0595e6b53e8d63df0d1816b1dc7e483d09ada** is under automation-artifacts/2026-10-20-syn-isolated-library-validation/opentelemetry-ci-39531269242. No repository edit, CI restart or Iris interaction was performed.

### Concrete production delivery proposal and official artifact inspection (Historical `DELIVERY.md` snapshot-break checkpoint — superseded by `DELIVERY_CURRENT.md`)

> [!NOTE]
> **Historical checkpoint — superseded by [`DELIVERY_CURRENT.md` (`70d733a`)](#consolidated-api-preserving-delivery-reference-70d733a--delivery_currentmd-and-procmacro2-mixed-mode-validation-3d8718c):** The earlier `DELIVERY.md` proposal below assumed that removing `KotlinError.wrapped` (the snapshot-break candidate) was required. That assumption is **superseded** by the validated API-preserving closure (`exporter 32f6c1ac...` + `header 99186d1d...` at `db24b93` / `70d733a`, documented in `syn-kotlin/verification/swift-exporter-patches/syn-library/DELIVERY_CURRENT.md`, SHA256 `92110deb7f5af5a5020afd4e1b9449f2d4e5ba8bbd110ef97048091eee039744`), which preserves `public var wrapped: KotlinBase` without an API break. Do not treat `KotlinError.wrapped` removal as mandatory.

Read-only Central inspection on 21 October 2026 found latest non-prerelease 2.4.21 and latest preview 2.5.0-Beta1. Actual published 2.4.20, 2.4.21 and 2.5.0-Beta1 artifacts all retain the implementation-only bridge import and KotlinError.wrapped:KotlinBase storage. Central SHA1 checksums were verified and artifact/resource SHA256 values recorded. The preview additionally consumes wrapped in reverse-error identity paths, so the 2.4.20 String-snapshot resource is not a drop-in upgrade for it. No preview compiler or consumer build was run. Exact evidence is production-delivery/official-artifact-inspection.json under the existing Syn isolated-validation artifact root, SHA256 **f9f9111f8f00706e85890bc7b4934c1ea9d481f60571c0c4866e5a8a5058b835**. The index lists no examined official compatible release containing the tested repair; this is not an all-historical-versions claim.

The concrete proposal in syn-kotlin/verification/swift-exporter-patches/DELIVERY.md names repository **KotlinMania/kotlin-swift-export-tooling** and immutable coordinate **io.github.kotlinmania:swift-export-embeddable:2.4.20-km.1**, with ProcMacro2 as the first adoption scope. Both remain proposals; read-only checks found no accessible repository at that path and Central returned 404 for that version. The proposal uses the existing KotlinMania publishing namespace, subject to publisher access verification, rather than JetBrains coordinates or an assumed new tooling namespace. It describes exact patch ordering, pinned official base/bootstrap/source closure, two deterministic source-patch builds, full class/resource/ABI audits, Apache/bundled licensing and modification notices, exact-final CI/main CI/tagged Central Portal publication, independent published-artifact verification and consumer rollback. It explicitly distinguishes this source-patch assembly from rebuilding every unchanged Kotlin compiler module. Licensing/provenance additions require a new reviewed final artifact hash and renewed validation.

The production decision is whether to authorize that named repository/component release after its gates and explicit ProcMacro2-only pinning, accepting removal of KotlinError.wrapped and diagnostic snapshot semantics, while submitting upstream patches in parallel; otherwise use a verified future official release. This is not another diagnostic-permission request. No repository creation, upstream submission, publication, production pin, installed-tool change or fleet rollout has occurred. Matching scope remains KGP/Native 2.4.20 and the recorded Swift 6.3.3/coroutines 1.11.0 inputs. The original 22 asynchronous cases and each other consumer own their separate gates.

### Fresh production-default ProcMacro2 Swift failure

The official production exporter still fails at ProcMacro2 head **3d8718c2412be6aa99175ab88663edec8dbfa6db**, [run39534052281 Swift job119006447304](https://github.com/KotlinMania/proc-macro2-kotlin/actions/runs/39534052281/job/119006447304), after catalog alignment and the targeted swiftExportSmokeTest entry. Independent log inspection confirms the actual KotlinRuntimeSupport frontend uses Swift6/Werror. Located errors are RuntimeSupport.swift1:22 implementation-only import without library evolution,5:16 wrapped:KotlinBase non-Sendable storage, and2:1 the preconcurrency suggestion, which was not applied. Zero of the three Swift cases executed. The retained log is automation-artifacts/2026-10-20-proc-macro2-publication-repair/current-head-production-ci/proc-macro2-kotlin-swift-119006447304.log, SHA256 **1fb088fbdfc45b55c6764b9e2044b0e34e15149a0deae45f99bda43cfb079149**.

This is a production-default negative at a different source revision from isolated91639/component57182, whose actual Native build and three Swift cases passed. Preserve both results; neither implies production adoption or a published compatible producer. The existing owner continues monitoring the original production runs. No duplicate CI, producer selection change or repository source edit was performed for this documentation update.

### Distinct recursive-bound exporter failure in BitFlags

Actual BitFlags source **32d45a573509701d67d5f2697178b7bccdba4b9c** fails during macosArm64DebugSwiftExport, before generated Native bridge/runtime compilation. Its single direct stacktrace execution15478 records 73 repetitions through UpperBoundTypeProjectionRenderer and KaTypeRenderer. Raw stacktrace SHA256 **6db4b70770ccaf71aa09477257eddb623e3f1a4228c228aefb80417c73cad954** is retained under automation-artifacts/2026-10-20-ansi-pr99/bitflags-public-flags. The explicit exporter version and retained task-history path match official2.4.20 artifact24c615; the full worker classpath was not captured. Separate JVM signature inspection is not Native/Swift proof.

Pinned TypeRenderer.kt source at revision890ac1d, Git blob **5a64ea9f1dd8b33c02533e03646cf1b03ef3ec87**, replaces a type parameter with its single upper bound and recursively renders it with the same renderer. A genuine B:Flags<B> bound therefore re-enters B. Exact SirFunctionFromKtSymbol.buildForwardKotlinCall lines143–151 uses the renderer output as explicit generic arguments in generated Kotlin calls, so this is not only diagnostic formatting. The first triggering exported declaration is not isolated. A terminating Any/star/Nothing substitution is not accepted without proving the generated recursive-bound call and behavior. Published2.4.20 and2.4.21 have identical projection-renderer class bytes; the preview differs and was inspected but not executed.

Syn verification/swift-exporter-patches/RECURSIVE_BOUNDS.md records the mechanism and validation contract. Independent classification receipt SHA256 **f8ae59a7d3e51df42752143d266ee45db9172330a0bc60eb5721b37214f3101b** is under the existing Syn isolated-validation production-delivery artifact directory. Preserve actual bounds, flags widths, public APIs and Native/Swift gates. Component57182 is not claimed to repair this new family, and its ProcMacro2/enum successes remain separate. This classification used read-only source/log/tool inspection, with no consumer or producer code edit and no duplicate compiler/CI run.

### Executed recursive-bound factory reproduction and rejected inference candidate

A genuine authored recursive factory fixture now reproduces stock2.4.20 StackOverflowError in actual79429 after its main Kotlin/Native library compiles. The complete exporter tooling classpath is captured and hashed. Separately, a one-input captured factory call compiled/linked under Native2.4.20/Werror in34733 and its ARM64 Mach-O executable passed identity/value assertions in77287. Actual19399 rejects two independently erased inputs because their captured B types are unrelated. This is Native evidence, separate from JVM and Swift.

The experimental inferred-call source avoids recursive explicit-bound rendering without replacing the bound. Reconstructing unchanged SirFunctionFromKtSymbol requires same-module AnnotationUtils, lazyWithSessions and TypeTranslationUtils plus the supported name-based destructuring syntax flag. Actual30095 matches all14 baseline classes' signatures, instructions and full member flags with the unchanged comparator. Candidate25902 compiles; audit11305 finds only one changed call body and two private helpers(flags18/26). Isolated d0d307231e0dc02dbfa6749aa607ba3d3e3348ebabc2a0f1bac7384b65ff8bd1 changes one existing class atop57182,6263 file entries, no additions/removals.

Actual71898 passes export generation, then fails the actual generated Native bridge with five errors. The two-input factory cannot infer one shared recursive type; allocation/initialization emit FlagIterator<Flags<*>>, violating the real bound. Untouched generated source also requires a public-factory visibility check. This candidate is **rejected as a complete repair**, not included in the accepted delivery patch order, and not installed/adopted/published. Native bridge linking and Swift tests did not execute. Preserve the positive direct Native source test, stock crash, and generated Native failure as separate results. Next correction must retain shared generic relationships and well-typed class allocation before strict Swift behavior tests.

Durable authored fixtures, rejected patch and exact result hashes are in syn-kotlin/verification/swift-exporter-patches/recursive-bounds. Raw inputs/commands/logs and generated source remain in automation-artifacts/2026-10-20-syn-isolated-library-validation/recursive-bound-repair. Checkpoint SHA256 **43af6f95180239a47ec5aec77d847928297bec708eb65bc46386fd2b370290f2**. None of the earlier ProcMacro2/enum success is claimed to fix this recursive family.

### Rust reference tooling and release contents

Sydney permits local manually run reference tooling while requiring vendored frozen Rust to be absent from published code. Preserve the shared reference trees; do not delete them to satisfy a packaging boundary. Publication/adoption/external submission of the proposed exporter component remains unapproved.

Direct inventory of57182 finds6263 files:6228 host-tool classes,22 Kotlin module metadata files,6 XML files,1 manifest and6 runtime source resources(2 Kotlin,2 Swift,2 C headers). There are no Rust source/Cargo manifest or lock entries. The nine ordered repair patches touch eight distinct Kotlin/Swift/C-header files; no Rust patch is involved. Syn tracks no Rust/tmp reference sources. This is an inspected-entry/source-closure statement, not a complete implementation-origin or transitive licensing certification. It does not turn Kotlin/Native product output into a JVM library.

A no-vendored-frozen-Rust option keeps patches/manifests/fetch recipes and retrieves official Kotlin inputs by immutable revision/blob/checksum at build time. Local Rust oracles and toolchain trees remain outside publication staging, source archives and product dependencies. Final source/binary archives must be explicitly allowlisted and inspected before any separately approved publication. Licensing/notices/SBOM, deterministic rebuilds, artifact-derived header provenance and exact Native/Swift gates remain requirements; no release staging exists. Exact inventory SHA256 **cc83fccc7222458518d9151b7cc87e39a64b1a96edc25f2827b877f53a0f44fe** is in production-delivery/release-source-scope-inspection.json. Durable explanation: syn-kotlin/verification/swift-exporter-patches/RELEASE_CONTENTS.md.

### Checked Swift representation barrier for the recursive factory

The continuation inspected actual official2.4.20 SirFunction, SirClass and SirCallable member tables: they carry no declaration generic-parameter list. The emitted withRemaining method consequently accepts two unrelated Flags values. A generic Native entry declaration alone cannot establish their shared B equality. Exact pinned StandaloneSirTypeNamer source reconstructs parameterized names from upper-bound classes, and SirRegularInitFromKtSymbol uses that representation for allocation/initialization; this explains the invalid constructor shape in71898 without attributing it to an unverified old source tree.

Actual3613 ran an authored language check with Swift object compilation, mode6/Werror, explicit MacOSX26.5 SDK and ARM64/macOS15 target. Swift rejects a direct Flags<B:Flags<B>> class declaration as self-referential generic requirements; a separate factory-expression diagnostic failure is preserved. Earlier73435 permissionDenied startup and47120 missing-default-SDK failures are harness history, not this type-system result. This was not generated-output editing or product behavior execution.

The current concrete barrier is preservation of the original shared type relation through the public Swift representation and a checked Native witness, including well-typed allocation. Neither literal recursive-superclass emission nor independently erased arguments provides it. No verified compatible protocol/associated-type encoding has been implemented; this does not prove every equivalent encoding impossible. Placeholder bounds, unsafe conversion, hidden APIs or a weaker public contract are not accepted. The five Native failures and original tests remain preserved; this continuation executed zero additional Native bridge builds or product Swift cases. Publication/adoption/external submission remain on hold.

Durable expected-negative source and evidence are in syn-kotlin/verification/swift-exporter-patches/recursive-bounds/relationship-model. Exact checkpoint SHA256 **b2d8933a6848189911b53a0b1f8167e2e61427403ac014ae58d3279a239310d0** is under the matching workspace artifact directory. All commands are terminal; the next implementation must supply that missing typed representation and Native proof before strict Swift behavior acceptance.


### Runtime diagnostic behavior: real crash, wrapper ownership defect, five actual passes

The independent RuntimeDiagnostic fixture now tests actual Kotlin/Native objects rather than inferring behavior from successful module compilation. Its first execution60081 starts five Swift cases but aborts before any completes: public KotlinError.init(wrapped: KotlinBase) calls a Native helper that forcibly casts a plain Kotlin object to Throwable. Preserve the raw crash and zero-completed result; XCTest's separate zero-test wrapper summary is not acceptance.

The source correction in syn-kotlin/verification/swift-exporter-patches/ordinary-runtime/KotlinRuntimeSupport.kt.patch uses a checked Throwable conversion and returns the existing absent-message value for other objects. Valid Throwable message semantics and the exported C signature remain unchanged. Component **339db91315cbfc46f0a25539f908de36972872188fcb22131955ee13808f1543** changes exactly that resource atop57182, with6263 file entries and no class/add/remove changes. Actual Native90132 passes. Swift19883 executes five cases: four pass, but immediate wrapper release fails. Adding real Native GC in87391/7146 still yields four passes and one failure. Replacing successful test-framework extraction with a checked guard in62427 also fails the same lifetime assertion; the instrumentation-retention hypothesis is rejected, not presented as a repair.

Exact official2.4.20 source revision **890ac1d94fdb80eb85f0eeb5be5e4352df987b2f** establishes the next boundary. KotlinBase.h Git blob **5ec1617148b9203e94475523336b119c5d79b127** is byte-identical to the consumed Native SDK header, SHA256 **14390b9a40bcd8f924909692249ca95468cd3de36157be762371b1673dab86f3**. ObjCExportClasses.mm blob **48c1ab0a73b873e77860cb998f9f36d56f8d6a07** returns owned alloc/init results from both wrapper factories. Actual Swift6/Werror importer SIL30109 declares both results autoreleased. This is a Native runtime return-ownership mismatch, distinct from String diagnostic storage. The source-header patch declares the actual retained-return convention without changing selectors, parameter/result types or Native binary implementation. It does not hide exported Kotlin APIs or add Kotlin Objective-C decorators.

A separate artifact-local Native SDK copy was checked across **68,681 files**: only konan/swift_export/kotlin_runtime/KotlinBase.h changes, no binaries change and no symlinks exist. Corrected header SHA256 **99186d1dd029a8ba5b66dae390a3aec0d6d0e5293eff641d85daab5e212723c3**; audit SHA256 **fc307f74503549f798b5be8b5d05b1401db69eade9c15a36b7767fc9f35d43c9**. Installed/shared SDK inputs remain unchanged. Native24366 compiles, statically links and embeds successfully. Every generated Native runtime header matches the corrected source input;14 runtime resource copies match the pinned exporter component. Native ARM64 archive SHA256 **d44714fc6d46b1b6ccc469031482f46603d148a0139382b1f136d0652c49af6c**.

Actual Swift7394 exits0. All five named cases pass exactly once with no failures/skips: capturesMessage, snapshotSurvivesMutation, fallbackForMissingMessages, releasesOriginalWrapper and transfersOwnedDiagnostic. They test Unicode/empty messages, mutation-independent snapshots, missing/non-Throwable fallback, real wrapper release after Native collection while retaining the message, and detached-task String transfer. All ten product/test frontend invocations across RuntimeDiagnostic, KotlinRuntimeSupport, KotlinCoroutineSupport, ExportedKotlinPackages and RuntimeSupportContractTests use **Swift6, warnings-as-errors, ARM64/macOS15**. Two Package.swift configuration frontends are separate; their language modes do not establish dependency modes. All63 pinned emitted inputs remain unchanged. The weak-reference test now proves the exercised existential-wrapper lifecycle; it is not a general GC/ARC leak certificate or a class-factory-specific behavior test.

Reproduction inputs live under automation-artifacts/2026-10-20-syn-isolated-library-validation/runtime-wrapper-corrected. Final35-input manifest SHA256 **ecc09a91dfbecbac8234f27ef3cd3382028dd28d61cb90c0431a942c7c63df61** and verified result SHA256 **f09cad94737b93834a83136d901192967464a266fd2539da91ac2e710341b79f** freeze paths, hashes, source blobs, commands and negative histories. Source fixtures and the two additional diffs are durable in syn-kotlin/verification/swift-exporter-patches/ordinary-runtime. Apply diffs to the exact upstream source paths; package only the KotlinRuntimeSupport.kt resource change atop the pinned parent and audit every archive entry. Apply the KotlinBase.h source correction to a separate Native SDK input before generation; never edit emitted files. Preserve all other SDK file hashes. The SDK/header input must be coherent with the exporter resource and Native2.4.20 binaries.

Executed command sequence from the Syn checkout:

```sh
runtimeArtifacts=../automation-artifacts/2026-10-20-syn-isolated-library-validation/runtime-wrapper-corrected
ruby "$runtimeArtifacts/run-embed.rb"
python3 "$runtimeArtifacts/pin-output.py"
ruby "$runtimeArtifacts/run-contract-tests.rb"
```

The retained runners use the existing Gradle9.8.0 launcher, offline published dependencies, workers2/in-process Kotlin, artifact-local project/output/Gradle/Konan/SwiftPM/module/temp directories, explicit source-header SDK selection, real Native embed and verbose SwiftPM execution. Invocation files intentionally reject accidental reruns. For a fresh reproduction use a new artifact directory and refreshed complete manifest while retaining the original logs; copy only the hand-authored fixture/runner files and reuse pinned read-only tool inputs. The runtime fixture's collector uses the real kotlin.native.runtime.GC.collect API with its explicit NativeRuntimeApi opt-in. The weak-nil/message assertions remain. [Kotlin's GC/ARC object reclamation rules](https://kotlinlang.org/docs/native-arc-integration.html#object-reclamation) and [Clang's retained-return contract](https://clang.llvm.org/docs/AutomaticReferenceCounting.html#retained-return-values) explain the tested boundaries.

This result expands the delivery closure beyond an exporter-only component. It does not authorize installed compiler/SDK replacement, production adoption, external submission or publication. The intentional removal of mutable KotlinError.wrapped, arbitrary reverse-callback ownership gap and original22 suite remain separate. AWS/OpenTelemetry each require exact-head strict compilation and nonzero actual behavioral tests; these five fixture passes are not their acceptance. Iris's active OpenTelemetry checkout/build ownership remains preserved. Reported SDK/semantic-conventions matrix job labels and the base OpenTelemetry publication predating strict configuration do not establish strict Swift or emulator/QEMU runtime coverage without actual test reports.

### Actual Syn six-case strict validation and KotlinError compatibility reference (3364aa7 — historical checkpoint before 32f6c1ac execution)

> [!NOTE]
> **Historical checkpoint — item 2 (`dbfc468a` "prepared / unbuilt") was subsequently built and verified at [`db24b93`](#api-preserving-kotlinerror-mixed-mode-validation-exporter-32f6c1ac--header-99186d1d-durable-ref-db24b93) and [`70d733a`](#consolidated-api-preserving-delivery-reference-70d733a--delivery_currentmd-and-procmacro2-mixed-mode-validation-3d8718c):** At commit `3364aa7`, only the API-breaking snapshot candidate (`11da4d91...`) had been executed while the source-compatible `KotlinError.wrapped` resource (`dbfc468a...`) was still prepared/unbuilt. The source-compatible option was subsequently packaged as exporter `32f6c1ac5f137a204bdcaca192b0e59a9999f72d9c549ea0018b6d5fe77b797e` + header `99186d1dd029a8ba5b66dae390a3aec0d6d0e5293eff641d85daab5e212723c3` and passed 6/6 Syn tests, 5/5 Native-backed legacy `KotlinError.wrapped` tests, and 3/3 ProcMacro2 tests under strict mixed-mode `-warnings-as-errors`. Preserve the `11da4d91` negative client proof (`69114`) below as historical API-break evidence.

Syn's compatibility reference is committed and pushed at **3364aa7a3ddb76aea2357f0329c367365bf796b1** in `syn-kotlin` PR #166 (`syn-kotlin/verification/swift-exporter-patches/syn-library/KOTLIN_ERROR_COMPATIBILITY.md`, SHA256 **e6af35e078d3addac6a6fbf94216b6654eb845664833d303b554389749528a78**; `compatibility-evidence.json`, SHA256 **02ea8fd87ed15bb6b01d76d53931f0634979deca4084105bab1d40abccbaf184**). Keep the two candidates strictly distinct:

1. **TESTED six-case snapshot candidate (`deaafb4edd143e82c13346244b35d1e5b4af0e46`):**
   - Uses Syn source **deaafb4edd143e82c13346244b35d1e5b4af0e46** (durable verification files committed at `1c71b20ecc216ea2e78e40629d310f79a042895d`), coherent exporter SHA256 **11da4d91a1985c1e079e5b363ad33a1404fbd223d702131d19439446c01ba555**, and coherent Native ownership header SHA256 **99186d1dd029a8ba5b66dae390a3aec0d6d0e5293eff641d85daab5e212723c3**.
   - Native session **38329** and Swift session **42332** both exited 0: all **six unchanged Syn Swift test cases** executed and passed once with zero failures or skips, and all **18 product/test frontend invocations across 9 modules** used **Swift 6, `-warnings-as-errors`, and macOS 15** (`automation-artifacts/2026-10-21-syn-real-swift-deaafb4/optional-set-corrected/verified-result.json`, SHA256 **f6e72bab92c954380c03f1a491e073f29bcdcc4ff6be9934e8e495a1bece1ca9**).
   - Incremental `SirAsSwiftSourcesPrinter.kt` (combining empty package-level `Sealed` marker conformances onto one extension to fix generated-header ordering) and `TypeBridging.kt` (explicit `Optional.some` wrapping and non-redundant `Set<String>` return) patches preserve named declarations and C headers; no `Array` bridge repair is claimed.
   - **Public contract break:** This candidate replaces `KotlinError`'s mutable `public var wrapped: KotlinBase` with an immutable `public let message: String` snapshot. Actual old-client negative compilation (session **69114**, `automation-artifacts/2026-10-21-syn-runtime-compatibility/client-result.json`, SHA256 **1674b235aef595556643757a4e7fa0a65e38c99ec9a5c62b81cf9e2a963a4d74**) proves that legacy getter and setter callers fail with `KotlinError has no member wrapped`.
2. **PREPARED source-compatible original `KotlinError` struct option (`dbfc468a`, unbuilt/unaccepted):**
   - `automation-artifacts/2026-10-21-syn-runtime-compatibility/source-compatible-preparation/KotlinRuntimeSupport.swift` (SHA256 **dbfc468afd0cbf3325e555ec2ded72f583e1fe4ac3fd690cf831ff8bf3668266**; patch SHA256 **4ce5f4ef806ab54fef3485802f2422f664f0ee5481d22cd817c4221ba3d9826f**) restores the original `KotlinError` struct (`public var wrapped: KotlinBase`, reference identity, mutation, and lazy `description` evaluation) while retaining `internal import` and the redundant-cast fixes alongside the checked Throwable message helper and coherent Native ownership header.
   - Its intended target configuration is **repaired dependency modules in Swift 5 language mode plus consumer modules in Swift 6 language mode, with `-warnings-as-errors` on BOTH and Native `-Werror`**.
   - **Not yet built or accepted:** This source-compatible candidate has **not** been compiled, packaged, or tested. Neither of the two consumer language-mode comparison arms (`async-trait-kotlin` `report.json` SHA256 **7902e419248e09a0868e0d989863a508c7d28a713e106a20e183d52aaec41ae3**; `openssl-sys-kotlin` `report.json` SHA256 **2ff2e0380ba11b70839a31ab086537b0a8797970212ebefaf3e66605db135226**) is an accepted strict result because their passing Swift 5 dependency controls disabled dependency `-warnings-as-errors` and suppressed generated Native bridge warnings.
   - Scanning 9,252 Kotlin/Swift source files across five repositories (`Syn`, `AWS`, `Libc`, `Anyhow`, `ProcMacro2`; `repository-scan.json` SHA256 **3fae889f5124ce0b83017c4f7f4d4eb30d30f47ae7f5a1a31466781b36372ef1**) found zero handwritten `KotlinError` or `.wrapped` uses, which is an inventory observation and **not** a public-client compatibility guarantee.
3. **Required next validation and ownership boundaries:**
   - Next validation of the source-compatible producer option requires a coherent repaired producer and real consumer build with `-warnings-as-errors` enabled on both dependency and consumer Swift modules and Native `-Werror` retained, plus behavioral tests for legacy `wrapped` getter/setter, reference identity, lazy `description` evaluation, wrapper lifetime, and unchanged real consumer tests.
   - Shared producer ownership remains `syn-kotlin`; do not duplicate the unchanged language-mode comparison. The exporter JAR alone cannot deliver the required `KotlinBase.h` Native ownership header.
   - Do not present an API break or a new compiler product coordinate as the sole or authorized route. Neither the scoped Syn six-case pass nor this prepared source-compatible option establishes `async-trait-kotlin` or `openssl-sys-kotlin` consumer acceptance or clears their Kanban cards.

<a id="api-preserving-kotlinerror-mixed-mode-validation-exporter-32f6c1ac--header-99186d1d-durable-ref-db24b93"></a>
### API-preserving KotlinError mixed-mode validation (exporter 32f6c1ac + header 99186d1d, durable ref db24b93)

Syn's consolidated compatibility reference is clean and pushed at **db24b934e8db9b0d843a83f3902d9db057e65e67** on `syn-kotlin` PR #166 (`syn-kotlin/verification/swift-exporter-patches/syn-library/KOTLIN_ERROR_COMPATIBILITY.md`, SHA256 **cf633dcb167bca214b2d33e248d6a640a3b60c63cd43a2474b61b1a685627022**; `legacy-api-results.json`, SHA256 **025a82079a034e77eccf73293e8ba2b5f4828527eac47fc277a80cf51bd9b4fa**; retained original receipt `automation-artifacts/2026-10-21-syn-legacy-error-validation/verified-result.json` with identical SHA256 **025a82079a034e77eccf73293e8ba2b5f4828527eac47fc277a80cf51bd9b4fa**; and the five executed fixture bodies in `LegacyErrorTests.swift` committed alongside that reference). All earlier negative language-mode matrices and the snapshot candidate's API-break evidence remain preserved:

1. **Coherent API-preserving producer closure (tested against Syn source `3364aa7a3ddb76aea2357f0329c367365bf796b1`):**
   - Exporter component SHA256 **32f6c1ac5f137a204bdcaca192b0e59a9999f72d9c549ea0018b6d5fe77b797e**, paired with coherent Native ownership header SHA256 **99186d1dd029a8ba5b66dae390a3aec0d6d0e5293eff641d85daab5e212723c3**, preserves the exact original `KotlinError` `wrapped` struct (`public var wrapped: KotlinBase`).
2. **Scoped Syn and legacy-client validation:**
   - Actual Native task **28844** and Syn Swift task **20241** exited **0**: all **six unchanged named Syn cases** (`smokeTest`, `pathRemainsModuleStyleWithLeadingColon`, `tokenTransformPreservesTheTokenStream`, `diagnosticRetainsItsMessageAndIdentity`, `fileResultExposesItsSyntaxTree`, `tokenResultExposesItsTokenStream`) passed once with zero skips.
   - Raw Syn wrapper exit **1** (`accepted: false`) is retained as a parser-only outcome with independent retained-log classification (`syn/actual-result-classification.json` SHA256 `fbe3755692aedbebe08d8bc43b85a09ee1da89fbd1ea18c1b27d2a90fc15db77`); tests were not rerun to replace that receipt.
   - Unchanged `wrapped` getter/setter client (`WrappedClient.swift`, task **99880**) compiled with exit **0** (`syn/client-result.json` SHA256 `e4514fa28a6010c0c96d4ade249f97c64d52eff7f049c6372e7823cddd02d68c`).
3. **Five-case Native-backed legacy `KotlinError` contract suite:**
   - Separate actual Native task **5575** and Swift task **17955** exited **0**: all **five real Native-backed cases** (`preservesOriginalReference`, `observesMessageMutationLazily`, `replacesWrappedReference`, `copiesKeepTheirOwnMutableSlot`, `retainsUntilErrorIsReleased`) passed once with zero skips.
4. **Effective language modes and flags (must remain explicitly qualified):**
   - Generated dependency modules explicitly compile in **Swift 5** language mode; authored consumer modules compile in **Swift 6** language mode.
   - All observed product and test frontends retain **`-warnings-as-errors`** and target **`arm64-apple-macosx15.0`**, with Native `-Werror` enabled.
   - The generated SwiftPM `PackageTests` runner target lacks an explicit language-version flag (compiler default with `-warnings-as-errors` retained) and must remain separately qualified rather than claimed as an explicit Swift 6 frontend.
5. **Scope and non-guarantee boundaries:**
   - Scope remains **source-compatible mixed-mode validation**. It provides **no** precompiled binary-old-client ABI guarantee, **no** exported `Throwable` subtype/cause/`catch` round-trip guarantee, **no** arbitrary cross-actor safety guarantee, **no** automatic `Array` lowering repair, **no** arbitrary reverse-callback repair, **no** `aws-kotlin` or `libc-kotlin` acceptance, **no** `async-trait-kotlin` or `openssl-sys-kotlin` consumer acceptance, **no** full-KMP gate, and **no** all-module Swift 6 guarantee.
   - All producer sessions and grants have returned. No production policy change, adoption, Actions dispatch, version bump, merge, publication, or new tooling product is authorized by these results. Do not promote this across the fleet or clear unrelated consumer cards.

<a id="consolidated-api-preserving-delivery-reference-70d733a--delivery_currentmd-and-procmacro2-mixed-mode-validation-3d8718c"></a>
### Consolidated API-preserving delivery reference (70d733a / DELIVERY_CURRENT.md) and ProcMacro2 mixed-mode validation (3d8718c)

Syn's current delivery reference is clean and pushed at **70d733ae43bfb25c06b74523bc112b52efcc8706** on `syn-kotlin` PR #166 (`syn-kotlin/verification/swift-exporter-patches/syn-library/DELIVERY_CURRENT.md`, SHA256 **92110deb7f5af5a5020afd4e1b9449f2d4e5ba8bbd110ef97048091eee039744**; independent ProcMacro2 downstream review `syn-kotlin/verification/swift-exporter-patches/syn-library/downstream-independent-review.json`). All historical snapshot API-break evidence (`11da4d91a1985c1e079e5b363ad33a1404fbd223d702131d19439446c01ba555` + `client-result.json` `1674b235aef595556643757a4e7fa0a65e38c99ec9a5c62b81cf9e2a963a4d74`) and earlier negative language-mode matrices remain preserved, while `DELIVERY_CURRENT.md` supersedes the old snapshot-break delivery proposal so accepting `KotlinError.wrapped` removal is **not** presented as mandatory:

1. **API-preserving closure across Syn (6 cases + 5 legacy cases) and independent ProcMacro2 (`3d8718c`):**
   - Using the API-preserving exporter component SHA256 **32f6c1ac5f137a204bdcaca192b0e59a9999f72d9c549ea0018b6d5fe77b797e** plus coherent Native ownership header SHA256 **99186d1dd029a8ba5b66dae390a3aec0d6d0e5293eff641d85daab5e212723c3** (preserving the original `public var wrapped: KotlinBase` property, reference identity, mutable slot copy semantics, and lazy `description` evaluation), ProcMacro2 at clean source **3d8718c2412be6aa99175ab88663edec8dbfa6db** (session **33903**) completed terminally with exit **0** (`automation-artifacts/2026-10-21-proc-macro2-mixed-mode-3d8718c/verified-result.json`, SHA256 **3f6ca3ee7743d7c72fe8dcd59a7a51aa1e7883d9402c6a213c64d917adcc8035**).
   - Actual Native main compilation, generated Native bridge compilation, ARM64 static linking, and embedding succeeded (`native_exit: 0`, `swift_exit: 0`). All **three unchanged named ProcMacro2 Swift cases** passed once (`tests: 3`, `failures: 0`, `skips: 0`).
   - The **282-public-record surface guard** and **same-version C-header guard** both passed. The producer `// swift-tools-version: 5.9` manifest was retained with no API-output rewrite, and all **84 source pins**, **16 worker tool JARs**, generated outputs, and archive remained unchanged (`request.json` `18975e479b737a5cd9b60f87f8e6033b13ac7e98f409039a6e5dadd637eea16f`, `result.json` `ab4404bcb3f056812910e8bf3df12d231a781f28af4e2780989ad3974a574788`, `logs/gradle.log` `2924cbb1322230977aafcab5ecd3f87902884da25a47f95a708b70543cdd5c16`, `logs/swift.log` `ebb9532ed0093abebbd2e617c7bd0b8cc2b7d269db76c9f4bfea9c94429f56de`, `generated-inputs.json` `9ab42d8fa7631d200aa4e344da4cfb46ccbff1c28f81fdf6a0032f5c86d68ce5`, `compiler-classpath.json` `5e0c8873e53ff25f25a53018257ed240d9f5efc06800f0954f8a1c12712eb116`, `recipe-pins.json` `8579b7e76091ef4ac07926051018cd1ef3232b702777128f3f70262f097f221b`, `source-pins.json` `812164f95def7f7a88d1bb2f78b39a98580a6699ed5cc8523d78c2dc67111bf7`).
2. **Independently audited ProcMacro2 frontend flags (16 product/test records total):**
   - **6 dependency modules** (`KotlinRuntimeSupport`, `ExportedKotlinPackages`, `KotlinCoroutineSupport`, `KotlinStdlib`, `IoGithubKotlinmaniaProcMacroKotlin`, `ProcMacro2`) contribute **12 frontend records** explicitly in **Swift 5** language mode (`-swift-version 5`).
   - **Authored consumer tests** (`SwiftTestHarnessTests`) contribute **2 frontend records** explicitly in **Swift 6** language mode (`-swift-version 6`).
   - **Generated `SwiftTestHarnessPackageTests` runner** contributes **2 frontend records** with **no explicit language-mode flag** (`modes: ["None"]`, `generated_runner_explicit_mode: false`) and must remain separately qualified rather than claimed as an explicit Swift 6 frontend.
   - All **16 product/test records** enforce **`-warnings-as-errors`** and target **`arm64-apple-macosx15.0`** (`werror: true`, `mac15: true`), with 2 `Package.swift` configuration frontends classified separately.
3. **Production delivery requirements vs diagnostic classpath boundaries (`DELIVERY_CURRENT.md`):**
   - **Worker tooling vs runtime:** The 16 resolved worker tooling dependencies on `swiftExportClasspath` remain strictly separate from product runtime dependencies (`commonMain` / published KMP coordinates), and artifact-local diagnostic classpaths are not production delivery.
   - **Minimum closure for any custom KotlinMania-owned tooling route:**
     1. Complete reproducible source/class/resource patch closure against the exact upstream revision (including the original `KotlinError` API resource and `KotlinBase.h` diff), full signature/instruction/access-flag/facade/archive-delta audits against the unmodified baseline, and **two identical clean-build hashes** reproduced from pinned inputs.
     2. **Coherent project-local Native SDK/header selection (`kotlin.native.home`):** Because an exporter JAR alone cannot deliver `KotlinBase.h`, any custom delivery requires a verified project-local SDK copy applying only the reviewed `KotlinBase.h` ownership correction while verifying unchanged Native binaries and **never mutating installed/shared SDKs**.
     3. **Explicit tool-only `swiftExportClasspath` selection** with locked immutable version/checksum, preserving real published KMP product dependencies (no `mavenLocal`, source substitution, JetBrains namespace impersonation, or cache overwrites).
     4. **Explicit dependency (Swift 5) vs consumer (Swift 6) language-mode configuration** with both Swift `-warnings-as-errors` and Native `-Werror` intact, generated APIs preserved, and the generated `PackageTests` runner qualified separately.
     5. **Licensing, notices, and Rust-free package contents:** The experimental 6,263-entry archive contains zero Rust/Cargo entries (`RELEASE_CONTENTS.md`), but currently lacks license/notice entries; any redistributable release requires verified upstream/bundled license notices, modified-file attribution, SBOM/provenance, and a fresh audited archive hash after notice additions.
   - **Exact authorization, deterministic two-build reproducibility, SDK helper verification, Maven Central publications (`io.github.kotlinmania:swift-export-embeddable:2.4.20-km.1`, `io.github.kotlinmania:proc-macro2-kotlin:0.1.7`, `io.github.kotlinmania:syn-kotlin:0.4.0`) & legitimate-dependency-edge downstream adoption (`KotlinMania/kotlin-swift-export-tooling`, 2026-10-21):**
     - **Published release, green CI runs & immutable handoff receipt (`published-handoff-2.4.20-km.1.json`):**
       - Repository: [`https://github.com/KotlinMania/kotlin-swift-export-tooling`](https://github.com/KotlinMania/kotlin-swift-export-tooling), source/tag **`3687a03e783c3f7215c431dc4600314a64678761`**, release [`https://github.com/KotlinMania/kotlin-swift-export-tooling/releases/tag/v2.4.20-km.1`](https://github.com/KotlinMania/kotlin-swift-export-tooling/releases/tag/v2.4.20-km.1).
       - Green CI runs: PR CI [`39590144174`](https://github.com/KotlinMania/kotlin-swift-export-tooling/actions/runs/39590144174), exact-`main` CI [`39591983416`](https://github.com/KotlinMania/kotlin-swift-export-tooling/actions/runs/39591983416) (`main_native_swift_receipt_sha256`: **`6f97378c016801c1c2da0116750c81a78c2a4353c346bc76ee9299c3b034a1fd`**), and release/Maven Central retrieval CI [`39594386832`](https://github.com/KotlinMania/kotlin-swift-export-tooling/actions/runs/39594386832) (`retrieval_receipt_sha256`: **`ee3ed221eecccd1bd2dd78ece8470eefa163bc07e2d2318079502fb12cac37b1`**, `resolved_worker_receipt_sha256`: **`b17aa63531ff3430e296a335b4b92b6c92b55d93d63068a913270cc9f98176a5`**, signing public key fingerprint: **`1B07CFB6CD172EF8B1E078A7BC3EFBDB5650D775`**).
       - Immutable handoff receipt: [`automation-artifacts/2026-10-21-swift-tooling-release/published-handoff-2.4.20-km.1.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-swift-tooling-release/published-handoff-2.4.20-km.1.json) (`SHA256: 1a4499a7b5c2d57517c565c486bdd184b784d526b7c64aace7be8143ad23d363`).
       - All **6 published & independently retrieved Maven Central files** at `https://repo.maven.apache.org/maven2/io/github/kotlinmania/swift-export-embeddable/2.4.20-km.1/`:
         - `swift-export-embeddable-2.4.20-km.1.jar`: **`e1b871c93dd9556fb7235cd05f70b3c2bc7d32f2cb1b69f1d9161918fd088f3a`** (identical across clean builds `rebuild-d` and `rebuild-e`; kept strictly distinct from earlier patched-JAR diagnostic component `32f6c1ac5f137a204bdcaca192b0e59a9999f72d9c549ea0018b6d5fe77b797e`)
         - `swift-export-embeddable-2.4.20-km.1.pom`: **`a149229ad82bb239bbe39419948d43b9ab37cb9f6f7e0f9117c762504e841bf8`** (resolving all **16 worker components** in `b17aa635...` **without** a duplicate official `org.jetbrains.kotlin:swift-export-embeddable`)
         - `swift-export-embeddable-2.4.20-km.1-sources.jar`: **`201b896f4aa305840dbe82196af4d6d85edd84cd04c3303e3da24c2ff0efc714`**
         - `swift-export-embeddable-2.4.20-km.1-javadoc.jar`: **`e9f4031419d4223ab17106996f2f57f728cda652fe2a16c2af89b634d7d0569e`**
         - `swift-export-embeddable-2.4.20-km.1-sbom.json`: **`a39d1122bcb85b4ed496a93b3b6c021bf885e52800d01a3a56e3d78c22318f80`**
         - `swift-export-embeddable-2.4.20-km.1-native-sdk-helper.zip`: **`791b07cdd195008ad46f182693e3316747530fcb0b765478cb0201ac23cb2fb2`** (packaging `scripts/prepare_native_sdk.py` `c5a6f22b55cebaa21eef856cded66210b1dde4bc98ccb69046a2d498c6df1775` and `patches/KotlinBase.h.patch` `86295c6ad02441d080f8136dc4f5cb8dd0ed33f66aeff9caf271aa2d51f291fe`)
     - **Project-local Native SDK helper proofs & required `-Xauto-cache-dir=<owned build output>` (preserved separately):**
       1. **Earlier `9,719`-file project-local SDK helper validation (used by `ProcMacro2` session `26558`):** Real execution produced `/Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-swift-tooling-release/sdk-helper-validation/native-sdk` across **`9,719` files**, modifying **only** `konan/swift_export/kotlin_runtime/KotlinBase.h` to `99186d1dd029a8ba5b66dae390a3aec0d6d0e5293eff641d85daab5e212723c3` while leaving all Native binaries and the installed source SDK untouched (`installed_source_modified: false`). Receipt: [`automation-artifacts/2026-10-21-swift-tooling-release/sdk-helper-validation/receipt.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-swift-tooling-release/sdk-helper-validation/receipt.json) (`SHA256: e9caac5c120f889268fc4c9ea216cdb5bfdac5cf7e5c0610ca5688abf27d259c`). **Provenance qualification:** In this earlier run, the helper script source hash (`/Volumes/stuff/Projects/kotlinmania/kotlin-swift-export-tooling/scripts/prepare_native_sdk.py`, `SHA256: c5a6f22b55cebaa21eef856cded66210b1dde4bc98ccb69046a2d498c6df1775`) was **observed AFTER execution**, not frozen beforehand, whereas the SDK creation receipt (`e9caac5c...`) was frozen prior to consumer execution.
       2. **Pre-publication `40,932`-file packaged-helper validation with pre-execution frozen hashes:** Candidate helper ZIP (`distribution-a/swift-export-embeddable-2.4.20-km.1-native-sdk-helper.zip`, `SHA256: e7628656305829d8664329cdb95f8167b667b6c997a91eac51b16add597d8237`) was extracted and its helper/source/patch hashes (`prepare_native_sdk.py` `c5a6f22b55cebaa21eef856cded66210b1dde4bc98ccb69046a2d498c6df1775`, `inputs.lock.json` `6d24fd4210e2e835c78f189e2191bbdf3309f4b6d432abaa8d860a9134923ea9`, `KotlinBase.h.patch` `86295c6ad02441d080f8136dc4f5cb8dd0ed33f66aeff9caf271aa2d51f291fe`) were **frozen BEFORE execution** in [`automation-artifacts/2026-10-21-swift-tooling-release/packaged-helper-validation/frozen-before-execution.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-swift-tooling-release/packaged-helper-validation/frozen-before-execution.json) (`SHA256: c702f7c499cd86a95dbaaec422e5156116768955839431ccf80d4636b23a4ffa`). Executed against the official Maven SDK archive (`SHA256: b70d4fd2855fdb054fc9604beaa7de83c18d786454ae948de72a226e81f07dc8`, **`40,932` input files**, `unchanged_file_count: 40931`), **only** `konan/swift_export/kotlin_runtime/KotlinBase.h` changed to `99186d1dd029a8ba5b66dae390a3aec0d6d0e5293eff641d85daab5e212723c3`, with `unchanged_native_binaries: true` and `installed_source_modified: false` ([`automation-artifacts/2026-10-21-swift-tooling-release/packaged-helper-validation/sdk-audit.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-swift-tooling-release/packaged-helper-validation/sdk-audit.json), `SHA256: 9efd1fd9d66e226bdf5fd129310ca0da2be1a1e46c140515be832165a0252f4e`).
       3. **Published helper ZIP (`791b07cd...`) & exact-`main` CI acceptance (`6f97378c...`) with `KotlinNativeLink` `-Xauto-cache-dir`:** During SDK preparation against the `40,932`-file official SDK (`b70d4fd2...`), the helper modifies **one** original SDK file (`KotlinBase.h` `14390b9a...` $\to$ `99186d1d...`), leaving **`40,931` files unchanged**. In exact-`main` CI run [`39591983416`](https://github.com/KotlinMania/kotlin-swift-export-tooling/actions/runs/39591983416) (`6f97378c016801c1c2da0116750c81a78c2a4353c346bc76ee9299c3b034a1fd`), configuring `tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach { val cache = layout.buildDirectory.dir("native-auto-cache/$name"); toolOptions.freeCompilerArgs.add(cache.map { "-Xauto-cache-dir=${it.asFile.absolutePath}" }) }` ([`kotlin-swift-export-tooling/README.md:45-58`](file:///Volumes/stuff/Projects/kotlinmania/kotlin-swift-export-tooling/README.md#L45-L58)) ensured that cold dependency auto-cache generation during linking never wrote into `$PREPARED_NATIVE_SDK/klib/cache`; **after preparation**, all **`40,932` prepared SDK input files** remained unchanged across the complete Native build/link/embed and `3/3` unchanged `ProcMacro2` Swift test execution (`12` dependency-mode-5 + `2` authored-mode-6 + `2` generated-runner-no-explicit-mode frontend records, all with `-warnings-as-errors` and `arm64-apple-macosx15.0`).
     - **Independent `ProcMacro2` pre-publication release-candidate validation (`session 26558`, clean `3d8718c2412be6aa99175ab88663edec8dbfa6db`):** Using release candidate `e1b871c9...` + the `9,719`-file helper SDK (kept strictly distinct from earlier diagnostic session `33903` using `32f6c1ac...`), Native main/bridge/static ARM64 link/embed and `swift test` exited `0` (`native_and_swift_exit: [0, 0]`) and all **3 unchanged named Swift cases** (`tokenStreamParsingAndFormatting`, `tokenStreamParseFailure`, `literalAndPunctExport`) executed and passed once (`failures: 0`, `skips: 0`). Across **16 actual product/test frontends** (`configuration_frontends: 2` separate), `12` dependency records across 6 modules compiled in explicit **Swift 5**, `2` authored `SwiftTestHarnessTests` records compiled in explicit **Swift 6**, and `2` generated `SwiftTestHarnessPackageTests` runner records compiled without an explicit mode flag (`modes: ["None"]`, `generated_runner_explicit_mode: false`), with **all 16 enforcing `-warnings-as-errors` and `arm64-apple-macosx15.0`** and Native `-Werror` retained. All `282` public declaration records and same-version C-header guards passed (`// swift-tools-version: 5.9` retained). Receipt: [`automation-artifacts/2026-10-21-proc-macro2-release-e1b871c/verified-result.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-proc-macro2-release-e1b871c/verified-result.json) (`SHA256: d3efd67db7e934bc08c3009f21e28028d066c9ec3726d2b8e7982eafa2e3577e`; pinning `request.json` `b694d60bd5d1fae2b5541de6baf92980382ee9e37582ac0641d35db3e255423f`, `result.json` `3b1a9a1c31b9c508fc5dae56d0c5db778b9ead1db04b861d1d900caee8efe208`, `source-pins.json` `812164f95def7f7a88d1bb2f78b39a98580a6699ed5cc8523d78c2dc67111bf7`, `producer-inputs.json` `b83f1754f84e7520df4369195379db1feeebdd21b41691d0f0d1a02a23b9c0bf`, `sdk-pins.json` `5e08bc52d5dda2a80b8351a1281708a77ad6a48a2165f4beb0bd79fc8e5c98fd`, `compiler-classpath.json` `69014c298668d54c47cefc866c7d5ab9325b07a6384ecef82f3b862b6fc0b420`, `recipe-pins.json` `8eeb456a9ab277f786fd02714d649cb44b58e08ec76628929746538ba6e6c783`, `generated-inputs.json` `4a84db0df722397b850979351de62ccef10006cab5ff93934a72a506ca57a709`, `logs/gradle.log` `d22699d197d0b5b7bc239f589583068b1800facf4f91434422c920ef71c1dd16`, `logs/swift.log` `3c3eeee3a8d4041ab51daf0a4c12e00b76535db1510b9bd4efc0a4f5ead88bbf`); Syn independent review SHA256: **`73f7786053078f88de249ca517850768a4b4c5dbbcec02fb8cea1742094c38ac`**.
     - **Published `io.github.kotlinmania:proc-macro2-kotlin:0.1.7` (`80e3e1a7...`) & `io.github.kotlinmania:syn-kotlin:0.4.0` (`94d99bde...`) releases and legitimate-dependency-edge downstream adoption:**
       1. **Published `proc-macro2-kotlin:0.1.7` ([`verified-handoff.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-proc-macro2-central-0.1.7/verified-handoff.json) `SHA256: 8bd790091d5497cbfefef868cda860a7ceacb6c89ca85fe73606a951d6414519`):** Tag/source [`80e3e1a7ce0106f2eca66b4e3893c6dc07e16c4c`](https://github.com/KotlinMania/proc-macro2-kotlin/releases/tag/v0.1.7) ([PR #103](https://github.com/KotlinMania/proc-macro2-kotlin/pull/103), PR CI [`39601562312`](https://github.com/KotlinMania/proc-macro2-kotlin/actions/runs/39601562312) green, exact-`main` CI [`39602916681`](https://github.com/KotlinMania/proc-macro2-kotlin/actions/runs/39602916681) `12/12` green with `793` KMP + `3` Swift executions, publication CI [`39604506913`](https://github.com/KotlinMania/proc-macro2-kotlin/actions/runs/39604506913) where accepted Central deployment `920dae47-341f-4722-a294-479a89459a8f` timed out only during status polling after `PUBLISHING` `120/120` with no repeat upload, qualified in [`publication-timeout.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-proc-macro2-central-0.1.7/publication-timeout.json) `6caea44c97449270d195a2413aa2d3ce99b3cd41ff143cc7d3cceecbc80ece20`; [`published-metadata.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-proc-macro2-central-0.1.7/published-metadata.json) `42d74e06d8f2fa98d86fcfa5e9e5568b2d0a205b137de19d56b4af1cfa8c1a36`, [`signature-verification.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-proc-macro2-central-0.1.7/signature-verification.json) `c024d08409c9a1075a729ef2fe88d8d737a3902ad331ea85f2ebd7fae1c95169`, [`explicit-return.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-proc-macro2-central-0.1.7/explicit-return.json) `30997757299ef5dcbd23ed6dcfc322d7f80923d1bde7bcff6a4516da4b5b2c95`; `proc-macro2-kotlin-0.1.7.module` `f519acc1fb3b7628aad84581b07ad0a409f9cf0d083140b8db2a32a84b2749f5`, `.pom` `bc8a9215b2c280589280964c43da47d8f6b1eec4cbb0bdebcb1c4641f57745c7`, `-android-0.1.7.module` `cdc93d2355fd44c07d75af057726b66ffbb69f025f7779f1323be99fe59c59fc`, `-android-0.1.7.pom` `575e873725864553f3e501274b7f7ed6c3e8d4c0b5bc85f4fd002758d8d8a2bd`, `-android-0.1.7.aar` `cfad6973e50765bb8d15a249c8c5e3023eb8e9b41447b5664e26e0b48445bd33`), selecting published `io.github.kotlinmania:proc-macro-kotlin:0.1.4` (`proc-macro-kotlin-0.1.4.module` `c19a2c9fd36a19518dd360bfc21f89b6a8dc99818f4b227be964bfe382da9223`, `-android-0.1.4.module` `e46d349c80725f08bd82ba82bd9d48d75514add36433c237ff4802904a08dbbe`, `-android-0.1.4.aar` `864cbedbafdf952aac7737d843468f18771863cd6efc73d6f6ef7d8977a3933d`, `classes.jar` `0aa3fa7422cbe9f17640be4f0ff1c77b770228dc2099fa66c9c6b0a6db02d403` relocating bundled `ApiStatus` classes under `io/github/kotlinmania/procmacro/vendored`) and passing **3/3 unchanged Swift cases** (`tokenStreamParsingAndFormatting`, `tokenStreamParseFailure`, `literalAndPunctExport`, `0` failures/skips) under dependency mode `5` (`-warnings-as-errors`), authored consumer mode `6` (`-warnings-as-errors`), and Native bridge `-Werror` (with the generated `PackageTests` runner separately qualified) plus **2/2 independent public Central consumer cases** (`parseFailureRetainsDiagnostic`, `tokenValuesSurvivePublishedBoundary`, `0` failures/skips, `gradle_exit: 0`, `consumer_resolution_sha256: 4bcef15cda4061986861905ea181dea76f5ab1e039bcb7870a0ce108e372ea5e`, [`consumer-v2-command.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-proc-macro2-central-0.1.7/consumer-v2-command.json) `72489226583e2a0245a9c5f880d57611fe716085ffb7101e195fe6c68c6d244c`, [`consumer-v2-exit.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-proc-macro2-central-0.1.7/consumer-v2-exit.json) `e89df0eae5ec7db81135aed16463968955c578bcc4f2ec43172eff351f080c17`, with explicit existing JDK 21 qualified after fresh-home preflight only discovered JDK 25).
       2. **Published `syn-kotlin:0.4.0` (`verified-handoff.json` `SHA256: 450bb79064b17a7d5831f9c4ca241323df5db516c0573dc71301a21d1accd0f8`):** Tag/source [`94d99bde9aa9c785fb190f2f87e0b7f6b53693a6`](https://github.com/KotlinMania/syn-kotlin/releases/tag/v0.4.0) ([PR #166](https://github.com/KotlinMania/syn-kotlin/pull/166)), green PR final CI [`39611272033`](https://github.com/KotlinMania/syn-kotlin/actions/runs/39611272033) (`12/12`), green exact-`main` CI [`39616216695`](https://github.com/KotlinMania/syn-kotlin/actions/runs/39616216695) (`12/12`), and green release publication CI [`39620970747`](https://github.com/KotlinMania/syn-kotlin/actions/runs/39620970747) (Central deployment `e03f7c1e-93b3-4ace-a4d7-e8a5074e65e5`, `18` published components, `79` retrieved signed files under key `1B07CFB6CD172EF8B1E078A7BC3EFBDB5650D775`, `43` archives with zero frozen Rust/tmp sources, public `syn-kotlin-android-0.4.0.aar` `3aaabe825d1765c3d7431bb62519eeae524ac6aca8972b8941cb162f43d431b0`; [`verified-handoff.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-syn-release-preparation/proc-macro2-017/publication-v0.4.0/verified-handoff.json) pinning `retrieved-artifacts.json` `cefbcb2146ac98bc4b7c08d29e4a3c4491f31e73e9529dfaa993c2cc0bfa6cfa`, `signature-verification.json` `30b0b6dc42ef4f560d910334b5418000c0fd75dbce3f211d1623c08e8b3776db`, `public-metadata.json` `532ff294f8818d1156a97b5f618b5576ada2a2be8886e7067817887f3afee785`, `verified-public-consumer.json` `188a82aaea123975567d7102d93329fc689e9ae9be439f6e852c8ffd0d4fb548`, `repository-index.json` `2f3762ed8f293fc91a227379ed6388931cb08707fbce1521c1f24b35ec09a4d4`, and `workflow-raw.log` `2df90e563dc33654f80ec01d2f3c6e3418e376bd6339138cbb4fc18a7b1d9ca8`). Passed **6/6 unchanged Swift cases** (`0` failures/skips, `40,932` prepared SDK files unchanged) plus **3/3 fresh-cache public `AndroidHost` JVM consumer cases** (`parsedFileCrossesPublicationBoundary`, `typedTokenAdapterCrossesPublicationBoundary`, `diagnosticTokensCrossPublicationBoundary`, `0` failures/skips, `exit 0`, resolving `proc-macro2-kotlin:0.1.7` $\to$ `proc-macro-kotlin:0.1.4` from public Central and compiling against the Android API — scoped as an `AndroidHost` JVM + Android API compilation/execution proof, not device/emulator). *(Later `README.md`-only `main` commit `8c9147ea8f2c45de36de890ccf1a6a8de815a3d8` via [PR #167](https://github.com/KotlinMania/syn-kotlin/pull/167) leaves tag `94d99bde...` and compiler inputs unchanged; do not attribute the earlier release CI runs to that prose-only commit.)*
       3. **Legitimate-dependency-edge & per-project acceptance rules:** Downstream repositories may adopt `2.4.20-km.1`, `proc-macro2-kotlin:0.1.7`, and `syn-kotlin:0.4.0` **only where they are legitimate project dependency or tool edges** (never `mavenLocal`, never source substitution, and never inventing a direct source dependency to force a transitive version), and each project owner must still run its own unchanged behavioral tests under dependency mode `5` (`-warnings-as-errors`), authored consumer mode `6` (`-warnings-as-errors`), and Native bridge `-Werror` (with the generated `PackageTests` runner separately qualified) before removing temporary warning/API workarounds.
     - Scope of the validated mixed-mode proof (`32f6c1ac...` & `e1b871c9...` + `99186d1d...`) does **not** extend to all-module Swift 6 dependency compilation, `Array` lowering, arbitrary reverse-call ownership, `aws-kotlin`, `libc-kotlin`, `async-trait-kotlin`, `openssl-sys-kotlin`, or full-KMP acceptance. All resources and sessions have returned; no new CI dispatch, consumer edit, or build is authorized by this reference.
4. **Complete downstream handoff receipt (`downstream-handoff.json` SHA256 `234f455a6571f5bf1222697fd66c2253a9a18f6c00e96f41e51ba7717a8a64fe`):**
   - Path: `automation-artifacts/2026-10-21-syn-legacy-error-validation/downstream-handoff.json`.
   - **Producer component:** `automation-artifacts/2026-10-21-syn-legacy-error-validation/legacy-api-swift-export-embeddable-2.4.20.jar` (`SHA256: 32f6c1ac5f137a204bdcaca192b0e59a9999f72d9c549ea0018b6d5fe77b797e`).
   - **Separate project-local Native SDK input:** `automation-artifacts/2026-10-20-syn-isolated-library-validation/runtime-wrapper-corrected/native-sdk` (selected via public Gradle property `-Pkotlin.native.home=<prepared-sdk-copy>`, containing `konan/swift_export/kotlin_runtime/KotlinBase.h` `SHA256: 99186d1dd029a8ba5b66dae390a3aec0d6d0e5293eff641d85daab5e212723c3` with zero Native binary changes and zero shared/installed SDK writes).
   - **Generated dependency manifest (`syn/build/SPMPackage/macosArm64/Debug/Package.swift`):** `SHA256: 072318fc3251ab8299eab9dd923c39771b23f4fe927bbb4b5b89364045a8ab7c` (`producer_tools_version: "5.9"`, `post_generation_source_rewrite: false`).
   - **Pinned runner & verification files in `downstream-handoff.json`:**
     - `syn/strict.xcconfig`: `410b1cfdb8edef8050c57818df28602309bff50634876c7c20713a4fddabb6ab` (`SWIFT_VERSION = 5`, `SWIFT_TREAT_WARNINGS_AS_ERRORS = YES`, `GCC_TREAT_WARNINGS_AS_ERRORS = YES`)
     - `syn/isolation.init.gradle`: `e92d9e1a9f6a4798346cbb5a4fe2c665feccc36cdb7a4007010e10f40b9dc889` *(Diagnostic-only init script: its `task.actions = actions.take(1)` on `*GenerateSPMPackage` prevents legacy repository post-actions from rewriting generated sources/manifests during isolated diagnostics and is **NOT** a supported production recipe.)*
     - `syn/run-native.rb`: `7b775b624e937f151146698ab54cf416225bd5005cd26c2fc419ae06eac75d47`
     - `syn/run-swift.rb`: `8df4fb587d3f59b4a68cc72a09fe23bfd44bdf8c4acab8e083b11e45daa7737a`
     - `syn/actual-result-classification.json`: `fbe3755692aedbebe08d8bc43b85a09ee1da89fbd1ea18c1b27d2a90fc15db77`
     - `package-audit.json`: `456f9d7f929c1bd3beb45357bd3fbfb5a2287751de06145337a722dd6d949159`
     - `verified-result.json`: `025a82079a034e77eccf73293e8ba2b5f4828527eac47fc277a80cf51bd9b4fa`
   - **Resolved 16-JAR worker classpath (`swiftExportClasspath`, never `commonMain`):**

     | # | Worker Classpath Artifact (`tool-dependencies/` + producer component) | SHA256 |
     | :-: | :--- | :--- |
     | 1 | `annotations-23.0.0.jar` | `7b0f19724082cbfcbc66e5abea2b9bc92cf08a1ea11e191933ed43801eb3cd05` |
     | 2 | `caffeine-2.9.3.jar` | `1e0a7bbef1dd791653143f3f05d0e489934bf5481e58a87c9e619cd46b68729b` |
     | 3 | `checker-qual-3.43.0.jar` | `3fbc2e98f05854c3df16df9abaa955b91b15b3ecac33623208ed6424640ef0f6` |
     | 4 | `error_prone_annotations-2.28.0.jar` | `f3fc8a3a0a4020706a373b00e7f57c2512dd26d1f83d28c7d38768f8682b231e` |
     | 5 | `kotlin-build-tools-api-2.4.20.jar` | `47a622dce7231b1916334b69a00bc1094adf6577e6492f9f06b3d9c2450fe459` |
     | 6 | `kotlin-compiler-embeddable-2.4.20.jar` | `cf97161430683fb9af96dc6a7017ffae1fe8737b80d12eee11f2fe3e76f8ded8` |
     | 7 | `kotlin-daemon-embeddable-2.4.20.jar` | `eef72cf8f17e6d98d33e5b7c7e1cab31a1cf5fc34084dd5e563e7b1f3ef53435` |
     | 8 | `kotlin-reflect-1.6.10.jar` | `3277ac102ae17aad10a55abec75ff5696c8d109790396434b496e75087854203` |
     | 9 | `kotlin-script-runtime-2.4.20.jar` | `fc5a19df78be445d84e8352e0d01098a6a8181324aa50c6f7bf850338e81e1b4` |
     | 10 | `kotlin-stdlib-2.4.20.jar` | `2226de463d309d4a5500a481320b3dea515a6981dcae1def531fbc158884e25f` |
     | 11 | `kotlinx-coroutines-core-jvm-1.10.2-intellij-1.jar` | `1e9b4b60644ca8dd5e9687ad096aee0028bd9d3a24afed3e38565b22ca726ee0` |
     | 12 | `kotlinx-coroutines-core-jvm-1.8.0.jar` | `9860906a1937490bf5f3b06d2f0e10ef451e65b95b269f22daf68a3d1f5065c5` |
     | 13 | `kotlinx-serialization-core-jvm-1.7.3.jar` | `f0adde45864144475385cf4aa7e0b7feb27f61fcf9472665ed98cc971b06b1eb` |
     | 14 | `opentelemetry-api-1.41.0.jar` | `9336668f388de68a0a2c3e114154febd29db19a7644f27c4eba548f5de852258` |
     | 15 | `opentelemetry-context-1.41.0.jar` | `5e3432a4464a432abfdab73be7142e516d25a84aa8426fce1192ffb0532faa35` |
     | 16 | `legacy-api-swift-export-embeddable-2.4.20.jar` | `32f6c1ac5f137a204bdcaca192b0e59a9999f72d9c549ea0018b6d5fe77b797e` |

   - **Closed historical diagnostic record (`run-native.rb` + `run-swift.rb` with `-I isolation.init.gradle` — do NOT rerun or borrow scratch caches):**
     - **Historical-only boundary & non-rerun guards:** The retained directory `automation-artifacts/2026-10-21-syn-legacy-error-validation/syn` has **returned its execution grant** (`execution-grant.json` `active: false`) and `run-native.rb` / `run-swift.rb` enforce one-shot guards that abort (`Existing Native invocation` / `Existing invocation`) because `native-invocation.json` and `swift-invocation.json` already exist. **Do NOT rerun returned grants, do NOT overwrite retained artifact directories, and do NOT borrow historical shared scratch caches** (`2026-10-21-syn-real-swift-deaafb4/gradle-home`, `konan-data`, or `2026-10-20-syn-isolated-library-validation/.../native-sdk`) for new builds. During that closed diagnostic, `-I "$ART_ROOT/isolation.init.gradle"` (`run-native.rb:23`) redirected `layout.buildDirectory` to `$ART_ROOT/build`, selected the 16-JAR worker classpath containing the historical patched-JAR proof `32f6c1ac...` (distinct from the published `2.4.20-km.1` clean-build release `e1b871c9...` in `KotlinMania/kotlin-swift-export-tooling`), and stripped legacy repository `GenerateSPMPackage` post-actions (`task.actions = actions.take(1)` — **diagnostic only; never copy task-action stripping into production**). For the supported published-input production recipe (which requires the prepared SDK/helper receipt, `-Xauto-cache-dir=<owned build output>`, immutable published `2.4.20-km.1` worker closure, fresh empty evidence/scratch/build output, nonzero `KotlinBase.h` verification, and legitimate project dependency edges), see [`template/V2_SWIFT.md` §7](file:///Volumes/stuff/Projects/kotlinmania/template/V2_SWIFT.md#clean-generation-and-consumer-recipes).
     ```bash
     #!/usr/bin/env bash
     set -euo pipefail

     # HISTORICAL RECORD ONLY (tasks 28844 / 20241 in 2026-10-21-syn-legacy-error-validation/syn).
     # Execution grant is returned ("active": false) and native-invocation.json / swift-invocation.json
     # are sealed. Do NOT rerun this closed grant or reuse its historical scratch caches.
     ART_ROOT="/Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-syn-legacy-error-validation/syn"
     REPO="/Volumes/stuff/Projects/kotlinmania/syn-kotlin"
     PREPARED_NATIVE_SDK_PATH="/Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-20-syn-isolated-library-validation/runtime-wrapper-corrected/native-sdk"
     GRADLE_HOME_ISOLATED="/Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-syn-real-swift-deaafb4/gradle-home"
     KONAN_DATA_ISOLATED="/Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-syn-real-swift-deaafb4/konan-data"

     if [ -e "$ART_ROOT/native-invocation.json" ] || [ -e "$ART_ROOT/swift-invocation.json" ]; then
       echo "Historical diagnostic grant is closed and sealed; refusing to rerun." >&2
       exit 1
     fi

     # 1. Dependency xcconfig: Swift 5 language mode with Swift & Clang warnings-as-errors
     cat > "$ART_ROOT/strict.xcconfig" <<'EOF'
     SWIFT_VERSION = 5
     SWIFT_TREAT_WARNINGS_AS_ERRORS = YES
     GCC_TREAT_WARNINGS_AS_ERRORS = YES
     EOF

     # 2. Native KLIB, SwiftExport generation, Kotlin bridge compile (-Werror), static link & SPM build
     # Note: -I "$ART_ROOT/isolation.init.gradle" (run-native.rb:23) redirected build/ -> $ART_ROOT/build
     # and selected the 16-JAR worker classpath with 32f6c1ac...
     (
       cd "$REPO"
       KOTLINMANIA_SYN_REPAIR_ROOT="$ART_ROOT" \
       KOTLINMANIA_RUNTIME_CONTRACT="$ART_ROOT" \
       GRADLE_USER_HOME="$GRADLE_HOME_ISOLATED" \
       KONAN_DATA_DIR="$KONAN_DATA_ISOLATED" \
       TMPDIR="$ART_ROOT/tmp/" \
       JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$ART_ROOT/tmp -Duser.home=$ART_ROOT/process-home -Didea.system.path=$ART_ROOT/idea-system -Didea.config.path=$ART_ROOT/idea-config" \
       CLANG_MODULE_CACHE_PATH="$ART_ROOT/clang-cache" \
       SWIFT_MODULECACHE_PATH="$ART_ROOT/module-cache" \
       SWIFTPM_MODULECACHE_OVERRIDE="$ART_ROOT/module-cache" \
       BUILT_PRODUCTS_DIR="$ART_ROOT/build/swift-test" \
       TARGET_BUILD_DIR="$ART_ROOT/build/swift-test" \
       SDK_NAME="macosx" CONFIGURATION="Debug" ARCHS="arm64" \
       FRAMEWORKS_FOLDER_PATH="Frameworks" \
       MACOSX_DEPLOYMENT_TARGET="15.0" \
       DEPLOYMENT_TARGET_SETTING_NAME="MACOSX_DEPLOYMENT_TARGET" \
       XCODE_XCCONFIG_FILE="$ART_ROOT/strict.xcconfig" \
       CI="true" \
       ./gradlew embedSwiftExportForXcode \
         --offline --no-daemon --max-workers=2 \
         --no-configuration-cache --no-build-cache --console=plain \
         --project-cache-dir "$ART_ROOT/project-cache" \
         -I "$ART_ROOT/isolation.init.gradle" \
         -Pkotlin.native.home="$PREPARED_NATIVE_SDK_PATH" \
         -Pkotlin.project.persistent.dir="$ART_ROOT/kotlin-project" \
         -Pkotlin.compiler.execution.strategy=in-process \
         "-Dorg.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g -Djava.io.tmpdir=$ART_ROOT/tmp" \
         2>&1 | tee "$ART_ROOT/native.log"
     )

     # 3. Verify nonzero emitted KotlinBase.h headers (>= 2) and exact SHA256 on every file
     HEADER_COUNT="$(find "$ART_ROOT/build" -type f -name 'KotlinBase.h' | wc -l | tr -d ' ')"
     test "$HEADER_COUNT" -ge 2
     find "$ART_ROOT/build" -type f -name 'KotlinBase.h' -exec shasum -a 256 {} + | tee "$ART_ROOT/header-hashes.txt"
     awk 'END { if (NR < 2) exit 1 } $1 != "99186d1dd029a8ba5b66dae390a3aec0d6d0e5293eff641d85daab5e212723c3" { exit 1 }' "$ART_ROOT/header-hashes.txt"

     # 4. Real Swift 6 consumer test execution with warnings-as-errors and macOS 15.0 target
     (
       cd "$REPO/src/swift/swift-test-harness"
       KOTLINMANIA_SWIFT_EXPORT_BUILD="$ART_ROOT/build" \
       TMPDIR="$ART_ROOT/tmp/" \
       CLANG_MODULE_CACHE_PATH="$ART_ROOT/clang-cache" \
       SWIFT_MODULECACHE_PATH="$ART_ROOT/module-cache" \
       SWIFTPM_MODULECACHE_OVERRIDE="$ART_ROOT/module-cache" \
       /usr/bin/xcrun swift test -v \
         --package-path "$REPO/src/swift/swift-test-harness" \
         --scratch-path "$ART_ROOT/swift-test-build" \
         --cache-path "$ART_ROOT/swiftpm/cache" \
         --config-path "$ART_ROOT/swiftpm/config" \
         --security-path "$ART_ROOT/swiftpm/security" \
         --jobs 2 \
         -Xswiftc -warnings-as-errors \
         -Xswiftc -target -Xswiftc arm64-apple-macosx15.0 \
         2>&1 | tee "$ART_ROOT/swift.log"
     )

     # 5. Record raw log manifest
     shasum -a 256 "$ART_ROOT/strict.xcconfig" "$ART_ROOT/header-hashes.txt" "$ART_ROOT/native.log" "$ART_ROOT/swift.log" > "$ART_ROOT/raw-log-manifest.sha256"
     ```
5. **Independently verified `v8-kotlin` PR #121 / PR #122 (`main` @ `4be04116172bd955580511da3ab264eb69797c00`) project-specific workarounds and non-`-Werror` qualification (Antigravity session `035cfd35-895c-497a-927a-3af4da50a6b1`, canonical write yield confirmed):**
   - **Repository & commits:** [`https://github.com/KotlinMania/v8-kotlin`](https://github.com/KotlinMania/v8-kotlin), source commit `4be04116172bd955580511da3ab264eb69797c00` ([PR #121](https://github.com/KotlinMania/v8-kotlin/pull/121), merged from `da6fa39` / `7997b92`), followed by merged doc-sync [PR #122](https://github.com/KotlinMania/v8-kotlin/pull/122) (mirroring template commit `51d31a8`, [### 15.](#15-backticked-kotlin-reserved-keywords-on-public-declarations-emit-unbackticked-calls-in-the-swift-export-kotlin-bridge) and [### 16.](#16-public-mutablemap--mutableset--mutablelist-properties-trigger-global-ktypewkotlincollectionsmutableset-already-exists-during-static-linking) above).
   - **Retained failure logs, upstream Rust visibility, and public-spelling verification:**
     1. **Step `1878` & Step `1874` ([`steps/1874/output.txt`](file:///Users/sydney/.gemini/antigravity/brain/035cfd35-895c-497a-927a-3af4da50a6b1/.system_generated/steps/1874/output.txt), `SHA256: e197fb6d905f6d523b6d18145c57fa9ad519f200d5566aa04c61c565c0f8d22c`):** Generated `V8.kt:12310-12315` emitted `val _result = run { __self.null() }`, failing `compileSwiftExportMainKotlinMacosArm64` at `V8.kt:12313:19` (`Cannot infer type for type parameter 'R'`) and `V8.kt:12313:32` (`Reference has a nullable type 'Nothing?'` / `No receiver can be passed to this function or property`). `Isolate.kt:83,85` (`` internal fun `null`() `` + `public fun nullValue()`) and `Function.kt:127,129, 179,181` (`` internal fun `this`() `` + `public fun thisValue()`) preserve the public operations via a **qualified project-specific public spelling migration** (`nullValue()` / `thisValue()`); this is a source workaround for unescaped keyword emission, **not** a generator fix or blanket API-hiding license.
     2. **Step `1904` ([`steps/1904/output.txt`](file:///Users/sydney/.gemini/antigravity/brain/035cfd35-895c-497a-927a-3af4da50a6b1/.system_generated/steps/1904/output.txt), `SHA256: 4eba8703b0e74c84a6b899d252aa2a2d6e05b3fbb722c9b0ed1334bf8767ceaf`):** `linkSwiftExportBinaryDebugStaticMacosArm64` failed with `Global 'ktypew:kotlin.collections.MutableSet' already exists`, caused by `KotlinStdlib.kt:20` (`@file:BindClassToObjCName(kotlin.collections.MutableSet::class, ...)`) plus reverse `MutableMap.entries`/`keys` bridges at `KotlinStdlib.kt:341, 351`. `src/common/commonMain/kotlin/io/github/kotlinmania/v8/Isolate.kt:97` (`internal val slots: MutableMap<String, Any> = mutableMapOf()`) on `class IsolateAnnex internal constructor(...)` corresponds to upstream Rust `tmp/v8/src/isolate.rs:1899` (`pub(crate) struct IsolateAnnex`) and its private field `slots: HashMap<TypeId, RawSlot, BuildTypeIdHasher>` at line `1901`. Making `slots` `internal` is faithful crate-private visibility alignment, **not** hiding a public upstream API.
     3. The Syn producer owner received the exact regression evidence and recorded both the `ktypew:kotlin.collections.MutableSet` duplicate-global symbol and backticked-keyword escaping into [`kotlin-swift-export-tooling/verification/UPSTREAM_ISSUES.md`](file:///Volumes/stuff/Projects/kotlinmania/kotlin-swift-export-tooling/verification/UPSTREAM_ISSUES.md) (local documentation only; no external upstream bug report was filed).
   - **Effective compiler/mode/warning audit (`description.json` SHA256 `8928e61883026d1a103582e9c599ba389f804abd5a1f077bea29b814da4c0d92`):**
     - No verbose `swift-frontend` command lines were captured in Step `1942`. Retained `.build/arm64-apple-macosx/debug/description.json` under Apple Swift `6.3.3` establishes that generated dependency modules compiled in **Swift 5** language mode, authored `SwiftTestHarnessTests` compiled in **Swift 6** language mode, the generated `SwiftTestHarnessPackageTests` runner compiled with no explicit language-version flag, and **ALL Swift targets compiled WITHOUT `-warnings-as-errors`**.
     - `v8-kotlin/build.gradle.kts:370-374` disables `allWarningsAsErrors` on `compileSwiftExportMain*` tasks (`generated Kotlin Werror = false`); line `1389` rewrites `swift-tools-version: 6.0` $\to$ `5.9`; and line `1474` invokes plain `swift test` without `-Xswiftc -warnings-as-errors`.
   - **Retained test execution counts (`08:52–08:53Z` XML reports + Step `1942` Swift output):**
     - **Swift test count ([`steps/1942/output.txt`](file:///Users/sydney/.gemini/antigravity/brain/035cfd35-895c-497a-927a-3af4da50a6b1/.system_generated/steps/1942/output.txt), `SHA256: ee9995f8033abadf5fe5ee135617c0207ed80ec7370a6abab75a114871240228`):** **1 Swift Testing test (`testSwiftModuleLoads`) executed and passed** (`Test run with 1 test in 1 suite passed`, exercising actual `PropertyAttribute`, `PropertyFilter`, `HandlerFlags`, `MicrotasksPolicy`, and `version` operations), despite XCTest's separate `Executed 0 tests` summary.
     - **KMP XML reports:** `jvmTest` = **`46` passed** (`0` failures/errors/skips), and **10 additional host-runnable tasks** = **`45` passed each** (`0` failures/errors/skips), totaling **11 runnable environments** (`496` XML test executions). Reporting "`17` target builds / `275` tasks" establishes only compilation/link task coverage across 17 configured targets, **NOT** `17` executed target runtimes and **NOT** strict Swift `-warnings-as-errors` acceptance.
6. **Independently audited `rama-tls-rustls-kotlin` (`main` @ `97e74acfc8ae5c718c5f4c960d6609596778e2b0` / PR #116) non-`-Werror` mixed-mode behavioral pass (`audit.json` SHA256 `de47cb532482613ec859ae3c400a68e0a028e5920d95c67b9330957d0b01a042`):**
   - **Repository & clean head:** `/Volumes/stuff/Projects/kotlinmania/rama-tls-rustls-kotlin` at clean `main` commit `97e74acfc8ae5c718c5f4c960d6609596778e2b0` (merging `b6b4adb` / PR #116; `git status` clean). Read-only receipt: `automation-artifacts/2026-10-21-rama-tls-rustls-readonly-audit/audit.json` (`SHA256: de47cb532482613ec859ae3c400a68e0a028e5920d95c67b9330957d0b01a042`).
   - **Actual Gradle daemon & SwiftPM manifest evidence (`daemon-15916.out.log` SHA256 `d80b660f0eb3f881db3ea309580c4e8e3b5b42b63496d7bd6907e538be308a3c`; `.build/arm64-apple-macosx/debug/description.json` SHA256 `7e52d4cf2a53c9a83e526757c585ce341e511dd4a286466b8eb2be9786ef3849`):**
     - **Dependency frontends (lines `1280–1440`):** **16 frontend invocations across 8 generated dependency modules** (`ExportedKotlinPackages`, `IoGithubKotlinmaniaRamaCoreKotlin`, `IoGithubKotlinmaniaRamaNetKotlin`, `KotlinCoroutineSupport`, `KotlinRuntimeSupport`, `KotlinStdlib`, `OrgJetbrainsKotlinxKotlinxSerializationCore`, `RamaTlsRustls`) compiled in explicit **Swift 5** language mode (`-swift-version 5`) with **NO `-warnings-as-errors`** (`Werror: false`).
     - **Authored consumer test frontends (lines `1579–1580`):** `SwiftTestHarnessTests` compiled in explicit **Swift 6** language mode (`-swift-version 6`) with **NO `-warnings-as-errors`** (`Werror: false`).
     - **Generated test-runner frontends (lines `1584–1585`):** `SwiftTestHarnessPackageTests` compiled with **no explicit language-version flag** and **NO `-warnings-as-errors`** (`Werror: false`).
     - **Executed Swift behavioral tests (lines `1608–1612, 1614`):** **5/5 named Swift tests executed and passed** (`testServerName()`, `testNoServerCertVerifierCompanion()`, `testRamaTlsRustlsCrateMarker()`, `testNoServerCertVerifier()`, `testUnixTime()` $\to$ `Test run with 5 tests in 1 suite passed`). This is a **real 5/5 Swift behavioral pass**, **NOT** an all-module Swift 6 or strict `-warnings-as-errors` pass.
   - **Build script, header, and toolchain provenance qualifications:**
     - `rama-tls-rustls-kotlin/build.gradle.kts:338` disables generated Kotlin bridge `-Werror` (`compileSwiftExportMain*` sets `allWarningsAsErrors = false`); the smoke test runs `swift test -v` without `XCODE_XCCONFIG_FILE` or `-Xswiftc -warnings-as-errors`. Although `verification/enforce_swift6.py` and `verification/strict-swift.xcconfig` exist in the repository tree, neither is wired into the build.
     - Generated `build/SPMPackage/macosArm64/Debug/Package.swift` is `// swift-tools-version: 5.9` (`SHA256: 626933636eefe5dfc7dc8baec96e0b1a07e46b939533f1c0f8bd50319a5b8086`).
     - Retained `KotlinBase.h` in the build output matches the **stock** Kotlin 2.4.20 header (`SHA256: 14390b9a40bcd8f924909692249ca95468cd3de36157be762371b1673dab86f3`), **not** the repaired ownership header (`99186d1d...`). Stock `swift-export-embeddable-2.4.20.jar` (`24c6159bcf0087ecf86bfc9bad01a975e192abcf75005b248292d85a139c2055`) is present in the local Gradle cache as a candidate only; the exact consumed worker classpath was not pinned by that build.
   - **Retained KMP XML test reports (`08:07–08:08Z`):**
     - **19 passing test cases each across 11 runnable environments** (`jvmTest`, `testAndroidHostTest`, `macosArm64Test`, `iosSimulatorArm64Test`, `tvosSimulatorArm64Test`, `watchosSimulatorArm64Test`, `jsNodeTest`, `jsBrowserTest`, `wasmJsNodeTest`, `wasmJsBrowserTest`, `wasmWasiNodeTest`), with `0` failures, `0` errors, and `0` skips (`209` total XML test executions). These are retained local XML reports without a frozen source-input pin manifest, and no 17-runtime or host-foreign execution is claimed.
7. **Independently audited `tokio-util-kotlin` ([PR #128](https://github.com/KotlinMania/tokio-util-kotlin/pull/128), `main` @ `ea40187bf6268a56a8f2d193cf3e1f48d2e52d67`) pre-Xcode `XCODE_XCCONFIG_FILE` guard failure (`summary.json` SHA256 `689a9f98d7b44560a55ef1c006a5dad729bd60ebab2003d67618289c84d81e4f`):**
   - **Read-only receipt & ownership:** [`automation-artifacts/2026-10-21-tokio-util-ci-readonly/summary.json`](file:///Volumes/stuff/Projects/kotlinmania/automation-artifacts/2026-10-21-tokio-util-ci-readonly/summary.json) (`SHA256: 689a9f98d7b44560a55ef1c006a5dad729bd60ebab2003d67618289c84d81e4f`), auditing merged [PR #128](https://github.com/KotlinMania/tokio-util-kotlin/pull/128) at `main` / CI head `ea40187bf6268a56a8f2d193cf3e1f48d2e52d67` (implementation head `f4d00e02ad201c5850d3ff5dca9e0147f348c7b8`; existing owner session `47ba7261` notified on Kanban card `t_61edec8d`).
   - **CI Run `39572583245` / Swift Job `119130174153` (`lines 387–389`):**
     - Failed the repository's Gradle `doFirst` guard (`Required XCODE_XCCONFIG_FILE missing`) on `macosArm64DebugBuildSPMPackage` **before `xcodebuild` or `swift-frontend` ever launched**; **`0` Swift test cases executed**.
     - Although the workflow prints `Swift 6.3.3 / Xcode 26.6` and the generated `Package.swift` (`SHA256: db09b82daca448984f659c6c09cfbcd9c3f9fb363b3736b02093a7a5c4628807`) contains rewritten `swift-tools-version: 6.0` / `swiftLanguageModes: [.v6]` declarations, that file is **manifest generation / post-rewrite evidence only** — **not** a `KotlinRuntimeSupport` Swift 6 compilation failure (because Xcode compilation never ran).
   - **Independently counted runtime test executions (`192` total) & workflow gaps:**
     - `11` CI XML environments $\times$ `16` passing cases each = **`176` XML cases** (`0` failures/errors/skips) + **`16` Android host log cases** = **`192` verified runtime executions**.
     - `.github/workflows/ci.yml` invokes `11` of `12` platform workflows and **omits `jvm.yml`**; Android device APK is assembled only; `androidNativeArm64`/`androidNativeX64` are compile-only; no `linuxArm64` runtime execution exists; and Swift executed `0` tests. Retained local iOS/tvOS/watchOS simulator XML reports each record `16` cases across `7` suites (a local screenshot citing "11 classes / 32 assertions" reflects a different metric and is not independently verified; local browser XML dates from Oct 14).

For the standalone, task-oriented technical reference covering all sections with stable anchors and linked table of contents, see [`template/V2_SWIFT.md`](file:///Volumes/stuff/Projects/kotlinmania/template/V2_SWIFT.md).
