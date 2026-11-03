package com.eipna.centsation.battery;

import android.content.Context;

import com.eipna.centsation.util.AnimationUtil;
import com.eipna.centsation.util.AppExecutors;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BatteryOptimizationTest {

    @Test
    public void testAppExecutorsDiskIOSubmission() throws InterruptedException {
        AppExecutors executors = AppExecutors.getInstance();
        Assert.assertNotNull(executors);
        Assert.assertNotNull(executors.diskIO());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ran = new AtomicBoolean(false);

        executors.diskIO().execute(() -> {
            ran.set(true);
            latch.countDown();
        });

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        Assert.assertTrue("Task should execute within 2 seconds", completed);
        Assert.assertTrue("Runnable should have executed", ran.get());
    }

    @Test
    public void testAnimationUtilPowerSaveModeNullContextHandledSafely() {
        // Must never throw an exception on null context
        boolean isPowerSave = AnimationUtil.isPowerSaveMode(null);
        Assert.assertFalse("Null context should return false without throwing", isPowerSave);
    }
}
