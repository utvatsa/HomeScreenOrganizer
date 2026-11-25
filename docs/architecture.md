# Architecture

## System Components

1. **Host Orchestration Engine (`automation/`)**:
   - `adb/`: Serial-pinned ADB wrapper with safety preflight and prohibited-command guardrails.
   - `device/` & `launcher/`: Device profile discovery, One UI `LauncherProvider` read-only dump parser, UIAutomator hierarchy parser, and state machine.
   - `inventory/`: Canonical application inventory collector combining `PackageManager`/launcher activity queries, icon extraction, and multi-category/banking classification.
   - `wallpaper/`: Read-only wallpaper metadata inspection, safe candidate matching, and screenshot-based preview fallback.
   - `planner/`: Declarative layout validator and reconciliation planner generating idempotent operation plans.
   - `gestures/` & `executor/`: Reusable grid-aware gesture abstraction, bounded retries, UI verification, and append-only execution journal + checkpoints.
   - `reporting/`: Local HTML reports, visual page mockups, contact sheets, and before/after comparisons.

2. **Home Studio Android Companion App (`android/home-studio/`, `com.homestudio.local`)**:
   - Offline, zero-sensitive-permission Kotlin companion app (not a replacement launcher).
   - Configurable transparent Category Header `AppWidgetProvider` (`RemoteViews`) supporting global theme inheritance + per-widget (`appWidgetId`) overrides.
   - Transparent Wallpaper Placeholder `AppWidgetProvider` (zero visual footprint, non-clickable) for retaining wallpaper-only pages if needed.
   - Jetpack Compose management UI for theme/category editing, configuration JSON import/export, diagnostics, and launcher-visible app inventory/icon export.

3. **Declarative Configuration (`config/`)**:
   - `schema.json` / `desired-layout.json` / `approved-layout.json` (source of truth)
   - `observed-layout.json` (reconstructed live device state)
   - `theme.json`, `category-rules.json`, `user-overrides.json`, `launcher-capabilities.json`
