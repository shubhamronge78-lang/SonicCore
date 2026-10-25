package com.soniccore.app.diagnostics

/**
 * Log of diagnostic operations performed.
 */
class OperationLog {
    val entries: MutableList<String> = mutableListOf()

    fun add(entry: String) {
        entries.add(entry)
    }

    companion object {
        @JvmStatic
        fun record(category: String, message: String, result: OperationResult) {
            // Session-scoped in-memory log
        }
    }
}
