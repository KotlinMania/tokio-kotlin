# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 32/372 (8.6%)
- **Function parity:** 209/3370 matched (target 319) — 6.2%
- **Class/type parity:** 24/662 matched (target 49) — 3.6%
- **Combined symbol parity:** 233/4032 matched (target 368) — 5.8%
- **Average inline-code cosine:** 0.17 (function body across 32 matched files)
- **Average documentation cosine:** 0.61 (doc text across 32 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 31 files with <0.60 function similarity
- **Needs Review:** 0 files with 0.60-0.84 function similarity
- **Excellent:** 1 files with >=0.85 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

### 1. metrics.io
- **Similarity:** 0.36 (needs 49% improvement)
- **Dependencies:** 85
- **Priority Score:** 85000408.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_
- **Action:** Deep review - likely missing major functionality

### 2. std.unsafe_cell
- **Similarity:** 0.27 (needs 58% improvement)
- **Dependencies:** 25
- **Priority Score:** 25000408.0
- **Functions:** 3/3 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_
- **Action:** Deep review - likely missing major functionality

### 3. std.atomic_usize
- **Similarity:** 0.07 (needs 78% improvement)
- **Dependencies:** 17
- **Priority Score:** 17000810.0
- **Functions:** 6/6 matched (target 18)
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_
- **Action:** Deep review - likely missing major functionality

### 4. io.async_write
- **Similarity:** 0.06 (needs 79% improvement)
- **Dependencies:** 14
- **Priority Score:** 14203309.0
- **Functions:** 12/32 matched (target 17)
- **Missing functions:** `poll_write`, `poll_write_vectored`, `is_write_vectored`, `poll_flush`, `poll_shutdown`, `poll_write`, `poll_write_vectored`, `is_write_vectored`, `poll_flush`, `poll_shutdown`, `poll_write`, `poll_write_vectored`, `is_write_vectored`, `poll_flush`, `poll_shutdown`, `poll_write`, `poll_write_vectored`, `is_write_vectored`, `poll_flush`, `poll_shutdown`
- **Types:** 1/1 matched (target 4)
- **Missing types:** _none_
- **Symbol Deficit:** 20 (functions: 20, types: 0)
- **Action:** Deep review - likely missing major functionality

### 5. util.mem
- **Similarity:** 0.26 (needs 59% improvement)
- **Dependencies:** 13
- **Priority Score:** 13002007.0
- **Functions:** 18/18 matched (target 26)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_
- **Action:** Deep review - likely missing major functionality

### 6. metrics.scheduler
- **Similarity:** 0.41 (needs 44% improvement)
- **Dependencies:** 11
- **Priority Score:** 11000406.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_
- **Action:** Deep review - likely missing major functionality

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

4. **runtime.handle** (11 deps)
   - Path: `runtime/handle.rs`
   - Essential for 11 other files

5. **sync.notify** (10 deps)
   - Path: `sync/notify.rs`
   - Essential for 10 other files

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. metrics.io

- **Target:** `metrics.Io`
- **Similarity:** 0.36
- **Dependents:** 85
- **Priority Score:** 85000408.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 2. std.unsafe_cell

- **Target:** `std.UnsafeCell`
- **Similarity:** 0.27
- **Dependents:** 25
- **Priority Score:** 25000408.0
- **Functions:** 3/3 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 3. std.atomic_usize

- **Target:** `std.AtomicUsize`
- **Similarity:** 0.07
- **Dependents:** 17
- **Priority Score:** 17000810.0
- **Functions:** 6/6 matched (target 18)
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_
- **Lint issues:** 1

### 4. io.async_write

- **Target:** `io.AsyncWrite`
- **Similarity:** 0.06
- **Dependents:** 14
- **Priority Score:** 14203309.0
- **Functions:** 12/32 matched (target 17)
- **Missing functions:** `poll_write`, `poll_write_vectored`, `is_write_vectored`, `poll_flush`, `poll_shutdown`, `poll_write`, `poll_write_vectored`, `is_write_vectored`, `poll_flush`, `poll_shutdown`, `poll_write`, `poll_write_vectored`, `is_write_vectored`, `poll_flush`, `poll_shutdown`, `poll_write`, `poll_write_vectored`, `is_write_vectored`, `poll_flush`, `poll_shutdown`
- **Types:** 1/1 matched (target 4)
- **Missing types:** _none_

### 5. util.mem

- **Target:** `util.Mem`
- **Similarity:** 0.26
- **Dependents:** 13
- **Priority Score:** 13002007.0
- **Functions:** 18/18 matched (target 26)
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

### 7. metrics.scheduler

- **Target:** `metrics.Scheduler`
- **Similarity:** 0.41
- **Dependents:** 11
- **Priority Score:** 11000406.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 8. io.async_buf_read

- **Target:** `io.AsyncBufRead`
- **Similarity:** 0.06
- **Dependents:** 7
- **Priority Score:** 7020709.5
- **Functions:** 4/6 matched (target 8)
- **Missing functions:** `poll_fill_buf`, `consume`
- **Types:** 1/1 matched (target 3)
- **Missing types:** _none_

### 9. io.ready

- **Target:** `io.Ready`
- **Similarity:** 0.26
- **Dependents:** 7
- **Priority Score:** 7012107.5
- **Functions:** 19/19 matched (target 29)
- **Missing functions:** _none_
- **Types:** 1/2 matched
- **Missing types:** `Output`

### 10. io.interest

- **Target:** `io.Interest`
- **Similarity:** 0.14
- **Dependents:** 7
- **Priority Score:** 7011608.5
- **Functions:** 14/14 matched (target 21)
- **Missing functions:** _none_
- **Types:** 1/2 matched
- **Missing types:** `Output`

### 11. util.wake_list

- **Target:** `util.WakeList`
- **Similarity:** 0.13
- **Dependents:** 6
- **Priority Score:** 6020808.5
- **Functions:** 5/6 matched (target 5)
- **Missing functions:** `drop`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `DropGuard`

### 12. io.async_read

- **Target:** `io.AsyncRead`
- **Similarity:** 0.11
- **Dependents:** 5
- **Priority Score:** 5010409.0
- **Functions:** 2/3 matched (target 4)
- **Missing functions:** `poll_read`
- **Types:** 1/1 matched (target 3)
- **Missing types:** _none_

### 13. io.read_buf

- **Target:** `io.ReadBuf`
- **Similarity:** 0.12
- **Dependents:** 4
- **Priority Score:** 4032608.8
- **Functions:** 22/25 matched (target 32)
- **Missing functions:** `slice_to_uninit_mut`, `slice_assume_init`, `slice_assume_init_mut`
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_

### 14. util.memchr

- **Target:** `util.Memchr`
- **Similarity:** 0.02
- **Dependents:** 1
- **Priority Score:** 1040509.8
- **Functions:** 1/5 matched (target 1)
- **Missing functions:** `memchr`, `memchr_test`, `memchr_all`, `memchr_empty`
- **Types:** 0/0 matched
- **Missing types:** _none_
- **Tests:** 0/3 matched

### 15. util.bit

- **Target:** `util.Bit`
- **Similarity:** 0.18
- **Dependents:** 1
- **Priority Score:** 1021008.2
- **Functions:** 7/9 matched (target 11)
- **Missing functions:** `fmt`, `unpack`
- **Types:** 1/1 matched
- **Missing types:** _none_

### 16. util.sync_wrapper

- **Target:** `util.SyncWrapper`
- **Similarity:** 0.06
- **Dependents:** 1
- **Priority Score:** 1020409.4
- **Functions:** 1/3 matched (target 4)
- **Missing functions:** `new`, `downcast_ref_sync`
- **Types:** 1/1 matched
- **Missing types:** _none_

### 17. util.buf_reader

- **Target:** `util.BufReader`
- **Similarity:** 0.08
- **Dependents:** 0
- **Priority Score:** 102109.2
- **Functions:** 11/20 matched (target 11)
- **Missing functions:** `start_seek`, `poll_complete`, `poll_write`, `poll_write_vectored`, `is_write_vectored`, `poll_flush`, `poll_shutdown`, `fmt`, `assert_unpin`
- **Types:** 0/1 matched
- **Missing types:** `SeekState`
- **Tests:** 0/1 matched

### 18. util.buf_writer

- **Target:** `util.BufWriter`
- **Similarity:** 0.08
- **Dependents:** 0
- **Priority Score:** 82109.2
- **Functions:** 13/20 matched (target 14)
- **Missing functions:** `start_seek`, `poll_complete`, `poll_read`, `poll_fill_buf`, `consume`, `fmt`, `assert_unpin`
- **Types:** 0/1 matched
- **Missing types:** `SeekState`
- **Tests:** 0/1 matched

### 19. util.empty

- **Target:** `util.Empty`
- **Similarity:** 0.07
- **Dependents:** 0
- **Priority Score:** 61209.3
- **Functions:** 6/12 matched (target 8)
- **Missing functions:** `poll_fill_buf`, `consume`, `start_seek`, `poll_complete`, `fmt`, `assert_unpin`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 20. util.cacheline

- **Target:** `util.Cacheline`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 40510.0
- **Functions:** 0/3 matched
- **Missing functions:** `new`, `deref`, `deref_mut`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `Target`

### 21. util.copy

- **Target:** `util.Copy`
- **Similarity:** 0.10
- **Dependents:** 0
- **Priority Score:** 20809.0
- **Functions:** 4/5 matched (target 7)
- **Missing functions:** `new`
- **Types:** 2/3 matched (target 2)
- **Missing types:** `Output`

### 22. util.sink

- **Target:** `util.Sink`
- **Similarity:** 0.08
- **Dependents:** 0
- **Priority Score:** 20509.2
- **Functions:** 3/5 matched (target 7)
- **Missing functions:** `fmt`, `assert_unpin`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 23. io.stdout

- **Target:** `io.Stdout`
- **Similarity:** 0.13
- **Dependents:** 0
- **Priority Score:** 20508.7
- **Functions:** 3/5 matched (target 7)
- **Missing functions:** `as_raw_fd`, `as_fd`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

### 24. io.stderr

- **Target:** `io.Stderr`
- **Similarity:** 0.13
- **Dependents:** 0
- **Priority Score:** 20508.7
- **Functions:** 3/5 matched (target 7)
- **Missing functions:** `as_raw_fd`, `as_fd`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

### 25. util.copy_buf

- **Target:** `util.CopyBuf`
- **Similarity:** 0.05
- **Dependents:** 0
- **Priority Score:** 20309.5
- **Functions:** 1/2 matched (target 4)
- **Missing functions:** `assert_unpin`
- **Types:** 0/1 matched
- **Missing types:** `Output`
- **Tests:** 0/1 matched

### 26. io.stdin

- **Target:** `io.Stdin`
- **Similarity:** 0.10
- **Dependents:** 0
- **Priority Score:** 20309.0
- **Functions:** 1/3 matched
- **Missing functions:** `as_raw_fd`, `as_fd`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

### 27. util.take

- **Target:** `util.Take`
- **Similarity:** 0.14
- **Dependents:** 0
- **Priority Score:** 11108.6
- **Functions:** 10/11 matched (target 12)
- **Missing functions:** `assert_unpin`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 28. util.chain

- **Target:** `util.Chain`
- **Similarity:** 0.16
- **Dependents:** 0
- **Priority Score:** 11008.4
- **Functions:** 9/10 matched
- **Missing functions:** `assert_unpin`
- **Types:** 0/0 matched (target 2)
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 29. util.repeat

- **Target:** `util.Repeat`
- **Similarity:** 0.05
- **Dependents:** 0
- **Priority Score:** 10209.5
- **Functions:** 1/2 matched (target 4)
- **Missing functions:** `assert_unpin`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 30. io.split

- **Target:** `io.Split`
- **Similarity:** 0.28
- **Dependents:** 0
- **Priority Score:** 1307.2
- **Functions:** 12/12 matched (target 15)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 3)
- **Missing types:** _none_

### 31. util.rand

- **Target:** `util.Rand`
- **Similarity:** 0.47
- **Dependents:** 0
- **Priority Score:** 905.3
- **Functions:** 7/7 matched (target 11)
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 32. util.metric_atomics

- **Target:** `util.MetricAtomics`
- **Similarity:** 0.12
- **Dependents:** 0
- **Priority Score:** 708.8
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

