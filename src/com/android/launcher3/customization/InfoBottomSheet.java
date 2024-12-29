package com.android.launcher3.customization;

import android.app.ActivityOptions;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Rect;

import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.widget.WidgetsBottomSheet;
import com.android.launcher3.util.PackageManagerHelper;

public class InfoBottomSheet extends WidgetsBottomSheet {
    private final FragmentManager mFragmentManager;
    protected static Rect mSourceBounds;
    protected static Context mViewContext;

    public InfoBottomSheet(Context context) {
        this(context, null);
    }

    public InfoBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFragmentManager = Launcher.getLauncher(context).getFragmentManager();
    }

    public void configureBottomSheet(Rect sourceBounds, Context context) {
        mSourceBounds = sourceBounds;
        mViewContext = context;
    }

    @Override
    public void populateAndShow(ItemInfo itemInfo) {
        super.populateAndShow(itemInfo);
        TextView title = findViewById(R.id.title);
        title.setText((itemInfo.title == null || itemInfo.title.isEmpty()) 
            ? getContext().getResources().getString(R.string.app_info_title) 
            : itemInfo.title);

        PrefsFragment fragment =
                (PrefsFragment) mFragmentManager.findFragmentById(R.id.sheet_prefs);
        fragment.loadForApp(itemInfo);
    }

    @Override
    public void onDetachedFromWindow() {
        Fragment pf = mFragmentManager.findFragmentById(R.id.sheet_prefs);
        if (pf != null) {
            mFragmentManager.beginTransaction()
                    .remove(pf)
                    .commitAllowingStateLoss();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onWidgetsBound() {
    }

    public static class PrefsFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
        private static final String KEY_ICON_PACK = "pref_app_info_icon_pack";
        private static final String KEY_SOURCE = "pref_app_info_source";
        private static final String KEY_LAST_UPDATE = "pref_app_info_last_update";
        private static final String KEY_VERSION = "pref_app_info_version";
        private static final String KEY_MORE = "pref_app_info_more";

        private Context mContext;

        private ItemInfo mItemInfo;

        private ComponentName mComponent;
        private ComponentKey mKey;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mContext = getActivity();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.app_info_preferences);
        }

        @Override
        public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
                                                 Bundle savedInstanceState) {
            RecyclerView view = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
            view.setOverScrollMode(View.OVER_SCROLL_NEVER);
            return view;
        }

        public void loadForApp(ItemInfo itemInfo) {
            mComponent = itemInfo.getTargetComponent();
            mItemInfo = itemInfo;
            mKey = new ComponentKey(mComponent, itemInfo.user);
            MetadataExtractor extractor = new MetadataExtractor(mContext, mComponent);

            Preference iconPack = findPreference(KEY_ICON_PACK);
            iconPack.setOnPreferenceChangeListener(this);
            iconPack.setSummary(R.string.app_info_icon_pack_none);
            findPreference(KEY_SOURCE).setSummary(extractor.getSource());
            findPreference(KEY_LAST_UPDATE).setSummary(extractor.getLastUpdate());
            findPreference(KEY_VERSION).setSummary(mContext.getString(
                    R.string.app_info_version_value,
                    extractor.getVersionName(),
                    extractor.getVersionCode()));
            findPreference(KEY_MORE).setOnPreferenceClickListener(this);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (KEY_ICON_PACK.equals(preference.getKey())) {
                // Reload in launcher.
            }
            return false;
        }

        private void onMoreClick() {
            PackageManagerHelper.startDetailsActivityForInfo(InfoBottomSheet.mViewContext, mItemInfo,
                    InfoBottomSheet.mSourceBounds, ActivityOptions.makeBasic().toBundle());
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (KEY_MORE.equals(preference.getKey())) {
                  onMoreClick();
            }
            return false;
        }
    }
}
