"""CLI entrypoint for Android Home Studio."""

from __future__ import annotations

import json
from dataclasses import asdict
from pathlib import Path
import click

from automation.adb.client import AdbClient, AdbError, DeviceTarget
from automation.device.discovery import discover_device_profile, save_device_profile
from automation.executor.planner_ops import plan_reconciliation_ops
from automation.inventory.classifier import classify_inventory
from automation.launcher.dump_parser import parse_oneui_dump
from automation.models import DesiredLayout, PageSpec, HeaderInstanceConfig, PositionSpec
from automation.planner.layout_planner import generate_proposed_layout
from automation.planner.validator import validate_layout
from automation.reporting.previews import generate_all_previews


def _get_adb() -> AdbClient:
    return AdbClient(DeviceTarget.from_private_config())


@click.group()
def main() -> None:
    """Android Home Studio CLI."""


@main.command()
def doctor() -> None:
    """Verify host environment, ADB connectivity, and device safety preflight."""
    try:
        adb = _get_adb()
        fg = adb.preflight(require_launcher=False)
        click.echo(json.dumps({"status": "ok", "preflight": fg}, indent=2))
    except (FileNotFoundError, AdbError) as exc:
        raise click.ClickException(str(exc))


@main.command()
def discover() -> None:
    """Discover physical device profile and installed Samsung modules."""
    adb = _get_adb()
    adb.preflight(require_launcher=False)
    profile = discover_device_profile(adb)
    out = save_device_profile(profile)
    click.echo(f"Saved device profile to {out}")


@main.command()
def inspect() -> None:
    """Capture LauncherProvider dump and save observed-layout.json."""
    adb = _get_adb()
    adb.preflight(require_launcher=False)
    dump = adb.shell("dumpsys activity provider com.sec.android.app.launcher")
    snap = parse_oneui_dump(dump)
    out = Path("config/observed-layout.json")
    payload = {
        "commonSettings": snap.common_settings,
        "oneUiSettings": snap.oneui_settings,
        "homeUpSettings": snap.homeup_settings,
        "pages": snap.pages,
        "dockItems": snap.dock_items,
    }
    out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    click.echo(f"Saved observed layout ({len(snap.pages)} pages) to {out}")


@main.command()
@click.option("--refresh", is_flag=True, help="Re-classify existing applications.json")
def inventory(refresh: bool) -> None:
    """Classify or summarize the canonical application inventory."""
    apps_path = Path("inventory/applications.json")
    apps = json.loads(apps_path.read_text(encoding="utf-8"))
    summary = classify_inventory(apps, Path("config/user-overrides.json"))
    if refresh:
        apps_path.write_text(json.dumps(apps, indent=2) + "\n", encoding="utf-8")
    click.echo(json.dumps({
        "total": summary["total"],
        "assigned": summary["assigned"],
        "multiCategory": len(summary["multiCategory"]),
        "unassigned": len(summary["unassigned"]),
    }, indent=2))


@main.command()
@click.option("--columns", default=5, type=int)
@click.option("--rows", default=6, type=int)
def plan(columns: int, rows: int) -> None:
    """Generate and validate config/desired-layout.json."""
    apps = json.loads(Path("inventory/applications.json").read_text(encoding="utf-8"))
    layout, report = generate_proposed_layout(apps, columns=columns, rows=rows)
    warnings = validate_layout(layout, installed_ids={a["id"] for a in apps}, allow_duplicates=True)
    Path("config/desired-layout.json").write_text(layout.model_dump_json(indent=2) + "\n", encoding="utf-8")
    click.echo(json.dumps({"warnings": warnings, **report}, indent=2))


@main.command()
def preview() -> None:
    """Generate visual previews and contact sheet from config/desired-layout.json."""
    layout = DesiredLayout.model_validate_json(Path("config/desired-layout.json").read_text(encoding="utf-8"))
    apps = json.loads(Path("inventory/applications.json").read_text(encoding="utf-8"))
    info = generate_all_previews(
        layout,
        apps,
        Path("assets/wallpaper/reconstructed_wallpaper.png"),
        Path("screenshots/before/page_05.png"),
    )
    click.echo(json.dumps(info, indent=2))


@main.command()
@click.option("--dry-run", is_flag=True, help="Validate and print operation plan without modifying device.")
@click.option("--clean/--no-clean", default=None, help="Reset non-Weather home pages before building.")
@click.option("--config", "config_path", default="config/approved-layout.json", help="Layout JSON path")
def apply(dry_run: bool, clean: bool | None, config_path: str) -> None:
    """Reconcile desired layout with device state."""
    p = Path(config_path)
    if not p.exists():
        p = Path("config/desired-layout.json")
    layout = DesiredLayout.model_validate_json(p.read_text(encoding="utf-8"))
    apps = json.loads(Path("inventory/applications.json").read_text(encoding="utf-8"))
    validate_layout(layout, installed_ids={a["id"] for a in apps}, allow_duplicates=True)
    observed = json.loads(Path("config/observed-layout.json").read_text(encoding="utf-8"))
    ops = plan_reconciliation_ops(layout, observed)
    if dry_run:
        Path("reports/dry-run-plan.json").write_text(json.dumps([asdict(o) for o in ops], indent=2) + "\n", encoding="utf-8")
        click.echo(f"Dry-run complete: {len(ops)} operations planned -> reports/dry-run-plan.json")
        return
    from automation.executor.live_applier import apply_layout_live
    Path("config/approved-layout.json").write_text(layout.model_dump_json(indent=2) + "\n", encoding="utf-8")
    res = apply_layout_live(layout, clean_canvas=clean)
    click.echo(json.dumps(res, indent=2))


