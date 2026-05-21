# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 13/372 (3.5%)
- **Function parity:** 110/3335 matched (target 186) — 3.3%
- **Class/type parity:** 14/662 matched (target 26) — 2.1%
- **Combined symbol parity:** 124/3997 matched (target 212) — 3.1%
- **Average inline-code cosine:** 0.58 (function body across 13 matched files)
- **Average documentation cosine:** 0.68 (doc text across 13 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 8 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

### 1. metrics.io
- **Similarity:** 0.81 (needs 4% improvement)
- **Dependencies:** 85
- **Priority Score:** 85000400.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_
- **Action:** Minor refinements needed

### 2. std.unsafe_cell
- **Similarity:** 0.44 (needs 41% improvement)
- **Dependencies:** 25
- **Priority Score:** 25000406.0
- **Functions:** 3/3 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_
- **Action:** Deep review - likely missing major functionality

### 3. std.atomic_usize
- **Similarity:** 0.45 (needs 40% improvement)
- **Dependencies:** 17
- **Priority Score:** 17000806.0
- **Functions:** 6/6 matched (target 18)
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_
- **Action:** Deep review - likely missing major functionality

### 4. io.async_write
- **Similarity:** 0.26 (needs 59% improvement)
- **Dependencies:** 14
- **Priority Score:** 14000607.0
- **Functions:** 5/5 matched (target 17)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 4)
- **Missing types:** _none_
- **Action:** Deep review - likely missing major functionality

### 5. util.mem
- **Similarity:** 0.75 (needs 10% improvement)
- **Dependencies:** 13
- **Priority Score:** 13001703.0
- **Functions:** 15/15 matched (target 26)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_
- **Action:** Review and complete missing sections

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

1. **macros.pin** (86 deps)
   - Path: `macros/pin.rs`
   - Essential for 86 other files

2. **runtime.context** (14 deps)
   - Path: `runtime/context.rs`
   - Essential for 14 other files

3. **sync.mutex** (14 deps)
   - Path: `sync/mutex.rs`
   - Essential for 14 other files

4. **metrics.scheduler** (11 deps)
   - Path: `runtime/metrics/scheduler.rs`
   - Essential for 11 other files

5. **runtime.handle** (11 deps)
   - Path: `runtime/handle.rs`
   - Essential for 11 other files

6. **sync.notify** (10 deps)
   - Path: `sync/notify.rs`
   - Essential for 10 other files

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. metrics.io

- **Target:** `metrics.Io`
- **Similarity:** 0.81
- **Dependents:** 85
- **Priority Score:** 85000400.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 2. std.unsafe_cell

- **Target:** `std.UnsafeCell`
- **Similarity:** 0.44
- **Dependents:** 25
- **Priority Score:** 25000406.0
- **Functions:** 3/3 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 3. std.atomic_usize

- **Target:** `std.AtomicUsize`
- **Similarity:** 0.45
- **Dependents:** 17
- **Priority Score:** 17000806.0
- **Functions:** 6/6 matched (target 18)
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_
- **Lint issues:** 1

### 4. io.async_write

- **Target:** `io.AsyncWrite`
- **Similarity:** 0.26
- **Dependents:** 14
- **Priority Score:** 14000607.0
- **Functions:** 5/5 matched (target 17)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 4)
- **Missing types:** _none_

### 5. util.mem

- **Target:** `util.Mem`
- **Similarity:** 0.75
- **Dependents:** 13
- **Priority Score:** 13001703.0
- **Functions:** 15/15 matched (target 26)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_

### 6. util.error

- **Target:** `util.Error`
- **Similarity:** 1.00
- **Dependents:** 12
- **Priority Score:** 12000000.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 7. io.ready

- **Target:** `io.Ready`
- **Similarity:** 0.77
- **Dependents:** 7
- **Priority Score:** 7012102.5
- **Functions:** 19/19 matched (target 29)
- **Missing functions:** _none_
- **Types:** 1/2 matched
- **Missing types:** `Output`

### 8. io.interest

