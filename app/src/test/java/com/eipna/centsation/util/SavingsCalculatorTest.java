package com.eipna.centsation.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.eipna.centsation.data.saving.Saving;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class SavingsCalculatorTest {

    @Test
    public void basicProgressAndRemainingMath_handlesPartialCompletedAndOverflow() {
        Saving partial = new Saving();
        partial.setGoal(1000.0);
        partial.setCurrentSaving(350.0);
        assertEquals(650.0, SavingsCalculator.getRemainingAmount(partial), 0.001);
        assertEquals(35, SavingsCalculator.getPercent(partial));
        assertFalse(SavingsCalculator.isCompleted(partial));

        Saving overfunded = new Saving();
        overfunded.setGoal(500.0);
        overfunded.setCurrentSaving(750.0);
        assertEquals(0.0, SavingsCalculator.getRemainingAmount(overfunded), 0.001);
        assertEquals(150, SavingsCalculator.getPercent(overfunded));
        assertTrue(SavingsCalculator.isCompleted(overfunded));

        assertEquals(0.0, SavingsCalculator.getRemainingAmount(null), 0.001);
        assertEquals(0, SavingsCalculator.getPercent(null));
        assertFalse(SavingsCalculator.isCompleted(null));
    }

    @Test
    public void calendarDaysBetween_normalizesLateEveningToNextMorningAsOneDay() {
        Calendar start = Calendar.getInstance();
        start.set(2026, Calendar.NOVEMBER, 10, 23, 55, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = Calendar.getInstance();
        end.set(2026, Calendar.NOVEMBER, 11, 0, 5, 0);
        end.set(Calendar.MILLISECOND, 0);

        assertEquals(1L, SavingsCalculator.getCalendarDaysBetween(start.getTimeInMillis(), end.getTimeInMillis()));
        assertEquals(-1L, SavingsCalculator.getCalendarDaysBetween(end.getTimeInMillis(), start.getTimeInMillis()));
    }

    @Test
    public void calendarDaysBetween_survivesDstTransition() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        cal.set(2026, Calendar.MARCH, 7, 10, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long march7 = cal.getTimeInMillis();

        cal.set(2026, Calendar.MARCH, 9, 10, 0, 0);
        long march9 = cal.getTimeInMillis();

        assertEquals(2L, SavingsCalculator.getCalendarDaysBetween(march7, march9));
    }

    @Test
    public void dailySavingsRate_coversFutureDueTodayOverdueCompletedAndNoAlarm() {
        Calendar ref = Calendar.getInstance();
        ref.set(2026, Calendar.NOVEMBER, 10, 9, 0, 0);
        ref.set(Calendar.MILLISECOND, 0);
        long now = ref.getTimeInMillis();

        assertEquals(Long.MIN_VALUE, SavingsCalculator.getDaysRemaining(AlarmSetter.NO_ALARM, now));

        // 5 days left -> 500 remaining => 100/day
        Calendar fiveDays = (Calendar) ref.clone();
        fiveDays.add(Calendar.DAY_OF_MONTH, 5);
        Saving futureGoal = new Saving();
        futureGoal.setGoal(1000.0);
        futureGoal.setCurrentSaving(500.0);
        futureGoal.setDeadline(fiveDays.getTimeInMillis());
        assertEquals(5L, SavingsCalculator.getDaysRemaining(futureGoal.getDeadline(), now));
        assertEquals(100.0, SavingsCalculator.getDailySavingsRate(futureGoal, now), 0.001);

        // Due today -> returns full remaining amount
        futureGoal.setDeadline(now);
        assertEquals(500.0, SavingsCalculator.getDailySavingsRate(futureGoal, now), 0.001);

        // Overdue -> returns full remaining amount
        Calendar yesterday = (Calendar) ref.clone();
        yesterday.add(Calendar.DAY_OF_MONTH, -2);
        futureGoal.setDeadline(yesterday.getTimeInMillis());
        assertEquals(-2L, SavingsCalculator.getDaysRemaining(futureGoal.getDeadline(), now));
        assertEquals(500.0, SavingsCalculator.getDailySavingsRate(futureGoal, now), 0.001);

        // Completed goal -> pace is 0
        futureGoal.setCurrentSaving(1000.0);
        assertEquals(0.0, SavingsCalculator.getDailySavingsRate(futureGoal, now), 0.001);
    }
}
