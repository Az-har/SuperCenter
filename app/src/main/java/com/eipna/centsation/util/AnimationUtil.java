package com.eipna.centsation.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.icu.text.NumberFormat;
import android.os.Build;
import android.os.PowerManager;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

public final class AnimationUtil {

    private static final FastOutSlowInInterpolator FAST_OUT_SLOW_IN = new FastOutSlowInInterpolator();
    private static final DecelerateInterpolator DECELERATE = new DecelerateInterpolator();
    private static final OvershootInterpolator OVERSHOOT = new OvershootInterpolator(1.2f);

    private AnimationUtil() {
    }

    /**
     * Checks if device is in Battery Saver mode to dynamically reduce continuous frame interpolations.
     */
    public static boolean isPowerSaveMode(Context context) {
        if (context == null) return false;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isPowerSaveMode();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Subtle tactile tick on button/card tap.
     */
    public static void performHapticFeedback(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    /**
     * Confirmation haptic feedback when a transaction or important action completes.
     */
    public static void performConfirmFeedback(View view) {
        if (view != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        }
    }

    /**
     * Smoothly scales down view on touch down and springs back on touch up/cancel.
     * Integrates subtle haptic feedback on touch down for tactile satisfaction.
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void applySpringPressEffect(View view) {
        if (view == null) return;
        final boolean powerSave = isPowerSaveMode(view.getContext());
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    performHapticFeedback(v);
                    if (!powerSave) {
                        v.animate()
                                .scaleX(0.965f)
                                .scaleY(0.965f)
                                .setDuration(110)
                                .setInterpolator(DECELERATE)
                                .start();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!powerSave) {
                        v.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(180)
                                .setInterpolator(OVERSHOOT)
                                .start();
                    }
                    break;
            }
            return false;
        });
    }

    /**
     * Smoothly animates a numeric value change inside a TextView with currency symbol.
     */
    public static void animateCurrencyValue(TextView textView, double from, double to, String currencySymbol, long duration) {
        if (textView == null) return;
        if (Math.abs(from - to) < 0.001 || isPowerSaveMode(textView.getContext())) {
            textView.setText(String.format("%s%s", currencySymbol, NumberFormat.getInstance().format(to)));
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat((float) from, (float) to);
        animator.setDuration(duration > 0 ? duration : 500);
        animator.setInterpolator(FAST_OUT_SLOW_IN);
        animator.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            textView.setText(String.format("%s%s", currencySymbol, NumberFormat.getInstance().format(val)));
        });
        animator.start();
    }

    /**
     * Smoothly animates a progress bar or progress indicator to the target value.
     */
    public static void animateProgress(ProgressBar progressBar, int targetProgress, long duration) {
        if (progressBar == null) return;
        int currentProgress = progressBar.getProgress();
        if (currentProgress == targetProgress) return;
        if (isPowerSaveMode(progressBar.getContext())) {
            progressBar.setProgress(targetProgress);
            return;
        }

        ObjectAnimator animator = ObjectAnimator.ofInt(progressBar, "progress", currentProgress, targetProgress);
        animator.setDuration(duration > 0 ? duration : 500);
        animator.setInterpolator(FAST_OUT_SLOW_IN);
        animator.start();
    }

    /**
     * Crossfades smoothly between two views (e.g. empty state <-> list content).
     */
    public static void crossfade(View hideView, View showView, int duration) {
        if (showView != null) {
            showView.setAlpha(0f);
            showView.setVisibility(View.VISIBLE);
            showView.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .setListener(null);
        }

        if (hideView != null && hideView.getVisibility() == View.VISIBLE) {
            hideView.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            hideView.setVisibility(View.GONE);
                        }
                    });
        }
    }
}
