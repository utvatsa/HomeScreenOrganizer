# Maintenance Guide

## Interactive Layout Editor (`reports/layout-editor.html`)

You can visually inspect, rearrange icons/suites, rename headers, adjust theme sliders, and add/remove pages or folders in your browser:

1. **Live Local Server Mode (Recommended — Save, Dry Run, and Apply directly from browser)**:
   ```bash
   .venv/bin/home-studio serve
   # Open http://127.0.0.1:8765 in your browser
   ```
2. **Offline / Standalone HTML Mode**:
   Open [layout-editor.html](file:///Users/utsav.v/Documents/personal/personal-code/android-home-studio/reports/layout-editor.html) directly in Safari/Chrome, make your edits, click **Export JSON** to save `desired-layout.json`, and run `.venv/bin/home-studio apply --config config/desired-layout.json`.

## Common CLI Workflows

```bash
# Check device health and lock state
.venv/bin/home-studio doctor

# Refresh observed home screen state
.venv/bin/home-studio inspect

# Add a new category page
.venv/bin/home-studio category add --id reading --title "Reading"

# Rename an existing category heading
.venv/bin/home-studio category rename --id ai-suite --title "AI & Intelligence"

# Update global transparent header theme
.venv/bin/home-studio theme update --font-size 24 --opacity 0.88

# Validate & dry-run reconciliation plan
.venv/bin/home-studio apply --dry-run
```

## Updating Header Widgets on the Phone

- **Via Home Studio App**: Open `Home Studio` -> `Theme` tab -> adjust Font Size, Weight, Opacity, Alignment, or Separator -> changes apply immediately to all inheriting `Category Header` widgets.
- **Per-Widget Override**: Long-press any `Category Header` widget on the home screen -> tap `Settings` -> toggle off `Inherit Global Theme` -> customize and tap `Save Header Widget`.
