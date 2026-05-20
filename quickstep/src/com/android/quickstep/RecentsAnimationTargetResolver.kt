package com.android.quickstep

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.TaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.content.ComponentName
import android.util.Log
import android.view.RemoteAnimationTarget
import com.android.wm.shell.Flags.enableShellTopTaskTracking

object RecentsAnimationTargetResolver {
    private const val TAG = "RecentsAnimationTargetResolver"

    @JvmStatic
    fun findTaskForLastAppearedTarget(
        targets: RecentsAnimationTargets,
        lastGestureState: GestureState?,
        taskId: Int,
    ): LastAppearedTask {
        findTaskById(targets.apps, taskId)?.let { return LastAppearedTask(it, it.taskId) }
        findLegacyHomeTarget(targets, lastGestureState, taskId)?.let { return it }
        val target = targets.findTask(taskId)
        return LastAppearedTask(target, target?.taskId ?: INVALID_TASK_ID)
    }

    private fun findLegacyHomeTarget(
        targets: RecentsAnimationTargets,
        lastGestureState: GestureState?,
        taskId: Int,
    ): LastAppearedTask? {
        if (enableShellTopTaskTracking() || lastGestureState == null) return null

        val runningTask = lastGestureState.runningTask ?: return null
        if (!runningTask.isHomeTask() || !runningTask.topGroupedTaskContainsTask(taskId)) {
            return null
        }

        val homeTarget =
            findUniqueMatchingHomeTarget(
                targets.apps,
                runningTask.getLegacyBaseTask(),
                runningTask.packageName,
            ) ?: return null

        // Legacy task tracking can cache the third-party home task id while Shell animates the
        // root home task. Keep the Shell target intact and report the cached id separately for
        // RecentsView lookup.
        return LastAppearedTask(homeTarget, taskId).also {
            Log.d(
                TAG,
                "onRecentsAnimationStart: using home target taskId=${homeTarget.taskId} " +
                    "for cachedTaskId=$taskId",
            )
        }
    }

    private fun findTaskById(
        targets: Array<RemoteAnimationTarget>?,
        taskId: Int,
    ): RemoteAnimationTarget? = targets?.firstOrNull { it.taskId == taskId }

    private fun findUniqueMatchingHomeTarget(
        targets: Array<RemoteAnimationTarget>?,
        cachedTask: TaskInfo?,
        packageName: String?,
    ): RemoteAnimationTarget? {
        return targets
            ?.singleOrNull {
                it.windowConfiguration?.activityType == ACTIVITY_TYPE_HOME &&
                    it.matchesCachedTask(cachedTask, packageName)
            }
    }

    private fun RemoteAnimationTarget.matchesCachedTask(
        cachedTask: TaskInfo?,
        packageName: String?,
    ): Boolean {
        val targetInfo = taskInfo
        if (cachedTask != null) {
            if (targetInfo == null) return false
            if (cachedTask.userId != targetInfo.userId) return false
            if (cachedTask.displayId != targetInfo.displayId) return false
        }

        if (packageName == null) return true
        if (targetInfo == null) return false
        if (targetInfo.topActivity.matchesPackage(packageName)) return true
        if (targetInfo.baseActivity.matchesPackage(packageName)) return true
        if (targetInfo.realActivity.matchesPackage(packageName)) return true
        return false
    }

    private fun ComponentName?.matchesPackage(packageName: String): Boolean =
        this?.packageName == packageName

    data class LastAppearedTask(
        val target: RemoteAnimationTarget?,
        val taskId: Int,
    )
}
