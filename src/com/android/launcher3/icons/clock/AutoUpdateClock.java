package com.android.launcher3.icons.clock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.FastBitmapDrawableDelegate;
import com.android.launcher3.icons.FastBitmapDrawableDelegate.DelegateFactory;
import com.android.launcher3.icons.IconShape;
import com.android.launcher3.model.data.ItemInfoWithIcon;

import java.util.TimeZone;

// AutoUpdateClock wraps FastBitmapDrawable (since FastBitmapDrawable is final)
// and implements Runnable for scheduling clock updates
class AutoUpdateClock implements Runnable {
    private final FastBitmapDrawable mDrawable;
    private ClockLayers mLayers;
    private ClockDelegate mDelegate;

    AutoUpdateClock(ItemInfoWithIcon info, ClockLayers layers, Context context) {
        mLayers = layers;
        // Create FastBitmapDrawable with clock delegate factory
        // Use IconShape.EMPTY as the base, delegate will handle custom drawing
        ClockDelegateFactory factory = new ClockDelegateFactory(layers.clone(), this);
        mDrawable = new FastBitmapDrawable(
                info.bitmap,
                IconShape.EMPTY,
                factory
        );
        mDrawable.setDisabled(info.isDisabled());
    }
    
    // Return the wrapped FastBitmapDrawable (used when FastBitmapDrawable is expected)
    public FastBitmapDrawable getDrawable() {
        return mDrawable;
    }
    
    // Delegate common Drawable methods
    public void setBounds(Rect bounds) {
        mDrawable.setBounds(bounds);
    }
    
    public Rect getBounds() {
        return mDrawable.getBounds();
    }
    
    public void invalidateSelf() {
        mDrawable.invalidateSelf();
    }
    
    public void scheduleSelf(Runnable what, long when) {
        mDrawable.scheduleSelf(what, when);
    }
    
    public void unscheduleSelf(Runnable what) {
        mDrawable.unscheduleSelf(what);
    }
    
    public void setDisabled(boolean disabled) {
        mDrawable.setDisabled(disabled);
    }

    private void rescheduleUpdate() {
        unscheduleSelf(this);
        long now = SystemClock.uptimeMillis();
        boolean hasSeconds = (mLayers != null && mLayers.mSecondIndex != -1);
        long interval = hasSeconds ? 1000L : 60_000L;
        long next = now - (now % interval) + interval;
        scheduleSelf(this, next);
    }

    // Used only by Google Clock
    void updateLayers(ClockLayers layers) {
        if (layers != null) {
            ClockLayers newLayers = layers.clone();
            if (newLayers != null) {
                mLayers = newLayers;
                if (mDelegate != null) {
                    mDelegate.updateLayers(newLayers);
                }
                if (newLayers.mDrawable != null) {
                    newLayers.mDrawable.setBounds(getBounds());
                }
            }
        }
        invalidateSelf();
    }

    void setTimeZone(TimeZone timeZone) {
        if (mLayers != null) {
            mLayers.setTimeZone(timeZone);
            invalidateSelf();
        }
    }

    @Override
    public void run() {
        if (mLayers != null && mLayers.updateAngles()) {
            invalidateSelf();
        }
        rescheduleUpdate();
    }

    private static class ClockDelegate implements FastBitmapDrawableDelegate {
        private ClockLayers mLayers;
        private final AutoUpdateClock mHost;

        ClockDelegate(ClockLayers layers, AutoUpdateClock host) {
            mLayers = layers;
            mHost = host;
        }

        void updateLayers(ClockLayers layers) {
            mLayers = layers;
        }

        @Override
        public void drawContent(@NonNull BitmapInfo info, @NonNull IconShape shape,
                @NonNull Canvas canvas, @NonNull Rect bounds, @NonNull Paint paint) {
            if (mLayers != null && mLayers.mDrawable != null) {
                canvas.drawBitmap(mLayers.bitmap, null, bounds, paint);
                mLayers.updateAngles();
                canvas.scale(mLayers.scale, mLayers.scale,
                        bounds.exactCenterX() + mLayers.offset,
                        bounds.exactCenterY() + mLayers.offset);
                canvas.clipPath(mLayers.mDrawable.getIconMask());
                mLayers.mDrawable.getForeground().draw(canvas);
            } else {
                // Draw default icon - delegate to parent or draw bitmap directly
                canvas.drawBitmap(info.icon, null, bounds, paint);
            }
        }

        @Override
        public void onBoundsChange(@NonNull Rect bounds) {
            if (mLayers != null && mLayers.mDrawable != null) {
                mLayers.mDrawable.setBounds(bounds);
            }
        }
    }

    private static class ClockDelegateFactory implements DelegateFactory {
        private final ClockLayers mLayers;
        private final AutoUpdateClock mClock;

        ClockDelegateFactory(ClockLayers layers, AutoUpdateClock clock) {
            mLayers = layers;
            mClock = clock;
        }

        @NonNull
        @Override
        public FastBitmapDrawableDelegate newDelegate(@NonNull BitmapInfo bitmapInfo,
                @NonNull IconShape iconShape, @NonNull Paint paint,
                @NonNull FastBitmapDrawable host) {
            ClockDelegate delegate = new ClockDelegate(mLayers, mClock);
            mClock.mDelegate = delegate;
            return delegate;
        }
    }
}
