# Claude Code Project Instructions — tokio-kotlin

## Project Overview

This is **tokio-kotlin**, a clean-room Kotlin Multiplatform port of the upstream Rust crate [`tokio`](https://crates.io/crates/tokio).

The upstream Rust source is the read-only translation oracle. When porting begins, clone it into `tmp/tokio/` (gitignored). **Never edit `tmp/`.** If upstream looks wrong, the bug is in the port or in your understanding of Rust, not in `tmp/`.

## Translator's mindset

This is a translation project, not a software-engineering project. While porting a file, you are
the Kotlin author of the same document a Rust author wrote. Architecture, optimization, design
critique, drift measurement — all later. While translating, the only job is the translation.

The discipline:

1. **Read the whole upstream file before you type.** A line-by-line port composes only when you
   know how the file ends. If the file is too long to read in one sitting, split your turn into
   "read the file" and "write the file" — never start typing on a file you've only half-read.

2. **One Rust file → one Kotlin file. Always.** No splitting one `.rs` across several `.kt`. No
   merging several `.rs` into one `.kt`. The 1:1 mapping is the contract; everything downstream
   (ast_distance, port-lint headers, code review) assumes it. If a `.rs` is genuinely too big for
   one Kotlin file, that's a sign you're in `mod.rs`-equivalent territory and the upstream itself
   is a re-export — verify, don't split.

3. **Translate top to bottom in upstream order.** Preserve the declaration order. Don't reorder
   for "logical flow" — the upstream's order *is* the logical flow.

4. **Comments are content.** License header, module-level doc, every `///` block, every inline
   `//` note, every upstream `// TODO`/`// FIXME` — all translate. Rust syntax inside doc comments
   gets rewritten to Kotlin equivalents (`Vec<T>` → `List<T>`, `Self::foo()` → `foo()`, lifetimes
   dropped, `cfg(test)` and `#[derive(...)]` lifted into prose).

5. **When a Rust idiom has no Kotlin analog, apply the mapping rule and move on.** `Box<T>`,
   `Arc<T>`, `Cell<T>`, `RefCell<T>`, `Rc<T>`, lifetimes, `PhantomData`, `mem::forget`,
   `drop_in_place`, `Pin`, `MaybeUninit`, `dyn Trait` — all collapse per the mapping table.
   An upstream Rust crate with no KMP equivalent becomes a *separate Kotlin port*, not a
   `// TODO` placeholder.

6. **Don't measure mid-port.** ast_distance, FnSim, similarity reports — useful *after* a file is
   done, useless *during*.

7. **Don't optimize the translation.** "This Kotlin shape would be simpler" is the wrong thought.
   The upstream shape is the spec.

8. **Don't re-architect mid-port.**

9. **Compile errors during translation are normal and expected.** Climb the dep tree bottom-up.

10. **Bottom-up always.** Port dependencies before consumers.

11. **Hard files are not skippable.** Skipping leaves a `// TODO`-shaped hole that grows every
    time another consumer needs it.

12. **Warnings are real, but `@Suppress` is never the answer.** `UNUSED_PARAMETER` on a callback
    helper means the function shape doesn't fit Kotlin — restructure the signature, don't suppress.
    `UNCHECKED_CAST` means the type system is missing an invariant — encode it.

13. **Stop at file boundaries, not function boundaries.** After every completed file, exhale,
    commit, move on.

14. **Doc-port discipline applies even when the upstream doc is awkward.** If the upstream
    author wrote a tortured English sentence, translate the tortured sentence. Don't smooth it.

15. **The cheat detector is your friend.** If `ast_distance` forces your file's score to 0
    because you left snake_case identifiers or `pub` keywords in Kotlin comments, take it as a
    literal instruction: rewrite those comments to be Kotlin-native.

## Port-lint headers (REQUIRED)

Every Kotlin file MUST start with:

```kotlin
// port-lint: source <path-relative-to-tmp/tokio>
package io.github.kotlinmania.tokio
```

Example:

```kotlin
// port-lint: source src/lib.rs
package io.github.kotlinmania.tokio
```

This is how `ast_distance` tracks provenance. Never remove or alter unless the file is being re-targeted to a different Rust source.

For files that have no single Rust counterpart (re-homed from a `mod.rs`, or pure Kotlin glue), use `// port-lint: ignore` and a one-line prose note explaining what it does.

## Build

```bash
./gradlew build
./gradlew test
```

Targets: macOS arm64/x64, Linux x64, mingw-x64, iOS arm64/x64/simulator-arm64, JS, Wasm-JS, Android.

There is no JVM-only target. `./gradlew jvmTest` is **not** valid.

## Forbidden

- `import kotlin.jvm.*` (`JvmName`, `JvmStatic`, `JvmField`, `JvmOverloads`)
- `import java.*`
- `import javax.*`
- `@Suppress(...)` for any reason — fix the underlying issue
- Empty stub classes / `TODO()` / `error("not implemented")` / placeholder code
- Re-export typealias files at root packages
- Subagent-driven `.kt` edits — translation happens in the main loop only

## Naming

| Kind | Form |
|---|---|
| Functions, parameters, locals | `camelCase` |
| Classes, data classes, sealed types | `PascalCase` |
| Interfaces | `PascalCase`, no `I` prefix |
| `const val`, `enum` entries, top-level constants | `SCREAMING_SNAKE_CASE` permitted |
| Type parameters | `T`, `K`, `V` |
| Packages | all lowercase, no underscores, no camelCase |

## Approved dependencies

- `kotlinx-coroutines-core`
- `kotlinx-serialization-core`, `kotlinx-serialization-json`
- `kotlinx-collections-immutable`
- `kotlinx-datetime`
- `com.ionspin.kotlin:bignum` (only if needed)
- `io.github.kotlinmania:*-kotlin` siblings (only when porting a transitive Rust dep)

Add a new dependency only when the stdlib + the above cannot reproduce the required behavior, and only after confirming it publishes artifacts for **every** target above.

## Commit messages

- No AI branding or attribution.
- Clear, descriptive, focused on what changed and why.
- No `Co-Authored-By` lines, no robot emoji, no "Generated with" footers.
