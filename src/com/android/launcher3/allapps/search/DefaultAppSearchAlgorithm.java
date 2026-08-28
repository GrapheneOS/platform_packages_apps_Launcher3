/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps.search;

import static com.android.launcher3.allapps.AlphabeticalAppsList.PRIVATE_SPACE_PACKAGE;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_EMPTY_SEARCH;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_RESULT;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.AnyThread;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.pm.UserCache.CachedUserInfo;
import com.android.launcher3.search.SearchAlgorithm;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.search.StringMatcherUtility;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.LooperExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * The default search implementation.
 */
public class DefaultAppSearchAlgorithm implements SearchAlgorithm<AdapterItem> {

    private static final int MAX_RESULTS_COUNT = 5;

    private final Context mContext;
    private final LauncherAppState mAppState;
    private final UserCache mUserCache;
    private final Handler mResultHandler;
    private final boolean mAddNoResultsMessage;
    private long mSearchGeneration;

    public DefaultAppSearchAlgorithm(Context context, LooperExecutor uiExecutor) {
        this(context, uiExecutor, false);
    }

    public DefaultAppSearchAlgorithm(
            Context context, LooperExecutor uiExecutor, boolean addNoResultsMessage) {
        mContext = context.getApplicationContext();
        mAppState = LauncherAppState.getInstance(context);
        mUserCache = UserCache.INSTANCE.get(context);
        mResultHandler = new Handler(uiExecutor.getLooper());
        mAddNoResultsMessage = addNoResultsMessage;
    }

    @Override
    public void cancel(boolean interruptActiveRequests) {
        mSearchGeneration++;
        if (interruptActiveRequests) {
            mResultHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void doSearch(String query, SearchCallback<AdapterItem> callback) {
        mSearchGeneration++;
        final long searchGeneration = mSearchGeneration;
        mAppState.getModel().enqueueModelUpdateTask((taskController, dataModel, apps) ->  {
            ArrayList<AdapterItem> result = getTitleMatchResult(
                    apps.data.stream().filter(this::isSearchableApp).toList(), query);
            if (isPrivateSpaceQuery(query) && isPrivateSpaceAvailable()) {
                if (result.size() == MAX_RESULTS_COUNT) {
                    result.remove(result.size() - 1);
                }
                result.add(0, new AdapterItem(VIEW_TYPE_PRIVATE_SPACE_RESULT));
            }
            if (mAddNoResultsMessage && result.isEmpty()) {
                result.add(getEmptyMessageAdapterItem(query));
            }
            mResultHandler.post(() -> {
                if (searchGeneration == mSearchGeneration) {
                    callback.onSearchResult(query, result);
                }
            });
        });
    }

    private static AdapterItem getEmptyMessageAdapterItem(String query) {
        AdapterItem item = new AdapterItem(VIEW_TYPE_EMPTY_SEARCH);
        // Add a place holder info to propagate the query
        AppInfo placeHolder = new AppInfo();
        placeHolder.title = query;
        item.itemInfo = placeHolder;
        return item;
    }

    /**
     * Filters {@link AppInfo}s matching specified query
     */
    @AnyThread
    public static ArrayList<AdapterItem> getTitleMatchResult(List<AppInfo> apps, String query) {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        final String queryTextLower = query.toLowerCase();
        final ArrayList<AdapterItem> result = new ArrayList<>();
        StringMatcherUtility.StringMatcher matcher =
                StringMatcherUtility.StringMatcher.getInstance();

        int resultCount = 0;
        int total = apps.size();
        for (int i = 0; i < total && resultCount < MAX_RESULTS_COUNT; i++) {
            AppInfo info = apps.get(i);
            if (StringMatcherUtility.matches(queryTextLower, info.title.toString(), matcher)) {
                result.add(AdapterItem.asApp(info));
                resultCount++;
            }
        }
        return result;
    }

    private boolean isSearchableApp(AppInfo info) {
        CachedUserInfo userInfo =
                mUserCache.getUserManagerState().getCachedInfo(info.user);
        return !PRIVATE_SPACE_PACKAGE.equals(info.getTargetPackage())
                && (!userInfo.getIconInfo().isPrivate()
                        || (userInfo.isUnlocked() && !userInfo.isQuietModeEnabled()));
    }

    private boolean isPrivateSpaceQuery(String query) {
        return query.equalsIgnoreCase(mContext.getString(R.string.private_space_label));
    }

    private boolean isPrivateSpaceAvailable() {
        // Cached user info is limited to the launcher context user's profile group.
        return mUserCache.getUserManagerState().getAllCachedInfos().stream()
                .anyMatch(userInfo -> userInfo.getIconInfo().isPrivate())
                || ApiWrapper.INSTANCE.get(mContext).getPrivateSpaceSettingsIntent() != null;
    }
}
