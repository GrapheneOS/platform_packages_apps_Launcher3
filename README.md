# Launcher3

## Running Launcher3QuickStepTests

This will run the QuickStep test files that are left in AOSP (used by Google's Pixel Launcher, 
NexusLauncher). This module also includes the regular Launcher3 tests:

```
atest Launcher3QuickStepTests
```

Launcher3QuickStepTests will put the device into test harness mode which is currently seems to only 
be removable by a factory reset.

The TAPL tests require you to not touch the screen at all.

The test APK is wired via `instrumentation_for: "Launcher3QuickStepForTesting"` in 
`quickstep/Android.bp`, so the tests run against the `Launcher3QuickStepForTesting` target (variant 
of `Launcher3QuickStep` that's debuggable and has `optimize` disabled).

Some tests such as `NavHandleLongPressHandlerTest`, `NavHandleLongPressInputConsumerTest`, and 
`ContextualSearchInvokerTest` are gated by `TestExtensions.overrideNavConfigFlag`
(`quickstep/tests/multivalentTests/src/com/android/quickstep/util/TestExtensions.kt`),
which calls `Assume.assumeTrue(BuildConfig.IS_DEBUG_DEVICE)`. To exercise them, set 
`IS_DEBUG_DEVICE = true` in `src_build_config/com/android/launcher3/BuildConfig.java`

### Known test failures (WIP)

With bluejay (Pixel 6a) and `IS_DEBUG_DEVICE = true`, totals: 2392 passed, 494 assumption failed, 11 
failed, 3 ignored. 

Some of these failures are due to hardcoded Google apps in expectations (e.g. 
`com.android.launcher3.model.LoaderTaskTest#loadsDataProperly`), and other failures are due to flaky
leak detection. Other failures are unknown

The 11 failures can be run with

```bash
atest Launcher3QuickStepTests --test-filter \
"com.android.launcher3.allapps.TaplAllAppsIconsWorkingTest#testAppIconLaunchFromAllAppsFromHome,"\
"com.android.launcher3.dragging.TaplUninstallRemoveTest#testAddDeleteShortcutOnHotseat,"\
"com.android.launcher3.model.LoaderTaskTest#loadsDataProperly,"\
"com.android.launcher3.model.gridmigration.ValidGridMigrationUnitTest#runExtensiveTestCases,"\
"com.android.launcher3.util.LayoutImportExportHelperTest#exportWidgetFromWorkspace,"\
"com.android.launcher3.workspace.TaplWorkspaceTest#testAddAndDeletePageAndFling,"\
"com.android.launcher3.workspace.TaplWorkspaceTest#testWorkspace,"\
"com.android.quickstep.InputConsumerUtilsTest#testNewBaseConsumer_nonTrackpadMouseEvent_desktop_returnsDefaultInputConsumer,"\
"com.android.quickstep.TaplOverviewIconTest#testSplitTaskTapBothIconMenus,"\
"com.android.quickstep.TaplOverviewIconTest#testOverviewActionsMenu,"\
"com.android.quickstep.TaplStartLauncherViaGestureTests#testStressPressOverview"
```

See https://github.com/GrapheneOS/platform_packages_apps_Launcher3/pull/72 for stacktraces
