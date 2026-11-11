package com.eipna.centsation.util

import com.eipna.centsation.data.Database
import com.eipna.centsation.habit.data.entity.TrackerEntity
import com.eipna.centsation.habit.data.entity.TrackerLogEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerPayloadTest {

    @Test
    fun serializeTrackersAndLogs_preservesAllFields() {
        val tracker = TrackerEntity(
            id = 7L,
            title = "Pushups",
            baseDailyTarget = 25,
            unit = "reps",
            scheduledHour = 20,
            scheduledMinute = 45,
            syncCalendar = true,
            calendarId = 3L,
            calendarEventId = 99L,
            isActive = true,
            createdAt = 1_700_000_000_000L
        )
        val log = TrackerLogEntity(
            id = 14L,
            trackerId = 7L,
            date = "2026-11-10",
            completedCount = 30,
            timestamp = 1_794_300_000_000L
        )

        val trackerArray = BackupManager.serializeTrackers(listOf(tracker))
        assertEquals(1, trackerArray.length())
        val tObj = trackerArray.getJSONObject(0)
        assertEquals(7L, tObj.getLong("id"))
        assertEquals("Pushups", tObj.getString("title"))
        assertEquals(25, tObj.getInt("baseDailyTarget"))
        assertEquals("reps", tObj.getString("unit"))
        assertEquals(20, tObj.getInt("scheduledHour"))
        assertEquals(45, tObj.getInt("scheduledMinute"))
        assertTrue(tObj.getBoolean("syncCalendar"))
        assertEquals(3L, tObj.getLong("calendarId"))
        assertEquals(99L, tObj.getLong("calendarEventId"))
        assertTrue(tObj.getBoolean("isActive"))
        assertEquals(1_700_000_000_000L, tObj.getLong("createdAt"))

        val logArray = BackupManager.serializeTrackerLogs(listOf(log))
        assertEquals(1, logArray.length())
        val lObj = logArray.getJSONObject(0)
        assertEquals(14L, lObj.getLong("id"))
        assertEquals(7L, lObj.getLong("trackerId"))
        assertEquals("2026-11-10", lObj.getString("date"))
        assertEquals(30, lObj.getInt("completedCount"))
        assertEquals(1_794_300_000_000L, lObj.getLong("timestamp"))
    }

    @Test
    fun buildBackupJsonPayload_emitsV2SchemaCountsAndTables() {
        val trackers = BackupManager.serializeTrackers(
            listOf(TrackerEntity(id = 1L, title = "Read", baseDailyTarget = 10, unit = "pages"))
        )
        val logs = BackupManager.serializeTrackerLogs(
            listOf(TrackerLogEntity(id = 2L, trackerId = 1L, date = "2026-11-11", completedCount = 5))
        )
        val prefs = JSONObject().put("currency", "INR")

        val json = BackupManager.buildBackupJsonPayload(
            JSONArray(),
            JSONArray(),
            JSONArray(),
            JSONArray(),
            trackers,
            logs,
            prefs,
            "INR",
            1_794_380_000_000L
        )

        val root = JSONObject(json)
        assertEquals(2, root.getInt("version"))
        assertEquals(0, root.getInt("savings_count"))
        assertEquals(0, root.getInt("transactions_count"))
        assertEquals(0, root.getInt("debts_count"))
        assertEquals(0, root.getInt("debt_transactions_count"))
        assertEquals(1, root.getInt("trackers_count"))
        assertEquals(1, root.getInt("tracker_logs_count"))
        assertEquals("INR", root.getString("currency"))
        assertTrue(root.has(Database.TABLE_SAVING))
        assertTrue(root.has(Database.TABLE_TRANSACTION))
        assertTrue(root.has(Database.TABLE_DEBT))
        assertTrue(root.has(Database.TABLE_DEBT_TRANSACTION))
        assertEquals(1, root.getJSONArray(BackupManager.TABLE_TRACKERS).length())
        assertEquals(1, root.getJSONArray(BackupManager.TABLE_TRACKER_LOGS).length())
    }
}
