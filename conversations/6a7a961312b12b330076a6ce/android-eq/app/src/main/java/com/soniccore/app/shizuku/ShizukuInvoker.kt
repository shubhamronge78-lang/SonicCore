package com.soniccore.app.shizuku

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Outcome of one bounded IPC call. */
sealed class ShizukuCall<out T> {
    data class Success<T>(val value: T, val elapsedMs: Long) : ShizukuCall<T>()
    data class Failure(
        val failure: ShizukuFailure,
        val reason: String,
        val elapsedMs: Long
    ) : ShizukuCall<Nothing>()
}

/**
 * Runs a Shizuku call off the caller's thread with a hard timeout, and converts
 * whatever it throws into a [ShizukuFailure].
 *
 * Binder calls are blocking and cannot be cancelled, so a stuck call must never
 * freeze the UI or block the next call: each invocation gets its own daemon
 * worker, the caller waits at most [timeoutMs], and a call that overruns is
 * abandoned (it can only finish on its own thread, and it can only read).
 *
 * Pure JVM code — the unit tests drive it with ordinary lambdas.
 */
object ShizukuInvoker {

    const val DEFAULT_TIMEOUT_MS = 2_000L

    private val threadFactory = ThreadFactory { runnable ->
        Thread(runnable, "soniccore-shizuku-ipc").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }

    private val executor = Executors.newCachedThreadPool(threadFactory)

    fun <T> call(timeoutMs: Long = DEFAULT_TIMEOUT_MS, block: () -> T): ShizukuCall<T> {
        require(timeoutMs > 0) { "timeoutMs must be positive" }
        val startedAt = System.nanoTime()
        val future = executor.submit(Callable { block() })
        return try {
            val value = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            ShizukuCall.Success(value, elapsed(startedAt))
        } catch (timeout: TimeoutException) {
            future.cancel(true)
            failure(
                ShizukuFailure.TIMEOUT,
                "No answer within $timeoutMs ms.",
                startedAt
            )
        } catch (execution: ExecutionException) {
            val cause = execution.cause ?: execution
            val mapped = ShizukuFailureMapper.classify(cause)
            failure(mapped, ShizukuFailureMapper.reasonFor(mapped, cause.message), startedAt)
        } catch (interrupted: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            failure(ShizukuFailure.INTERRUPTED, ShizukuFailure.INTERRUPTED.reason, startedAt)
        } catch (rejected: RuntimeException) {
            // Executor shut down (process teardown) — report it, never crash the UI.
            failure(ShizukuFailure.SERVICE_UNAVAILABLE, ShizukuFailure.SERVICE_UNAVAILABLE.reason, startedAt)
        }
    }

    private fun failure(failure: ShizukuFailure, reason: String, startedAt: Long): ShizukuCall.Failure =
        ShizukuCall.Failure(failure = failure, reason = reason, elapsedMs = elapsed(startedAt))

    private fun elapsed(startedAt: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
}
