package com.android.launcher3.taskbar.allapps

import com.android.launcher3.allapps.search.SearchSessionManager
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.views.ActivityContext
import javax.inject.Inject

@ActivityContextSingleton
class TaskbarSearchSessionControllerImpl @Inject constructor(
    val mActivityContext: ActivityContext
) : TaskbarSearchSessionController() {
    override fun canHandleBackInvoked(): Boolean {
        return SearchSessionManager.handleAllAppsSearchBackInvoked(mActivityContext, false)
    }

    override fun handleBackInvoked(): Boolean {
        return SearchSessionManager.handleAllAppsSearchBackInvoked(mActivityContext, true)
    }
}
