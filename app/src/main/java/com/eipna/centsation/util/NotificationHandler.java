package com.eipna.centsation.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.ui.activities.MainActivity;

public class NotificationHandler {

    private static final String TAG = "NotificationHandler";
    public static final String CHANNEL_DEADLINE_ID = "channel_deadline_v3";
    public static final String CHANNEL_DEADLINE_NAME = "Deadlines & Milestones";
    public static final int CHANNEL_DEADLINE_IMPORTANCE = NotificationManager.IMPORTANCE_HIGH;

    public static boolean canPostNotifications(Context context) {
        if (context == null) return false;
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static void createChannels(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                try {
                    if (notificationManager.getNotificationChannel("channel_deadline") != null) {
                        notificationManager.deleteNotificationChannel("channel_deadline");
                    }
                    if (notificationManager.getNotificationChannel("channel_deadline_v2") != null) {
                        notificationManager.deleteNotificationChannel("channel_deadline_v2");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed cleaning legacy notification channels", e);
                }

                NotificationChannel deadlineChannel = new NotificationChannel(
                        CHANNEL_DEADLINE_ID,
                        CHANNEL_DEADLINE_NAME,
                        CHANNEL_DEADLINE_IMPORTANCE
                );
                deadlineChannel.setDescription("Reminders for savings goal deadlines and advance alerts");
                deadlineChannel.enableVibration(true);
                deadlineChannel.setVibrationPattern(new long[]{0, 250, 150, 250});
                deadlineChannel.enableLights(true);
                deadlineChannel.setLightColor(0xFF22C55E);
                deadlineChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                deadlineChannel.setShowBadge(true);
                notificationManager.createNotificationChannel(deadlineChannel);
            }
        }
    }

    public static void create(Context context, Saving saving, boolean isAdvanceNotice) {
        if (context == null || saving == null || saving.getID() == null) return;
        if (!canPostNotifications(context)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        createChannels(context);

        int notificationId = (saving.getID() + (isAdvanceNotice ? "_adv" : "")).hashCode() & 0x7FFFFFFF;

        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        notificationIntent.putExtra(Database.COLUMN_SAVING_ID, saving.getID());
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        String notificationTitle = isAdvanceNotice
                ? context.getString(R.string.notification_deadline_advance_title, saving.getName())
                : context.getString(R.string.notification_deadline_title, saving.getName());
        String notificationBody = isAdvanceNotice
                ? context.getString(R.string.notification_deadline_advance_body, saving.getName())
                : context.getString(R.string.notification_deadline_body, saving.getName());

        int percent = savingsPercent(saving);
        PreferenceUtil preferenceUtil = new PreferenceUtil(context);
        String currencySymbol = Currency.getSymbol(preferenceUtil.getCurrency());
        String savedStr = currencySymbol + FormatUtil.formatAmount(saving.getCurrentSaving(), context);
        String goalStr = currencySymbol + FormatUtil.formatAmount(saving.getGoal(), context);
        double remainingVal = Math.max(0, saving.getGoal() - saving.getCurrentSaving());
        String remainingStr = currencySymbol + FormatUtil.formatAmount(remainingVal, context);

        String expandedContent = notificationBody + "\n\n"
                + "• Progress: " + savedStr + " / " + goalStr + " (" + percent + "% saved)\n"
                + "• Remaining: " + remainingStr;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DEADLINE_ID)
                .setSmallIcon(R.drawable.ic_stat_centsation)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(notificationTitle)
                .setContentText(notificationBody)
                .setSubText(percent + "% saved")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(expandedContent))
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_savings, "View Goal", pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false);

        try {
            notificationManager.notify(notificationId, builder.build());
        } catch (SecurityException se) {
            Log.w(TAG, "POST_NOTIFICATIONS permission denied while posting deadline notification", se);
        }
    }

    private static int savingsPercent(Saving saving) {
        if (saving.getGoal() <= 0) return 0;
        return (int) Math.round((saving.getCurrentSaving() / saving.getGoal()) * 100.0);
    }
}
