package com.neon.eq.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OperationResult(val label: String) {
    SUCCESS("OK"),
    UNAVAILABLE("UNAVAILABLE"),
    FAILED("FAILED"),
    INFO("INFO")
}

data class OperationLogEntry(
    val timeLabel: String,
    val category: String,
    val message: String,
    val result: OperationResult
)

/**
 * Session-scoped, bounded operation log.
 *
 * Deliberately in-memory: nothing is written to disk unless the user exports a
 * diagnostic report, so no log survives in the app's storage by default. The
 * buffer is capped so a long session cannot grow without limit.
 */
object OperationLog {

    const val MAX_ENTRIES = 100

    private val entries = ArrayDeque<OperationLogEntry>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun record(category: String, message: String, result: OperationResult) {
        val entry = OperationLogEntry(
            timeLabel = timeFormat.format(Date()),
            category = category,
            message = message,
            result = result
        )
        entries.addFirst(entry)
        while (entries.size > MAX_ENTRIES) {
            entries.removeLast()
        }
    }

    /** Newest first, matching the on-screen list order. */
    @Synchronized
    fun snapshot(): List<OperationLogEntry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
    }

    fun format(entries: List<OperationLogEntry>): String {
        if (entries.isEmpty()) return "(no operations recorded this session)"
        return entries.joinToString("\n") { entry ->
            "${entry.timeLabel}  ${entry.result.label.padEnd(11)}  ${entry.category}  ${entry.message}"
        }
    }
}
