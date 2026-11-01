package com.eipna.centsation.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.icu.text.NumberFormat;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.ui.activities.MainActivity;

public class NotificationHandler {

    public static final String CHANNEL_DEADLINE_ID = "channel_deadline";
    public static final String CHANNEL_DEADLINE_NAME = "Deadlines";
    public static final int CHANNEL_DEADLINE_IMPORTANCE = NotificationManager.IMPORTANCE_HIGH;

    public static void createChannels(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                NotificationChannel deadlineChannel = new NotificationChannel(
                        CHANNEL_DEADLINE_ID,
                        CHANNEL_DEADLINE_NAME,
                        CHANNEL_DEADLINE_IMPORTANCE
                );
                deadlineChannel.setDescription("Notifications for savings goal deadlines");
                deadlineChannel.enableVibration(true);
                notificationManager.createNotificationChannel(deadlineChannel);
            }
        }
    }

    public static void create(Context context, Saving saving) {
        if (context == null || saving == null || saving.getID() == null) return;
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        createChannels(context);

        int notificationId = saving.getID().hashCode() & 0x7FFFFFFF;

        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        notificationIntent.putExtra(Database.COLUMN_SAVING_ID, saving.getID());
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        String notificationTitle = context.getString(R.string.notification_deadline_title, saving.getName());
        String notificationBody = context.getString(R.string.notification_deadline_body, saving.getName());

        int percent = savingsPercent(saving);
        PreferenceUtil preferenceUtil = new PreferenceUtil(context);
        String currencySymbol = Currency.getSymbol(preferenceUtil.getCurrency());
        String savedStr = currencySymbol + NumberFormat.getInstance().format(saving.getCurrentSaving());
        String goalStr = currencySymbol + NumberFormat.getInstance().format(saving.getGoal());
        double remainingVal = Math.max(0, saving.getGoal() - saving.getCurrentSaving());
        String remainingStr = currencySymbol + NumberFormat.getInstance().format(remainingVal);

        String expandedContent = notificationBody + "\n\n"
                + "• Progress: " + savedStr + " / " + goalStr + " (" + percent + "%)\n"
                + "• Remaining: " + remainingStr;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DEADLINE_ID)
                .setSmallIcon(R.drawable.ic_stat_centsation)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentTitle(notificationTitle)
                .setContentText(notificationBody)
                .setSubText(percent + "% completed")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(expandedContent))
                .setProgress(100, Math.min(100, Math.max(0, percent)), false)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_savings, "View Goal", pendingIntent)
                .setAutoCancel(true);

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
        if (savingID == null || savingName == null) return;

        Saving saving = new Saving();
        saving.setID(savingID);
        saving.setName(savingName);
        create(context, saving);
    }

    public static void showTestNotification(Context context) {
        if (context == null) return;
        createChannels(context);

        Saving sample = new Saving();
        sample.setID("centsation-test-notification");
        sample.setName("Japan Vacation Fund 🎌");
        sample.setCurrentSaving(820.0);
        sample.setGoal(1200.0);
        sample.setDeadline(System.currentTimeMillis() + 86400000L * 2);
        create(context, sample);
    }
}
