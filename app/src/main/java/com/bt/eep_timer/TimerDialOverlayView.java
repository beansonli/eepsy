package com.bt.eep_timer;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

public class TimerDialOverlayView extends View {
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable bedIcon;
    private final Drawable bellIcon;
    private float progressFraction = 1f;

    public TimerDialOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        bedIcon = ContextCompat.getDrawable(context, R.drawable.ic_bed);
        bellIcon = ContextCompat.getDrawable(context, R.drawable.ic_bell);
        setWillNotDraw(false);
    }

    public void setProgressFraction(float progressFraction) {
        this.progressFraction = Math.max(0f, Math.min(1f, progressFraction));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float ringRadius = Math.min(getWidth(), getHeight()) * 0.416f;
        drawTicks(canvas, cx, cy, ringRadius * 0.76f);
        drawBadge(canvas, cx, cy - ringRadius, true, bedIcon);

        double angle = Math.toRadians(-90 + (360 * progressFraction));
        float bellX = cx + (float) Math.cos(angle) * ringRadius;
        float bellY = cy + (float) Math.sin(angle) * ringRadius;
        drawBadge(canvas, bellX, bellY, false, bellIcon);
    }

    private void drawTicks(Canvas canvas, float cx, float cy, float radius) {
        int tickColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant);
        tickPaint.setColor(applyAlpha(tickColor, 0.64f));
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians((i * 6) - 90);
            boolean major = i % 15 == 0;
            boolean medium = i % 5 == 0;
            float outer = radius;
            float inner = radius - dp(major ? 14 : medium ? 9 : 5);
            tickPaint.setStrokeWidth(dp(major ? 2.1f : medium ? 1.4f : 0.9f));

            float sx = cx + (float) Math.cos(angle) * inner;
            float sy = cy + (float) Math.sin(angle) * inner;
            float ex = cx + (float) Math.cos(angle) * outer;
            float ey = cy + (float) Math.sin(angle) * outer;
            canvas.drawLine(sx, sy, ex, ey, tickPaint);
        }
    }

    private void drawBadge(Canvas canvas, float cx, float cy, boolean quiet, Drawable icon) {
        float radius = dp(20);
        int primary = resolveThemeColor(com.google.android.material.R.attr.colorPrimary);
        int surface = resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainer);
        int onPrimary = resolveThemeColor(com.google.android.material.R.attr.colorOnPrimary);

        badgeFillPaint.setStyle(Paint.Style.FILL);
        badgeFillPaint.setColor(quiet ? surface : primary);
        badgeStrokePaint.setStyle(Paint.Style.STROKE);
        badgeStrokePaint.setStrokeWidth(dp(1.5f));
        badgeStrokePaint.setColor(primary);

        canvas.drawCircle(cx, cy, radius, badgeFillPaint);
        canvas.drawCircle(cx, cy, radius, badgeStrokePaint);

        if (icon == null) return;
        Drawable wrapped = DrawableCompat.wrap(icon.mutate());
        DrawableCompat.setTintList(wrapped, ColorStateList.valueOf(quiet ? primary : onPrimary));
        int iconSize = (int) dp(20);
        int left = Math.round(cx - iconSize / 2f);
        int top = Math.round(cy - iconSize / 2f);
        wrapped.setBounds(left, top, left + iconSize, top + iconSize);
        wrapped.draw(canvas);
    }

    private int resolveThemeColor(int attr) {
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    private int applyAlpha(int color, float alpha) {
        return Color.argb(Math.round(Color.alpha(color) * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}