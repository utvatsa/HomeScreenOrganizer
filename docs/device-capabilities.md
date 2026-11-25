# Device & Samsung One UI Home Capabilities

- **Device**: Samsung Galaxy S24 FE (`SM-S721B`, `r12s`)
- **OS**: Android 17 (SDK 37, Security Patch `2026-10-05`), One UI 9.0 (`90000`)
- **Display**: `1080x2340`, Physical Density `450 dpi`, Screen Zoom / Override Density `480 dpi`, Font Scale `1.0`, 3-button Navigation (`navigation_mode=0`)
- **Launcher**: `com.sec.android.app.launcher` (`18.0.07.53`)
- **Companion Modules**: Good Lock `3.0.16.6`, Home Up `18.0.00.38`, Smart Switch `3.7.74.7`, KWGT free `3.82b621115` (unlaunched, unused), Home Studio `0.1.0` (`com.homestudio.local`)

## Key Verified Findings

1. **Grid Options**: Native One UI Home supports `4x6` and `5x6`; Home Up unlocks `4..5 x 5..7`. Proposed target grid: **`5x6`** (5 columns x 6 rows = 25 app/folder cells below the full-width `5x1` Category Header).
2. **Transparent Category Headers**: Verified on-device (`widget-46-clean.png`) — `CategoryHeaderWidgetProvider` renders razor-sharp, 100% transparent typography with zero card background, border, or app label.
3. **Native Duplicate Shortcuts**: Supported natively (`PhonePe` on 3 places, `Gmail` & `Calendar` on 2 pages in baseline state).
4. **Empty Page Retention**: One UI Home `18.0.07.53` retains genuinely empty pages (observed `Page 5` and `Page 12` with 0 items). `WallpaperPlaceholderWidgetProvider` remains available as a zero-footprint fallback if needed.
5. **Single-App Folders**: Standard One UI Home dissolves 1-item folders; `ICICI Bank` (1 app: iMobile) and `Axis Bank` (1 app: Axis Mobile) are best represented as standalone icons on `Banking Suites` alongside the multi-app folders (`SBI` with 7 apps, `HDFC Bank` with 3 apps), unless the user wants to add a second shortcut or folder-stub.
6. **Widget Placement**: `AppWidgetManager.requestPinAppWidget` works with confirmation but drops onto the first open slot (Page 1); opening `Widgets` from Home Edit Mode on the target page places widgets directly onto that page.