- **Target:** `io.Interest`
- **Similarity:** 0.50
- **Dependents:** 7
- **Priority Score:** 7011605.0
- **Functions:** 14/14 matched (target 21)
- **Missing functions:** _none_
- **Types:** 1/2 matched
- **Missing types:** `Output`

### 9. io.async_read

- **Target:** `io.AsyncRead`
- **Similarity:** 0.40
- **Dependents:** 5
- **Priority Score:** 5000206.0
- **Functions:** 1/1 matched (target 4)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 3)
- **Missing types:** _none_

### 10. io.read_buf

- **Target:** `io.ReadBuf`
- **Similarity:** 0.53
- **Dependents:** 4
- **Priority Score:** 4002604.8
- **Functions:** 25/25 matched (target 35)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_

### 11. util.memchr

- **Target:** `util.Memchr`
- **Similarity:** 0.38
- **Dependents:** 1
- **Priority Score:** 1000406.2
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 3/3 matched

### 12. io.split

- **Target:** `io.Split`
- **Similarity:** 0.72
- **Dependents:** 0
- **Priority Score:** 1102.8
- **Functions:** 10/10 matched (target 15)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 3)
- **Missing types:** _none_

### 13. util.metric_atomics

- **Target:** `util.MetricAtomics`
- **Similarity:** 0.58
- **Dependents:** 0
- **Priority Score:** 704.2
- **Functions:** 5/5 matched (target 9)
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

## Next Commands

```bash
# Initialize task queue for systematic porting
cd tools/ast_distance
./ast_distance --init-tasks ../../tmp/tokio/src rust ../../src/commonMain/kotlin/io/github/kotlinmania/tokio kotlin tasks.json ../../AGENTS.md

# Get next high-priority task
./ast_distance --assign tasks.json <agent-id>
```
## Reexport / Wiring Modules

These files match `reexport_modules` patterns in `.ast_distance_config.json`. They are filtered out of
normal priority and missing-file ladders because they are wiring
modules, not direct logic ports. Consult them for call-site routing;
do not treat them as the next implementation target by default.

### Missing

