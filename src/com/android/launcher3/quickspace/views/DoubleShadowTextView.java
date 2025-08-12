/*
 * Copyright (C) 2018-2025 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law of agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.quickspace.views;

import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.launcher3.views.ShadowInfo;

public class DoubleShadowTextView extends TextView {

    private final ShadowInfo mShadowInfo;

    public DoubleShadowTextView(Context context) {
        this(context, null);
    }

    public DoubleShadowTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DoubleShadowTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // Initialize mShadowInfo immediately after the super constructor.
        mShadowInfo = ShadowInfo.Companion.fromContext(context, attrs, defStyle);
        // Now that mShadowInfo is initialized, apply the shadow layer state.
        updateShadowLayer();
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        // This will be called by the super constructor, but our null-check will handle it.
        // It's also needed for any subsequent color changes.
        updateShadowLayer();
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        super.setTextColor(colors);
        updateShadowLayer();
    }

    private void updateShadowLayer() {
        // Guard against calls from the super constructor before mShadowInfo is initialized.
        if (mShadowInfo == null) {
            return;
        }

        int textAlpha = Color.alpha(getCurrentTextColor());
        int keyShadowAlpha = Color.alpha(mShadowInfo.getKeyShadowColor());
        int ambientShadowAlpha = Color.alpha(mShadowInfo.getAmbientShadowColor());

        if (textAlpha == 0 || (keyShadowAlpha == 0 && ambientShadowAlpha == 0)) {
            getPaint().clearShadowLayer();
        } else if (ambientShadowAlpha > 0 && keyShadowAlpha == 0) {
            // Ambient shadow only
            getPaint().setShadowLayer(mShadowInfo.getAmbientShadowBlur(), 0, 0,
                    getTextShadowColor(mShadowInfo.getAmbientShadowColor(), textAlpha));
        } else {
            // Key shadow only, or both shadows present (use key shadow as primary)
            getPaint().setShadowLayer(
                    mShadowInfo.getKeyShadowBlur(),
                    mShadowInfo.getKeyShadowOffsetX(),
                    mShadowInfo.getKeyShadowOffsetY(),
                    getTextShadowColor(mShadowInfo.getKeyShadowColor(), textAlpha));
        }
    }

    // Multiplies the alpha of shadowColor by textAlpha.
    private static int getTextShadowColor(int shadowColor, int textAlpha) {
        return setColorAlphaBound(shadowColor,
                Math.round(Color.alpha(shadowColor) * textAlpha / 255f));
    }
}
