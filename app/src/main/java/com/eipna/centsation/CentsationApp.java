package com.eipna.centsation;

import android.app.Application;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.eipna.centsation.data.Theme;
import com.eipna.centsation.util.CrashHandler;
import com.eipna.centsation.util.DeadlineWorker;
import com.eipna.centsation.util.PreferenceUtil;

public class CentsationApp extends Application {

    private static final String TAG = "CentsationApp";

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.install(this);

        // Apply theme early in Application process lifecycle to prevent activity recreation flicker/crashes on cold start
        try {
            PreferenceUtil preferenceUtil = new PreferenceUtil(this);
            applyTheme(preferenceUtil.getTheme());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to apply initial theme", t);
        }

        try {
            DeadlineWorker.enqueue(this);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to enqueue deadline worker", t);
        }
    }

    public static void applyTheme(String theme) {
        if (theme == null) return;
        int targetMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (theme.equals(Theme.SYSTEM.VALUE)) {
            targetMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else if (theme.equals(Theme.BATTERY.VALUE)) {
            targetMode = AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY;
        } else if (theme.equals(Theme.LIGHT.VALUE)) {
            targetMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (theme.equals(Theme.DARK.VALUE)) {
            targetMode = AppCompatDelegate.MODE_NIGHT_YES;
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode);
        }
    }
}
