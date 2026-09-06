package com.android.launcher3.allapps.search;

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_RESULT;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.PrivateProfileManager;
import com.android.launcher3.views.ActivityContext;

public class PrivateSpaceSearchAdapterProvider extends DefaultSearchAdapterProvider {
    public PrivateSpaceSearchAdapterProvider(ActivityContext launcher) {
        super(launcher);
    }

    @Override
    public void onBindView(AllAppsGridAdapter.ViewHolder holder, int position) {
        super.onBindView(holder, position);
        if (holder.getItemViewType() == VIEW_TYPE_PRIVATE_SPACE_RESULT) {
            holder.itemView.setOnFocusChangeListener(
                    mLauncher.getAppsView().getSearchFocusChangeListener());
        }
    }

    @Override
    public boolean isViewSupported(int viewType) {
        return viewType == VIEW_TYPE_PRIVATE_SPACE_RESULT || super.isViewSupported(viewType);
    }

    @Override
    public AllAppsGridAdapter.ViewHolder onCreateViewHolder(
            LayoutInflater layoutInflater, ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_PRIVATE_SPACE_RESULT) {
            View view = layoutInflater.inflate(R.layout.private_space_search_result, parent, false);
            view.setOnClickListener(
                    clickedView -> {
                        ExtendedEditText editText =
                                mLauncher.getAppsView().getSearchUiManager().getEditText();
                        if (editText != null) {
                            editText.hideKeyboard();
                        }
                        PrivateProfileManager privateProfileManager =
                                mLauncher.getAppsView().getPrivateProfileManager();
                        if (privateProfileManager != null) {
                            privateProfileManager.openPrivateSpaceFromSearch(clickedView);
                        }
                    });
            return new AllAppsGridAdapter.ViewHolder(view);
        }
        return super.onCreateViewHolder(layoutInflater, parent, viewType);
    }

    @Override
    public int getItemsPerRow(int viewType, int appsPerRow) {
        return viewType == VIEW_TYPE_PRIVATE_SPACE_RESULT
                ? 1
                : super.getItemsPerRow(viewType, appsPerRow);
    }

    @Override
    public boolean launchHighlightedItem() {
        View highlightedView = getHighlightedItem();
        if (highlightedView != null
                && highlightedView.getId() == R.id.private_space_search_result) {
            return highlightedView.performClick();
        }
        return super.launchHighlightedItem();
    }
}
