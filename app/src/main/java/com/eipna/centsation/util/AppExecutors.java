package com.eipna.centsation.util;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Global executor pools for the whole application.
 * Designed for maximum battery efficiency:
 * - Bounded core pool with allowCoreThreadTimeOut(true) so idle threads terminate completely,
 *   allowing the CPU to enter low-power sleep states when idle.
 * - Reduced thread priority (NORM_PRIORITY - 1) to prevent background I/O from stuttering the UI.
 * - Non-closing wrapper on diskIO prevents transient UI components (Activities/ViewModels/Dialogs)
 *   from terminating the shared application-wide pool when they call shutdown().
 * - Self-healing getInstance(): automatically recreates the pool if it ever becomes terminated.
 */
public class AppExecutors {

    private static volatile AppExecutors sInstance;

    private final ThreadPoolExecutor rawDiskIO;
    private final ExecutorService diskIO;

    private AppExecutors(ThreadPoolExecutor rawDiskIO) {
        this.rawDiskIO = rawDiskIO;
        this.diskIO = new NonClosingExecutorService(rawDiskIO);
    }

    public static AppExecutors getInstance() {
        if (sInstance == null || sInstance.isTerminatedOrShutdown()) {
            synchronized (AppExecutors.class) {
                if (sInstance == null || sInstance.isTerminatedOrShutdown()) {
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

                    sInstance = new AppExecutors(executor);
                }
            }
        }
        return sInstance;
    }

    private boolean isTerminatedOrShutdown() {
        return rawDiskIO == null || rawDiskIO.isShutdown() || rawDiskIO.isTerminated();
    }

    public ExecutorService diskIO() {
        return diskIO;
    }

    /**
     * An ExecutorService wrapper that forwards all task execution to an underlying
     * ThreadPoolExecutor, but ignores shutdown() and shutdownNow() requests.
     * This protects the application-wide shared singleton from premature termination.
     */
    private static class NonClosingExecutorService implements ExecutorService {
        private final ThreadPoolExecutor delegate;

        NonClosingExecutorService(ThreadPoolExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(@NonNull Runnable command) {
            delegate.execute(command);
        }

        @Override
        public void shutdown() {
            // No-op to protect shared application-wide singleton pool
        }

        @NonNull
        @Override
        public List<Runnable> shutdownNow() {
            // No-op to protect shared application-wide singleton pool
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @NonNull
        @Override
        public <T> Future<T> submit(@NonNull Callable<T> task) {
            return delegate.submit(task);
        }

        @NonNull
        @Override
        public <T> Future<T> submit(@NonNull Runnable task, T result) {
            return delegate.submit(task, result);
        }

        @NonNull
        @Override
        public Future<?> submit(@NonNull Runnable task) {
            return delegate.submit(task);
        }

        @NonNull
        @Override
        public <T> List<Future<T>> invokeAll(@NonNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return delegate.invokeAll(tasks);
        }

        @NonNull
        @Override
        public <T> List<Future<T>> invokeAll(@NonNull Collection<? extends Callable<T>> tasks, long timeout, @NonNull TimeUnit unit) throws InterruptedException {
            return delegate.invokeAll(tasks, timeout, unit);
        }

        @NonNull
        @Override
        public <T> T invokeAny(@NonNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            return delegate.invokeAny(tasks);
        }

        @Override
        public <T> T invokeAny(@NonNull Collection<? extends Callable<T>> tasks, long timeout, @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.invokeAny(tasks, timeout, unit);
        }
    }
}
