package com.android.launcher3.popup;

import android.Manifest;
import android.app.ActivityOptions;
import android.app.StorageScope;
import android.content.Intent;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateFlag;
import android.ext.cscopes.ContactScopesApi;
import android.ext.micspoofing.MicSpoofingApi;
import android.view.View;
import android.window.SplashScreen;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;

public interface GrapheneSystemShortcut {

    abstract class ScopedFeatureShortcut<T extends ActivityContext> extends SystemShortcut<T> {

        protected final String targetPackage;

        private ScopedFeatureShortcut(
                int iconResId,
                int labelResId,
                T target,
                ItemInfo itemInfo,
                View originalView
        ) {
            super(iconResId, labelResId, target, itemInfo, originalView);
            targetPackage = itemInfo.getTargetPackage();
        }

        protected static boolean hasGosPackageStateFlag(ItemInfo itemInfo, int flag) {
            String pkg = itemInfo.getTargetPackage();
            if (pkg == null) {
                return false;
            }
            return GosPackageState.get(pkg, itemInfo.user).hasFlag(flag);
        }

        @RequiresPermission(Manifest.permission.INTERACT_ACROSS_USERS)
        @Override
        public void onClick(View view) {
            dismissTaskMenuView();

            Intent intent = getIntent(targetPackage);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            var options = ActivityOptions.makeBasic()
                    .setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR)
                    .toBundle();
            view.getContext().startActivityAsUser(intent, options, mItemInfo.user);
        }

        protected abstract Intent getIntent(String targetPkg);
    }

    /**
     * Storage
     */

    SystemShortcut.Factory<BaseActivity> STORAGE_SCOPES = StorageScopes::maybeGet;

    class StorageScopes<T extends ActivityContext> extends ScopedFeatureShortcut<T> {

        private StorageScopes(T target, ItemInfo itemInfo, View originalView) {
            super(
                    R.drawable.ic_sscopes_add_file,
                    R.string.storage_scopes_drop_target_label,
                    target,
                    itemInfo,
                    originalView
            );
        }

        @Nullable
        public static <T extends ActivityContext> StorageScopes<T> maybeGet(
                T target, ItemInfo itemInfo, View originalView
        ) {
            if (!hasGosPackageStateFlag(itemInfo, GosPackageStateFlag.STORAGE_SCOPES_ENABLED)) {
                return null;
            }
            return new StorageScopes<>(target, itemInfo, originalView);
        }

        @Override
        protected Intent getIntent(String targetPkg) {
            return StorageScope.createConfigActivityIntent(targetPkg);
        }
    }

    /**
     * Contacts
     */

    SystemShortcut.Factory<BaseActivity> CONTACT_SCOPES = ContactScopes::maybeGet;

    class ContactScopes<T extends ActivityContext> extends ScopedFeatureShortcut<T> {

        private ContactScopes(T target, ItemInfo itemInfo, View originalView) {
            super(
                    R.drawable.ic_cscopes,
                    R.string.contact_scopes_label,
                    target,
                    itemInfo,
                    originalView
            );
        }

        @Nullable
        public static <T extends ActivityContext> ContactScopes<T> maybeGet(
                T target, ItemInfo itemInfo, View originalView
        ) {
            if (!hasGosPackageStateFlag(itemInfo, GosPackageStateFlag.CONTACT_SCOPES_ENABLED)) {
                return null;
            }
            return new ContactScopes<>(target, itemInfo, originalView);
        }

        @Override
        protected Intent getIntent(String targetPkg) {
            return ContactScopesApi.createConfigActivityIntent(targetPkg);
        }
    }

    /**
     * Mic spoofing
     */

    SystemShortcut.Factory<BaseActivity> MIC_SPOOFING = MicSpoofing::maybeGet;

    class MicSpoofing<T extends ActivityContext> extends ScopedFeatureShortcut<T> {

        private MicSpoofing(T target, ItemInfo itemInfo, View originalView) {
            super(
                    R.drawable.ic_microphone_spoofing,
                    R.string.microphone_spoofing_drop_target_label,
                    target,
                    itemInfo,
                    originalView
            );
        }

        @Nullable
        public static <T extends ActivityContext> MicSpoofing<T> maybeGet(
                T target, ItemInfo itemInfo, View originalView
        ) {
            if (!hasGosPackageStateFlag(itemInfo, GosPackageStateFlag.MIC_SPOOFING_ENABLED)) {
                return null;
            }
            return new MicSpoofing<>(target, itemInfo, originalView);
        }

        @Override
        protected Intent getIntent(String targetPkg) {
            return MicSpoofingApi.createConfigActivityIntent(targetPkg);
        }
    }
}
