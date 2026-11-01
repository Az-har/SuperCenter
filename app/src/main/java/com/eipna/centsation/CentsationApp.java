package com.eipna.centsation;

import android.app.Application;

import com.eipna.centsation.util.CrashHandler;
import com.eipna.centsation.util.DeadlineWorker;

public class CentsationApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.install(this);
        DeadlineWorker.enqueue(this);
    }
}
