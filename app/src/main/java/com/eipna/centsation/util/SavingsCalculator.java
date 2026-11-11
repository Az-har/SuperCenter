package com.eipna.centsation.util;

import com.eipna.centsation.data.saving.Saving;

import java.util.Calendar;

public final class SavingsCalculator {

    private static final double MILLIS_PER_DAY = 24.0 * 60.0 * 60.0 * 1000.0;

    private SavingsCalculator() {}

    public static double getRemainingAmount(Saving saving) {
        if (saving == null) return 0.0;
        return Math.max(0.0, saving.getGoal() - saving.getCurrentSaving());
    }

    public static int getPercent(Saving saving) {
        if (saving == null || saving.getGoal() <= 0) return 0;
        int percent = (int) Math.round((saving.getCurrentSaving() / saving.getGoal()) * 100.0);
        return Math.max(0, percent);
    }

    public static boolean isCompleted(Saving saving) {
        if (saving == null) return false;
        return saving.getCurrentSaving() >= saving.getGoal();
    }

    /**
     * Returns signed calendar-day difference between two timestamps using local mid-day
     * normalization so DST transitions (+/-1h) do not distort day counts (API 24+ safe).
     */
    public static long getCalendarDaysBetween(long startMillis, long endMillis) {
        long startNoon = toLocalNoonMillis(startMillis);
        long endNoon = toLocalNoonMillis(endMillis);
        return Math.round((endNoon - startNoon) / MILLIS_PER_DAY);
    }

    private static long toLocalNoonMillis(long timestampMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMillis);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public static long getDaysRemaining(long deadlineMillis) {
        return getDaysRemaining(deadlineMillis, System.currentTimeMillis());
    }

    public static long getDaysRemaining(long deadlineMillis, long referenceMillis) {
        if (deadlineMillis == AlarmSetter.NO_ALARM || deadlineMillis <= 0L) return Long.MIN_VALUE;
        return getCalendarDaysBetween(referenceMillis, deadlineMillis);
    }

    public static double getDailySavingsRate(Saving saving) {
        return getDailySavingsRate(saving, System.currentTimeMillis());
    }

    public static double getDailySavingsRate(Saving saving, long referenceMillis) {
        if (saving == null || saving.getDeadline() == AlarmSetter.NO_ALARM || saving.getDeadline() <= 0L) return 0.0;
        if (isCompleted(saving)) return 0.0;

        long days = getDaysRemaining(saving.getDeadline(), referenceMillis);
        if (days <= 0) {
            return getRemainingAmount(saving); // Due today or overdue
        }
        return getRemainingAmount(saving) / days;
    }

}
