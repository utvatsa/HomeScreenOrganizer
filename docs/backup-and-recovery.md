# Backup and Recovery Guide

## Available Backup Layers

### 1. Samsung Home Up Backup (Primary Visual Layout Backup)
- **Tool**: Good Lock -> Home Up (`com.samsung.android.app.homestar` `18.0.00.38`) -> Back up and restore
- **Baseline Backup Entry**: `11/25/2026 03:17:30` (verified visible in Home Up Restore File list)
- **Scope**: Captures One UI Home layout (pages, icons, folders, dock, widget slots).
- **Restore Procedure**: Open Home Up -> `Back up and restore` -> tap `11/25/2026 03:17:30` under `File list` -> confirm restore. (Requires explicit user approval before invoking if overwriting a modified layout.)
- **Limitations**: Stored in protected app-private storage (cannot be pulled via non-root ADB). Third-party widgets (Calendar, CRED, Atomberg, etc.) may require manual re-binding or account sign-in after restoration.

### 2. Independent Logical Backup (Host-Local)
- **Manifest**: `backups/backup-manifest.json` (ID `baseline-20261125-031730`, SHA-256 verified)
- **Contents**:
  - `screenshots/before/page_01.png` .. `page_13.png` + XML hierarchies
  - `backups/initial-launcher-provider-dump.txt` (full One UI `item` and `item_group` tables + settings)
  - `backups/initial-appwidget-dump.txt` (all bound widget IDs and providers)
  - `config/observed-layout.json` & `config/device-profile.json`
  - `assets/wallpaper/reconstructed_wallpaper.png`
- **Limitations**: Observational snapshot, not a binary database rollback. Used for logical reconstruction via the reconciliation engine or manual reference.

### 3. Samsung Smart Switch (`com.sec.android.easyMover` `3.7.74.7`)
- Installed on device, **not used** for baseline because a full Smart Switch backup copies unrelated personal data. Home Up + logical backup provide targeted launcher coverage without duplicating sensitive personal data.
