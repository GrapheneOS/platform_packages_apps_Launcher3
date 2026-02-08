/*
 * Copyright (C) 2018-2025 crDroid Android Project
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

import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.quickspace.QuickspaceController.OnDataListener;
import com.android.launcher3.quickspace.receivers.QuickSpaceActionReceiver;
import com.android.launcher3.quickspace.views.AccentedTextClock;
import com.android.launcher3.util.Themes;

public class QuickSpaceView extends FrameLayout implements OnDataListener {

  private static final String TAG = "Launcher3:QuickSpaceView";

  private static final int BATTERY_FULL_THRESHOLD = 95;
  private static final int BATTERY_LOW_THRESHOLD = 20;
  private static final int BATTERY_CRITICAL_THRESHOLD = 10;
  private static final long SHIMMER_DURATION_MS = 2800;
  private static final float SHIMMER_WIDTH_RATIO = 0.30f;
  private static final int MIN_SHIMMER_WIDTH_DP = 30;
  private static final int BATTERY_CHANGE_THRESHOLD = 5;
  private static final long MIN_UPDATE_INTERVAL = 100;
  private static final int COLOR_TRANSITION_DURATION = 200;
  private static final int ANIMATE_IN_DURATION = 200;
  private static final int ANIMATE_OUT_DURATION = 250;
  private static final int BATTERY_PROGRESS_DURATION = 350;
  private static final int DEVICE_SWITCH_DURATION = 160;

  public ColorStateList mColorStateList;
  public BubbleTextView mBubbleTextView;
  public final int mQuickspaceBackgroundRes;

  public ViewGroup mQuickspaceContent;
  public ImageView mEventSubIcon;
  public ImageView mNowPlayingIcon;
  public TextView mEventTitleSub;

  public TextView mQuickspaceDayOfWeek;
  public AccentedTextClock mQuickspaceClock;
  public TextView mQuickspaceDate;
  public TextView mPSAMessage;
  public ViewGroup mNowPlayingContent;
  public TextView mNowPlayingText;
  public ViewGroup mContextualInfoRow;

  public ViewGroup mBatteryRow;
  public View mBatteryProgress;
  public TextView mBatteryDeviceName;
  public TextView mBatteryPercentage;
  public ImageView mBatteryIcon;
  public LinearLayout mBatteryDotsContainer;
  public ImageView mBatteryChargingOverlay;
  public View mBatteryShimmer;

  public TextView mEventTitleSubColored;
  public TextView mGreetingsExt;
  public TextView mGreetingsExtClock;
  public ViewGroup mWeatherContentSub;
  public ImageView mWeatherIconSub;
  public TextView mWeatherTempSub;
  public TextView mEventTitle;

  public boolean mIsQuickEvent;
  public boolean mFinishedInflate;
  public boolean mWeatherAvailable;
  public boolean mAttached;
  private volatile boolean mDestroyed = false;
  private volatile boolean mPendingDestroy = false;

  private boolean mIsAlternateStyle = false;
  private boolean mLastAccentState;
  private boolean mLastBlackTextState = false;
  private boolean mViewsLoaded = false;
  private String mLastEventTitle = "";
  private String mLastWeatherTemp = "";
  private boolean mLastNowPlayingState = false;
  private String mLastPSAMessage = "";
  private String mLastActionTitle = "";
  private int mLastBatteryLevel = -1;
  private int mLastDeviceCount = 0;
  private String mLastDeviceAddress = null;
  private boolean mLastChargingState = false;
  private boolean mBatteryAlphaRestoreNeeded = false;

  private ViewPropertyAnimator mCurrentAnimateIn;
  private ViewPropertyAnimator mCurrentAnimateOut;
  private ValueAnimator mBatteryProgressAnimator;
  private ValueAnimator mShimmerAnimator;

  private int mLastEventTitleHash = 0;
  private int mLastWeatherTempHash = 0;
  private int mLastActionTitleHash = 0;
  private long mLastUpdateTime = 0;

  private QuickSpaceActionReceiver mActionReceiver;

  private ColorStateList mCachedColorStateList;
  private boolean mCachedUseBlackText = false;

  private final android.content.BroadcastReceiver mWallpaperChangeReceiver =
      new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
          if (android.content.Intent.ACTION_WALLPAPER_CHANGED.equals(intent.getAction())) {
            post(
                () -> {
                  if (!mDestroyed) {
                    refreshColorStateList();
                    updateColorForViews();
                  }
                });
          }
        }
      };

  private boolean mIsLayoutSuppressed = false;
  private final Runnable mDeferredUpdateRunnable =
      new Runnable() {
        @Override
        public void run() {
          performDeferredUpdate();
        }
      };

  public QuickspaceController mController;

  private int mCurrentStyle = -1;

  public QuickSpaceView(Context context, AttributeSet set) {
    super(context, set);
    mController = QuickspaceController.getInstance(context);
    refreshColorStateList();
    mQuickspaceBackgroundRes = R.drawable.bg_quickspace;
    setClipChildren(false);
    setClipToPadding(false);
  }

  @Override
  public void onDataUpdated() {
    if (mDestroyed) {
      return;
    }

    removeCallbacks(mDeferredUpdateRunnable);
    postDelayed(mDeferredUpdateRunnable, 16);
  }

  private void performDeferredUpdate() {
    if (mDestroyed) {
      return;
    }

    int style = Integer.parseInt(LauncherPrefs.QUICKSPACE_UI_STYLE.get(getContext()));
    boolean styleChanged = mCurrentStyle != style;
    boolean useBlackText =
        LauncherPrefs.getPrefs(getContext()).getBoolean("pref_quickspace_black_text", false);
    boolean colorChanged = mLastBlackTextState != useBlackText;

    if (!mViewsLoaded || styleChanged) {
      prepareLayout(style);
      mViewsLoaded = true;
    }
    mIsQuickEvent = mController.isQuickEvent();
    mWeatherAvailable = mController.isWeatherAvailable();

    if (styleChanged || !mViewsLoaded || hasDataChanged() || colorChanged) {
      updateView(style);

      if ((styleChanged || colorChanged) && !mIsLayoutSuppressed) {
        post(
            () -> {
              if (!mDestroyed && mQuickspaceContent != null) {
                requestLayout();
              }
            });
        refreshColorStateList();
        updateColorForViews();
      }
      mLastBlackTextState = useBlackText;
    }
  }

  private void updateView(int style) {
    if (mDestroyed || mPendingDestroy) {
      return;
    }

    switch (style) {
      case 2:
        loadLargeStyle();
        break;
      case 1:
      case 0:
      default:
        loadDoubleLine(style == 1);
        break;
    }
  }

  private boolean hasDataChanged() {
    if (mDestroyed || mPendingDestroy) {
      return false;
    }

    long currentTime = System.currentTimeMillis();
    if (currentTime - mLastUpdateTime < MIN_UPDATE_INTERVAL) {
      return false;
    }

    if (mController == null) {
      return false;
    }

    QuickEventsController eventController = mController.getEventController();
    if (eventController == null) {
      return false;
    }

    boolean currentNowPlayingState = eventController.isNowPlaying();
    if (mLastNowPlayingState != currentNowPlayingState) {
      mLastNowPlayingState = currentNowPlayingState;
      mLastUpdateTime = currentTime;
      return true;
    }

    String currentEventTitle = eventController.getTitle();
    int eventTitleHash = currentEventTitle != null ? currentEventTitle.hashCode() : 0;

    String currentWeatherTemp = mController.getWeatherTemp();
    int weatherTempHash = currentWeatherTemp != null ? currentWeatherTemp.hashCode() : 0;

    String currentActionTitle = eventController.getActionTitle();
    int actionTitleHash = currentActionTitle != null ? currentActionTitle.hashCode() : 0;

    int batteryLevel =
        mController.getBatteryController() != null
            ? mController.getBatteryController().getBatteryLevel()
            : -1;
    boolean isCharging =
        mController.getBatteryController() != null
            && mController.getBatteryController().isCharging();

    boolean batteryVisible = batteryLevel >= 0;
    boolean lastBatteryVisible = mLastBatteryLevel >= 0;
    int deviceCount =
        mController.getBatteryController() != null
            ? mController.getBatteryController().getDeviceCount()
            : 0;

    boolean changed =
        mLastEventTitleHash != eventTitleHash
            || mLastWeatherTempHash != weatherTempHash
            || mLastActionTitleHash != actionTitleHash
            || mLastBatteryLevel != batteryLevel
            || batteryVisible != lastBatteryVisible
            || mLastDeviceCount != deviceCount
            || mLastChargingState != isCharging;

    if (changed) {
      mLastEventTitleHash = eventTitleHash;
      mLastWeatherTempHash = weatherTempHash;
      mLastActionTitleHash = actionTitleHash;
      mLastBatteryLevel = batteryLevel;
      mLastChargingState = isCharging;
      mLastUpdateTime = currentTime;
      mLastDeviceCount = deviceCount;

      mLastEventTitle = currentEventTitle != null ? currentEventTitle : "";
      mLastWeatherTemp = currentWeatherTemp != null ? currentWeatherTemp : "";
      mLastActionTitle = currentActionTitle != null ? currentActionTitle : "";
    }

    return changed;
  }

  private final void loadDoubleLine(boolean useAlternativeQuickspaceUI) {
    if (mDestroyed || mPendingDestroy) {
      return;
    }

    if (mIsLayoutSuppressed) {
      return;
    }

    if (mController == null || mController.getEventController() == null) {
      return;
    }

    beginBatchEdit();

    if (getBackground() == null) {
      setBackgroundResource(mQuickspaceBackgroundRes);
    }

    QuickEventsController eventController = mController.getEventController();

    String eventTitle = mController.getEventController().getTitle();
    updateTextViewIfNeeded(mEventTitle, eventTitle, false);

    if (useAlternativeQuickspaceUI) {
      String greetingsExt = mController.getEventController().getGreetings();
      updateTextViewIfNeeded(mGreetingsExt, greetingsExt, true);
      if (mGreetingsExt.getVisibility() == View.VISIBLE) {
        mGreetingsExt.setEllipsize(TruncateAt.END);
        mGreetingsExt.setOnClickListener(mController.getEventController().getAction());
      }
      String greetingsExtClock = mController.getEventController().getClockExt();
      updateTextViewIfNeeded(mGreetingsExtClock, greetingsExtClock, true);
      if (mGreetingsExtClock.getVisibility() == View.VISIBLE) {
        mGreetingsExtClock.setOnClickListener(mController.getEventController().getAction());
      }
    }
    boolean shouldShowPsa =
        mIsQuickEvent
            && (LauncherPrefs.SHOW_QUICKSPACE_PSONALITY.get(getContext())
                || mController.getEventController().isNowPlaying());

    updatePsaContent(shouldShowPsa, useAlternativeQuickspaceUI);
    updateWeatherContent();

    endBatchEdit();
  }

  private void updatePsaContent(boolean shouldShowPsa, boolean useAlternativeQuickspaceUI) {
    if (shouldShowPsa) {
      maybeSetMarquee(mEventTitle);
      mEventTitle.setOnClickListener(mController.getEventController().getAction());

      String actionTitle = mController.getEventController().getActionTitle();
      updateTextViewIfNeeded(mEventTitleSub, actionTitle, false);
      maybeSetMarquee(mEventTitleSub);
      mEventTitleSub.setOnClickListener(mController.getEventController().getAction());

      if (mEventTitleSub.getVisibility() != View.VISIBLE) {
        animateIn(mEventTitleSub);
      }

      if (useAlternativeQuickspaceUI) {
        updateNowPlayingState();
      } else {
        setEventSubIcon();
      }
    } else {
      animateOut(mEventTitleSub);
      animateOut(mEventSubIcon);
      if (useAlternativeQuickspaceUI) {
        animateOut(mEventTitleSubColored);
        animateOut(mNowPlayingIcon);
      }
    }
  }

  private void updateNowPlayingState() {
    if (mController.getEventController().isNowPlaying()) {
      animateOut(mEventSubIcon);
      animateIn(mEventTitleSubColored);
      animateIn(mNowPlayingIcon);

      String nowPlayingText = getContext().getString(R.string.qe_now_playing_by);
      updateTextViewIfNeeded(mEventTitleSubColored, nowPlayingText, false);
      mEventTitleSubColored.setOnClickListener(mController.getEventController().getAction());
    } else {
      setEventSubIcon();
      animateOut(mEventTitleSubColored);
      animateOut(mNowPlayingIcon);
    }
  }

  private void updateWeatherContent() {
    bindWeather(mWeatherContentSub, mWeatherTempSub, mWeatherIconSub);
  }

  private void updateTextViewIfNeeded(
      TextView textView, CharSequence newText, boolean setVisibility) {
    if (textView == null) return;

    boolean hasText = !TextUtils.isEmpty(newText);

    int currentVisibility = textView.getVisibility();
    CharSequence currentText = textView.getText();
    int desiredVisibility = hasText ? View.VISIBLE : View.GONE;

    if (setVisibility
        && currentVisibility == desiredVisibility
        && TextUtils.equals(currentText, newText)) {
      return;
    }

    if (setVisibility) {
      if (currentVisibility != desiredVisibility) {
        textView.setVisibility(desiredVisibility);
      }
    }

    if (!TextUtils.equals(currentText, newText)) {
      textView.setText(newText);
    }
  }

  private void maybeSetMarquee(TextView tv) {
    if (tv == null) return;
    tv.setSelected(false);
    tv.setEllipsize(TruncateAt.END);
    final float textWidth = tv.getPaint().measureText(tv.getText().toString());
    tv.post(
        () -> {
          android.text.Layout layout = tv.getLayout();
          if (layout != null && layout.getEllipsizedWidth() < textWidth) {
            tv.setEllipsize(TruncateAt.MARQUEE);
            tv.setMarqueeRepeatLimit(1);
            tv.setSelected(true);
          }
        });
  }

  private void setEventSubIcon() {
    if (mController == null || mController.getEventController() == null) {
      return;
    }

    Drawable icon = mController.getEventController().getActionIcon();
    if (icon != null) {
      if (mEventSubIcon.getVisibility() != View.VISIBLE) {
        animateIn(mEventSubIcon);
      }
      mEventSubIcon.setImageTintList(
          mController.getEventController().isNowPlaying() ? null : mColorStateList);
      mEventSubIcon.setImageDrawable(icon);
      mEventSubIcon.setOnClickListener(mController.getEventController().getAction());
    } else {
      animateOut(mEventSubIcon);
    }
  }

  private final void bindWeather(View container, TextView title, ImageView icon) {
    if (container == null || title == null || icon == null) return;

    if (!mWeatherAvailable || mController.getEventController().isNowPlaying()) {
      if (container.getVisibility() != View.GONE) {
        container.setVisibility(View.GONE);
      }
      return;
    }
    String weatherTemp = mController.getWeatherTemp();
    if (weatherTemp == null || weatherTemp.isEmpty()) {
      if (container.getVisibility() != View.GONE) {
        container.setVisibility(View.GONE);
      }
      return;
    }
    if (container.getVisibility() != View.VISIBLE) {
      animateIn(container);
    }

    updateTextViewIfNeeded(title, weatherTemp, false);

    Drawable weatherIcon = mController.getWeatherIcon();
    if (icon.getDrawable() != weatherIcon) {
      icon.setImageDrawable(weatherIcon);
    }
  }

  private QuickSpaceActionReceiver getActionReceiver() {
    if (mActionReceiver == null) {
      mActionReceiver = new QuickSpaceActionReceiver(getContext());
    }
    return mActionReceiver;
  }

  private void loadLargeStyle() {
    if (mDestroyed || mPendingDestroy) {
      return;
    }

    if (mIsLayoutSuppressed) {
      return;
    }

    if (mQuickspaceDayOfWeek == null || mBatteryRow == null) return;

    if (mController == null || mController.getEventController() == null) {
      return;
    }

    beginBatchEdit();

    boolean accentEnabled = LauncherPrefs.QUICKSPACE_VOLTAGE_ACCENT.get(getContext());
    if (mQuickspaceClock != null) {
      if (mLastAccentState != accentEnabled) {
        mQuickspaceClock.setAccentEnabled(accentEnabled);
        mLastAccentState = accentEnabled;
      }
    }

    String dayOfWeek = QuickEventsController.getDayOfWeek(getContext());
    updateTextViewIfNeeded(mQuickspaceDayOfWeek, dayOfWeek, false);

    String shortDate = mController.getEventController().getShortDate(getContext());
    updateTextViewIfNeeded(mQuickspaceDate, shortDate, false);

    if (mWeatherContentSub.getVisibility() != View.VISIBLE) {
      mWeatherContentSub.setVisibility(View.VISIBLE);
    }

    View.OnClickListener openClockListener =
        v -> {
          try {
            getContext()
                .startActivity(
                    new Intent(AlarmClock.ACTION_SHOW_ALARMS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
          } catch (ActivityNotFoundException e) {
            e.printStackTrace();
          }
        };

    View.OnClickListener openCalendarListener =
        v -> {
          try {
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            ContentUris.appendId(builder, System.currentTimeMillis());
            Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
          } catch (ActivityNotFoundException e) {
            e.printStackTrace();
          }
        };

    mQuickspaceClock.setOnClickListener(openClockListener);
    mQuickspaceDayOfWeek.setOnClickListener(openClockListener);
    mQuickspaceDate.setOnClickListener(openCalendarListener);

    bindWeather(mWeatherContentSub, mWeatherTempSub, mWeatherIconSub);

    boolean isNowPlaying = mController.getEventController().isNowPlaying();
    if (isNowPlaying) {
      if (mContextualInfoRow.getVisibility() != View.VISIBLE) {
        mContextualInfoRow.setVisibility(View.VISIBLE);
      }
      if (mPSAMessage.getVisibility() != View.GONE) {
        mPSAMessage.setVisibility(View.GONE);
      }
      if (mNowPlayingContent.getVisibility() != View.VISIBLE) {
        mNowPlayingContent.setVisibility(View.VISIBLE);
      }

      if (mController == null || mController.getEventController() == null) {
        endBatchEdit();
        return;
      }

      String nowPlaying =
          mController.getEventController().getTitle()
              + " - "
              + mController.getEventController().getActionTitle();
      updateTextViewIfNeeded(mNowPlayingText, nowPlaying, false);
      post(() -> maybeSetMarquee(mNowPlayingText));
    } else {
      if (mNowPlayingContent.getVisibility() != View.GONE) {
        mNowPlayingContent.setVisibility(View.GONE);
      }
      if (mIsQuickEvent && LauncherPrefs.SHOW_QUICKSPACE_PSONALITY.get(getContext())) {
        if (mContextualInfoRow.getVisibility() != View.VISIBLE) {
          mContextualInfoRow.setVisibility(View.VISIBLE);
        }
        if (mPSAMessage.getVisibility() != View.VISIBLE) {
          mPSAMessage.setVisibility(View.VISIBLE);
        }

        if (mController == null || mController.getEventController() == null) {
          endBatchEdit();
          return;
        }

        String actionTitle = mController.getEventController().getActionTitle();
        updateTextViewIfNeeded(mPSAMessage, actionTitle, false);
        mPSAMessage.setOnClickListener(mController.getEventController().getAction());
        post(() -> maybeSetMarquee(mPSAMessage));
      } else {
        if (mContextualInfoRow.getVisibility() != View.GONE) {
          mContextualInfoRow.setVisibility(View.GONE);
        }
      }
    }

    boolean showBattery = LauncherPrefs.SHOW_QUICKSPACE_BATTERY.get(getContext());
    QuickBatteryController batController = mController.getBatteryController();
    if (showBattery && batController != null) {
      int level = batController.getBatteryLevel();

      if (level >= 0) {
        updateBatteryPillContent();

        if (mBatteryRow.getVisibility() != View.VISIBLE) {
          mBatteryRow.setAlpha(1f);
          animateIn(mBatteryRow);
        } else if (mBatteryAlphaRestoreNeeded) {
          mBatteryRow.animate().cancel();
          mBatteryRow.setAlpha(1f);
          mBatteryAlphaRestoreNeeded = false;
        }

        mBatteryRow.setOnTouchListener(
            (v, event) -> {
              switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                  v.animate().cancel();
                  if (mShimmerAnimator != null) mShimmerAnimator.cancel();
                  v.animate().scaleX(0.98f).scaleY(0.98f).alpha(0.96f).setDuration(80).start();
                  v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                  return true;
                case MotionEvent.ACTION_UP:
                  v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                  if (batController.getDeviceCount() > 1) {
                    cycleBatteryDevice();
                  } else {
                    batController.launchBatterySettings();
                  }
                  postDelayed(
                      () ->
                          updateChargingShimmer(
                              batController.isCharging(), batController.getBatteryLevel()),
                      500);
                  return true;
                case MotionEvent.ACTION_CANCEL:
                  v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                  postDelayed(
                      () ->
                          updateChargingShimmer(
                              batController.isCharging(), batController.getBatteryLevel()),
                      500);
                  return true;
              }
              return false;
            });

        mBatteryRow.setOnClickListener(null);

        mBatteryRow.setOnLongClickListener(
            v -> {
              batController.launchBatterySettings();
              return true;
            });
      } else {
        animateOut(mBatteryRow);
      }
    } else {
      if (mBatteryRow.getVisibility() != View.GONE) {
        mBatteryRow.setVisibility(View.GONE);
      }
    }

    endBatchEdit();
  }

  private void refreshColorStateList() {
    boolean useBlack =
        LauncherPrefs.getPrefs(getContext()).getBoolean("pref_quickspace_black_text", false);

    if (mCachedColorStateList != null && mCachedUseBlackText == useBlack) {
      mColorStateList = mCachedColorStateList;
      return;
    }

    int color =
        useBlack ? Color.BLACK : Themes.getAttrColor(getContext(), R.attr.workspaceTextColor);
    mColorStateList = ColorStateList.valueOf(color);
    mCachedColorStateList = mColorStateList;
    mCachedUseBlackText = useBlack;
  }

  private void updateColorForViews() {
    if (mColorStateList == null) {
      refreshColorStateList();
      if (mColorStateList == null) return;
    }

    int targetColor = mColorStateList.getDefaultColor();

    if (mEventTitle != null) {
      animateTextColor(mEventTitle, targetColor);
      updateShadows(mEventTitle);
    }
    if (mQuickspaceDayOfWeek != null) {
      animateTextColor(mQuickspaceDayOfWeek, targetColor);
      updateShadows(mQuickspaceDayOfWeek);
    }
    if (mQuickspaceClock != null) {
      animateTextColor(mQuickspaceClock, targetColor);
      updateShadows(mQuickspaceClock);
    }
    if (mQuickspaceDate != null) {
      animateTextColor(mQuickspaceDate, targetColor);
      updateShadows(mQuickspaceDate);
    }
    if (mPSAMessage != null) {
      animateTextColor(mPSAMessage, targetColor);
      updateShadows(mPSAMessage);
    }
    if (mWeatherTempSub != null) {
      animateTextColor(mWeatherTempSub, targetColor);
      updateShadows(mWeatherTempSub);
    }
    if (mEventTitleSub != null) {
      animateTextColor(mEventTitleSub, targetColor);
      updateShadows(mEventTitleSub);
    }
    if (mNowPlayingText != null) {
      animateTextColor(mNowPlayingText, targetColor);
      updateShadows(mNowPlayingText);
    }
    if (mGreetingsExt != null) {
      animateTextColor(mGreetingsExt, targetColor);
      updateShadows(mGreetingsExt);
    }
    if (mGreetingsExtClock != null) {
      animateTextColor(mGreetingsExtClock, targetColor);
      updateShadows(mGreetingsExtClock);
    }
    if (mEventTitleSubColored != null) {
      animateTextColor(mEventTitleSubColored, targetColor);
      updateShadows(mEventTitleSubColored);
    }

    if (mBatteryDeviceName != null) {
      animateTextColor(mBatteryDeviceName, targetColor);
    }

    if (mBatteryIcon != null) mBatteryIcon.setImageTintList(mColorStateList);
    if (mBatteryChargingOverlay != null) mBatteryChargingOverlay.setImageTintList(mColorStateList);
  }

  private void animateTextColor(TextView view, int toColor) {
    if (view == null) return;

    int fromColor = view.getCurrentTextColor();
    if (fromColor == toColor) return;

    ValueAnimator colorAnim = ValueAnimator.ofArgb(fromColor, toColor);
    colorAnim.setDuration(COLOR_TRANSITION_DURATION);
    colorAnim.addUpdateListener(
        animator -> {
          if (view != null && !mDestroyed) {
            view.setTextColor((int) animator.getAnimatedValue());
          }
        });
    colorAnim.start();
  }

  private void updateShadows(TextView view) {
    if (view == null) return;
    view.setShadowLayer(0, 0, 0, 0);
  }

  private void updateBatteryPillContent() {
    QuickBatteryController batController = mController.getBatteryController();
    if (batController == null) return;

    String currentAddress = batController.getCurrentDeviceAddress();

    refreshColorStateList();
    updateColorForViews();

    boolean deviceChanged =
        mLastDeviceAddress != null && !TextUtils.equals(currentAddress, mLastDeviceAddress);

    if (deviceChanged) {
      performDeviceSwitchTransition(batController);
    } else {
      applyBatteryData(batController);
    }

    mLastDeviceAddress = currentAddress;
  }

  private void performDeviceSwitchTransition(QuickBatteryController batController) {
    mBatteryRow.animate().cancel();
    mBatteryRow
        .animate()
        .alpha(0f)
        .translationX(-dpToPx(6))
        .setDuration(DEVICE_SWITCH_DURATION)
        .setInterpolator(new PathInterpolator(0.4f, 0f, 1f, 1f))
        .withEndAction(
            () -> {
              applyBatteryData(batController);

              mBatteryRow.setTranslationX(dpToPx(6));
              mBatteryRow.animate().cancel();
              mBatteryRow
                  .animate()
                  .alpha(1f)
                  .translationX(0f)
                  .setDuration(DEVICE_SWITCH_DURATION)
                  .setInterpolator(new PathInterpolator(0f, 0f, 0.2f, 1f))
                  .start();
            })
        .start();
  }

  private void applyBatteryData(QuickBatteryController batController) {
    String deviceName = batController.getDeviceName();
    int level = batController.getBatteryLevel();
    boolean isAudio = batController.isAudioDevice();
    boolean isCharging = getActualChargingState(batController);

    updateTextViewIfNeeded(mBatteryDeviceName, deviceName, false);
    String percentStr = level + "%";
    updateTextViewIfNeeded(mBatteryPercentage, percentStr, false);

    updateBatteryColors(level, isCharging);

    if (mBatteryIcon != null) {
      int iconRes = isAudio ? R.drawable.ic_audio_device : R.drawable.ic_battery_std;
      mBatteryIcon.setImageResource(iconRes);
    }

    if (mBatteryChargingOverlay != null) {
      if (mBatteryChargingOverlay.getVisibility() != View.GONE) {
        mBatteryChargingOverlay.setVisibility(View.GONE);
        mBatteryChargingOverlay.setAlpha(0f);
      }
    }

    animateBatteryProgress(level, isCharging);
    updateChargingShimmer(isCharging, level);

    updateBatteryDots();
  }

  private boolean getActualChargingState(QuickBatteryController batController) {
    boolean controllerState = batController.isCharging();

    try {
      android.os.BatteryManager bm =
          (android.os.BatteryManager)
              getContext().getSystemService(android.content.Context.BATTERY_SERVICE);
      if (bm != null) {
        int status = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS);
        return (status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
            || status == android.os.BatteryManager.BATTERY_STATUS_FULL);
      }
    } catch (Exception e) {
    }

    return controllerState;
  }

  private void cycleBatteryDevice() {
    if (mController.getBatteryController() == null) return;

    mController.getBatteryController().advanceIndex();
    updateBatteryPillContent();
  }

  private void updateBatteryDots() {
    if (mBatteryDotsContainer == null) return;
    int count = mController.getBatteryController().getDeviceCount();
    int current = mController.getBatteryController().getCurrentIndex();

    if (count <= 1) {
      mBatteryDotsContainer.setVisibility(View.GONE);
      return;
    }
    mBatteryDotsContainer.setVisibility(View.VISIBLE);

    if (mBatteryDotsContainer.getChildCount() != count) {
      mBatteryDotsContainer.removeAllViews();
      for (int i = 0; i < count; i++) {
        View dot = new View(getContext());
        int size = dpToPx(5);
        int margin = dpToPx(4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginStart(margin);
        dot.setLayoutParams(lp);
        dot.setBackgroundResource(R.drawable.bg_battery_dot);
        mBatteryDotsContainer.addView(dot);
      }
    }

    for (int i = 0; i < count; i++) {
      View dot = mBatteryDotsContainer.getChildAt(i);
      if (i == current) {
        dot.setAlpha(1.0f);
      } else {
        dot.setAlpha(0.4f);
      }
    }
  }

  private void updateChargingShimmer(boolean isCharging, int level) {
    if (mBatteryShimmer == null) return;

    if (mShimmerAnimator != null) {
      mShimmerAnimator.cancel();
      mShimmerAnimator = null;
    }

    if (isCharging && level < BATTERY_FULL_THRESHOLD) {
      mBatteryShimmer.setVisibility(View.VISIBLE);
      mBatteryShimmer.setAlpha(1f);
      startShimmerAnimation();
    } else {
      mBatteryShimmer.setVisibility(View.GONE);
      mBatteryShimmer.setAlpha(0f);
      mBatteryShimmer.setTranslationX(0f);
    }
  }

  private void startShimmerAnimation() {
    if (mShimmerAnimator != null) mShimmerAnimator.cancel();

    if (mBatteryProgress == null || mBatteryShimmer == null) return;

    if (mBatteryProgress.getWidth() <= 0) {
      mBatteryProgress
          .getViewTreeObserver()
          .addOnGlobalLayoutListener(
              new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                  if (mBatteryProgress.getWidth() > 0) {
                    mBatteryProgress.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    if (mBatteryShimmer.getVisibility() == View.VISIBLE) {
                      startShimmerAnimationInternal();
                    }
                  }
                }
              });
      return;
    }

    startShimmerAnimationInternal();
  }

  private void startShimmerAnimationInternal() {
    mBatteryShimmer.post(
        () -> {
          if (mBatteryProgress == null || mBatteryShimmer == null || mDestroyed) return;

          mShimmerAnimator = ValueAnimator.ofFloat(0, 1);
          mShimmerAnimator.setDuration(SHIMMER_DURATION_MS);
          mShimmerAnimator.setInterpolator(new LinearInterpolator());
          mShimmerAnimator.setRepeatCount(ValueAnimator.INFINITE);
          mShimmerAnimator.addUpdateListener(
              val -> {
                if (mBatteryProgress == null || mBatteryShimmer == null || mDestroyed) return;

                float currentParentWidth = mBatteryProgress.getWidth();
                if (currentParentWidth <= 0) return;

                float desiredShimmerWidth = currentParentWidth * SHIMMER_WIDTH_RATIO;
                float minShimmer = dpToPx(MIN_SHIMMER_WIDTH_DP);
                if (desiredShimmerWidth < minShimmer) desiredShimmerWidth = minShimmer;

                if (mBatteryShimmer.getLayoutParams().width != (int) desiredShimmerWidth) {
                  ViewGroup.LayoutParams lp = mBatteryShimmer.getLayoutParams();
                  lp.width = (int) desiredShimmerWidth;
                  mBatteryShimmer.setLayoutParams(lp);
                }

                float currentShimmerWidth = desiredShimmerWidth;

                float frac = val.getAnimatedFraction();
                float trans =
                    (currentParentWidth + currentShimmerWidth) * frac - currentShimmerWidth;
                mBatteryShimmer.setTranslationX(trans);
              });
          mShimmerAnimator.start();
        });
  }

  private void updateBatteryColors(int level, boolean isCharging) {
    if (mBatteryProgress == null || mBatteryPercentage == null) return;

    int progressColor;
    int textColor;
    int backplateColor;

    if (level <= BATTERY_LOW_THRESHOLD) {
      if (level <= BATTERY_CRITICAL_THRESHOLD) {
        progressColor = Themes.getAttrColor(getContext(), android.R.attr.colorError);
      } else {
        progressColor = 0xFFFBC02D;
      }
      backplateColor = progressColor;
      textColor = Color.BLACK;
      mBatteryPercentage.setTypeface(Typeface.DEFAULT_BOLD);
    } else {
      progressColor = Themes.getAttrColor(getContext(), R.attr.workspaceAccentColor);
      backplateColor = 0x4D000000;

      if (level >= 90 && !isCharging) {
        textColor = mColorStateList.withAlpha(150).getDefaultColor();
      } else {
        textColor = mColorStateList.getDefaultColor();
      }
      mBatteryPercentage.setTypeface(isCharging ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    mBatteryPercentage.setAlpha(1.0f);
    mBatteryPercentage.setTextColor(textColor);
    mBatteryPercentage.setBackgroundTintList(ColorStateList.valueOf(backplateColor));

    Drawable bg = mBatteryProgress.getBackground();
    if (bg instanceof GradientDrawable) {
      GradientDrawable gd = (GradientDrawable) bg;
      int r = Color.red(progressColor);
      int g = Color.green(progressColor);
      int b = Color.blue(progressColor);

      int startColor = Color.argb(80, r, g, b);
      int midColor = Color.argb(25, r, g, b);
      int endColor = 0x00FFFFFF;

      if (level < BATTERY_LOW_THRESHOLD) {
        startColor = Color.argb(200, r, g, b);
        midColor = Color.argb(100, r, g, b);
      }

      gd.setColors(new int[] {startColor, midColor, endColor});
      gd.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);

      gd.setAlpha(level < BATTERY_LOW_THRESHOLD ? 255 : 255);
    }
  }

  private void animateBatteryProgress(int level, boolean isCharging) {
    mBatteryRow.post(
        () -> {
          if (mBatteryRow == null || mBatteryProgress == null || mBatteryRow.getWidth() <= 0)
            return;

          int totalWidth = mBatteryRow.getWidth();
          int targetWidth = (int) ((totalWidth * level) / 100f);

          boolean significantChange =
              Math.abs(level - mLastBatteryLevel) >= BATTERY_CHANGE_THRESHOLD;
          boolean stateChange = isCharging != mLastChargingState;
          boolean firstRun = mLastBatteryLevel == -1;

          mLastBatteryLevel = level;

          if (mBatteryProgress.getLayoutParams().width != targetWidth) {
            if (mBatteryProgressAnimator != null && mBatteryProgressAnimator.isRunning()) {
              mBatteryProgressAnimator.cancel();
            }

            if (!firstRun && (significantChange || stateChange)) {
              mBatteryProgressAnimator =
                  ValueAnimator.ofInt(mBatteryProgress.getLayoutParams().width, targetWidth);
              mBatteryProgressAnimator.setDuration(BATTERY_PROGRESS_DURATION);
              mBatteryProgressAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
              mBatteryProgressAnimator.addUpdateListener(
                  animation -> {
                    if (mBatteryProgress == null || mDestroyed) return;
                    ViewGroup.LayoutParams lp = mBatteryProgress.getLayoutParams();
                    lp.width = (int) animation.getAnimatedValue();
                    mBatteryProgress.setLayoutParams(lp);
                  });
              mBatteryProgressAnimator.start();
            } else {
              ViewGroup.LayoutParams lp = mBatteryProgress.getLayoutParams();
              lp.width = targetWidth;
              mBatteryProgress.setLayoutParams(lp);
            }
          }
        });
  }

  private void beginBatchEdit() {
    if (mQuickspaceContent != null && !mIsLayoutSuppressed) {
      mIsLayoutSuppressed = true;
      mQuickspaceContent.suppressLayout(true);

      ViewGroup parent = (ViewGroup) getParent();
      if (parent != null) {
        parent.suppressLayout(true);
      }
    }
  }

  private void endBatchEdit() {
    if (mQuickspaceContent != null) {
      mQuickspaceContent.suppressLayout(false);
    }
    if (mIsLayoutSuppressed) {
      mIsLayoutSuppressed = false;
      ViewGroup parent = (ViewGroup) getParent();
      if (parent != null) {
        parent.suppressLayout(false);
      }
    }
  }

  private final void loadViews() {
    mEventTitle = (TextView) findViewById(R.id.quick_event_title);
    mEventTitleSub = (TextView) findViewById(R.id.quick_event_title_sub);
    mEventTitleSubColored = (TextView) findViewById(R.id.quick_event_title_sub_colored);
    mNowPlayingIcon = (ImageView) findViewById(R.id.now_playing_icon_sub);
    mEventSubIcon = (ImageView) findViewById(R.id.quick_event_icon_sub);
    mWeatherIconSub = (ImageView) findViewById(R.id.quick_event_weather_icon);
    mQuickspaceContent = (ViewGroup) findViewById(R.id.quickspace_content);
    mWeatherContentSub = (ViewGroup) findViewById(R.id.quick_event_weather_content);
    mWeatherTempSub = (TextView) findViewById(R.id.quick_event_weather_temp);

    if (mQuickspaceContent != null) {
      mQuickspaceContent.setClipChildren(false);
      mQuickspaceContent.setClipToPadding(false);
    }

    if (mCurrentStyle == 1) {
      mGreetingsExtClock = (TextView) findViewById(R.id.extended_greetings_clock);
      mGreetingsExt = (TextView) findViewById(R.id.extended_greetings);
    }

    if (mCurrentStyle == 2) {
      mQuickspaceDayOfWeek = findViewById(R.id.quickspace_day_of_week);
      mQuickspaceClock = (AccentedTextClock) findViewById(R.id.quickspace_clock);
      mQuickspaceDate = findViewById(R.id.quickspace_date);
      mPSAMessage = findViewById(R.id.quickspace_psa_message);
      mNowPlayingContent = findViewById(R.id.now_playing_content);
      mNowPlayingText = findViewById(R.id.now_playing_text);
      mContextualInfoRow = findViewById(R.id.contextual_info_row);

      mBatteryRow = findViewById(R.id.battery_info_row);
      mBatteryProgress = findViewById(R.id.battery_progress_bar);
      mBatteryDeviceName = findViewById(R.id.battery_device_name);
      mBatteryPercentage = findViewById(R.id.battery_percentage);
      mBatteryIcon = findViewById(R.id.battery_icon);
      mBatteryChargingOverlay = findViewById(R.id.battery_charging_overlay);
      mBatteryDotsContainer = findViewById(R.id.battery_dots_container);
      mBatteryShimmer = findViewById(R.id.battery_shimmer_view);

      if (mBatteryRow != null) {
        mBatteryRow.setClipChildren(false);
        mBatteryRow.setClipToPadding(false);
        mBatteryRow.setContentDescription("Battery information");
      }

      if (mQuickspaceClock != null) {
        mQuickspaceClock.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        mQuickspaceClock.setContentDescription("Current time");
      }

      if (mQuickspaceDate != null) {
        mQuickspaceDate.setContentDescription("Current date");
      }
    }

    boolean hasGoogleApp =
        isPackageEnabled("com.google.android.googlequicksearchbox", getContext());
    if (mWeatherContentSub != null) {
      mWeatherContentSub.setOnClickListener(
          hasGoogleApp ? getActionReceiver().getWeatherAction() : null);
    }

    View.OnClickListener mediaClickListener =
        v -> {
          if (mController != null && mController.getEventController() != null) {
            View.OnClickListener action = mController.getEventController().getAction();
            if (action != null) {
              action.onClick(v);
            }
          }
        };

    if (mNowPlayingIcon != null) {
      mNowPlayingIcon.setOnClickListener(mediaClickListener);
    }

    if (mNowPlayingContent != null) {
      mNowPlayingContent.setOnClickListener(mediaClickListener);
    }
  }

  private void prepareLayout(int style) {
    if (mCurrentStyle == style && mViewsLoaded) {
      return;
    }

    mCurrentStyle = style;
    int indexOfChild = indexOfChild(mQuickspaceContent);
    if (mQuickspaceContent != null) {
      removeView(mQuickspaceContent);
    }
    int layoutId;
    switch (style) {
      case 1:
        layoutId = R.layout.quickspace_alternate_double;
        break;
      case 2:
        layoutId = R.layout.quickspace_large_style;
        break;
      default:
        layoutId = R.layout.quickspace_doubleline;
    }
    addView(LayoutInflater.from(getContext()).inflate(layoutId, this, false), indexOfChild);

    loadViews();
    getQuickSpaceView();
  }

  private void getQuickSpaceView() {
    if (mQuickspaceContent == null) return;

    if (mQuickspaceContent.getVisibility() != View.VISIBLE) {
      mQuickspaceContent.setVisibility(View.VISIBLE);
      mQuickspaceContent.setAlpha(0.8f);
      mQuickspaceContent
          .animate()
          .setDuration(100)
          .alpha(1.0f)
          .setInterpolator(new DecelerateInterpolator(1.5f))
          .start();
    }
  }

  private void animateIn(View view) {
    if (mDestroyed || mPendingDestroy || view == null) {
      return;
    }

    view.animate().cancel();

    if (view.getVisibility() == View.VISIBLE && view.getAlpha() == 1f) {
      return;
    }

    view.setVisibility(View.VISIBLE);
    view.setAlpha(0f);
    view.setTranslationY(view.getHeight() / 2f);
    mCurrentAnimateIn =
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ANIMATE_IN_DURATION)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(null);
    mCurrentAnimateIn.start();
  }

  private void animateOut(View view) {
    if (mDestroyed || mPendingDestroy || view == null) {
      return;
    }

    if (view.getVisibility() != View.VISIBLE) {
      return;
    }
    view.animate().cancel();

    mCurrentAnimateOut =
        view.animate()
            .alpha(0f)
            .translationY(view.getHeight() / 2f)
            .setDuration(ANIMATE_OUT_DURATION)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(
                () -> {
                  view.setVisibility(View.GONE);
                  view.setTranslationY(0f);
                  view.setAlpha(1f);
                  view.setOnClickListener(null);
                });

    mCurrentAnimateOut.start();
  }

  private void cancelAllAnimations() {
    View[] animatedViews = {
      mEventTitleSub,
      mEventSubIcon,
      mEventTitleSubColored,
      mNowPlayingIcon,
      mWeatherContentSub,
      mQuickspaceContent,
      mBatteryRow
    };

    for (View view : animatedViews) {
      if (view != null) {
        view.animate().cancel();
      }
    }

    if (mCurrentAnimateIn != null) {
      mCurrentAnimateIn.cancel();
      mCurrentAnimateIn = null;
    }
    if (mCurrentAnimateOut != null) {
      mCurrentAnimateOut.cancel();
      mCurrentAnimateOut = null;
    }
    if (mBatteryProgressAnimator != null) {
      mBatteryProgressAnimator.removeAllUpdateListeners();
      mBatteryProgressAnimator.cancel();
      mBatteryProgressAnimator = null;
    }
    if (mShimmerAnimator != null) {
      mShimmerAnimator.removeAllUpdateListeners();
      mShimmerAnimator.cancel();
      mShimmerAnimator = null;
    }
  }

  private void clearClickListeners() {
    View[] clickableViews = {
      mEventTitle, mEventTitleSub, mEventTitleSubColored,
      mGreetingsExt, mGreetingsExtClock, mEventSubIcon,
      mNowPlayingIcon, mWeatherContentSub, mQuickspaceDayOfWeek,
      mQuickspaceClock, mQuickspaceDate, mPSAMessage,
      mNowPlayingContent, mNowPlayingText, mBatteryRow
    };

    for (View view : clickableViews) {
      if (view != null) {
        view.setOnClickListener(null);
        view.setOnLongClickListener(null);
      }
    }
  }

  private void safeRemoveListener() {
    if (mController != null && !mDestroyed) {
      try {
        mController.removeListener(this);
      } catch (Exception e) {
      }
    }
  }

  @Override
  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (mAttached || mDestroyed || mPendingDestroy) {
      return;
    }

    mAttached = true;

    try {
      android.content.IntentFilter filter =
          new android.content.IntentFilter(android.content.Intent.ACTION_WALLPAPER_CHANGED);
      getContext().registerReceiver(mWallpaperChangeReceiver, filter);
    } catch (Exception e) {
    }

    mBatteryAlphaRestoreNeeded = true;

    post(
        () -> {
          if (mController != null && mFinishedInflate && !mDestroyed && mAttached) {
            mController.addListener(this);
          }
          onDataUpdated();
        });
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (!mAttached) {
      return;
    }

    try {
      getContext().unregisterReceiver(mWallpaperChangeReceiver);
    } catch (Exception e) {
    }

    cancelAllAnimations();
    safeRemoveListener();

    mAttached = false;
  }

  public boolean isPackageEnabled(String pkgName, Context context) {
    try {
      return context.getPackageManager().getApplicationInfo(pkgName, 0).enabled;
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();
    loadViews();
    mFinishedInflate = true;
    mBubbleTextView = findViewById(R.id.dummyBubbleTextView);
    mBubbleTextView.setTag(
        new ItemInfo() {
          @Override
          public ComponentName getTargetComponent() {
            return new ComponentName(getContext(), "");
          }
        });
    mBubbleTextView.setContentDescription("");
    if (isAttachedToWindow()) {
      if (mController != null) {
        mController.addListener(this);
      }
    }
  }

  @Override
  public void onLayout(boolean b, int n, int n2, int n3, int n4) {
    super.onLayout(b, n, n2, n3, n4);
  }

  public void onPause() {
    safeRemoveListener();
    if (mController != null) {
      try {
        mController.onPause();
      } catch (Exception e) {
        mController = null;
      }
    }
  }

  public void onResume() {
    removeCallbacks(mDeferredUpdateRunnable);

    if (mController != null && mFinishedInflate && !mDestroyed && !mPendingDestroy) {
      mController.addListener(this);
      try {
        mController.onResume();
      } catch (Exception e) {
        mController = null;
      }

      mBatteryAlphaRestoreNeeded = true;

      post(
          () -> {
            if (!mDestroyed && mQuickspaceContent != null) {
              mQuickspaceContent.requestLayout();
              requestLayout();
            }
          });
    }
  }

  public void prepareForDestroy() {
    cancelAllAnimations();
    safeRemoveListener();
    clearClickListeners();
  }

  public void onDestroy() {
    if (mDestroyed) {
      return;
    }

    mDestroyed = true;
    mPendingDestroy = true;

    removeCallbacks(mDeferredUpdateRunnable);

    cancelAllAnimations();

    safeRemoveListener();
    clearClickListeners();

    if (mController != null) {
      safeRemoveListener();
    }
    mActionReceiver = null;
    mController = null;
    mBubbleTextView = null;
    mQuickspaceContent = null;
    mEventSubIcon = null;
    mNowPlayingIcon = null;
    mEventTitleSub = null;
    mEventTitleSubColored = null;
    mGreetingsExt = null;
    mGreetingsExtClock = null;
    mWeatherContentSub = null;
    mWeatherIconSub = null;
    mWeatherTempSub = null;
    mEventTitle = null;

    mQuickspaceDayOfWeek = null;
    mQuickspaceClock = null;
    mQuickspaceDate = null;
    mPSAMessage = null;
    mNowPlayingContent = null;
    mNowPlayingText = null;
    mContextualInfoRow = null;

    mBatteryRow = null;
    mBatteryProgress = null;
    mBatteryDeviceName = null;
    mBatteryPercentage = null;
    mBatteryIcon = null;
    mBatteryChargingOverlay = null;
    mBatteryDotsContainer = null;
    mBatteryShimmer = null;

    mCachedColorStateList = null;

    setBackground(null);
    mAttached = false;
    mFinishedInflate = false;
    mViewsLoaded = false;
    mLastEventTitle = "";
    mLastWeatherTemp = "";
    mLastActionTitle = "";
    mLastDeviceAddress = null;
    mLastBatteryLevel = -1;
    mBatteryAlphaRestoreNeeded = false;
  }

  public void setPadding(int n, int n2, int n3, int n4) {
    super.setPadding(0, 0, 0, 0);
  }

  private int dpToPx(int dp) {
    return (int) (dp * getResources().getDisplayMetrics().density);
  }
}
