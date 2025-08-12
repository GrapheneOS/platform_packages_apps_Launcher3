/*
 * Copyright (C) 2018-2025 crDroid Android Project
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

import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.view.ViewPropertyAnimator;
import android.widget.TextView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.quickspace.views.AccentedTextClock;
import com.android.launcher3.util.Themes;

import com.android.launcher3.quickspace.QuickspaceController.OnDataListener;
import com.android.launcher3.quickspace.receivers.QuickSpaceActionReceiver;

public class QuickSpaceView extends FrameLayout implements OnDataListener {

    private static final String TAG = "Launcher3:QuickSpaceView";
    private static final boolean DEBUG = false;

    public final ColorStateList mColorStateList;
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
    private boolean mViewsLoaded = false;
    private String mLastEventTitle = "";
    private String mLastWeatherTemp = "";
    private boolean mLastNowPlayingState = false;
    private String mLastPSAMessage = "";
    private String mLastActionTitle = "";

    private ViewPropertyAnimator mCurrentAnimateIn;
    private ViewPropertyAnimator mCurrentAnimateOut;
    private int mLastEventTitleHash = 0;
    private int mLastWeatherTempHash = 0;
    private int mLastActionTitleHash = 0;
    private long mLastUpdateTime = 0;
    private static final long MIN_UPDATE_INTERVAL = 100;

    private QuickSpaceActionReceiver mActionReceiver;

    private boolean mIsLayoutSuppressed = false;
    private final Runnable mDeferredUpdateRunnable = new Runnable() {
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
        mColorStateList = ColorStateList.valueOf(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        mQuickspaceBackgroundRes = R.drawable.bg_quickspace;
        setClipChildren(false);
    }

    @Override
    public void onDataUpdated() {
	if (mDestroyed) {
            return;
        }

        removeCallbacks(mDeferredUpdateRunnable);
        postDelayed(mDeferredUpdateRunnable, 16); // ~60fps
    }
    
    private void performDeferredUpdate() {
	if (mDestroyed) {
            return;
        }

        int style = Integer.parseInt(LauncherPrefs.QUICKSPACE_UI_STYLE.get(getContext()));
        boolean styleChanged = mCurrentStyle != style;
        if (!mViewsLoaded || styleChanged) {
            prepareLayout(style);
            mViewsLoaded = true;
        }
        mIsQuickEvent = mController.isQuickEvent();
        mWeatherAvailable = mController.isWeatherAvailable();

        if (styleChanged || !mViewsLoaded || hasDataChanged()) {
            updateView(style);

            if (styleChanged && !mIsLayoutSuppressed) {
                requestLayout();
            }

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
            case 1: // Extended
            case 0: // Default
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


        boolean changed = mLastEventTitleHash != eventTitleHash ||
                         mLastWeatherTempHash != weatherTempHash ||
                         mLastActionTitleHash != actionTitleHash;

        if (changed) {
            mLastEventTitleHash = eventTitleHash;
            mLastWeatherTempHash = weatherTempHash;
            mLastActionTitleHash = actionTitleHash;
            mLastUpdateTime = currentTime;

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
        boolean titleChanged = updateTextViewIfNeeded(mEventTitle, eventTitle, false);

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
        boolean shouldShowPsa = mIsQuickEvent && (LauncherPrefs.SHOW_QUICKSPACE_PSONALITY.get(getContext()) ||
                        mController.getEventController().isNowPlaying());

        updatePsaContent(shouldShowPsa, useAlternativeQuickspaceUI, titleChanged);
        updateWeatherContent();

        endBatchEdit();
    }
    
    private void updatePsaContent(boolean shouldShowPsa, boolean useAlternativeQuickspaceUI, boolean titleChanged) {
        if (shouldShowPsa) {
            if (titleChanged) {
                maybeSetMarquee(mEventTitle);
            }
            mEventTitle.setOnClickListener(mController.getEventController().getAction());

            String actionTitle = mController.getEventController().getActionTitle();
            if (updateTextViewIfNeeded(mEventTitleSub, actionTitle, false)) {
                maybeSetMarquee(mEventTitleSub);
            }
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
    
    private boolean updateTextViewIfNeeded(TextView textView, CharSequence newText, boolean setVisibility) {
        if (textView == null) return false;

        boolean hasText = !TextUtils.isEmpty(newText);

        int currentVisibility = textView.getVisibility();
        CharSequence currentText = textView.getText();
        int desiredVisibility = hasText ? View.VISIBLE : View.GONE;

        if (setVisibility && currentVisibility == desiredVisibility && 
            TextUtils.equals(currentText, newText)) {
            return false;
        }

        // Update visibility if requested
        if (setVisibility) {
            if (currentVisibility != desiredVisibility) {
                textView.setVisibility(desiredVisibility);
            }
        }

        // Update text content only if it has changed
        if (!TextUtils.equals(currentText, newText)) {
            textView.setText(newText);
            return true;
        }
        return false;
    }

    private void maybeSetMarquee(TextView tv) {
        if (tv == null) return;
        tv.setSelected(false);
        tv.removeCallbacks(null);
        tv.setEllipsize(TruncateAt.END);
        final float textWidth = tv.getPaint().measureText(tv.getText().toString());
        tv.post(() -> {
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
            mEventSubIcon.setImageTintList(mController.getEventController().isNowPlaying() ? null : mColorStateList);
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

        if (mQuickspaceDayOfWeek == null) return; // Views not inflated for this style

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

        View.OnClickListener openClockListener = v -> {
            try {
                getContext().startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
            }
        };

        View.OnClickListener openCalendarListener = v -> {
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
        mQuickspaceDayOfWeek.setOnClickListener(openClockListener); // Both clock and day open the Clock app
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

            String nowPlaying = mController.getEventController().getTitle() + " - " + mController.getEventController().getActionTitle();
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

        endBatchEdit();
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
        if (mCurrentStyle == 1) { // Extended style
            mGreetingsExtClock = (TextView) findViewById(R.id.extended_greetings_clock);
            mGreetingsExt = (TextView) findViewById(R.id.extended_greetings);
        }

        if (mCurrentStyle == 2) { // Large style
            mQuickspaceDayOfWeek = findViewById(R.id.quickspace_day_of_week);
            mQuickspaceClock = (AccentedTextClock) findViewById(R.id.quickspace_clock);
            mQuickspaceDate = findViewById(R.id.quickspace_date);
            mPSAMessage = findViewById(R.id.quickspace_psa_message);
            mNowPlayingContent = findViewById(R.id.now_playing_content);
            mNowPlayingText = findViewById(R.id.now_playing_text);
            mContextualInfoRow = findViewById(R.id.contextual_info_row);
        }
        boolean hasGoogleApp = isPackageEnabled("com.google.android.googlequicksearchbox", getContext());
        if (mWeatherContentSub != null) {
            mWeatherContentSub.setOnClickListener(hasGoogleApp ? getActionReceiver().getWeatherAction() : null);
        }

       View.OnClickListener mediaClickListener = v -> {
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
            return; // Avoid unnecessary layout inflation
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
            mQuickspaceContent.animate()
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

        if (view.getVisibility() == View.VISIBLE && view.getAlpha() == 1f) {
            return; // Already visible
        }

        view.animate().cancel();

        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.setTranslationY(view.getHeight() / 2f);
        mCurrentAnimateIn = view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(null);
        mCurrentAnimateIn.start();
    }

     private void animateOut(View view) {
        if (mDestroyed || mPendingDestroy || view == null) {
            return;
        }

        if (view.getVisibility() != View.VISIBLE) {
            return; // Already hidden
        }
        view.animate().cancel();
        
        mCurrentAnimateOut = view.animate()
            .alpha(0f)
            .translationY(view.getHeight() / 2f)
            .setDuration(250)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(() -> {
                view.setVisibility(View.GONE);
                view.setTranslationY(0f);
                view.setAlpha(1f);
            });
        
        mCurrentAnimateOut.start();
    }

    private void cancelAllAnimations() {
        if (mCurrentAnimateIn != null) {
            mCurrentAnimateIn.cancel();
            mCurrentAnimateIn = null;
        }
        if (mCurrentAnimateOut != null) {
            mCurrentAnimateOut.cancel();
            mCurrentAnimateOut = null;
        }
        
        if (mEventTitleSub != null) mEventTitleSub.animate().cancel();
        if (mEventSubIcon != null) mEventSubIcon.animate().cancel();
        if (mEventTitleSubColored != null) mEventTitleSubColored.animate().cancel();
        if (mNowPlayingIcon != null) mNowPlayingIcon.animate().cancel();
        if (mWeatherContentSub != null) mWeatherContentSub.animate().cancel();
        if (mQuickspaceContent != null) mQuickspaceContent.animate().cancel();
    }
    
    private void clearClickListeners() {
        View[] clickableViews = {
            mEventTitle, mEventTitleSub, mEventTitleSubColored,
            mGreetingsExt, mGreetingsExtClock, mEventSubIcon,
            mNowPlayingIcon, mWeatherContentSub, mQuickspaceDayOfWeek,
            mQuickspaceClock, mQuickspaceDate, mPSAMessage,
            mNowPlayingContent, mNowPlayingText
        };
        
        for (View view : clickableViews) {
            if (view != null) {
                view.setOnClickListener(null);
            }
        }
    }

    private void safeRemoveListener() {
        if (mController != null && !mDestroyed) {
            try {
                mController.removeListener(this);
            } catch (Exception e) {
                // Ignore - controller might be destroyed
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
        post(() -> {
            if (mController != null && mFinishedInflate && !mDestroyed && mAttached) {
            mController.addListener(this);
            }
        });
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mAttached) {
             return;
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
        mBubbleTextView.setTag(new ItemInfo() {
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
            // Just remove this view's listener
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
        
        // Nullify Voltage style views
        mQuickspaceDayOfWeek = null;
        mQuickspaceClock = null;
        mQuickspaceDate = null;
        mPSAMessage = null;
        mNowPlayingContent = null;
        mNowPlayingText = null;
        mContextualInfoRow = null;
        setBackground(null);
        mAttached = false;
        mFinishedInflate = false;
        mViewsLoaded = false;
        mLastEventTitle = "";
        mLastWeatherTemp = "";
        mLastActionTitle = "";
    }

    public void setPadding(int n, int n2, int n3, int n4) {
        super.setPadding(0, 0, 0, 0);
    }
}
