/*
 * Copyright (C) 2020-2025 crDroid Android Project
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
package com.android.launcher3.quickspace;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.icu.text.DateFormat;
import android.icu.text.DisplayContext;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.core.content.ContextCompat;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;

import java.util.Calendar;
import java.util.concurrent.ThreadLocalRandom;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.android.launcher3.util.MSMHProxy;

public class QuickEventsController {

    private final Context mContext;
    private final Resources mResources;

    private String mEventTitle;
    private String mEventTitleSub;
    private String mGreetings;
    private String mClockExt;
    private OnClickListener mEventTitleSubAction = null;
    private Drawable mEventSubIcon = null;

    private boolean mIsQuickEvent = false;
    private boolean mRegistered = false;

    private final Map<Integer, String[]> mCachedPSAMap = new HashMap<>();

    // PSA + Personality
    private String[] mPSAStr;
    private BroadcastReceiver mPSAListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            psonalityEvent();
        }
    };

    // NowPlaying
    private boolean mEventNowPlaying = false;
    private String mNowPlayingTitle;
    private String mNowPlayingArtist;
    private boolean mPlayingActive = false;

    public QuickEventsController(Context context) {
        mContext = context;
        mResources = context.getResources();
        initQuickEvents();
    }

    public void initQuickEvents() {
        registerPSAListener();
        updateQuickEvents();
    }

    private void registerPSAListener() {
        if (mRegistered) return;
        mRegistered = true;
        IntentFilter psonalityIntent = new IntentFilter();
        psonalityIntent.addAction(Intent.ACTION_TIME_TICK);
        psonalityIntent.addAction(Intent.ACTION_TIME_CHANGED);
        psonalityIntent.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        mContext.registerReceiver(mPSAListener, psonalityIntent, Context.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterPSAListener() {
        if (!mRegistered) return;
        mRegistered = false;
        mContext.unregisterReceiver(mPSAListener);
    }

    public void updateQuickEvents() {
        if (!mRegistered) return;
        nowPlayingEvent();
        initNowPlayingEvent();
        psonalityEvent();
    }

    private void nowPlayingEvent() {
        if (mEventNowPlaying && !mPlayingActive) {
            mIsQuickEvent = false;
            mEventNowPlaying = false;
        }
    }

    private void initNowPlayingEvent() {
        if (!LauncherPrefs.SHOW_QUICKSPACE_NOWPLAYING.get(mContext)) return;

        if (!mPlayingActive) return;

        if (mNowPlayingTitle == null) return;

        mEventTitle = mNowPlayingTitle;
        mGreetings = mResources.getString(R.string.qe_now_playing_ext_one);
        mClockExt = "";
        if (mNowPlayingArtist == null ) {
            mEventTitleSub = mResources.getString(R.string.qe_now_playing_unknown_artist);
        } else {
            mEventTitleSub = mNowPlayingArtist;
        }
        mEventSubIcon = MSMHProxy.INSTANCE(mContext).getMediaAppIcon();
        mIsQuickEvent = true;
        mEventNowPlaying = true;

        mEventTitleSubAction = view -> MSMHProxy.INSTANCE(mContext).launchMediaApp();
    }

    private static String formatDateTime(Context context) {
        String styleText;
        DateFormat dateFormat;
        if (LauncherPrefs.SHOW_QUICKSPACE_ALT.get(context)) {
            styleText = context.getString(R.string.quickspace_date_format_minimalistic);
        } else {
            styleText = context.getString(R.string.quickspace_date_format);
        }
        dateFormat = DateFormat.getInstanceForSkeleton(styleText, Locale.getDefault());
        dateFormat.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE);
        return dateFormat.format(System.currentTimeMillis());
    }

    private void psonalityEvent() {
        if (mEventNowPlaying) return;

	    mEventTitle = formatDateTime(mContext);
        mEventTitleSubAction = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent calendarIntent = new Intent(Intent.ACTION_MAIN);
                calendarIntent.addCategory(Intent.CATEGORY_APP_CALENDAR);

                Intent clockIntent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);

                PackageManager packageManager = mContext.getPackageManager();
                List<ResolveInfo> calendarApps = packageManager.queryIntentActivities(calendarIntent, PackageManager.MATCH_DEFAULT_ONLY);
                List<ResolveInfo> clockApps = packageManager.queryIntentActivities(clockIntent, PackageManager.MATCH_DEFAULT_ONLY);

                if (!calendarApps.isEmpty()) {
                    calendarIntent.setPackage(calendarApps.get(0).activityInfo.packageName);
                    try {
                        mContext.startActivity(calendarIntent);
                    } catch (ActivityNotFoundException e) {
                    }
                } else if (!clockApps.isEmpty()) {
                    clockIntent.setPackage(clockApps.get(0).activityInfo.packageName);
                    try {
                        mContext.startActivity(clockIntent);
                    } catch (ActivityNotFoundException e) {
                    }
                } else {
                    Toast.makeText(mContext, R.string.intent_no_app_clock_found, Toast.LENGTH_SHORT).show();
                }
            }
        };

        int hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        if (hourOfDay >= 5 && hourOfDay <= 9) {
            mGreetings = mResources.getString(R.string.quickspace_grt_morning);
            mClockExt = mResources.getString(R.string.quickspace_ext_one);
        } else if (hourOfDay >= 12 && hourOfDay <= 15) {
            mGreetings = mResources.getString(R.string.quickspace_grt_afternoon);
            mClockExt = mResources.getString(R.string.quickspace_ext_two);
        } else if (hourOfDay >= 16 && hourOfDay <= 20) {
            mGreetings = mResources.getString(R.string.quickspace_grt_evening);
            mClockExt = mResources.getString(R.string.quickspace_ext_two);
        } else if (hourOfDay >= 21 && hourOfDay <= 23) {
            mGreetings = mResources.getString(R.string.quickspace_grt_night);
            mClockExt = mResources.getString(R.string.quickspace_ext_two);
        } else if (hourOfDay >= 0 && hourOfDay <= 3) {
            mGreetings = mResources.getString(R.string.quickspace_grt_night);
            mClockExt = mResources.getString(R.string.quickspace_ext_two);
        } else {
            mGreetings = mResources.getString(R.string.quickspace_grt_general);
            mClockExt = mResources.getString(R.string.quickspace_ext_two);
        }

        if (!LauncherPrefs.SHOW_QUICKSPACE_PSONALITY.get(mContext)) {
            mIsQuickEvent = false;
            return;
        }

        int luckNumber = getLuckyNumber(13);
        if (luckNumber < 7) {
            mIsQuickEvent = false;
            return;
        } else if (luckNumber == 7) {
            mPSAStr = mResources.getStringArray(R.array.quickspace_psa_random);
            mEventTitleSub = mPSAStr[getLuckyNumber(0, mPSAStr.length - 1)];
            mEventSubIcon = ContextCompat.getDrawable(mContext, R.drawable.ic_quickspace_crdroid);
            mIsQuickEvent = true;
            return;
        }

        mEventSubIcon = null;

        mPSAStr = getPSAStr(hourOfDay);

        if (mPSAStr != null) {
            mEventTitleSub = mPSAStr[getLuckyNumber(0, mPSAStr.length - 1)];
            mIsQuickEvent = true;
        } else {
            mIsQuickEvent = false;
        }
    }

    private String[] getPSAStr(int hour) {
        if (hour >= 0 && hour <= 3) {
            return getCachedArray(R.array.quickspace_psa_midnight);
        } else if (hour >= 5 && hour <= 9) {
            return getCachedArray(R.array.quickspace_psa_morning);
        } else if (hour >= 12 && hour <= 15) {
            return getCachedArray(R.array.quickspace_psa_noon);
        } else if (hour >= 16 && hour <= 18) {
            return getCachedArray(R.array.quickspace_psa_early_evening);
        } else if (hour >= 19 && hour <= 21) {
            return getCachedArray(R.array.quickspace_psa_evening);
        } else {
            return null;
        }
    }

    public boolean isQuickEvent() {
        return mIsQuickEvent;
    }

    public String getTitle() {
        return mEventTitle;
    }

    public String getActionTitle() {
        return mEventTitleSub;
    }

    public String getClockExt() {
        return mClockExt;
    }

    public String getGreetings() {
        return mGreetings;
    }

    public OnClickListener getAction() {
        return mEventTitleSubAction;
    }

    public Drawable getActionIcon() {
        return mEventSubIcon;
    }

    public int getLuckyNumber(int max) {
        return getLuckyNumber(0, max);
    }

    public int getLuckyNumber(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public void setMediaInfo(String title, String artist, boolean activePlayback) {
        mNowPlayingTitle = title;
        mNowPlayingArtist = artist;
        mPlayingActive = activePlayback;
    }

    public boolean isNowPlaying() {
        return mPlayingActive;
    }

    public void onPause() {
        unregisterPSAListener();
    }

    public void onResume() {
        registerPSAListener();
    }

    private String[] getCachedArray(int resId) {
        if (!mCachedPSAMap.containsKey(resId)) {
            mCachedPSAMap.put(resId, mResources.getStringArray(resId));
        }
        return mCachedPSAMap.get(resId);
    }
}
