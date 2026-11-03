package com.eipna.centsation.habit

import com.eipna.centsation.habit.calendar.CalendarHabitParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class CalendarHabitParserTest {

    @Test
    fun parse_numberWithUnitInTitle_pushups() {
        val parsed = CalendarHabitParser.parse("20 Pushups")
        assertEquals("Pushups", parsed.title)
        assertEquals(20, parsed.baseDailyTarget)
        assertEquals("reps", parsed.unit)
    }

    @Test
    fun parse_readPages() {
        val parsed = CalendarHabitParser.parse("Read 30 pages")
        assertEquals("Read", parsed.title)
        assertEquals(30, parsed.baseDailyTarget)
        assertEquals("pages", parsed.unit)
    }

    @Test
    fun parse_drinkWaterGlasses() {
        val parsed = CalendarHabitParser.parse("Drink 8 glasses of water")
        assertEquals("Drink water", parsed.title)
        assertEquals(8, parsed.baseDailyTarget)
        assertEquals("glasses", parsed.unit)
    }

    @Test
    fun parse_walkSteps() {
        val parsed = CalendarHabitParser.parse("Walk 10000 steps")
        assertEquals("Walk", parsed.title)
        assertEquals(10000, parsed.baseDailyTarget)
        assertEquals("steps", parsed.unit)
    }

    @Test
    fun parse_runKm() {
        val parsed = CalendarHabitParser.parse("Run 5 km")
        assertEquals("Run", parsed.title)
        assertEquals(5, parsed.baseDailyTarget)
        assertEquals("km", parsed.unit)
    }

    @Test
    fun parse_workoutMinutes() {
        val parsed = CalendarHabitParser.parse("Workout 45 mins")
        assertEquals("Workout", parsed.title)
        assertEquals(45, parsed.baseDailyTarget)
        assertEquals("mins", parsed.unit)
    }

    @Test
    fun parse_studyHours() {
        val parsed = CalendarHabitParser.parse("Study 2 hours")
        assertEquals("Study", parsed.title)
        assertEquals(2, parsed.baseDailyTarget)
        assertEquals("hours", parsed.unit)
    }

    @Test
    fun parse_titleWithCheckmarkPrefixAndDoneSuffix() {
        val parsed = CalendarHabitParser.parse("✅ 15 pullups [Done]")
        assertEquals("Pullups", parsed.title)
        assertEquals(15, parsed.baseDailyTarget)
        assertEquals("reps", parsed.unit)
    }

    @Test
    fun parse_fallbackForGenericTask_meditation() {
        val parsed = CalendarHabitParser.parse("Meditation")
        assertEquals("Meditation", parsed.title)
        assertEquals(15, parsed.baseDailyTarget)
        assertEquals("mins", parsed.unit)
    }

    @Test
    fun parse_fallbackForGenericTask_duolingo() {
        val parsed = CalendarHabitParser.parse("Duolingo lesson")
        assertEquals("Duolingo lesson", parsed.title)
        assertEquals(1, parsed.baseDailyTarget)
        assertEquals("lessons", parsed.unit)
    }

    @Test
    fun parse_preservesScheduledTime() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 30)
        }
        val parsed = CalendarHabitParser.parse(
            rawTitle = "Morning Run 5 km",
            startMillis = cal.timeInMillis,
            isAllDay = false
        )
        assertEquals("Morning Run", parsed.title)
        assertEquals(5, parsed.baseDailyTarget)
        assertEquals("km", parsed.unit)
        assertEquals(7, parsed.scheduledHour)
        assertEquals(30, parsed.scheduledMinute)
    }

    @Test
    fun parse_allDayEventDefaultsTo21PM() {
        val parsed = CalendarHabitParser.parse(
            rawTitle = "Daily Pushups 10 reps",
            startMillis = 12345678L,
            isAllDay = true
        )
        assertEquals(21, parsed.scheduledHour)
        assertEquals(0, parsed.scheduledMinute)
    }

    @Test
    fun parse_numberInDescription() {
        val parsed = CalendarHabitParser.parse(
            rawTitle = "Fitness Routine",
            description = "Daily quota: 50 pushups"
        )
        assertEquals("Fitness Routine", parsed.title)
        assertEquals(50, parsed.baseDailyTarget)
        assertEquals("reps", parsed.unit)
    }
}
