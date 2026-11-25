# Troubleshooting Guide

- **ADB device not found / unauthorized**: Check USB cable, run `~/Library/Android/sdk/platform-tools/adb devices -l`, and accept USB debugging prompt on phone if shown.
- **Phone locked during automation**: `AdbClient.preflight()` halts immediately on `isKeyguardLocked=true`. Unlock the phone manually and re-run `home-studio resume` / command.
- **UIAutomator dump hangs on Widget Picker**: Samsung One UI's animated Widget Picker sheet can cause `uiautomator dump` to block waiting for idle. Use `screencap` + `dumpsys activity top` instead.
- **Stale `wsCellLayoutContainer` page description**: One UI Home 18 reports stale `Home screen Page X of Y.` in `content-desc`. Always read the selected `PageIndicatorMarker` (`..S...`) from `dumpsys activity top` as ground truth.
- **Home Studio widget shows blank or old style**: Open `Home Studio` -> `Tools` -> tap `Reload Local Config & Refresh Widgets`.
