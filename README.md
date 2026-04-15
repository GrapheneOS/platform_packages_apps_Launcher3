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
