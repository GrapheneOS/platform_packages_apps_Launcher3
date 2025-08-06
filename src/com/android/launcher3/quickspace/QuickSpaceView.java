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
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    public ViewGroup mDateWeatherRow;
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

    private boolean mIsAlternateStyle = false;

    private QuickSpaceActionReceiver mActionReceiver;
    public QuickspaceController mController;

    private int mCurrentStyle = -1;

    public QuickSpaceView(Context context, AttributeSet set) {
        super(context, set);
        mController = new QuickspaceController(context);
        mColorStateList = ColorStateList.valueOf(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        mQuickspaceBackgroundRes = R.drawable.bg_quickspace;
        setClipChildren(false);
    }

    @Override
    public void onDataUpdated() {
        int style = Integer.parseInt(LauncherPrefs.QUICKSPACE_UI_STYLE.get(getContext()));
        if (mEventTitle == null || mCurrentStyle != style) {
            prepareLayout(style);
        }
        mIsQuickEvent = mController.isQuickEvent();
        mWeatherAvailable = mController.isWeatherAvailable();
        updateView(style);
    }

    private void updateView(int style) {
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

    private final void loadDoubleLine(boolean useAlternativeQuickspaceUI) {
        setBackgroundResource(mQuickspaceBackgroundRes);
        mEventTitle.setText(mController.getEventController().getTitle());
        if (useAlternativeQuickspaceUI) {
            String greetingsExt = mController.getEventController().getGreetings();
            if (greetingsExt != null && !greetingsExt.isEmpty()) {
                mGreetingsExt.setVisibility(View.VISIBLE);
                mGreetingsExt.setText(greetingsExt);
                mGreetingsExt.setEllipsize(TruncateAt.END);
                mGreetingsExt.setOnClickListener(mController.getEventController().getAction());
            } else {
                mGreetingsExt.setVisibility(View.GONE);
            }
            String greetingsExtClock = mController.getEventController().getClockExt();
            if (greetingsExtClock != null && !greetingsExtClock.isEmpty()) {
                mGreetingsExtClock.setVisibility(View.VISIBLE);
                mGreetingsExtClock.setText(greetingsExtClock);
                mGreetingsExtClock.setOnClickListener(mController.getEventController().getAction());
            } else {
                mGreetingsExtClock.setVisibility(View.GONE);
            }
        }
        boolean shouldShowPsa = mIsQuickEvent && (LauncherPrefs.SHOW_QUICKSPACE_PSONALITY.get(getContext()) ||
                        mController.getEventController().isNowPlaying());

        if (shouldShowPsa) {
            maybeSetMarquee(mEventTitle);
            mEventTitle.setOnClickListener(mController.getEventController().getAction());
            mEventTitleSub.setText(mController.getEventController().getActionTitle());
            maybeSetMarquee(mEventTitleSub);
            mEventTitleSub.setOnClickListener(mController.getEventController().getAction());

            if (mEventTitleSub.getVisibility() != View.VISIBLE) {
                animateIn(mEventTitleSub);
            }

            if (useAlternativeQuickspaceUI) {
                if (mController.getEventController().isNowPlaying()) {

                    animateOut(mEventSubIcon);
                    animateIn(mEventTitleSubColored);
                    animateIn(mNowPlayingIcon);
                    mNowPlayingIcon.setOnClickListener(mController.getEventController().getAction());
                    mEventTitleSubColored.setText(getContext().getString(R.string.qe_now_playing_by));
                    mEventTitleSubColored.setOnClickListener(mController.getEventController().getAction());
                } else {
                    setEventSubIcon();
                    animateOut(mEventTitleSubColored);
                    animateOut(mNowPlayingIcon);
                }
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
        bindWeather(mWeatherContentSub, mWeatherTempSub, mWeatherIconSub);
        boolean hasGoogleApp = isPackageEnabled("com.google.android.googlequicksearchbox", getContext());
        mWeatherContentSub.setOnClickListener(hasGoogleApp ? getActionReceiver().getWeatherAction() : null);
    }

    private void maybeSetMarquee(TextView tv) {
        tv.setSelected(false);
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
        if (!mWeatherAvailable || mController.getEventController().isNowPlaying()) {
            container.setVisibility(View.GONE);
            return;
        }
        String weatherTemp = mController.getWeatherTemp();
        if (weatherTemp == null || weatherTemp.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        if (container.getVisibility() != View.VISIBLE) {
            animateIn(container);
        }
        title.setText(weatherTemp);
        icon.setImageDrawable(mController.getWeatherIcon());
    }

    private QuickSpaceActionReceiver getActionReceiver() {
        if (mActionReceiver == null) {
            mActionReceiver = new QuickSpaceActionReceiver(getContext());
        }
        return mActionReceiver;
    }

    private void loadLargeStyle() {
        if (mQuickspaceDayOfWeek == null) return; // Views not inflated for this style

        boolean accentEnabled = LauncherPrefs.QUICKSPACE_VOLTAGE_ACCENT.get(getContext());
        if (mQuickspaceClock != null) {
            mQuickspaceClock.setAccentEnabled(accentEnabled);
        }

        mQuickspaceDayOfWeek.setText(QuickEventsController.getDayOfWeek(getContext()));
        mQuickspaceDate.setText(mController.getEventController().getShortDate(getContext()));
        mWeatherContentSub.setVisibility(View.VISIBLE);

        mDateWeatherRow.setVisibility(View.VISIBLE);

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

        boolean hasGoogleApp = isPackageEnabled("com.google.android.googlequicksearchbox", getContext());
        mWeatherContentSub.setOnClickListener(hasGoogleApp ? getActionReceiver().getWeatherAction() : null);

        bindWeather(mWeatherContentSub, mWeatherTempSub, mWeatherIconSub);

        boolean isNowPlaying = mController.getEventController().isNowPlaying();
        if (isNowPlaying) {
            mContextualInfoRow.setVisibility(View.VISIBLE);
            mPSAMessage.setVisibility(View.GONE);
            mNowPlayingContent.setVisibility(View.VISIBLE);
            String nowPlaying = mController.getEventController().getTitle() + " - " + mController.getEventController().getActionTitle();
            mNowPlayingText.setText(nowPlaying);
            mNowPlayingContent.setOnClickListener(mController.getEventController().getAction());
        } else {
            mNowPlayingContent.setVisibility(View.GONE);
            if (mIsQuickEvent && LauncherPrefs.SHOW_QUICKSPACE_PSONALITY.get(getContext())) {
                mContextualInfoRow.setVisibility(View.VISIBLE);
                mNowPlayingContent.setVisibility(View.GONE);
                mPSAMessage.setVisibility(View.VISIBLE);
                mPSAMessage.setText(mController.getEventController().getActionTitle());
                mPSAMessage.setOnClickListener(mController.getEventController().getAction());
                maybeSetMarquee(mPSAMessage);
            } else {
                mContextualInfoRow.setVisibility(View.GONE);
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
            mDateWeatherRow = findViewById(R.id.date_weather_row);
            mContextualInfoRow = findViewById(R.id.contextual_info_row);
        }
    }

    private void prepareLayout(int style) {
        mCurrentStyle = style;
        int indexOfChild = indexOfChild(mQuickspaceContent);
        removeView(mQuickspaceContent);
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
        if (mQuickspaceContent.getVisibility() != View.VISIBLE) {
        	 mQuickspaceContent.setVisibility(View.VISIBLE);
            mQuickspaceContent.setAlpha(0.0f);
            mQuickspaceContent.animate().setDuration(200).alpha(1.0f);
        }
    }

    private void animateIn(View view) {
        if (view.getVisibility() == View.VISIBLE && view.getAlpha() == 1f) {
            return; // Already visible
        }
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.setTranslationY(view.getHeight() / 2f);
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    private void animateOut(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return; // Already hidden
        }
        view.animate()
            .alpha(0f)
            .translationY(view.getHeight() / 2f)
            .setDuration(400)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(() -> view.setVisibility(View.GONE))
            .start();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached)
            return;

        mAttached = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mAttached)
            return;

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
        mController.onPause();
    }

    public void onResume() {
        if (mController != null && mFinishedInflate) {
            mController.addListener(this);
        }
        mController.onResume();
    }

    public void onDestroy() {
        mController.onDestroy();
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
        mDateWeatherRow = null;
        mContextualInfoRow = null;
    }

    public void setPadding(int n, int n2, int n3, int n4) {
        super.setPadding(0, 0, 0, 0);
    }
}
