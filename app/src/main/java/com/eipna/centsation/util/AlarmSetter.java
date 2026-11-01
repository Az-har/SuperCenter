package com.eipna.centsation.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.receiver.DeadlineReceiver;

public class AlarmSetter {

    private static final String TAG = "AlarmSetter";
    public static final int NO_ALARM = 0;
    public static final String ACTION_DEADLINE_ALARM = "com.eipna.centsation.ACTION_DEADLINE_ALARM";

    public static void set(Context context, Saving saving) {
        if (context == null || saving == null || saving.getID() == null) return;
        if (saving.getDeadline() == NO_ALARM) return;

        // Never set alarms for archived savings or already completed goals
        if (saving.getIsArchived() == Saving.IS_ARCHIVE || saving.getCurrentSaving() >= saving.getGoal()) {
            cancel(context, saving);
            return;
        }

        long now = System.currentTimeMillis();
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // 1. Primary Deadline Alarm (Due Today morning)
        if (saving.getDeadline() > now) {
            Intent intent = new Intent(context, DeadlineReceiver.class);
            intent.setAction(ACTION_DEADLINE_ALARM);
            intent.setPackage(context.getPackageName());
            intent.putExtra(Database.COLUMN_SAVING_ID, saving.getID());
            intent.putExtra(Database.COLUMN_SAVING_NAME, saving.getName());
            intent.putExtra(Database.COLUMN_SAVING_DEADLINE, saving.getDeadline());
            intent.putExtra("is_advance_notice", false);

            int requestCode = saving.getID().hashCode() & 0x7FFFFFFF;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, saving.getDeadline(), pendingIntent);
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException scheduling primary deadline alarm", e);
            }
        }

        // 2. Advance Notice Alarm (24 hours prior to deadline)
        long advanceTime = saving.getDeadline() - 86400000L;
        if (advanceTime > now) {
            Intent advanceIntent = new Intent(context, DeadlineReceiver.class);
            advanceIntent.setAction(ACTION_DEADLINE_ALARM);
            advanceIntent.setPackage(context.getPackageName());
            advanceIntent.putExtra(Database.COLUMN_SAVING_ID, saving.getID());
            advanceIntent.putExtra(Database.COLUMN_SAVING_NAME, saving.getName());
            advanceIntent.putExtra(Database.COLUMN_SAVING_DEADLINE, saving.getDeadline());
            advanceIntent.putExtra("is_advance_notice", true);

            int advanceRequestCode = (saving.getID() + "_adv").hashCode() & 0x7FFFFFFF;
            PendingIntent advancePendingIntent = PendingIntent.getBroadcast(
                    context,
                    advanceRequestCode,
                    advanceIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, advanceTime, advancePendingIntent);
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException scheduling advance deadline alarm", e);
            }
        }
    }

    public static void cancel(Context context, Saving saving) {
        if (context == null || saving == null || saving.getID() == null) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Cancel Primary Alarm
        cancelIntent(context, alarmManager, saving.getID().hashCode() & 0x7FFFFFFF);

        // Cancel Advance Notice Alarm
        cancelIntent(context, alarmManager, (saving.getID() + "_adv").hashCode() & 0x7FFFFFFF);
    }

    private static void cancelIntent(Context context, AlarmManager alarmManager, int requestCode) {
        Intent intent = new Intent(context, DeadlineReceiver.class);
        intent.setAction(ACTION_DEADLINE_ALARM);
        intent.setPackage(context.getPackageName());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }
}
