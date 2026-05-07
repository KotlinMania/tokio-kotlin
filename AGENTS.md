# Agent guide — tokio-kotlin

Quick reference for translators. Long-form discipline is in [CLAUDE.md](CLAUDE.md).

## What this repo is

Clean-room Kotlin Multiplatform port of the upstream Rust crate [`tokio`](https://crates.io/crates/tokio). Every Kotlin file is a faithful translation of one Rust file. No JVM-only deps, no Java imports, no shortcuts via established Java/Kotlin libraries.

## Workflow per file

1. Clone upstream into `tmp/tokio/` (gitignored) if not already present.
2. Pick the next file to port — leaves of the dep tree first.
3. Read the whole `.rs` before typing.
4. Create the matching `.kt`. First line: `// port-lint: source <path-relative-to-tmp/tokio/>`. Second line: `package io.github.kotlinmania.tokio.<subpkg>`.
5. Translate top-to-bottom in upstream order. Translate every doc comment, every inline `//`, every `///`. Rewrite Rust syntax inside docs to Kotlin equivalents.
6. Compile errors mid-port are expected. Don't paper over with stubs — port the missing dep.
7. Commit per file.

## Rust → Kotlin idiom mapping

| Rust | Kotlin |
|---|---|
| `Result<T, E>` | `Result<T>` (or sealed class when `E` carries data) |
| `Option<T>` | `T?` |
| `Vec<T>` | `MutableList<T>` / `List<T>` |
| `HashMap<K, V>` | `MutableMap<K, V>` / `Map<K, V>` |
| `HashSet<T>` | `MutableSet<T>` / `Set<T>` |
| `BTreeMap<K, V>` | `TreeMap<K, V>` from `btree-kotlin`, or sorted map |
| `Box<T>` | bare `T` (delete the wrapper) |
| `Rc<T>`, `Arc<T>` | bare `T` reference unless cross-coroutine sharing matters |
| `Cell<T>`, `RefCell<T>` | `var T`; `kotlin.concurrent.atomics.AtomicReference` if shared |
| `PhantomData<T>` | drop the field; declare `out T` / `in T` |
| `mem::transmute` | `as T` (verified) — no shim function |
| `dyn Trait` | plain interface reference |
| trait | `interface` |
| trait default method `where T: Bound` | extension function carrying its own bound |
| trait default method tied to class generic `where K: Ord` | `Comparator<in K>` field + dispatch helper |
| struct with fields | `data class` |
| enum with payload variants | `sealed class` / `sealed interface` |
| `proc-macro` derive (`#[derive(Serialize, ...)]`) | `@Serializable` / explicit codegen — never elide |
| `pub type X = Y` | `typealias X = Y` (only if Rust actually has it) |
| `impl Iterator for X` | class implementing `kotlin.collections.Iterator<T>` |

## Don't

- `@Suppress`. Ever. Fix the cause.
- Empty class shells where the Rust struct has fields/methods.
- `TODO()` or `error("not implemented")` or placeholder bodies.
- Re-export typealiases at root packages — they create import ambiguity webs.
- Repo-wide `find … -exec` or global `sed` / `perl`. Edits are task-scoped, not pattern-scoped.
- Bulk-edit comments. Comment changes are intentional and reviewed in the diff.
- Subagent-driven translation. `.kt` edits happen in the main loop only.

## Verification

Build gate: `./gradlew test` on every shipped target.

```bash
./gradlew test                    # all targets
./gradlew macosArm64Test
./gradlew linuxX64Test
./gradlew jsNodeTest
./gradlew wasmJsNodeTest
```

Provenance gate: `ast_distance --deep tmp/tokio/src rust src/commonMain/kotlin/io/github/kotlinmania/tokio kotlin`.

## Scope discipline

- One Rust file → one Kotlin file. No exceptions.
- Test files port too. Every `#[test]` becomes a `@Test`.
