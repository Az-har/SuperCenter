package com.eipna.centsation.util;

import com.eipna.centsation.data.saving.Saving;

import java.util.concurrent.TimeUnit;

public final class SavingsCalculator {

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

    public static long getDaysRemaining(long deadlineMillis) {
        if (deadlineMillis == AlarmSetter.NO_ALARM) return Long.MIN_VALUE;
        long diff = deadlineMillis - System.currentTimeMillis();
        return TimeUnit.MILLISECONDS.toDays(diff);
    }

    public static double getDailySavingsRate(Saving saving) {
        if (saving == null || saving.getDeadline() == AlarmSetter.NO_ALARM) return 0.0;
        if (isCompleted(saving)) return 0.0;

        long days = getDaysRemaining(saving.getDeadline());
        if (days <= 0) {
            return getRemainingAmount(saving); // Due today or overdue
        }
        return getRemainingAmount(saving) / days;
    }

}
