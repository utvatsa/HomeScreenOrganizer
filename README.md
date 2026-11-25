# Android Home Studio

Autonomous, declarative Android home screen management, transparent Category Header widgets, and One UI layout automation (`v0.2.0`).

## Features

- **Home Studio Android App (`com.homestudio.local`, offline & `$0`)**:
  - **Setup Checklist tab**: Automatically detects Samsung One UI Home (and active default home status), Good Lock, Home Up, Smart Switch, KWGT (optional), AppWidget pinning support, bound widget counts, and optional `UsageStats` top-8 Most Used readiness.
  - **Built-in Guide tab**: Step-by-step interactive walkthrough covering backup, `4x6` one-hand layout (`<= 8` items/folders in rows `4–5`), transparent typography styling, and host automation.
  - **Transparent `CategoryHeaderWidgetProvider` (`4x1`) & `WallpaperSpacer` (`4x1` / `1x1`)**: Crisp anti-aliased editorial page headings rendered transparently over your live/static wallpaper.
- **Most Used Left-of-Home Page (`order: 0`)**:
  - Pulls foreground usage stats (`dumpsys usagestats`) merged with pinned apps to build a **`Most Used`** page immediately **to the left of your Default Home page (`Daily Essentials`)**.
- **Interactive HTML Layout Editor & CLI**:
  - Visual browser editor (`.venv/bin/home-studio serve` -> `http://127.0.0.1:8765`, or standalone `reports/layout-editor.html`) with live Save, Dry-Run, and Apply to Phone.

## Setup Checklist Before Applying Layouts

1. Install the latest signed APK from [GitHub Releases](https://github.com/utvatsa/android-home-studio/releases) (`adb install -r home-studio-0.2.0-release.apk`).
2. Open **Home Studio -> Checklist** (or run `.venv/bin/home-studio checklist`) and verify:
   - **Samsung One UI Home** is active (`READY`).
   - **Samsung Good Lock + Home Up** are installed and you have created an **Immediate Backup** (`Good Lock -> Home Up -> Backup and Restore`).
   - Optionally grant **Usage Access** if you want the Android app to display live top-8 foreground usage stats on-device.

## Quick Start (Host Automation)

```bash
./scripts/bootstrap.sh
.venv/bin/home-studio checklist
.venv/bin/home-studio discover
.venv/bin/home-studio inspect
.venv/bin/home-studio most-used --app Rapido --app BHIM --app smoketrack --app PennyWise --app "YT Music" --app YouTube --app Darwinbox --app Teams
.venv/bin/home-studio serve
```

## Safety Guarantees

See [docs/safety-policy.md](docs/safety-policy.md). Zero network permissions, zero telemetry, zero app uninstallation, zero `pm clear`, and zero sensitive user data committed to Git.