@main.command("sync-headers")
@click.option("--config", "config_path", default="config/desired-layout.json", help="Layout JSON path")
def sync_headers(config_path: str) -> None:
    """Push updated category titles and global header theme to Home Studio widgets."""
    from automation.executor.live_applier import LiveApplier
    p = Path(config_path)
    layout = DesiredLayout.model_validate_json(p.read_text(encoding="utf-8"))
    apps = json.loads(Path("inventory/applications.json").read_text(encoding="utf-8"))
    applier = LiveApplier(_get_adb(), layout, {a["id"]: a for a in apps})
    applier.seed_existing_widget_mappings()
    applier.sync_home_studio_config()
    click.echo(json.dumps({"syncedHeaders": len(applier.widget_mappings)}, indent=2))


@main.command()
def status() -> None:
    """Report current layout & checkpoint status."""
    ckpt = Path("checkpoints/latest-checkpoint.json")
    if ckpt.exists():
        click.echo(ckpt.read_text(encoding="utf-8"))
    else:
        click.echo(json.dumps({"phase": "approval-gate", "status": "awaiting_user_approval"}, indent=2))


@main.command()
@click.option("--live", is_flag=True, help="Capture fresh live dump from phone before verifying.")
def verify(live: bool) -> None:
    """Compare observed-layout.json against desired/approved layout."""
    cfg_path = Path("config/approved-layout.json")
    if not cfg_path.exists():
        cfg_path = Path("config/desired-layout.json")
    layout = DesiredLayout.model_validate_json(cfg_path.read_text(encoding="utf-8"))
    if live:
        adb = _get_adb()
        adb.preflight(require_launcher=False)
        snap = parse_oneui_dump(adb.shell("dumpsys activity provider com.sec.android.app.launcher"))
        observed = {
            "commonSettings": snap.common_settings,
            "oneUiSettings": snap.oneui_settings,
            "homeUpSettings": snap.homeup_settings,
            "pages": snap.pages,
            "dockItems": snap.dock_items,
        }
        Path("config/observed-layout.json").write_text(json.dumps(observed, indent=2) + "\n", encoding="utf-8")
    else:
        observed = json.loads(Path("config/observed-layout.json").read_text(encoding="utf-8"))
    ops = plan_reconciliation_ops(layout, observed)
    spacers = sum(
        1
        for p in observed.get("pages", [])
        for it in p.get("items", [])
        if it.get("type") == "widget"
        and (
            "WallpaperPlaceholderWidgetProvider" in str(it.get("component", ""))
            or "WallpaperSpacer1x1Provider" in str(it.get("component", ""))
        )
    )
    click.echo(json.dumps({
        "desiredPages": len(layout.pages),
        "observedPages": len(observed.get("pages", [])),
        "pendingOps": len(ops),
        "temporarySpacers": spacers,
    }, indent=2))


@main.group()
def category() -> None:
    """Manage categories in desired-layout.json."""


@category.command("add")
@click.option("--id", "cat_id", required=True)
@click.option("--title", required=True)
def category_add(cat_id: str, title: str) -> None:
    p = Path("config/desired-layout.json")
    layout = DesiredLayout.model_validate_json(p.read_text(encoding="utf-8"))
    if any(pg.id == cat_id for pg in layout.pages):
        raise click.ClickException(f"Category {cat_id} already exists.")
    insert_order = max((pg.order for pg in layout.pages if pg.id != "wallpaper"), default=0) + 1
    for pg in layout.pages:
        if pg.id == "wallpaper":
            pg.order = insert_order + 1
    layout.pages.append(
        PageSpec(
            id=cat_id,
            title=title,
            order=insert_order,
            header=HeaderInstanceConfig(
                enabled=True,
                inheritTheme=True,
                position=PositionSpec(row=0, column=0, rowSpan=1, columnSpan=layout.launcher.grid.columns),
            ),
            items=[],
        )
    )
    validate_layout(layout)
    p.write_text(layout.model_dump_json(indent=2) + "\n", encoding="utf-8")
    click.echo(f"Added category {cat_id} ({title}) at order {insert_order}.")


@category.command("rename")
@click.option("--id", "cat_id", required=True)
@click.option("--title", required=True)
def category_rename(cat_id: str, title: str) -> None:
    p = Path("config/desired-layout.json")
    layout = DesiredLayout.model_validate_json(p.read_text(encoding="utf-8"))
    for pg in layout.pages:
        if pg.id == cat_id:
            pg.title = title
            p.write_text(layout.model_dump_json(indent=2) + "\n", encoding="utf-8")
            click.echo(f"Renamed {cat_id} -> {title}")
            return
    raise click.ClickException(f"Category {cat_id} not found.")


