package com.android.launcher3.qsb;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.qsb.QsbContainerView;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Themes;
import android.view.View;

public class QsbLayout extends FrameLayout implements Reorderable {

    private static final String TAG = "QsbLayout";

    private ImageView micIcon;
    private ImageView gIcon;
    private ImageView lensIcon;
    private ImageView geminiIcon;
    private FrameLayout inner;

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    private float mScaleForReorderBounce = 1f;

    private boolean mIsThemed;

    public QsbLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public QsbLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        micIcon = findViewById(R.id.mic_icon);
        gIcon = findViewById(R.id.g_icon);
        lensIcon = findViewById(R.id.lens_icon);
        geminiIcon = findViewById(R.id.gemini_icon);
        inner = findViewById(R.id.inner);

        setUpMainSearch();
        setUpBackground();
        clipIconRipples();

        mIsThemed = LauncherPrefs.DOCK_THEME.get(getContext());

        setupGIcon();
        setupLensIcon();
        setupMicIcon();
        setupGeminiIcon();
    }

    private void clipIconRipples() {
        float cornerRadius = getCornerRadius();
        PaintDrawable pd = new PaintDrawable(Color.TRANSPARENT);
        pd.setCornerRadius(cornerRadius);
        micIcon.setClipToOutline(cornerRadius > 0);
        micIcon.setBackground(pd);
        lensIcon.setClipToOutline(cornerRadius > 0);
        lensIcon.setBackground(pd);
        gIcon.setClipToOutline(cornerRadius > 0);
        gIcon.setBackground(pd);
        geminiIcon.setClipToOutline(cornerRadius > 0);
        geminiIcon.setBackground(pd);
    }

    private void setUpBackground() {
        float cornerRadius = getCornerRadius();
        int alphaValue = (LauncherPrefs.HOTSEAT_QSB_OPACITY.get(getContext()) * 255) / 100;
        int baseColor = Themes.getAttrColor(getContext(), R.attr.qsbFillColor);
        if (LauncherPrefs.DOCK_THEME.get(getContext()))
            baseColor = Themes.getAttrColor(getContext(), R.attr.qsbFillColorThemed);
        int color = Color.argb(alphaValue, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        float strokeWidth = LauncherPrefs.HOTSEAT_QSB_STROKE_WIDTH.get(getContext());

        PaintDrawable backgroundDrawable = new PaintDrawable(color);
        backgroundDrawable.setCornerRadius(cornerRadius);

        if (strokeWidth != 0f) {
            PaintDrawable strokeDrawable = new PaintDrawable(Themes.getColorAccent(getContext()));
            strokeDrawable.getPaint().setStyle(Paint.Style.STROKE);
            strokeDrawable.getPaint().setStrokeWidth(strokeWidth);
            strokeDrawable.setCornerRadius(cornerRadius);
            LayerDrawable combinedDrawable = new LayerDrawable(new Drawable[]{backgroundDrawable, strokeDrawable});

            inner.setClipToOutline(cornerRadius > 0);
            inner.setBackground(combinedDrawable);
        } else {
            inner.setClipToOutline(cornerRadius > 0);
            inner.setBackground(backgroundDrawable);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(width, height);

        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child != null) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setOnClickListener(null);
        if (gIcon != null) gIcon.setOnClickListener(null);
        if (lensIcon != null) lensIcon.setOnClickListener(null);
        if (micIcon != null) micIcon.setOnClickListener(null);
        if (geminiIcon != null) geminiIcon.setOnClickListener(null);
        if (inner != null) inner.setBackground(null);
    }

    private void setUpMainSearch() {
        try {
            setOnClickListener(view -> {
                Intent intent = new Intent();
                intent.setAction("android.search.action.GLOBAL_SEARCH");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.setPackage(QsbContainerView.getSearchWidgetPackageName(view.getContext()));
                view.getContext().startActivity(intent);
            });
        } catch (Exception e) {
            // Do nothing
        }
    }

    private void setupGIcon() {
        try {
            gIcon.setImageResource(mIsThemed ? R.drawable.ic_super_g_themed : R.drawable.ic_super_g_color);
            gIcon.setOnClickListener(view -> {
                Intent intent = view.getContext().getPackageManager().getLaunchIntentForPackage(Utilities.GSA_PACKAGE);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    view.getContext().startActivity(intent);
                }
            });
        } catch (Exception e) {
            // Do nothing
        }
    }

    private void setupLensIcon() {
        try {
            lensIcon.setImageResource(mIsThemed ? R.drawable.ic_lens_themed : R.drawable.ic_lens_color);
            lensIcon.setOnClickListener(view -> {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setComponent(new ComponentName(Utilities.GSA_PACKAGE, Utilities.LENS_ACTIVITY));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.setData(Uri.parse(Utilities.LENS_URI));
                intent.putExtra("LensHomescreenShortcut", true);
                view.getContext().startActivity(intent);
            });
        } catch (Exception e) {
            lensIcon.setVisibility(View.GONE);
        }
    }

    private void setupMicIcon() {
        try {
            boolean isMusicSearch = Utilities.isMusicSearchEnabled(getContext());
            if (isMusicSearch) {
                micIcon.setImageResource(mIsThemed ? R.drawable.ic_music_themed : R.drawable.ic_music_color);
            } else {
                micIcon.setImageResource(mIsThemed ? R.drawable.ic_mic_themed : R.drawable.ic_mic_color);
            }
            micIcon.setOnClickListener(view -> {
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                if (isMusicSearch) {
                    intent.setAction("com.google.android.googlequicksearchbox.MUSIC_SEARCH");
                    intent.setPackage(QsbContainerView.getSearchWidgetPackageName(view.getContext()));
                } else {
                    intent.setAction("android.intent.action.VOICE_COMMAND");
                }
                view.getContext().startActivity(intent);
            });
        } catch (Exception e) {
            micIcon.setVisibility(View.GONE);
        }
    }

    private void setupGeminiIcon() {
        if (geminiIcon == null) return;

        if (!Utilities.isPackageInstalled(getContext(), Utilities.GEMINI_PACKAGE)) {
            geminiIcon.setVisibility(View.GONE);
            return;
        }

        geminiIcon.setVisibility(View.VISIBLE);
        geminiIcon.setImageResource(mIsThemed
                ? R.drawable.ic_gemini_themed
                : R.drawable.ic_gemini_color);

        geminiIcon.setOnClickListener(view -> {
            Context ctx = view.getContext();
            try {
                Intent intent = view.getContext().getPackageManager().getLaunchIntentForPackage(Utilities.GEMINI_PACKAGE);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    view.getContext().startActivity(intent);
                }
            } catch (Exception e) {
                geminiIcon.setVisibility(View.GONE);
                Log.e(TAG, "Gemini launch failed", e);
            }
        });
    }

    private float getCornerRadius() {
        Resources res = getContext().getResources();
        float qsbWidgetHeight = res.getDimension(R.dimen.qsb_widget_height);
        float qsbWidgetPadding = res.getDimension(R.dimen.qsb_widget_vertical_padding);
        float innerHeight = qsbWidgetHeight - 2 * qsbWidgetPadding;
        return (innerHeight / 2) * ((float)LauncherPrefs.SEARCH_RADIUS_SIZE.get(getContext()) / 100f);
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }
}
