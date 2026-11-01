package com.eipna.centsation.util;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingRepository;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DeadlineWorker extends Worker {

    private static final String TAG = "DeadlineWorker";
    public static final String UNIQUE_WORK_NAME = "centsation_deadline_worker";

    public DeadlineWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        Log.d(TAG, "Executing periodic background check for upcoming savings deadlines");

        try (SavingRepository repo = new SavingRepository(context)) {
            List<Saving> activeSavings = repo.getSavings(Saving.NOT_ARCHIVE);
            if (activeSavings == null || activeSavings.isEmpty()) {
                return Result.success();
            }

            Calendar calendarToday = Calendar.getInstance();
            int currentYear = calendarToday.get(Calendar.YEAR);
            int currentDayOfYear = calendarToday.get(Calendar.DAY_OF_YEAR);

            for (Saving saving : activeSavings) {
                if (saving.getDeadline() == AlarmSetter.NO_ALARM) continue;
                if (saving.getCurrentSaving() >= saving.getGoal()) continue;

                // Ensure AlarmManager has active alarms scheduled as primary trigger
                AlarmSetter.set(context, saving);

                // Fail-safe: if app was not used for a long time and alarms were killed by OS,
                // check if deadline is today or tomorrow and notify
                Calendar deadlineCal = Calendar.getInstance();
                deadlineCal.setTimeInMillis(saving.getDeadline());

                int deadlineYear = deadlineCal.get(Calendar.YEAR);
                int deadlineDayOfYear = deadlineCal.get(Calendar.DAY_OF_YEAR);

                if (deadlineYear == currentYear && deadlineDayOfYear == currentDayOfYear) {
                    Log.d(TAG, "Fail-safe: Goal is due today, posting notification for " + saving.getName());
                    NotificationHandler.create(context, saving, false);
                } else if (deadlineYear == currentYear && deadlineDayOfYear == currentDayOfYear + 1) {
                    Log.d(TAG, "Fail-safe: Goal is due tomorrow, posting advance alert for " + saving.getName());
                    NotificationHandler.create(context, saving, true);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error executing deadline worker", e);
            return Result.retry();
        }

        return Result.success();
    }

    public static void enqueue(Context context) {
        if (context == null) return;
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                DeadlineWorker.class,
                24, TimeUnit.HOURS,
                4, TimeUnit.HOURS
        ).setConstraints(new Constraints.Builder().build()).build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
        );
    }
}
