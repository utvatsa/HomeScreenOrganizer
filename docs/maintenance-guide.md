# Maintenance & Usage Guide (v0.2.0)

## 1. Using the Home Studio Android App

Open **Home Studio** on your phone:

1. **Checklist Tab (`Setup`)**:
   - Verifies **Samsung One UI Home** (installed + active launcher), **Good Lock**, **Home Up**, **Smart Switch**, **KWGT (optional)**, **AppWidget Pinning Support**, bound **Category Headers**, and optional **Usage Access** for live **Top-8 Most Used** detection.
   - Tap **Grant Usage Access** (optional) to let the app rank your top 8 most-used apps directly on-device.
2. **Guide Tab**:
   - Step-by-step reference covering Home Up backups, one-hand bottom-row placement, global vs. per-widget header styling, and troubleshooting.
3. **Headers Tab**:
   - Add or view category definitions synced with your `Category Header` widgets.
4. **Theme Tab**:
   - Adjust Font Size, Opacity, Font Weight, Alignment, and Subtle Separator across all inheriting widgets in real time.
5. **Tools Tab**:
   - Pin new `Category Header`, `Wallpaper Spacer 4x1`, or `Wallpaper Spacer 1x1` widgets, edit per-widget overrides, or **Export / Import JSON** backups via Android Storage Access Framework (`SAF`).

## 2. Left-of-Home `Most Used` Page

Keep `Daily Essentials` as your Default Home Page (`isDefaultHome: true`) and place `Most Used` at `order: 0` (one swipe left from Home):

```bash
# Build/update the Most Used leftmost page using live `dumpsys usagestats` + 8 pinned apps
.venv/bin/home-studio most-used \
  --app Rapido --app BHIM --app smoketrack --app PennyWise \
  --app "YT Music" --app YouTube --app Darwinbox --app Teams

# Add `--apply-live` when the phone is connected over ADB to apply immediately:
.venv/bin/home-studio most-used \
  --app Rapido --app BHIM --app smoketrack --app PennyWise \
  --app "YT Music" --app YouTube --app Darwinbox --app Teams --apply-live
```

## 3. Interactive HTML Layout Editor

1. **Live Server Mode (Save, Dry Run, and Apply from browser)**:
   ```bash
   .venv/bin/home-studio serve
   # Open http://127.0.0.1:8765
   ```
2. **Standalone HTML**: Open `reports/layout-editor.html` in any browser, edit pages/slots/theme, click **Export JSON**, and apply with `.venv/bin/home-studio apply --config config/desired-layout.json`.

## 4. Common CLI Workflows

```bash
.venv/bin/home-studio doctor            # ADB & lock-state health check
.venv/bin/home-studio checklist         # Companion app & readiness check
.venv/bin/home-studio inspect           # Refresh config/observed-layout.json
.venv/bin/home-studio verify --live     # Verify live phone matches desired layout
.venv/bin/home-studio sync-headers      # Push updated titles/theme to widgets
```
