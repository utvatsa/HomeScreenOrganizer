# Maintenance & Usage Guide (v0.6.0)

> **Android Only:** **HomeScreenOrganizer** (`com.homestudio.local`) targets **Android 12+ (API 31+)** on **Samsung One UI Home**.

## 1. Companion Sub-Apps Checklist

Before organizing or applying layouts, open **HomeScreenOrganizer -> Setup** (or run `.venv/bin/homescreenorganizer checklist`):
- **Samsung One UI Home** (`com.sec.android.app.launcher`) — Must be active default home app.
- **Samsung Good Lock** (`com.samsung.android.goodlock`) & **Samsung Home Up** (`com.samsung.android.app.homestar`) — Create an Immediate Backup inside Home Up before major restructuring.
- **Samsung Smart Switch** (`com.sec.android.easyMover`) — Optional full backup utility.
- **KWGT Free** (`org.kustom.widget`) — Optional; native Category Header widgets work without KWGT.

## 2. Using the HomeScreenOrganizer App (`v0.3.0`)

1. **Studio Tab (First-Use Presets & Layout Builder)**:
   - Pick **Brief (3 layouts)**, **Balanced (6 layouts)**, or **Detailed (Full All-Apps)** to generate a complete layout from **installed apps only**.
   - Adjust **Max Apps / Items Per Layout** globally (`4`, `6`, `8 One-Hand`, `10`, `12`, `16`) or per individual page.
   - Tap **+ Suggested** (e.g., *Payments Suite*) to preview detected installed apps and folders: check/uncheck solo apps or folders, rename folders inline, check/uncheck apps inside folders, or tap **Break Down Folder**.
   - Tap **+ Custom** to build a page from scratch with a custom header, row/alignment placement, and custom-grouped folders.
   - Use **Up/Down**, **Set Home**, **Edit Header** (reposition row `0..2`, alignment, bind widget ID), **Edit Items**, **Realign**, and **Remove** on any page card, or **Export Layout JSON** to apply over ADB.
2. **Catalog Tab (Custom Apps)**:
   - Tag any installed app with custom categories (or add new categories) and a suite folder name.
3. **Settings Tab**:
   - Switch between **System Default**, **Light Mode**, and **Dark Mode**, and customize font size, opacity, weight, alignment, and subtle separator across all inheriting widgets.
4. **Setup & Guide Tabs**:
   - Verify companion sub-apps, optionally grant **Usage Access** for live top-8 foreground ranking, and read the Android-only setup guide.

## 3. Host CLI & Interactive Web Editor

```bash
.venv/bin/homescreenorganizer doctor            # ADB & lock-state health check
.venv/bin/homescreenorganizer checklist         # Companion sub-apps check
.venv/bin/homescreenorganizer serve             # Local browser editor (http://127.0.0.1:8765)
.venv/bin/homescreenorganizer most-used --app Rapido --app BHIM --app smoketrack --app PennyWise --app "YT Music" --app YouTube --app Darwinbox --app Teams --apply-live
.venv/bin/homescreenorganizer verify --live     # Verify live phone matches desired layout
```
