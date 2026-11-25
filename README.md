# Android Home Studio

Autonomous, declarative Android home screen management, design, and automation system for Samsung One UI Home (`SM-S721B`).

## Architecture

- `automation/`: Host-side Python orchestration engine, ADB client, inventory collector, layout planner, gesture engine, live idempotent applier, local web editor, verification, and reporting.
- `android/home-studio/`: Offline Kotlin companion APK (`com.homestudio.local`) providing transparent configurable `CategoryHeaderWidgetProvider` headings, temporary layout spacer providers (`WallpaperPlaceholderWidgetProvider`, `WallpaperSpacer1x1Provider`), and local theme/configuration management.
- `config/`: Declarative layout configurations (`desired-layout.json`, `approved-layout.json`, `observed-layout.json`, `theme.json`, `schema.json`).
- `docs/`: Architecture, device capabilities, safety policy, backup/recovery, maintenance, and troubleshooting guides.

## Interactive Layout Editor & Customization

- **Recommended live workflow** (load current phone layout, rearrange items/suites/pages, rename headers, dry-run, and apply back to phone):
  ```bash
  .venv/bin/home-studio serve
  # Open http://127.0.0.1:8765
  ```
- **Offline standalone HTML**: Open `reports/layout-editor.html` in any browser to edit, export `desired-layout.json`, and apply with `.venv/bin/home-studio apply --config config/desired-layout.json`.

## Verification & Quick CLI

```bash
./scripts/doctor.sh
.venv/bin/home-studio verify --live
.venv/bin/home-studio sync-headers
.venv/bin/home-studio editor
```

See [docs/maintenance-guide.md](docs/maintenance-guide.md) and [docs/safety-policy.md](docs/safety-policy.md).
