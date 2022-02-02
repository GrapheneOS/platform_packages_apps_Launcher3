package com.android.launcher3.qsb;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import androidx.core.view.ViewCompat;
import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.qsb.QsbContainerView;
import com.android.launcher3.views.ActivityContext;
import android.view.View;

public class QsbLayout extends FrameLayout {

    ImageButton lensIcon;
    AssistantIconView assistantIcon;
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
        assistantIcon.setIcon();
        lensIcon = findViewById(R.id.lens_icon);
        String searchPackage = QsbContainerView.getSearchWidgetPackageName(mContext);
        setOnClickListener(view -> {
            mContext.startActivity(new Intent("android.search.action.GLOBAL_SEARCH").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK).setPackage(searchPackage));
        });
        if (searchPackage == "com.google.android.googlequicksearchbox") {
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
        Intent lensIntent = Intent.makeMainActivity(new ComponentName("com.google.ar.lens", "com.google.vr.apps.ornament.app.lens.LensLauncherActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (getContext().getPackageManager().resolveActivity(lensIntent, 0) == null){
            return;
        }
        lensIcon.setVisibility(View.VISIBLE);
        lensIcon.setImageResource(R.drawable.ic_lens_color);

        lensIcon.setOnClickListener(view -> {
            mContext.startActivity(lensIntent);
        });
    }

}
