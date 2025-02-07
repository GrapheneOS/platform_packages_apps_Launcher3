/*
 * Copyright (C) 2022 Project Kaleidoscope
 *               2024-2025 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quickstep.views;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.NavigationMode.TWO_BUTTONS;
import static com.android.launcher3.util.NavigationMode.THREE_BUTTONS;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.graphics.Rect;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout.LayoutParams;
import android.widget.TextView;

import com.android.internal.util.MemInfoReader;

import com.android.settingslib.utils.ThreadUtils;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Insettable;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.NavigationMode;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.Runnable;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public class MemInfoView extends TextView implements Insettable {

    private static final int UNIT_CONVERT_THRESHOLD = 1024; /* MiB */
    private static final BigDecimal GB2MB = new BigDecimal(1024);

    private static final int ALPHA_STATE_CTRL = 0;
    public static final int ALPHA_FS_PROGRESS = 1;

    private static final String TAG = "MemInfoView";

    public static final FloatProperty<MemInfoView> STATE_CTRL_ALPHA =
            new FloatProperty<MemInfoView>("state control alpha") {
                @Override
                public Float get(MemInfoView view) {
                    return view.getAlpha(ALPHA_STATE_CTRL);
                }

                @Override
                public void setValue(MemInfoView view, float v) {
                    view.setAlpha(ALPHA_STATE_CTRL, v);
                }
            };

    private final Rect mInsets = new Rect();

    private DeviceProfile mDp;
    private MultiValueAlpha mAlpha;
    private ActivityManager mActivityManager;

    private Handler mHandler;

    private String mMemInfoText;

    private ActivityManager.MemoryInfo memInfo;
    private MemInfoReader mMemInfoReader;

    private Context mContext;

    String mTotalResult;

    public MemInfoView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mAlpha = new MultiValueAlpha(this, 2);
        mAlpha.setUpdateVisibility(true);
        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        memInfo = new ActivityManager.MemoryInfo();
        mMemInfoReader = new MemInfoReader();
        mTotalResult = formatTotalMemory();

        mMemInfoText = context.getResources().getString(R.string.meminfo_text);
        setListener(context);
    }

    @Override
    public void setVisibility(int visibility) {
        if (visibility == VISIBLE) {
            boolean showMeminfo = LauncherPrefs.RECENTS_MEMINFO.get(getContext());
            if (!showMeminfo) visibility = GONE;
        }

        super.setVisibility(visibility);

        if (visibility == VISIBLE) {
            startMemoryMonitoring();
        } else {
            stopMemoryMonitoring();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateVerticalMargin(DisplayController.getNavigationMode(getContext()));
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        updateVerticalMargin(DisplayController.getNavigationMode(getContext()));
        updatePadding();
    }

    private void updatePadding() {
        setPadding(mInsets.left, 0, mInsets.right, 0);
    }

    public void setDp(DeviceProfile dp) {
        mDp = dp;
    }

    public void setAlpha(int alphaType, float alpha) {
        mAlpha.get(alphaType).setValue(alpha);
    }

    public float getAlpha(int alphaType) {
        return mAlpha.get(alphaType).getValue();
    }

    public void updateVerticalMargin(NavigationMode mode) {
        LayoutParams lp = (LayoutParams) getLayoutParams();
        int bottomMargin;

        if (!mDp.isTaskbarPresent && (mode == THREE_BUTTONS || mode == TWO_BUTTONS)) {
            bottomMargin = mDp.memInfoMarginThreeButtonPx;
        } else if (mDp.isTaskbarPresent && (mode == THREE_BUTTONS || mode == TWO_BUTTONS)) {
            bottomMargin = mDp.memInfoMarginTaskbarPx;
        } else if (mDp.isTaskbarPresent && !(mode == THREE_BUTTONS || mode == TWO_BUTTONS)) {
            bottomMargin = mDp.memInfoMarginTransientTaskbarPx;
        } else {
            bottomMargin = mDp.memInfoMarginGesturePx;
        }

        lp.setMargins(lp.leftMargin, lp.topMargin, lp.rightMargin, bottomMargin);
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
    }

    private String formatTotalMemory() {
        mActivityManager.getMemoryInfo(memInfo);
        double totalMemoryGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0);
        int roundedMemoryGB = roundToNearestKnownRamSize(totalMemoryGB);
        return roundedMemoryGB + " GB";
    }

    private int roundToNearestKnownRamSize(double memoryGB) {
        int[] knownSizes = {1, 2, 3, 4, 6, 8, 10, 12, 16, 32, 48, 64};
        if (memoryGB <= 0) return 1;
        for (int size : knownSizes) {
            if (memoryGB <= size) return size;
        }
        return knownSizes[knownSizes.length - 1];
    }

    private long getZramSize() {
        if (!LauncherPrefs.RECENTS_MEMINFO_ZRAM.get(getContext()))
            return 0;

        long zramSize = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader("/sys/block/zram0/disksize"))) {
            zramSize = Long.parseLong(reader.readLine().trim());
        } catch (IOException | NumberFormatException e) {
            Log.w(TAG, "Primary ZRAM location failed, trying fallback", e);
        }

        if (zramSize == 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/swaps"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("zram0")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length > 2) {
                            zramSize = Long.parseLong(parts[2]) * 1024; // KB to bytes
                        }
                        break;
                    }
                }
            } catch (IOException | NumberFormatException e) {
                Log.w(TAG, "Fallback ZRAM location not available", e);
            }
        }

        return zramSize;
    }

    public void setListener(Context context) {
        setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.setClassName("com.android.settings", "com.android.settings.Settings$DevRunningServicesActivity");
            context.startActivity(intent);
        });
    }

    private long getTotalBackgroundMemory() {
        long totalBackgroundMemory = 0;
        List<ActivityManager.RunningAppProcessInfo> runningProcesses = mActivityManager.getRunningAppProcesses();
        if (runningProcesses != null) {
            int[] pids = new int[runningProcesses.size()];
            for (int i = 0; i < runningProcesses.size(); i++) {
                pids[i] = runningProcesses.get(i).pid;
            }
            Debug.MemoryInfo[] memoryInfos = mActivityManager.getProcessMemoryInfo(pids);
            for (int i = 0; i < memoryInfos.length; i++) {
                ActivityManager.RunningAppProcessInfo info = runningProcesses.get(i);
                if (info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                    long memorySize = memoryInfos[i].getTotalPss() * 1024L;
                    totalBackgroundMemory += memorySize;
                }
            }
        }
        return totalBackgroundMemory;
    }

    private void startMemoryMonitoring() {
        stopMemoryMonitoring();
        if (mHandler == null) {
            mHandler = MODEL_EXECUTOR.getHandler();
        }
        mHandler.post(mWorker);
    }

    private void stopMemoryMonitoring() {
        synchronized (this) {
            if (mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
                mHandler = null;
            }
        }
    }

    private static class MemoryWorker implements Runnable {
        private final WeakReference<MemInfoView> viewRef;

        MemoryWorker(MemInfoView view) {
            viewRef = new WeakReference<>(view);
        }

        @Override
        public void run() {
            MemInfoView view = viewRef.get();
            if (view == null || view.mHandler == null) {
                return;
            }

            view.mMemInfoReader.readMemInfo();
            long freeMemory = view.mMemInfoReader.getFreeSize() +
                              view.mMemInfoReader.getCachedSize() +
                              view.getTotalBackgroundMemory();
            long zramSize = view.getZramSize();

            String availResult = Formatter.formatShortFileSize(view.mContext, freeMemory);
            String text;
            if (zramSize > 0) {
                String zramResult = Formatter.formatShortFileSize(view.mContext, zramSize);
                text = String.format(Locale.getDefault(), view.mMemInfoText, availResult, view.mTotalResult + " + " + zramResult);
            } else {
                text = String.format(Locale.getDefault(), view.mMemInfoText, availResult, view.mTotalResult);
            }

            ThreadUtils.postOnMainThread(() -> view.setText(text));

            if (view.mHandler != null) {
                view.mHandler.postDelayed(this, 1000);
            }
        }
    }

    private final MemoryWorker mWorker = new MemoryWorker(this);

    @Override
    protected void onDetachedFromWindow() {
        stopMemoryMonitoring();
        super.onDetachedFromWindow();
    }
}
