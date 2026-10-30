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

        // Never schedule alarms for the past (AlarmManager fires immediately if time <= now)
        long now = System.currentTimeMillis();
        if (saving.getDeadline() <= now) {
            cancel(context, saving);
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, DeadlineReceiver.class);
        intent.setAction(ACTION_DEADLINE_ALARM);
        intent.setPackage(context.getPackageName());
        intent.putExtra(Database.COLUMN_SAVING_ID, saving.getID());
        intent.putExtra(Database.COLUMN_SAVING_NAME, saving.getName());
        intent.putExtra(Database.COLUMN_SAVING_DEADLINE, saving.getDeadline());

        int requestCode = saving.getID().hashCode() & 0x7FFFFFFF;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, saving.getDeadline(), pendingIntent);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while scheduling deadline alarm", e);
        }
    }

    public static void cancel(Context context, Saving saving) {
        if (context == null || saving == null || saving.getID() == null) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, DeadlineReceiver.class);
        intent.setAction(ACTION_DEADLINE_ALARM);
        intent.setPackage(context.getPackageName());

        int requestCode = saving.getID().hashCode() & 0x7FFFFFFF;
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
