package com.eipna.centsation.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.NotificationHandler;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeadlineReceiver extends BroadcastReceiver {

    private static final String TAG = "DeadlineReceiver";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        final boolean isBootOrReplaced = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);
        final boolean isDeadlineAlarm = AlarmSetter.ACTION_DEADLINE_ALARM.equals(action);

        if (!isBootOrReplaced && !isDeadlineAlarm) {
            Log.w(TAG, "Ignoring unexpected action: " + action);
            return;
        }

        final Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        final PendingResult pendingResult = goAsync();

        EXECUTOR.execute(() -> {
            try {
                if (isBootOrReplaced) {
                    Log.d(TAG, "Device booted or package replaced (" + action + "), rescheduling active deadlines");
                    rescheduleDeadlines(appContext);
                } else {
                    handleDeadlineTrigger(appContext, intent);
                }
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void rescheduleDeadlines(Context context) {
        try (SavingRepository savingRepository = new SavingRepository(context)) {
            // 1. Proactively purge any alarms for archived savings
            ArrayList<Saving> archivedSavings = new ArrayList<>(savingRepository.getSavings(Saving.IS_ARCHIVE));
            for (Saving archived : archivedSavings) {
                AlarmSetter.cancel(context, archived);
            }

            // 2. Only reschedule strictly active, incomplete goals with future deadlines
            ArrayList<Saving> activeSavings = new ArrayList<>(savingRepository.getSavings(Saving.NOT_ARCHIVE));
            long now = System.currentTimeMillis();
            for (Saving saving : activeSavings) {
                if (saving.getDeadline() != AlarmSetter.NO_ALARM
                        && saving.getDeadline() > now
                        && saving.getCurrentSaving() < saving.getGoal()) {
                    AlarmSetter.set(context, saving);
                } else {
                    AlarmSetter.cancel(context, saving);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while rescheduling deadlines", e);
        }
    }

    private void handleDeadlineTrigger(Context context, Intent intent) {
        String savingID = intent.getStringExtra(Database.COLUMN_SAVING_ID);
        if (savingID == null || savingID.isEmpty()) {
            Log.w(TAG, "Received deadline alarm with missing saving ID");
            return;
        }

        try (SavingRepository savingRepository = new SavingRepository(context)) {
            Saving saving = savingRepository.getSaving(savingID);
            if (saving == null) {
                Log.d(TAG, "Saving no longer exists, dropping notification for ID: " + savingID);
                return;
            }

            // Critical Guard: Never notify for archived savings
            if (saving.getIsArchived() == Saving.IS_ARCHIVE) {
                Log.d(TAG, "Saving is archived, dropping notification and ensuring alarm cancelled: " + saving.getName());
                AlarmSetter.cancel(context, saving);
                return;
            }

            // Critical Guard: Never notify if goal is already completed
            if (saving.getCurrentSaving() >= saving.getGoal()) {
                Log.d(TAG, "Goal already achieved for " + saving.getName() + ", dropping notification");
                AlarmSetter.cancel(context, saving);
                return;
            }

            // Critical Guard: Never notify if deadline was removed
            if (saving.getDeadline() == AlarmSetter.NO_ALARM) {
                Log.d(TAG, "Deadline was removed for " + saving.getName() + ", dropping notification");
                return;
            }

            // All checks passed -> Notify
            NotificationHandler.create(context, saving);
        } catch (Exception e) {
            Log.e(TAG, "Error handling deadline trigger", e);
        }
    }
}
