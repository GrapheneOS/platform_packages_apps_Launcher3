package com.android.launcher3.util;

import android.content.Context;

public class MSMHProxy {
    public static MediaSessionManagerHelper INSTANCE(Context context) {
        return MediaSessionManagerHelper.Companion.getInstance(context);
    }
}
