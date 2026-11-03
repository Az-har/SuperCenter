package com.eipna.centsation.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.ui.activities.MainActivity;

public class NotificationHandler {

    public static final String CHANNEL_DEADLINE_ID = "channel_deadline_v3";
    public static final String CHANNEL_DEADLINE_NAME = "Deadlines & Milestones";
    public static final int CHANNEL_DEADLINE_IMPORTANCE = NotificationManager.IMPORTANCE_HIGH;

    public static void createChannels(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                // Delete legacy channels so Android applies new HIGH importance channel cleanly
                try {
                    notificationManager.deleteNotificationChannel("channel_deadline");
                    notificationManager.deleteNotificationChannel("channel_deadline_v2");
                } catch (Exception ignored) {}

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

    public static void create(Context context, Saving saving) {
        create(context, saving, false);
    }

    public static void create(Context context, Saving saving, boolean isAdvanceNotice) {
        if (context == null || saving == null || saving.getID() == null) return;
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

        notificationManager.notify(notificationId, builder.build());
    }

    private static int savingsPercent(Saving saving) {
        if (saving.getGoal() <= 0) return 0;
        return (int) Math.round((saving.getCurrentSaving() / saving.getGoal()) * 100.0);
    }

    public static void create(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String savingID = intent.getStringExtra(Database.COLUMN_SAVING_ID);
        String savingName = intent.getStringExtra(Database.COLUMN_SAVING_NAME);
        boolean isAdvanceNotice = intent.getBooleanExtra("is_advance_notice", false);
        if (savingID == null || savingName == null) return;

        Saving saving = new Saving();
        saving.setID(savingID);
        saving.setName(savingName);
        create(context, saving, isAdvanceNotice);
    }

}
