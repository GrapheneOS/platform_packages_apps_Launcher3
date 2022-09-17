/*
 * Copyright (C) 2021-2025 crDroid Android Project
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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.util.crdroid.OmniJawsClient;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.MediaSessionManagerHelper;
import com.android.launcher3.util.MSMHProxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuickspaceController implements OmniJawsClient.OmniJawsObserver,
        MediaSessionManagerHelper.MediaMetadataListener {

    private static final String TAG = "Launcher3:QuickspaceController";

    private final List<OnDataListener> mListeners =
        Collections.synchronizedList(new ArrayList<>());
    private final Context mContext;
    private final Map<String, Integer> mConditionMap;
    private QuickEventsController mEventsController;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherInfo;
    private Drawable mConditionImage;

    private Runnable mOnDataUpdatedRunnable = new Runnable() {
            @Override
            public void run() {
                for (OnDataListener list : mListeners) {
                    list.onDataUpdated();
                }
            }
        };

    private Runnable mWeatherRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    mWeatherClient.queryWeather(mContext);
                    mWeatherInfo = mWeatherClient.getWeatherInfo();
                    if (mWeatherInfo != null) {
                        mConditionImage = mWeatherClient.getWeatherConditionImage(mContext, mWeatherInfo.conditionCode);
                    }
                    notifyListeners();
                } catch(Exception e) {
                    // Do nothing
                }
            }
        };

    public interface OnDataListener {
        void onDataUpdated();
    }

    public QuickspaceController(Context context) {
        mContext = context;
        mConditionMap = initializeConditionMap();
        mEventsController = new QuickEventsController(context);
        mWeatherClient = OmniJawsClient.get();
    }

    private void addWeatherProvider() {
        if (!LauncherPrefs.SHOW_QUICKSPACE_WEATHER.get(mContext)) return;
        mWeatherClient.addObserver(mContext, this);
        queryAndUpdateWeather();
    }

    public void addListener(OnDataListener listener) {
        mListeners.add(listener);
        addWeatherProvider();
        MSMHProxy.INSTANCE(mContext).addMediaMetadataListener(this);
        listener.onDataUpdated();
    }

    private void removeListener(OnDataListener listener) {
        if (mWeatherClient != null) {
            mWeatherClient.removeObserver(mContext, this);
        }
        mListeners.remove(listener);
    }

    public boolean isQuickEvent() {
        return mEventsController.isQuickEvent();
    }

    public QuickEventsController getEventController() {
        return mEventsController;
    }

    public boolean isWeatherAvailable() {
        return mWeatherClient != null && mWeatherClient.isOmniJawsEnabled(mContext);
    }

    public Drawable getWeatherIcon() {
        return mConditionImage;
    }

    public String getWeatherTemp() {
        if (mWeatherInfo == null) return null;

        boolean shouldShowCity = LauncherPrefs.SHOW_QUICKSPACE_WEATHER_CITY.get(mContext);
        boolean showWeatherText = LauncherPrefs.SHOW_QUICKSPACE_WEATHER_TEXT.get(mContext);

        StringBuilder weatherTemp = new StringBuilder();
        if (shouldShowCity) {
            weatherTemp.append(mWeatherInfo.city).append(" ");
        }
        weatherTemp.append(mWeatherInfo.temp)
                   .append(mWeatherInfo.tempUnits);

        if (showWeatherText) {
            weatherTemp.append(" • ").append(getConditionText(mWeatherInfo.condition));
        }

        return weatherTemp.toString();
    }

    private String getConditionText(String input) {
        if (input == null || input.isEmpty()) return "";

        Locale locale = mContext.getResources().getConfiguration().getLocales().get(0);
        boolean isEnglish = locale.getLanguage().toLowerCase(Locale.ROOT).startsWith("en");
        String lowerCaseInput = input.toLowerCase();

        if (!isEnglish) {
            for (Map.Entry<String, Integer> entry : mConditionMap.entrySet()) {
                if (lowerCaseInput.contains(entry.getKey())) {
                    return mContext.getResources().getString(entry.getValue());
                }
            }
        }
        return capitalizeWords(lowerCaseInput);
    }

    private Map<String, Integer> initializeConditionMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("clouds", R.string.quick_event_weather_clouds);
        map.put("rain", R.string.quick_event_weather_rain);
        map.put("clear", R.string.quick_event_weather_clear);
        map.put("storm", R.string.quick_event_weather_storm);
        map.put("snow", R.string.quick_event_weather_snow);
        map.put("wind", R.string.quick_event_weather_wind);
        map.put("mist", R.string.quick_event_weather_mist);
        return map;
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) return input;

        String[] words = input.split("\\s+");
        StringBuilder capitalized = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                capitalized.append(Character.toUpperCase(word.charAt(0)))
                           .append(word.substring(1).toLowerCase())
                           .append(" ");
            }
        }
        return capitalized.toString().trim();
    }

    public void onPause() {
        cancelListeners();
    }

    public void onResume() {
        mEventsController.onResume();
        updateMediaController();
        notifyListeners();
    }

    private void cancelListeners() {
        if (mEventsController != null) {
            mEventsController.onPause();
        }
        for (OnDataListener listener : new ArrayList<>(mListeners)) {
            removeListener(listener);
        }
        unregisterMediaController();
    }

    public void onDestroy() {
        cancelListeners();
        mWeatherClient = null;
        mWeatherInfo = null;
        mConditionImage = null;
    }

    @Override
    public void weatherUpdated() {
        queryAndUpdateWeather();
    }

    @Override
    public void weatherError(int errorReason) {
        Log.d(TAG, "weatherError " + errorReason);
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            mWeatherInfo = null;
            notifyListeners();
        }
    }

    @Override
    public void updateSettings() {
        Log.i(TAG, "updateSettings");
        queryAndUpdateWeather();
    }

    private void queryAndUpdateWeather() {
        MAIN_EXECUTOR.execute(mWeatherRunnable);
    }

    public void notifyListeners() {
        MAIN_EXECUTOR
            .getHandler()
            .post(mOnDataUpdatedRunnable);
    }

    private void unregisterMediaController() {
        MSMHProxy.INSTANCE(mContext).removeMediaMetadataListener(this);
    }

    private void updateMediaController() {
        if (!LauncherPrefs.SHOW_QUICKSPACE_NOWPLAYING.get(mContext)) {
            unregisterMediaController();
            return;
        }
        MediaMetadata mediaMetadata = MSMHProxy.INSTANCE(mContext).getCurrentMediaMetadata();
        boolean isPlaying = MSMHProxy.INSTANCE(mContext).isMediaPlaying();
        String trackArtist = isPlaying && mediaMetadata != null ? mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST) : "";
        String trackTitle = isPlaying && mediaMetadata != null ? mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE) : "";
        mEventsController.setMediaInfo(trackTitle, trackArtist, isPlaying);
        mEventsController.updateQuickEvents();
        notifyListeners();
    }

    @Override
    public void onMediaMetadataChanged() {
        updateMediaController();
    }

    @Override
    public void onPlaybackStateChanged() {
        updateMediaController();
    }
}
