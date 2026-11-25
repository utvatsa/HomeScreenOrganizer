# HomeScreenOrganizer

Autonomous, declarative Android home screen management, intelligent layout studio, transparent Category Header widgets, and One UI layout automation (`v0.5.0`).

> **Platform Note (Android Only):** **HomeScreenOrganizer** is built exclusively for **Android 12+ (API 31+)** and tailored for **Samsung One UI Home**. iOS and non-Android launchers are not supported. (The Android package retains internal ID `com.homestudio.local` for seamless upgrades with active home-screen widgets.)

## Companion Sub-Apps Needed (All Free / $0)

Open the app's **Setup** tab (or run `.venv/bin/homescreenorganizer checklist`) to automatically verify whether each sub-app is installed:

1. **Samsung One UI Home** (`com.sec.android.app.launcher`) — Required active launcher for multi-page home screens, widget pinning, and folder organization.
2. **Samsung Good Lock** (`com.samsung.android.goodlock`, free on Galaxy Store) — Required container to install and open Samsung Home Up.
3. **Samsung Home Up** (`com.samsung.android.app.homestar`, free Good Lock module) — Recommended for creating an **Immediate Backup** before restructuring and restoring anytime.
4. **Samsung Smart Switch** (`com.sec.android.easyMover`, free) — Optional safety backup for launcher/device state.
5. **KWGT Kustom Widget Maker** (`org.kustom.widget`, Free edition) — Strictly optional; built-in `Category Header` & `Wallpaper Spacer` widgets run standalone.

## Features (`v0.5.0`)

- **Animated Brand Splash & Verbose Live Spotlight Tutorial**:
  - Smooth zero-flash **Splash Screen** shown on every app launch.
  - **Interactive Spotlight Coachmark Tutorial** on first open (and replayable anytime via Hamburger Drawer -> **Interactive Tutorial**): moves step-by-step across live screens with illuminated spotlight highlights, locks editing during the tour, and supports **Next**, **Previous**, and **Skip Tutorial**.
- **One UI 9 Floating Dock & Hamburger Drawer**:
  - Floating bottom dock houses **Studio**, **Current** (exact live layout viewer), and **Catalog**; the left Hamburger menu (`≡`) provides **Settings** (Light / Dark / System mode + Header Theme + Wallpaper), **Setup Checklist**, **User Guide**, and **About** (`utvatsa/HomeScreenOrganizer`).
  - On first load, **HomeScreenOrganizer** checks core requirements and routes directly to **Setup** if any required companion item is missing.
- **Studio & Current Layout Viewers (Vertical Stack / Horizontal Pages + Wallpaper)**:
  - Switch cleanly between stacked **Vertical Stack** cards and swipeable **Horizontal Pages** with interactive dot indicators.
  - Toggle **Wallpaper: On/Off** in previews and open the **One UI Home Screen Wallpaper Picker** (Gallery + curated One UI presets + contrast dim slider; sets **unlocked Home Screen ONLY** and never modifies lock screen).
  - View the exact live **Current** One UI Home snapshot (including folders & widgets via `.venv/bin/homescreenorganizer sync-app --live` or JSON import), tap folders to inspect contents, or clone into **Studio**.
- **Studio Builder & Custom App Catalog**:
  - **Brief / Balanced / Detailed Presets**, `+ Suggested` suites with folder check/uncheck/rename/breakdown, `+ Custom` builder, global/per-page Max Apps (`4..16`), and Catalog app tagging.
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