| Source | Expected target | Deps | Source path | Expected path |
|--------|-----------------|------|-------------|---------------|
| `doc.mod` | `doc.Mod` | 0 | `doc/mod.rs` | `doc/Mod.kt` |
| `fs.mod` | `fs.Mod` | 0 | `fs/mod.rs` | `fs/Mod.kt` |
| `future.mod` | `future.Mod` | 0 | `future/mod.rs` | `future/Mod.kt` |
| `io.mod` | `io.Mod` | 0 | `io/mod.rs` | `io/Mod.kt` |
| `uring.mod` | `io.uring.Mod` | 0 | `io/uring/mod.rs` | `io/uring/Mod.kt` |
| `io.util.mod` | `io.util.Mod` | 0 | `io/util/mod.rs` | `io/util/Mod.kt` |
| `lib` | `Lib` | 0 | `lib.rs` | `Lib.kt` |
| `loom.mod` | `loom.Mod` | 0 | `loom/mod.rs` | `loom/Mod.kt` |
| `std.mod` | `loom.std.Mod` | 0 | `loom/std/mod.rs` | `loom/std/Mod.kt` |
| `macros.mod` | `macros.Mod` | 0 | `macros/mod.rs` | `macros/Mod.kt` |
| `net.mod` | `net.Mod` | 0 | `net/mod.rs` | `net/Mod.kt` |
| `tcp.mod` | `net.tcp.Mod` | 0 | `net/tcp/mod.rs` | `net/tcp/Mod.kt` |
| `datagram.mod` | `net.unix.datagram.Mod` | 0 | `net/unix/datagram/mod.rs` | `net/unix/datagram/Mod.kt` |
| `unix.mod` | `net.unix.Mod` | 0 | `net/unix/mod.rs` | `net/unix/Mod.kt` |
| `windows.mod` | `net.windows.Mod` | 0 | `net/windows/mod.rs` | `net/windows/Mod.kt` |
| `process.mod` | `process.Mod` | 0 | `process/mod.rs` | `process/Mod.kt` |
| `process.unix.mod` | `process.unix.Mod` | 0 | `process/unix/mod.rs` | `process/unix/Mod.kt` |
| `blocking.mod` | `runtime.blocking.Mod` | 0 | `runtime/blocking/mod.rs` | `runtime/blocking/Mod.kt` |
| `runtime.io.mod` | `runtime.io.Mod` | 0 | `runtime/io/mod.rs` | `runtime/io/Mod.kt` |
| `local_runtime.mod` | `runtime.localruntime.Mod` | 0 | `runtime/local_runtime/mod.rs` | `runtime/localruntime/Mod.kt` |
| `metrics.mod` | `runtime.metrics.Mod` | 0 | `runtime/metrics/mod.rs` | `runtime/metrics/Mod.kt` |
| `runtime.mod` | `runtime.Mod` | 0 | `runtime/mod.rs` | `runtime/Mod.kt` |
| `current_thread.mod` | `runtime.scheduler.currentthread.Mod` | 0 | `runtime/scheduler/current_thread/mod.rs` | `runtime/scheduler/currentthread/Mod.kt` |
| `scheduler.mod` | `runtime.scheduler.Mod` | 0 | `runtime/scheduler/mod.rs` | `runtime/scheduler/Mod.kt` |
| `multi_thread.mod` | `runtime.scheduler.multithread.Mod` | 0 | `runtime/scheduler/multi_thread/mod.rs` | `runtime/scheduler/multithread/Mod.kt` |
| `runtime.scheduler.util.mod` | `runtime.scheduler.util.Mod` | 0 | `runtime/scheduler/util/mod.rs` | `runtime/scheduler/util/Mod.kt` |
| `runtime.signal.mod` | `runtime.signal.Mod` | 0 | `runtime/signal/mod.rs` | `runtime/signal/Mod.kt` |
| `runtime.task.mod` | `runtime.task.Mod` | 0 | `runtime/task/mod.rs` | `runtime/task/Mod.kt` |
| `trace.mod` | `runtime.task.trace.Mod` | 0 | `runtime/task/trace/mod.rs` | `runtime/task/trace/Mod.kt` |
| `runtime.tests.mod` | `runtime.tests.Mod` | 0 | `runtime/tests/mod.rs` | `runtime/tests/Mod.kt` |
| `runtime.time.mod` | `runtime.time.Mod` | 0 | `runtime/time/mod.rs` | `runtime/time/Mod.kt` |
| `runtime.time.tests.mod` | `runtime.time.tests.Mod` | 0 | `runtime/time/tests/mod.rs` | `runtime/time/tests/Mod.kt` |
| `wheel.mod` | `runtime.time.wheel.Mod` | 0 | `runtime/time/wheel/mod.rs` | `runtime/time/wheel/Mod.kt` |
| `time_alt.mod` | `runtime.timealt.Mod` | 0 | `runtime/time_alt/mod.rs` | `runtime/timealt/Mod.kt` |
| `runtime.time_alt.wheel.mod` | `runtime.timealt.wheel.Mod` | 0 | `runtime/time_alt/wheel/mod.rs` | `runtime/timealt/wheel/Mod.kt` |
| `signal.mod` | `signal.Mod` | 0 | `signal/mod.rs` | `signal/Mod.kt` |
| `sync.mod` | `sync.Mod` | 0 | `sync/mod.rs` | `sync/Mod.kt` |
| `mpsc.mod` | `sync.mpsc.Mod` | 0 | `sync/mpsc/mod.rs` | `sync/mpsc/Mod.kt` |
| `sync.task.mod` | `sync.task.Mod` | 0 | `sync/task/mod.rs` | `sync/task/Mod.kt` |
| `tests.mod` | `sync.tests.Mod` | 0 | `sync/tests/mod.rs` | `sync/tests/Mod.kt` |
| `coop.mod` | `task.coop.Mod` | 0 | `task/coop/mod.rs` | `task/coop/Mod.kt` |
| `task.mod` | `task.Mod` | 0 | `task/mod.rs` | `task/Mod.kt` |
| `time.mod` | `time.Mod` | 0 | `time/mod.rs` | `time/Mod.kt` |
| `util.mod` | `util.Mod` | 0 | `util/mod.rs` | `util/Mod.kt` |

