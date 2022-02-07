package com.android.launcher3.qsb;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.core.view.ViewCompat;
import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.qsb.QsbContainerView;
import com.android.launcher3.views.ActivityContext;
import android.view.View;

public class QsbLayout extends FrameLayout {

    ImageView assistantIcon;
    ImageView gIcon;
    ImageView lensIcon;
    Context mContext;

    public QsbLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public QsbLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        assistantIcon = findViewById(R.id.mic_icon);
        gIcon = findViewById(R.id.g_icon);
        lensIcon = findViewById(R.id.lens_icon);

        if (LauncherPrefs.DOCK_THEME.get(mContext)) {
            assistantIcon.setImageResource(R.drawable.ic_mic_themed);
            gIcon.setImageResource(R.drawable.ic_super_g_themed);
            lensIcon.setImageResource(R.drawable.ic_lens_themed);
        } else {
            assistantIcon.setImageResource(R.drawable.ic_mic_color);
            gIcon.setImageResource(R.drawable.ic_super_g_color);
            lensIcon.setImageResource(R.drawable.ic_lens_color);
        }

        String searchPackage = QsbContainerView.getSearchWidgetPackageName(mContext);
        setOnClickListener(view -> {
            mContext.startActivity(new Intent("android.search.action.GLOBAL_SEARCH").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK).setPackage(searchPackage));
        });
        if (Utilities.isGSAEnabled(mContext)) {
            setupLensIcon();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int requestedWidth = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        DeviceProfile dp = ActivityContext.lookupContext(mContext).getDeviceProfile();
        int cellWidth = DeviceProfile.calculateCellWidth(requestedWidth, dp.getWorkspaceIconProfile().getCellLayoutBorderSpacePx().x, dp.numShownHotseatIcons);
        int iconSize = (int)(Math.round((dp.getWorkspaceIconProfile().getIconSizePx() * 0.92f)));
        int width = requestedWidth - (cellWidth - iconSize);
        setMeasuredDimension(width, height);

        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child != null) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
        }
    }

    private void setupLensIcon() {
        lensIcon.setVisibility(View.VISIBLE);
        try {
            lensIcon.setOnClickListener(view -> {
                Intent lensIntent = new Intent();
                lensIntent.setAction(Intent.ACTION_VIEW)
                    .setComponent(new ComponentName(Utilities.GSA_PACKAGE, Utilities.LENS_ACTIVITY))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .setData(Uri.parse(Utilities.LENS_URI))
                    .putExtra("LensHomescreenShortcut", true);
                mContext.startActivity(lensIntent);
            });
        } catch (Exception e) {
            lensIcon.setVisibility(View.GONE);
        }
    }

}