@main.group()
def theme() -> None:
    """Manage global header typography theme."""


@theme.command("update")
@click.option("--font-size", type=float, default=None)
@click.option("--opacity", type=float, default=None)
@click.option("--font-weight", type=int, default=None)
def theme_update(font_size: float | None, opacity: float | None, font_weight: int | None) -> None:
    p = Path("config/desired-layout.json")
    layout = DesiredLayout.model_validate_json(p.read_text(encoding="utf-8"))
    if font_size is not None:
        layout.theme.header.fontSizeSp = font_size
    if opacity is not None:
        layout.theme.header.opacity = opacity
    if font_weight is not None:
        layout.theme.header.fontWeight = font_weight
    p.write_text(layout.model_dump_json(indent=2) + "\n", encoding="utf-8")
    click.echo("Updated global header theme in config/desired-layout.json")


@main.command()
@click.option("--pkg", "extra_pkgs", multiple=True, help="Additional package names to verify installed.")
def checklist(extra_pkgs: tuple[str, ...]) -> None:
    """Verify required Samsung companion apps, Home Studio APK, and optional target packages."""
    adb = _get_adb()
    fg = adb.preflight(require_launcher=False)
    profile = discover_device_profile(adb)
    comp = profile.get("companionPackages", {})
    checks = {
        "oneUiHomeActive": "com.sec.android.app.launcher" in str(profile.get("activeLauncher", "")),
        "oneUiHome": comp.get("oneUiHome", {}),
        "goodLock": comp.get("goodLock", {}),
        "homeUp": comp.get("homeUp", {}),
        "smartSwitch": comp.get("smartSwitch", {}),
        "kwgtOptional": comp.get("kwgt", {}),
        "homeStudioApk": comp.get("homeStudio", {}),
    }
    pkg_status = {}
    for pkg in extra_pkgs:
        out = adb.shell(f"pm path {pkg}", check=False).strip()
        pkg_status[pkg] = bool(out)
    click.echo(json.dumps({"preflight": fg, "checklist": checks, "targetPackages": pkg_status}, indent=2))


@main.command("most-used")
@click.option("--app", "pinned_refs", multiple=True, help="Pinned app display name or package ID (repeat up to 8).")
@click.option("--title", default="Most Used", help="Header title for the leftmost page.")
@click.option("--config", "config_path", default="config/desired-layout.json", help="Layout config path to update.")
@click.option("--apply-live", is_flag=True, help="Also apply the leftmost Most Used page live on connected phone.")
def most_used(pinned_refs: tuple[str, ...], title: str, config_path: str, apply_live: bool) -> None:
    """Pull system usage stats over ADB and build/update a leftmost 'Most Used' page."""
    from automation.device.usage_stats import fetch_device_usage_stats
    from automation.planner.layout_planner import add_most_used_left_page

    p = Path(config_path)
    layout = DesiredLayout.model_validate_json(p.read_text(encoding="utf-8"))
    apps = json.loads(Path("inventory/applications.json").read_text(encoding="utf-8"))

    usage_ms: dict[str, int] = {}
    try:
        adb = _get_adb()
        adb.preflight(require_launcher=False)
        usage_ms = fetch_device_usage_stats(adb)
    except Exception:
        usage_ms = {}

    updated = add_most_used_left_page(
        layout,
        apps,
        pinned_refs=list(pinned_refs) if pinned_refs else None,
        usage_ms_by_pkg=usage_ms,
        title=title,
    )
    validate_layout(updated, installed_ids={a["id"] for a in apps}, allow_duplicates=True)
    p.write_text(updated.model_dump_json(indent=2) + "\n", encoding="utf-8")

    result: dict[str, object] = {
        "updatedConfig": str(p),
        "totalPages": len(updated.pages),
        "defaultHomePage": next((pg.title for pg in updated.pages if pg.isDefaultHome), None),
        "mostUsedApps": [it.title for it in updated.pages[0].items if it.type == "app"],
        "systemUsagePackagesRead": len(usage_ms),
    }
    if apply_live:
        from automation.executor.live_applier import LiveApplier
        Path("config/approved-layout.json").write_text(updated.model_dump_json(indent=2) + "\n", encoding="utf-8")
        applier = LiveApplier(_get_adb(), updated, {a["id"]: a for a in apps})
        result["liveApply"] = applier.apply_left_most_used_page(updated.pages[0])
    click.echo(json.dumps(result, indent=2))


@main.command()
@click.option("--port", default=8765, type=int)
def serve(port: int) -> None:
    """Start interactive local Home Studio layout editor server."""
    from automation.web_editor import serve_editor
    serve_editor(port=port)


@main.command()
def editor() -> None:
    """Regenerate standalone reports/layout-editor.html."""
    from automation.web_editor import write_static_editor
    out = write_static_editor()
    click.echo(f"Generated interactive layout editor: {out}")


if __name__ == "__main__":
    main()
