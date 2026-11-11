package com.eipna.centsation.habit

import com.eipna.centsation.habit.notification.TrackerAlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerAlarmSchedulerTest {

    @Test
    fun pendingIntentRequestCodes_areUniquePerSlotAndTrackerId() {
        val codes1 = listOf(
            TrackerAlarmScheduler.alarmRequestCode(1L),
            TrackerAlarmScheduler.snoozeRequestCode(1L),
            TrackerAlarmScheduler.contentRequestCode(1L),
            TrackerAlarmScheduler.directReplyRequestCode(1L),
            TrackerAlarmScheduler.quickCompleteRequestCode(1L),
            TrackerAlarmScheduler.snoozeActionRequestCode(1L)
        )
        assertEquals(6, codes1.distinct().size)

        val codes2 = listOf(
            TrackerAlarmScheduler.alarmRequestCode(2L),
            TrackerAlarmScheduler.snoozeRequestCode(2L),
            TrackerAlarmScheduler.contentRequestCode(2L),
            TrackerAlarmScheduler.directReplyRequestCode(2L),
            TrackerAlarmScheduler.quickCompleteRequestCode(2L),
            TrackerAlarmScheduler.snoozeActionRequestCode(2L)
        )
        assertEquals(6, codes2.distinct().size)
        assertEquals(12, (codes1 + codes2).distinct().size)

        assertNotEquals(
            TrackerAlarmScheduler.alarmRequestCode(42L),
            TrackerAlarmScheduler.alarmRequestCode(43L)
        )
    }

    @Test
    fun notificationId_masksPositive31BitsConsistently() {
        assertEquals(1, TrackerAlarmScheduler.notificationId(1L))
        assertEquals(42, TrackerAlarmScheduler.notificationId(42L))
        assertTrue(TrackerAlarmScheduler.notificationId(Long.MAX_VALUE) >= 0)
    }
}
