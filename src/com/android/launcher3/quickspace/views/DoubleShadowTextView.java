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
package com.android.launcher3.quickspace.views;

import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.content.Context;
import android.graphics.Canvas;
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
        mShadowInfo = ShadowInfo.Companion.fromContext(context, attrs, defStyle);
        setShadowLayer(
                Math.max(mShadowInfo.getKeyShadowBlur() +
                mShadowInfo.getKeyShadowOffsetX(),
                mShadowInfo.getAmbientShadowBlur()), 0f, 0f,
                mShadowInfo.getKeyShadowColor());
    }

    private boolean skipDoubleShadow() {
        int textAlpha = Color.alpha(getCurrentTextColor());
        int keyShadowAlpha = Color.alpha(mShadowInfo.getKeyShadowColor());
        int ambientShadowAlpha = Color.alpha(mShadowInfo.getAmbientShadowColor());
        if (textAlpha == 0 || (keyShadowAlpha == 0 && ambientShadowAlpha == 0)) {
            getPaint().clearShadowLayer();
            return true;
        } else if (ambientShadowAlpha > 0 && keyShadowAlpha == 0) {
            getPaint().setShadowLayer(mShadowInfo.getAmbientShadowBlur(), 0, 0,
                    getTextShadowColor(mShadowInfo.getAmbientShadowColor(), textAlpha));
            return true;
        } else if (keyShadowAlpha > 0 && ambientShadowAlpha == 0) {
            getPaint().setShadowLayer(
                    mShadowInfo.getKeyShadowBlur(),
                    mShadowInfo.getKeyShadowOffsetX(),
                    mShadowInfo.getKeyShadowOffsetY(),
                    getTextShadowColor(mShadowInfo.getKeyShadowColor(), textAlpha));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        // If text is transparent or shadow alpha is 0, don't draw any shadow
        if (skipDoubleShadow()) {
            super.onDraw(canvas);
            return;
        }
        getPaint().setShadowLayer(mShadowInfo.getKeyShadowBlur(), 0, mShadowInfo.getKeyShadowOffsetX(), mShadowInfo.getKeyShadowColor());
        super.onDraw(canvas);
    }

    // Multiplies the alpha of shadowColor by textAlpha.
    private static int getTextShadowColor(int shadowColor, int textAlpha) {
        return setColorAlphaBound(shadowColor,
                Math.round(Color.alpha(shadowColor) * textAlpha / 255f));
    }
}
