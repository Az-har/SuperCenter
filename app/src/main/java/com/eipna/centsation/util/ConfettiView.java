package com.eipna.centsation.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ConfettiView extends View {

    private static final int PARTICLE_COUNT = 75;
    private static final int[] COLORS = {
            Color.parseColor("#415F91"), // Primary Blue
            Color.parseColor("#FFB74D"), // Gold
            Color.parseColor("#4CAF50"), // Green
            Color.parseColor("#E91E63"), // Pink
            Color.parseColor("#9C27B0"), // Purple
            Color.parseColor("#00BCD4"), // Cyan
            Color.parseColor("#FF5722")  // Orange
    };

    private static class Particle {
        float x, y;
        float vx, vy;
        float size;
        float rotation;
        float rotSpeed;
        int color;
        boolean isCircle;
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private ValueAnimator animator;

    public ConfettiView(Context context) {
        super(context);
        init();
    }

    public ConfettiView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ConfettiView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setVisibility(GONE);
        paint.setStyle(Paint.Style.FILL);
    }

    public void startConfetti() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        particles.clear();
        int width = getWidth();
        int height = getHeight();
        if (width <= 0) width = 1080;
        if (height <= 0) height = 1920;

        float originX = width / 2.0f;
        float originY = height * 0.25f;

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            Particle p = new Particle();
            p.x = originX + (random.nextFloat() - 0.5f) * width * 0.4f;
            p.y = originY + (random.nextFloat() - 0.5f) * 120f;
            float angle = (float) (random.nextDouble() * 2 * Math.PI);
            float speed = 12f + random.nextFloat() * 22f;
            p.vx = (float) Math.cos(angle) * speed;
            p.vy = -Math.abs((float) Math.sin(angle) * speed) - 5f;
            p.size = 14f + random.nextFloat() * 18f;
            p.rotation = random.nextFloat() * 360f;
            p.rotSpeed = (random.nextFloat() - 0.5f) * 20f;
            p.color = COLORS[random.nextInt(COLORS.length)];
            p.isCircle = random.nextBoolean();
            particles.add(p);
        }

        setVisibility(VISIBLE);

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2400);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float gravity = 1.1f;
            for (Particle p : particles) {
                p.x += p.vx;
                p.y += p.vy;
                p.vy += gravity;
                p.vx *= 0.98f;
                p.rotation += p.rotSpeed;
            }
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                particles.clear();
                setVisibility(GONE);
            }
        });
        animator.start();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (particles.isEmpty()) return;

        for (Particle p : particles) {
            paint.setColor(p.color);
            canvas.save();
            canvas.translate(p.x, p.y);
            canvas.rotate(p.rotation);
            if (p.isCircle) {
                canvas.drawCircle(0, 0, p.size / 2f, paint);
            } else {
                canvas.drawRect(-p.size / 2f, -p.size / 3f, p.size / 2f, p.size / 3f, paint);
            }
            canvas.restore();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }
}
