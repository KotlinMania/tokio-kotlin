// port-lint: source util/error.rs
package io.github.kotlinmania.tokio.util

// Some combinations of features may not use these constants.

/** Error string explaining that the Tokio context hasn't been instantiated. */
internal const val CONTEXT_MISSING_ERROR: String =
    "there is no reactor running, must be called from the context of a Tokio 1.x runtime"

/** Error string explaining that the Tokio context is shutting down and cannot drive timers. */
internal const val RUNTIME_SHUTTING_DOWN_ERROR: String =
    "A Tokio 1.x context was found, but it is being shutdown."

/**
 * Error string explaining that the Tokio context is not available because the
 * thread-local storing it has been destroyed. This usually only happens during
 * destructors of other thread-locals.
 */
internal const val THREAD_LOCAL_DESTROYED_ERROR: String =
    "The Tokio context thread-local variable has been destroyed."
