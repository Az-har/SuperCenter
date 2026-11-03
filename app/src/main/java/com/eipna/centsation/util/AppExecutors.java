package com.eipna.centsation.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Global executor pools for the whole application.
 * Designed for maximum battery efficiency:
 * - Bounded core pool with allowCoreThreadTimeOut(true) so idle threads terminate completely,
 *   allowing the CPU to enter low-power sleep states when idle.
 * - Reduced thread priority (NORM_PRIORITY - 1) to prevent background I/O from stuttering the UI.
 */
public class AppExecutors {

    private static volatile AppExecutors sInstance;

    private final ExecutorService diskIO;
    private final Executor mainThread;

    private AppExecutors(ExecutorService diskIO, Executor mainThread) {
        this.diskIO = diskIO;
        this.mainThread = mainThread;
    }

    public static AppExecutors getInstance() {
        if (sInstance == null) {
            synchronized (AppExecutors.class) {
                if (sInstance == null) {
                    int coreThreads = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 2));
                    int maxThreads = 4;
                    ThreadPoolExecutor executor = new ThreadPoolExecutor(
                            coreThreads,
                            maxThreads,
                            60L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(),
                            r -> {
                                Thread t = new Thread(r, "app-disk-io");
                                t.setPriority(Thread.NORM_PRIORITY - 1);
                                return t;
                            }
                    );
                    executor.allowCoreThreadTimeOut(true);

                    sInstance = new AppExecutors(executor, new MainThreadExecutor());
                }
            }
        }
        return sInstance;
    }

    public ExecutorService diskIO() {
        return diskIO;
    }

    public Executor mainThread() {
        return mainThread;
    }

    private static class MainThreadExecutor implements Executor {
        private Handler mainThreadHandler;

        private Handler getHandler() {
            if (mainThreadHandler == null) {
                try {
                    Looper looper = Looper.getMainLooper();
                    if (looper != null) {
                        mainThreadHandler = new Handler(looper);
                    }
                } catch (Throwable ignored) {
                }
            }
            return mainThreadHandler;
        }

        @Override
        public void execute(@NonNull Runnable command) {
            Handler handler = getHandler();
            if (handler != null) {
                handler.post(command);
            } else {
                command.run();
            }
        }
    }
}
