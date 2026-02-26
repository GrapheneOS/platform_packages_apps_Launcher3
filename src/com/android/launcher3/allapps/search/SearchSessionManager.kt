package com.android.launcher3.allapps.search

import android.util.Log
import android.view.WindowInsets
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.AbstractFloatingView

/**
 * Manages search session for all apps drawer.
 *
 * If we need to keep track of state, turn this into a class and add to various Dagger modules.
 * NexusLauncher does something more complicated, since they want to hold reference to the
 * ActivityContext
 */
object SearchSessionManager {
    private const val TAG = "LSearchSessionManager"

    private inline fun verboseLog(makeLog: () -> String) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, makeLog())
        }
    }

    /**
     * Returns true if the search session handled the back invocation. If [doActions] is false,
     * then this method just becomes a query on whether the back invocation would be handled.
     *
     * [doActions] will be false on calls for things such as predictive back gesture queries.
     */
    @JvmStatic
    fun handleAllAppsSearchBackInvoked(context: ActivityContext, doActions: Boolean): Boolean {
        verboseLog { "isAllAppsBackButtonIntercepted(ctx, $doActions)" }

        val appsView = context.appsView ?: return false
        if (!appsView.isInAllApps) {
            return false
        }

        // This branch is present in NexusLauncher, but doesn't seem to be triggered right now in
        // AOSP Launcher3.
        val noInterceptTopOpenView = AbstractFloatingView.getTopOpenViewWithType(
            context,
            AbstractFloatingView.TYPE_TOUCH_CONTROLLER_NO_INTERCEPT
        )
        if (noInterceptTopOpenView != null) {
            Log.d(TAG, "topNoInterceptOpenView ${noInterceptTopOpenView::class.java.name}")
            if (noInterceptTopOpenView.canHandleBack()) {
                Log.d(TAG, "topNoInterceptOpenView canHandleBack")
                if (doActions) {
                    noInterceptTopOpenView.onBackInvoked()
                }
                return true;
            }
        }

        val manager = appsView.searchUiManager
        val editText = manager.editText ?: return false
        val isImeVisible = editText.rootWindowInsets?.isVisible(WindowInsets.Type.ime())
            ?: return false
        if (!manager.shouldInterceptBackButton()) {
            verboseLog { "manager not allowing intercepting back, isImeVisible $isImeVisible" }
            if (isImeVisible) {
                if (doActions) {
                    editText.hideKeyboard()
                }
                // handled back invoked since we hid the keyboard
                return true
            }
            return false
        }

        // appsView.requestFocus call is made here in NexusLauncher
        if (doActions) {
            appsView.requestFocus()
        }

        verboseLog { "handling back button, isImeVisible $isImeVisible" }
        // condition used in NexusLauncher
        if (editText.text.isEmpty() || !isImeVisible) {
            if (doActions) {
                Log.d(TAG, "ending search session from back button");
                manager.resetSearch()
            }
        } else if (doActions) {
            editText.hideKeyboard()
        }

        return true
    }
}
