package com.eipna.centsation.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

public class SwipeActionCallback extends ItemTouchHelper.SimpleCallback {

    public interface SwipeListener {
        void onSwipeRight(int position);
        void onSwipeLeft(int position);
    }

    private final SwipeListener listener;
    private final Drawable rightIcon;
    private final Drawable leftIcon;
    private final int rightBgColor;
    private final int leftBgColor;
    private final float cornerRadius;

    public SwipeActionCallback(Context context,
                               int rightIconRes,
                               int leftIconRes,
                               int rightBgColorAttr,
                               int leftBgColorAttr,
                               int rightIconColorAttr,
                               int leftIconColorAttr,
                               SwipeListener listener) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.listener = listener;

        Drawable rIcon = rightIconRes != 0 ? ContextCompat.getDrawable(context, rightIconRes) : null;
        if (rIcon != null) {
            rIcon = DrawableCompat.wrap(rIcon.mutate());
            int tint = MaterialColors.getColor(context, rightIconColorAttr, Color.WHITE);
            DrawableCompat.setTint(rIcon, tint);
        }
        this.rightIcon = rIcon;

        Drawable lIcon = leftIconRes != 0 ? ContextCompat.getDrawable(context, leftIconRes) : null;
        if (lIcon != null) {
            lIcon = DrawableCompat.wrap(lIcon.mutate());
            int tint = MaterialColors.getColor(context, leftIconColorAttr, Color.WHITE);
            DrawableCompat.setTint(lIcon, tint);
        }
        this.leftIcon = lIcon;

        this.rightBgColor = MaterialColors.getColor(context, rightBgColorAttr, Color.LTGRAY);
        this.leftBgColor = MaterialColors.getColor(context, leftBgColorAttr, Color.GRAY);
        this.cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 22f, context.getResources().getDisplayMetrics()
        );
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;

        if (direction == ItemTouchHelper.RIGHT) {
            listener.onSwipeRight(position);
        } else if (direction == ItemTouchHelper.LEFT) {
            listener.onSwipeLeft(position);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c,
                            @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY,
                            int actionState,
                            boolean isCurrentlyActive) {
        View itemView = viewHolder.itemView;
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            if (dX > 0) { // Swiping right
                paint.setColor(rightBgColor);
                RectF background = new RectF(
                        itemView.getLeft(),
                        itemView.getTop(),
                        itemView.getLeft() + dX,
                        itemView.getBottom()
                );
                c.drawRoundRect(background, cornerRadius, cornerRadius, paint);

                if (rightIcon != null) {
                    int iconMargin = (itemView.getHeight() - rightIcon.getIntrinsicHeight()) / 2;
                    int iconTop = itemView.getTop() + iconMargin;
                    int iconBottom = iconTop + rightIcon.getIntrinsicHeight();
                    int iconLeft = itemView.getLeft() + iconMargin;
                    int iconRight = iconLeft + rightIcon.getIntrinsicWidth();

                    rightIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                    int alpha = Math.min(255, (int) (Math.abs(dX) / (float) (itemView.getWidth() * 0.3f) * 255));
                    rightIcon.setAlpha(alpha);
                    rightIcon.draw(c);
                }
            } else if (dX < 0) { // Swiping left
                paint.setColor(leftBgColor);
                RectF background = new RectF(
                        itemView.getRight() + dX,
                        itemView.getTop(),
                        itemView.getRight(),
                        itemView.getBottom()
                );
                c.drawRoundRect(background, cornerRadius, cornerRadius, paint);

                if (leftIcon != null) {
                    int iconMargin = (itemView.getHeight() - leftIcon.getIntrinsicHeight()) / 2;
                    int iconTop = itemView.getTop() + iconMargin;
                    int iconBottom = iconTop + leftIcon.getIntrinsicHeight();
                    int iconRight = itemView.getRight() - iconMargin;
                    int iconLeft = iconRight - leftIcon.getIntrinsicWidth();

                    leftIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                    int alpha = Math.min(255, (int) (Math.abs(dX) / (float) (itemView.getWidth() * 0.3f) * 255));
                    leftIcon.setAlpha(alpha);
                    leftIcon.draw(c);
                }
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}
