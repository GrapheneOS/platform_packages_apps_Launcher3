package com.android.launcher3.quickspace.views;

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.widget.TextClock;

import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

public class AccentedTextClock extends TextClock {

    private boolean mAccentEnabled = false;
    private int mAccentColor;

    public AccentedTextClock(Context context) {
        this(context, null);
    }

    public AccentedTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Using android.R.attr.colorAccent as it's a more reliable system-wide
        // attribute for the theme's accent color. The launcher-specific
        // 'workspaceAccentColor' can often be the same as the text color,
        // making the accent invisible.
        mAccentColor = Themes.getAttrColor(context, android.R.attr.colorAccent);
    }

    public void setAccentEnabled(boolean enabled) {
        if (mAccentEnabled == enabled) {
            return;
        }
        mAccentEnabled = enabled;
        // Force the clock to update its text immediately to reflect the change.
        forceRefresh();
    }

    private void forceRefresh() {
        // Re-setting the format is a safe way to trigger the internal onTimeChanged()
        // method, which in turn calls our overridden setText().
        setFormat12Hour(getFormat12Hour());
        setFormat24Hour(getFormat24Hour());
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        // Only apply the accent if it's enabled, we have text, and the accent color
        // is actually different from the main text color.
        if (mAccentEnabled && text != null && text.length() > 0 && getCurrentTextColor() != mAccentColor) {
            SpannableStringBuilder ssb = new SpannableStringBuilder(text);
            
            // Apply the accent color span to the first character.
            ssb.setSpan(
                new ForegroundColorSpan(mAccentColor),
                0, // Start index
                1, // End index (exclusive)
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            super.setText(ssb, BufferType.SPANNABLE);
        } else {
            // If accent is disabled or colors match, ensure we show plain text
            // to remove any previous spans.
            if (text instanceof Spannable) {
                super.setText(text.toString(), BufferType.NORMAL);
            } else {
                super.setText(text, type);
            }
        }
    }
}
