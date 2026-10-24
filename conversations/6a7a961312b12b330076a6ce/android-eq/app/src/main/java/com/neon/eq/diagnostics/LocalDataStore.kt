package com.neon.eq.diagnostics

import android.content.Context
import java.io.File

/**
 * What SonicCore keeps on this device, and the only place it is removed from.
 *
 * Everything read here is local application data: the JSONL session log the
 * capture pipeline appends to `filesDir`, and the app's own cache/preference
 * footprint. No audio is ever stored, and nothing is uploaded.
 */
object LocalDataStore {

    /** Session records the capture pipeline has appended, one JSON object per line. */
    fun sessionCount(context: Context): Int = try {
        val file = File(context.filesDir, SESSIONS_FILE)
        if (!file.exists()) 0 else file.readLines().count { it.isNotBlank() }
    } catch (_: Throwable) {
        0
    }

    /**
     * Deletes the recorded session log. Returns false when there was nothing to
     * clear or the delete did not take effect — never a silent success.
     */
    fun clearSessions(context: Context): Boolean = try {
        val file = File(context.filesDir, SESSIONS_FILE)
        if (!file.exists()) false else file.delete()
    } catch (_: Throwable) {
        false
    }

    /** Bytes SonicCore holds locally: its cache directory plus its private files. */
    fun locallyStoredBytes(context: Context): Long =
        directorySize(context.cacheDir) + directorySize(context.filesDir)

    private fun directorySize(dir: File?): Long = try {
        if (dir == null || !dir.exists()) 0L
        else dir.listFiles()?.sumOf { if (it.isDirectory) directorySize(it) else it.length() } ?: 0L
    } catch (_: Throwable) {
        0L
    }

    private const val SESSIONS_FILE = "sessions.jsonl"
}
