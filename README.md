# HomeScreenOrganizer

Autonomous, declarative Android home screen management, intelligent layout studio, transparent Category Header widgets, and One UI layout automation (`v0.3.0`).

> **Platform Note (Android Only):** **HomeScreenOrganizer** is built exclusively for **Android 12+ (API 31+)** and tailored for **Samsung One UI Home**. iOS and non-Android launchers are not supported. (The Android package retains internal ID `com.homestudio.local` for seamless upgrades with active home-screen widgets.)

## Companion Sub-Apps Needed (All Free / $0)

Open the app's **Setup** tab (or run `.venv/bin/homescreenorganizer checklist`) to automatically verify whether each sub-app is installed:

1. **Samsung One UI Home** (`com.sec.android.app.launcher`) — Required active launcher for multi-page home screens, widget pinning, and folder organization.
2. **Samsung Good Lock** (`com.samsung.android.goodlock`, free on Galaxy Store) — Required container to install and open Samsung Home Up.
3. **Samsung Home Up** (`com.samsung.android.app.homestar`, free Good Lock module) — Recommended for creating an **Immediate Backup** before restructuring and restoring anytime.
4. **Samsung Smart Switch** (`com.sec.android.easyMover`, free) — Optional safety backup for launcher/device state.
5. **KWGT Kustom Widget Maker** (`org.kustom.widget`, Free edition) — Strictly optional; built-in `Category Header` & `Wallpaper Spacer` widgets run standalone.

## Features (`v0.3.0`)

- **Studio Tab (Presets, Suggested Suites, Custom Builder & Layout Manager)**:
  - **First-Use Collection Presets**: Choose **Brief** (3 layouts), **Balanced** (6 layouts), or **Detailed** (8–12+ layouts covering all recognized installed apps).
  - **Intelligent Suite Suggestions**: Pick a suite (such as *Payments Suite*, *Banking Suites*, *AI Suite*, *Productivity*, *Most Used*, *Travel*, *Health*, *Entertainment*, etc.). HomeScreenOrganizer filters its bundled **offline LLM-curated app catalog** (`app_catalog.json`) + `PackageManager` strictly to **apps already installed on your phone**, then lets you check/uncheck apps or folders, rename folders, check/uncheck inside folders, or break down folders into solo icons.
  - **Custom Layout Builder & Max Apps Per Layout**: Create completely custom pages with a header widget + chosen installed apps/folders, configure global or per-page max items per layout (`4..16`, `8` one-hand default), reposition/edit headers (`Row 0–2`, alignment), reorder/remove pages, and realign slots cleanly.
- **Catalog Tab (Custom App Recognition)**:
  - Search all installed apps and tag any custom/work/regional app with categories and a suite folder name so future suggestions and presets include it automatically.
- **Settings & Appearance Tab (Light / Dark Mode + Widget Header Theme)**:
  - Switch between **System Default**, **Light Mode**, and **Dark Mode** across the entire organizer UI, customize live transparent header typography, and open Widget & Export tools.
- **Setup Checklist & Built-In Guide Tabs**:
  - Automated companion sub-app checklist + optional `UsageStats` Top-8 ranking, and an interactive step-by-step guide.
- **Host ADB Automation & Interactive Web Editor**:
  - `.venv/bin/homescreenorganizer most-used ... --apply-live` adds a left-of-home `Most Used` page (`order: 0`) while keeping `Daily Essentials` as Default Home.
  - `.venv/bin/homescreenorganizer serve` launches the local browser layout editor (`http://127.0.0.1:8765`).

## Quick Start

```bash
./scripts/bootstrap.sh
.venv/bin/homescreenorganizer checklist
.venv/bin/homescreenorganizer discover
.venv/bin/homescreenorganizer inspect
.venv/bin/homescreenorganizer serve
```

See [docs/maintenance-guide.md](docs/maintenance-guide.md) and [docs/safety-policy.md](docs/safety-policy.md).
