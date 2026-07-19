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
which calls `Assume.assumeTrue(BuildConfig.IS_DEBUG_DEVICE)`. `IS_DEBUG_DEVICE` is enabled for the
instrumentation test build via `Launcher3QuickStepLibForTesting` (the `launcher-build-config-testing`
genrule in `Android.bp`), so these run without any manual change. `Launcher3QuickStep` keeps 
`IS_DEBUG_DEVICE = false`.

### Known test failures (WIP)

Latest run on tokay: totals: 2915 passed, 788 assumption failed, 8 failed, 2 ignored.

The 8 failures were:

- `com.android.launcher3.celllayout.IntegrationResizeWidgetsTest#invalidResize_noChange` -- resize
  returned `-1`, expected `0`.
- `com.android.launcher3.celllayout.IntegrationResizeWidgetsTest#resizeWithSibling_hasSpace_movesSiblings`
  -- resize returned `1`, expected `0`.
- `com.android.launcher3.model.gridmigration.ValidGridMigrationUnitTest#runExtensiveTestCases` --
  `TestTimedOutException` after 300000 ms; the thread was stuck in protobuf `MessageLiteToString`
  reflection from `ItemInfo.dumpProperties`.
- `com.android.launcher3.taskbar.rules.TaskbarModeRuleTest#testTaskbarMode_transient_overridesTaskbarUtil`
  -- expected `NO_BUTTON`, got `THREE_BUTTONS`.
- `com.android.launcher3.taskbar.rules.TaskbarModeRuleTest#testTaskbarMode_threeButtons_overridesDeviceProfile`
  -- expected `false`.
- `com.android.launcher3.taskbar.rules.TaskbarModeRuleTest#testTaskbarMode_pinned_overridesTaskbarUtil`
  -- expected `NO_BUTTON`, got `THREE_BUTTONS`.
- `com.android.launcher3.util.LayoutImportExportHelperTest#exportWidgetFromWorkspace` --
  `AssertionError` in the import/export round-trip
- `com.android.quickstep.InputConsumerUtilsTest#testNewBaseConsumer_nonTrackpadMouseEvent_desktop_returnsDefaultInputConsumer`
  -- expected a `ResetGestureInputConsumer`, got an `OtherActivityInputConsumer`.

The 8 failures can be re-run with

```bash
atest Launcher3QuickStepTests --test-filter \
"com.android.launcher3.celllayout.IntegrationResizeWidgetsTest#invalidResize_noChange,"\
"com.android.launcher3.celllayout.IntegrationResizeWidgetsTest#resizeWithSibling_hasSpace_movesSiblings,"\
"com.android.launcher3.model.gridmigration.ValidGridMigrationUnitTest#runExtensiveTestCases,"\
"com.android.launcher3.taskbar.rules.TaskbarModeRuleTest#testTaskbarMode_transient_overridesTaskbarUtil,"\
"com.android.launcher3.taskbar.rules.TaskbarModeRuleTest#testTaskbarMode_threeButtons_overridesDeviceProfile,"\
"com.android.launcher3.taskbar.rules.TaskbarModeRuleTest#testTaskbarMode_pinned_overridesTaskbarUtil,"\
"com.android.launcher3.util.LayoutImportExportHelperTest#exportWidgetFromWorkspace,"\
"com.android.quickstep.InputConsumerUtilsTest#testNewBaseConsumer_nonTrackpadMouseEvent_desktop_returnsDefaultInputConsumer"
```

