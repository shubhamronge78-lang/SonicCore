package com.soniccore.app.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationLogTest {

    @After
    fun tearDown() {
        OperationLog.clear()
    }

    @Test
    fun snapshotReturnsNewestEntryFirst() {
        OperationLog.record("Test", "first", OperationResult.INFO)
        OperationLog.record("Test", "second", OperationResult.SUCCESS)

        val entries = OperationLog.snapshot()
        assertEquals(2, entries.size)
        assertEquals("second", entries[0].message)
        assertEquals("first", entries[1].message)
    }

    @Test
    fun bufferIsBoundedToMaxEntries() {
        repeat(OperationLog.MAX_ENTRIES + 25) { index ->
            OperationLog.record("Test", "entry-$index", OperationResult.INFO)
        }

        val entries = OperationLog.snapshot()
        assertEquals(OperationLog.MAX_ENTRIES, entries.size)
        assertEquals("entry-${OperationLog.MAX_ENTRIES + 24}", entries.first().message)
    }

    @Test
    fun clearEmptiesTheBuffer() {
        OperationLog.record("Test", "something", OperationResult.FAILED)
        OperationLog.clear()
        assertTrue(OperationLog.snapshot().isEmpty())
    }

    @Test
    fun formatHandlesEmptyAndPopulatedLogs() {
        assertEquals("(no operations recorded this session)", OperationLog.format(emptyList()))

        val formatted = OperationLog.format(
            listOf(
                OperationLogEntry("12:00:00", "Shizuku", "State probe: Connected", OperationResult.SUCCESS),
                OperationLogEntry("12:00:01", "Permissions", "Open settings unavailable", OperationResult.UNAVAILABLE)
            )
        )
        assertTrue(formatted.contains("OK"))
        assertTrue(formatted.contains("UNAVAILABLE"))
        assertTrue(formatted.contains("State probe: Connected"))
        assertEquals(2, formatted.lines().size)
    }
}
