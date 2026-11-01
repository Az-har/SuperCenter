package com.eipna.centsation.util;

import android.content.Context;
import android.os.Build;
import android.os.CombinedVibration;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

public final class HapticUtil {

    private HapticUtil() {}

    public static void performTap(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    public static void performSuccess(View view) {
        if (view != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        }
    }

    public static void performCelebration(Context context) {
        if (context == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    long[] timings = {0, 80, 60, 120, 80, 200};
                    int[] amplitudes = {0, 180, 0, 255, 0, 220};
                    vm.vibrate(CombinedVibration.createParallel(
                            VibrationEffect.createWaveform(timings, amplitudes, -1)
                    ));
                    return;
                }
            }

            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    long[] timings = {0, 80, 60, 120, 80, 200};
                    int[] amplitudes = {0, 180, 0, 255, 0, 220};
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 80, 60, 120, 80, 200}, -1);
                }
            }
        } catch (Exception ignored) {}
    }
}
