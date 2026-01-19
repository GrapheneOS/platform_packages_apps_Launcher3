/*
 * Copyright (C) 2021-2025 crDroid Android Project
 * Copyright (C) 2025-2026 VoltageOS
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
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.os.Handler;
import android.util.Log;
import com.android.internal.util.crdroid.OmniJawsClient;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.util.MSMHProxy;
import com.android.launcher3.util.MediaSessionManagerHelper;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuickspaceController
    implements OmniJawsClient.OmniJawsObserver, MediaSessionManagerHelper.MediaMetadataListener {

  private static final String TAG = "Launcher3:QuickspaceController";

  private final List<WeakReference<OnDataListener>> mListeners =
      Collections.synchronizedList(new ArrayList<>());
  private final Context mContext;
  private final Map<String, Integer> mConditionMap;
  private QuickEventsController mEventsController;
  private QuickBatteryController mBatteryController;

  private OmniJawsClient mWeatherClient;
  private OmniJawsClient.WeatherInfo mWeatherInfo;
  private Drawable mConditionImage;
  private boolean mWeatherInitialized = false;
  private boolean mMediaInitialized = false;

  private static final String PREF_KEY_LAST_PSA_UPDATE_TIME = "pref_last_psa_update_time";
  private static final long MEDIA_UPDATE_DEBOUNCE_MS = 150;
  private static final long PSA_UPDATE_DELAY_MS = 3 * 60 * 1000;

  private final Handler mHandler = MAIN_EXECUTOR.getHandler();
  private final Runnable mPsaRunnable;
  private boolean mPsaScheduled = false;
  volatile boolean mDestroyed = false;
  private static QuickspaceController sInstance;

  private String mCachedWeatherTemp;
  private long mWeatherCacheTime = 0;
  private static final long WEATHER_CACHE_DURATION = 60 * 1000;
  private static final long NOTIFICATION_THROTTLE_MS = 100;

  private boolean mIsResuming = false;
  private final Object mNotificationLock = new Object();
  private boolean mHasPendingNotification = false;
  private long mLastNotificationTime = 0;

  private Runnable mOnDataUpdatedRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (mDestroyed) {
            return;
          }
          synchronized (mNotificationLock) {
            mHasPendingNotification = false;
            mLastNotificationTime = System.currentTimeMillis();
          }
          if (!mListeners.isEmpty()) {
            notifyListenersInternal();
          }
        }
      };

  private final Runnable mMediaUpdateRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (mDestroyed) {
            return;
          }
          updateMediaControllerInternal();
        }
      };

  private Runnable mWeatherRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (mDestroyed) {
            return;
          }
          try {
            if (mWeatherClient != null) {
              mWeatherClient.queryWeather(mContext);
              mWeatherInfo = mWeatherClient.getWeatherInfo();
              if (mWeatherInfo != null) {
                mConditionImage =
                    mWeatherClient.getWeatherConditionImage(mContext, mWeatherInfo.conditionCode);
              }
              mCachedWeatherTemp = null;
              mWeatherCacheTime = 0;
              MAIN_EXECUTOR.execute(() -> notifyListeners());
            }
          } catch (Exception e) {
            // Do nothing
          }
        }
      };

  public interface OnDataListener {
    void onDataUpdated();
  }

  public QuickspaceController(Context context) {
    mContext = context.getApplicationContext();
    mConditionMap = initializeConditionMap();
    mEventsController = new QuickEventsController(context);
    mBatteryController = new QuickBatteryController(context, this);

    mPsaRunnable =
        new Runnable() {
          @Override
          public void run() {
            if (mDestroyed) {
              return;
            }
            MODEL_EXECUTOR.execute(
                () -> {
                  if (mDestroyed) {
                    return;
                  }
                  long now = System.currentTimeMillis();
                  getPrefs().edit().putLong(PREF_KEY_LAST_PSA_UPDATE_TIME, now).apply();

                  if (mEventsController != null) {
                    mEventsController.updatePsonality();
                    MAIN_EXECUTOR.execute(() -> notifyListeners());
                  }

                  if (mDestroyed) {
                    return;
                  }

                  MAIN_EXECUTOR.execute(
                      () -> {
                        if (mPsaScheduled) {
                          mHandler.postDelayed(this, PSA_UPDATE_DELAY_MS);
                        }
                      });
                });
          }
        };
  }

  public static QuickspaceController getInstance(Context context) {
    if (sInstance != null && !sInstance.mDestroyed) {
      return sInstance;
    }
    synchronized (QuickspaceController.class) {
      if (sInstance == null || sInstance.mDestroyed) {
        sInstance = new QuickspaceController(context);
      }
      return sInstance;
    }
  }

  private synchronized void initializeWeatherIfNeeded() {
    if (mWeatherInitialized) {
      return;
    }

    if (LauncherPrefs.SHOW_QUICKSPACE_WEATHER.get(mContext)) {
      try {
        mWeatherClient = OmniJawsClient.get();
        if (mWeatherClient != null) {
          mWeatherClient.addObserver(mContext, this);
          mWeatherInitialized = true;
          queryAndUpdateWeather();
        }
      } catch (Exception e) {
        Log.w(TAG, "Failed to initialize weather client", e);
        mWeatherInitialized = false;
      }
    }
  }

  private synchronized void initializeMediaIfNeeded() {
    if (mMediaInitialized) {
      return;
    }

    if (LauncherPrefs.SHOW_QUICKSPACE_NOWPLAYING.get(mContext)) {
      try {
        MSMHProxy.INSTANCE(mContext).addMediaMetadataListener(this);
        mMediaInitialized = true;
      } catch (Exception e) {
        Log.w(TAG, "Failed to initialize media controller", e);
        mMediaInitialized = false;
      }
    }
  }

  public void addListener(OnDataListener listener) {
    if (mDestroyed || listener == null) {
      return;
    }

    removeListenerInternal(listener);

    cleanupDeadReferences();

    mListeners.add(new WeakReference<>(listener));

    if (mIsResuming) {
      MAIN_EXECUTOR.execute(() -> initializeComponentsDeferred());
      return;
    }

    initializeWeatherIfNeeded();
    initializeMediaIfNeeded();

    mEventsController.initQuickEvents();

    if (!mPsaScheduled) {
      startPsaScheduling();
    }

    if (mBatteryController != null) {
      mBatteryController.onResume();
    }

    listener.onDataUpdated();
  }

  private void initializeComponentsDeferred() {
    if (mDestroyed) return;

    MODEL_EXECUTOR.execute(
        () -> {
          initializeWeatherIfNeeded();
          initializeMediaIfNeeded();
          MAIN_EXECUTOR.execute(() -> notifyListeners());
        });
  }

  private void cleanupDeadReferences() {
    synchronized (mListeners) {
      Iterator<WeakReference<OnDataListener>> iterator = mListeners.iterator();
      while (iterator.hasNext()) {
        WeakReference<OnDataListener> ref = iterator.next();
        if (ref.get() == null) {
          iterator.remove();
        }
      }
    }
  }

  private void startPsaScheduling() {
    if (mPsaScheduled || mDestroyed) return;

    mPsaScheduled = true;
    long lastUpdateTime = getPrefs().getLong(PREF_KEY_LAST_PSA_UPDATE_TIME, 0);
    long now = System.currentTimeMillis();
    long timeSinceLastUpdate = now - lastUpdateTime;

    if (lastUpdateTime == 0 || timeSinceLastUpdate >= PSA_UPDATE_DELAY_MS) {
      if (!mDestroyed) {
        mHandler.post(mPsaRunnable);
      }
    } else {
      long remainingDelay = PSA_UPDATE_DELAY_MS - timeSinceLastUpdate;
      if (!mDestroyed) {
        mHandler.postDelayed(mPsaRunnable, remainingDelay);
      }
    }
  }

  private void stopPsaScheduling() {
    mPsaScheduled = false;
    mHandler.removeCallbacks(mPsaRunnable);
  }

  public void removeListener(OnDataListener listener) {
    removeListenerInternal(listener);

    if (mListeners.isEmpty()) {
      cleanup();
    }
  }

  private void removeListenerInternal(OnDataListener listener) {
    synchronized (mListeners) {
      Iterator<WeakReference<OnDataListener>> iterator = mListeners.iterator();
      while (iterator.hasNext()) {
        WeakReference<OnDataListener> ref = iterator.next();
        OnDataListener current = ref.get();
        if (current == null || current == listener) {
          iterator.remove();
        }
      }
    }
  }

  private void cleanup() {
    if (mWeatherClient != null && mWeatherInitialized) {
      mWeatherClient.removeObserver(mContext, this);
    }

    if (mMediaInitialized) {
      unregisterMediaController();
    }

    stopPsaScheduling();

    mWeatherInitialized = false;
    mMediaInitialized = false;

    if (mBatteryController != null) {
      mBatteryController.onPause();
    }
  }

  public boolean isQuickEvent() {
    return mEventsController.isQuickEvent();
  }

  public QuickEventsController getEventController() {
    return mEventsController;
  }

  public QuickBatteryController getBatteryController() {
    return mBatteryController;
  }

  public boolean isWeatherAvailable() {
    try {
      return !mDestroyed
          && LauncherPrefs.SHOW_QUICKSPACE_WEATHER.get(mContext)
          && mWeatherClient != null
          && mWeatherClient.isOmniJawsEnabled(mContext);
    } catch (Exception e) {
      return false;
    }
  }

  public Drawable getWeatherIcon() {
    return mConditionImage;
  }

  public String getWeatherTemp() {
    if (mWeatherInfo == null) return null;

    long now = System.currentTimeMillis();
    if (mCachedWeatherTemp != null && (now - mWeatherCacheTime) < WEATHER_CACHE_DURATION) {
      return mCachedWeatherTemp;
    }

    boolean shouldShowCity = LauncherPrefs.SHOW_QUICKSPACE_WEATHER_CITY.get(mContext);
    boolean showWeatherText = LauncherPrefs.SHOW_QUICKSPACE_WEATHER_TEXT.get(mContext);

    StringBuilder weatherTemp = new StringBuilder();
    if (shouldShowCity && mWeatherInfo.city != null) {
      weatherTemp.append(mWeatherInfo.city).append(" ");
    }
    weatherTemp.append(mWeatherInfo.temp).append(mWeatherInfo.tempUnits);

    if (showWeatherText && mWeatherInfo.condition != null) {
      weatherTemp.append(" • ").append(getConditionText(mWeatherInfo.condition));
    }

    mCachedWeatherTemp = weatherTemp.toString();
    mWeatherCacheTime = now;

    return mCachedWeatherTemp;
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
        capitalized
            .append(Character.toUpperCase(word.charAt(0)))
            .append(word.substring(1).toLowerCase())
            .append(" ");
      }
    }
    return capitalized.toString().trim();
  }

  public void onPause() {
    mHandler.removeCallbacks(mOnDataUpdatedRunnable);
    mHandler.removeCallbacks(mWeatherRunnable);
  }

  public void onResume() {
    mIsResuming = true;
    updateMediaController();
    if (mBatteryController != null) mBatteryController.onResume();
    mIsResuming = false;
    notifyListeners();
  }

  private void cancelListeners() {
    cleanupDeadReferences();
    for (WeakReference<OnDataListener> ref : new ArrayList<>(mListeners)) {
      OnDataListener listener = ref.get();
      if (listener != null) {
        removeListener(listener);
      }
    }
  }

  public void onDestroy() {
    if (mDestroyed) {
      return;
    }

    mDestroyed = true;

    if (sInstance == this) {
      sInstance = null;
    }

    mHandler.removeCallbacksAndMessages(null);
    stopPsaScheduling();

    cancelListeners();
    cleanup();

    mWeatherClient = null;
    mWeatherInfo = null;
    mConditionImage = null;
    mEventsController = null;
    mBatteryController = null;
    mCachedWeatherTemp = null;

    mWeatherCacheTime = 0;

    synchronized (mListeners) {
      mListeners.clear();
    }
  }

  private SharedPreferences getPrefs() {
    return mContext.getSharedPreferences(
        "com.android.launcher3.quickspace.prefs", Context.MODE_PRIVATE);
  }

  @Override
  public void weatherUpdated() {
    queryAndUpdateWeather();
  }

  @Override
  public void weatherError(int errorReason) {
    if (mDestroyed) {
      return;
    }

    if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
      mWeatherInfo = null;
      mCachedWeatherTemp = null;
      mWeatherCacheTime = 0;
      notifyListeners();
    }
  }

  @Override
  public void updateSettings() {
    if (mDestroyed) {
      return;
    }
    queryAndUpdateWeather();
  }

  private void queryAndUpdateWeather() {
    if (mWeatherClient != null && !mDestroyed) {
      MODEL_EXECUTOR.execute(mWeatherRunnable);
    }
  }

  public void notifyListeners() {
    long currentTime = System.currentTimeMillis();
    long timeSinceLastUpdate = currentTime - mLastNotificationTime;

    if (timeSinceLastUpdate < NOTIFICATION_THROTTLE_MS) {
      mHandler.removeCallbacks(mOnDataUpdatedRunnable);
      mHandler.postDelayed(mOnDataUpdatedRunnable, NOTIFICATION_THROTTLE_MS - timeSinceLastUpdate);
      return;
    }

    synchronized (mNotificationLock) {
      if (mHasPendingNotification) {
        return;
      }
      mHasPendingNotification = true;
      mLastNotificationTime = currentTime;
    }

    mHandler.removeCallbacks(mOnDataUpdatedRunnable);
    mHandler.post(mOnDataUpdatedRunnable);
  }

  private void notifyListenersInternal() {
    if (mDestroyed) {
      return;
    }
    cleanupDeadReferences();
    synchronized (mListeners) {
      for (WeakReference<OnDataListener> ref : mListeners) {
        OnDataListener listener = ref.get();
        if (listener != null) {
          listener.onDataUpdated();
        }
      }
    }
  }

  private void unregisterMediaController() {
    if (mMediaInitialized) {
      MSMHProxy.INSTANCE(mContext).removeMediaMetadataListener(this);
    }
  }

  private void updateMediaControllerInternal() {
    if (!LauncherPrefs.SHOW_QUICKSPACE_NOWPLAYING.get(mContext)) {
      unregisterMediaController();
      return;
    }

    if (!mMediaInitialized) {
      return;
    }

    if (mEventsController == null) {
      Log.w(TAG, "EventsController is null, skipping media update");
      return;
    }

    if (mDestroyed) {
      Log.w(TAG, "EventsController is null, skipping media update");
      return;
    }

    MODEL_EXECUTOR.execute(
        () -> {
          if (mDestroyed) {
            return;
          }
          MediaMetadata mediaMetadata = MSMHProxy.INSTANCE(mContext).getCurrentMediaMetadata();
          boolean isPlaying = MSMHProxy.INSTANCE(mContext).isMediaPlaying();
          String trackArtist =
              isPlaying && mediaMetadata != null
                  ? mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                  : "";
          String trackTitle =
              isPlaying && mediaMetadata != null
                  ? mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                  : "";

          MAIN_EXECUTOR.execute(
              () -> {
                if (mDestroyed) {
                  return;
                }
                if (mEventsController != null) {
                  mEventsController.setMediaInfo(trackTitle, trackArtist, isPlaying);
                  mEventsController.updateQuickEvents();
                  if (!mListeners.isEmpty()) {
                    notifyListeners();
                  }
                }
              });
        });
  }

  private void updateMediaController() {
    if (mDestroyed) return;

    mHandler.removeCallbacks(mMediaUpdateRunnable);
    mHandler.postDelayed(mMediaUpdateRunnable, MEDIA_UPDATE_DEBOUNCE_MS);
  }

  @Override
  public void onMediaMetadataChanged() {
    if (mDestroyed) {
      return;
    }

    updateMediaController();
  }

  @Override
  public void onPlaybackStateChanged() {
    if (mDestroyed) {
      return;
    }

    if (mDestroyed) return;

    mHandler.removeCallbacks(mMediaUpdateRunnable);
    mHandler.postDelayed(mMediaUpdateRunnable, 50);
  }
}
