"""Unit tests for Home Studio planner, validator, classifier, dump parser, and executor."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from automation.executor.planner_ops import ExecutionJournal, plan_reconciliation_ops
from automation.gestures.engine import LauncherGeometry
from automation.inventory.classifier import classify_inventory
from automation.launcher.dump_parser import parse_oneui_dump
from automation.models import DesiredLayout, GridSpec, HeaderInstanceConfig, LauncherConfig, PageItemSpec, PageSpec, PositionSpec
from automation.planner.layout_planner import generate_proposed_layout
from automation.planner.validator import LayoutValidationError, validate_layout


def _sample_apps():
    return [
        {
            "id": "u0:com.sbi.lotusintouch/com.sbi.lotusintouch.MainActivity",
            "displayName": "YONO SBI",
            "packageName": "com.sbi.lotusintouch",
            "activityName": "com.sbi.lotusintouch.MainActivity",
            "componentName": "com.sbi.lotusintouch/com.sbi.lotusintouch.MainActivity",
            "profileId": 0,
            "isSystemApp": False,
            "existingHomePages": [],
            "existingFolders": [],
        },
        {
            "id": "u0:com.sbi.upi/com.sbi.upi.SplashActivity",
            "displayName": "Bhim SBI Pay",
            "packageName": "com.sbi.upi",
            "activityName": "com.sbi.upi.SplashActivity",
            "componentName": "com.sbi.upi/com.sbi.upi.SplashActivity",
            "profileId": 0,
            "isSystemApp": False,
            "existingHomePages": [8],
            "existingFolders": ["SBI suite", "UPI"],
        },
        {
            "id": "u0:com.openai.chatgpt/com.openai.chatgpt.MainActivity",
            "displayName": "ChatGPT",
            "packageName": "com.openai.chatgpt",
            "activityName": "com.openai.chatgpt.MainActivity",
            "componentName": "com.openai.chatgpt/com.openai.chatgpt.MainActivity",
            "profileId": 0,
            "isSystemApp": False,
            "existingHomePages": [3],
            "existingFolders": [],
        },
        {
            "id": "u0:com.whatsapp/com.whatsapp.Main",
            "displayName": "WhatsApp",
            "packageName": "com.whatsapp",
            "activityName": "com.whatsapp.Main",
            "componentName": "com.whatsapp/com.whatsapp.Main",
            "profileId": 0,
            "isSystemApp": False,
            "existingHomePages": [7],
            "existingFolders": [],
        },
        {
            "id": "u95:com.whatsapp/com.whatsapp.Main",
            "displayName": "WhatsApp",
            "packageName": "com.whatsapp",
            "activityName": "com.whatsapp.Main",
            "componentName": "com.whatsapp/com.whatsapp.Main",
            "profileId": 95,
            "isSystemApp": False,
            "existingHomePages": [7],
            "existingFolders": [],
        },
    ]


def test_classifier_multi_category_and_banks():
    apps = _sample_apps()
    summary = classify_inventory(apps)
    assert summary["assigned"] == 5
    sbi_upi = next(a for a in apps if a["packageName"] == "com.sbi.upi")
    assert set(["banking-suites", "upi", "payments"]).issubset(set(sbi_upi["proposedCategories"]))
    assert sbi_upi["bankInstitution"] == "SBI"


def test_layout_planner_and_validator():
    apps = _sample_apps()
    classify_inventory(apps)
    layout, report = generate_proposed_layout(apps, columns=5, rows=6)
    installed = {a["id"] for a in apps}
    warnings = validate_layout(layout, installed_ids=installed, allow_duplicates=True)
    assert isinstance(warnings, list)
    page_ids = [p.id for p in layout.pages]
    assert "daily-essentials" in page_ids
    assert "banking-suites" in page_ids
    assert "upi" in page_ids
    assert page_ids[-1] == "wallpaper"
    assert report["totalPages"] == len(layout.pages)


def test_validator_rejects_collision():
    layout = DesiredLayout(
        launcher=LauncherConfig(grid=GridSpec(columns=4, rows=6)),
        pages=[
            PageSpec(
                id="p1",
                title="P1",
                order=0,
                isDefaultHome=True,
                header=HeaderInstanceConfig(
                    enabled=True,
                    position=PositionSpec(row=0, column=0, rowSpan=1, columnSpan=4),
                ),
                items=[
                    PageItemSpec(
                        id="a1",
                        type="app",
                        packageName="a.b",
                        activityName="a.b.C",
                        position=PositionSpec(row=0, column=0, rowSpan=1, columnSpan=1),
                    )
                ],
            )
        ],
    )
    with pytest.raises(LayoutValidationError, match="collision"):
        validate_layout(layout)


def test_geometry_grid_centers():
    geom = LauncherGeometry(columns=5, rows=6)
    x, y = geom.cell_center(0, 0)
    assert 41 < x < 241
    assert 125 < y < 400


def test_reconciliation_ops_dependencies_and_journal(tmp_path: Path):
    apps = _sample_apps()
    classify_inventory(apps)
    layout, _ = generate_proposed_layout(apps, columns=5, rows=6)
    observed = {"oneUiSettings": "Home Grid : 4 X 6", "pages": [{"page_index": 0, "items": []}]}
    ops = plan_reconciliation_ops(layout, observed)
    assert ops[0].opType == "ensure_grid"
    assert any(o.opType == "create_folder" for o in ops)
    assert any(o.opType == "add_header_widget" for o in ops)

    journal = ExecutionJournal(tmp_path / "journal.jsonl", tmp_path / "ckpt.json")
    journal.append(ops[0], "planned")
    journal.save_checkpoint("test-phase", ops)
    assert (tmp_path / "journal.jsonl").exists()
    assert json.loads((tmp_path / "ckpt.json").read_text())["phase"] == "test-phase"


def test_dump_parser_on_sample():
    sample = """
Home Common Settings
    Edit Lock : false
OneUI Settings
    Home Grid : 4 X 6
HomeUp Settings
    HomeSetting : HomeMasterData(enabled=true)
DB : OneUI
 table - item_group
 0 {    id=2    type=workspace    container_id=1    rank=0}
 1 {    id=3    type=page    container_id=2    rank=0}
 2 {    id=11    type=hotseat    container_id=1    rank=0}
 table - item
 0 {    id=10    type=app    title=Phone    component=com.samsung.android.dialer/.DialtactsActivity    app_widget_id=-1    position_x=0    position_y=0    spanX=-1    spanY=-1    rank=0    profile_id=0    container_type=0    container_id=11}
DB : HomeOnly
"""
    snap = parse_oneui_dump(sample)
    assert len(snap.pages) == 1
    assert len(snap.dock_items) == 1
    assert snap.dock_items[0]["title"] == "Phone"


def test_real_desired_layout_max_8_per_page():
    p = Path("config/desired-layout.json")
    if not p.exists():
        return
    layout = DesiredLayout.model_validate_json(p.read_text())
    apps = json.loads(Path("inventory/applications.json").read_text())
    warnings = validate_layout(layout, installed_ids={a["id"] for a in apps}, allow_duplicates=True)
    assert warnings == []
    for page in layout.pages:
        interactive = [it for it in page.items if it.type in ("app", "folder")]
        assert len(interactive) <= 8, f"Page {page.id} has {len(interactive)} > 8 items"


def test_web_editor_and_applier_helpers(tmp_path: Path):
    from automation.executor.live_applier import _escape_input_text, _finder_query_for_label, _norm_comp
    from automation.web_editor import build_editor_html

    assert _finder_query_for_label("OpenCode - AI") == "OpenCode%sAI"
    assert _finder_query_for_label("Domino's") == "Domino%ss"
    assert _escape_input_text("Credit & Bills") == "Credit%s\\&%sBills"
    assert _norm_comp("a.b/.C") == "a.b/a.b.C"

    html = build_editor_html('{"pages":[]}', "[]", "{}")
    assert "HomeScreenOrganizer" in html
    assert "Interactive Layout Editor" in html


def test_reconciliation_zero_ops_when_matched():
    obs_path = Path("config/observed-layout.json")
    app_path = Path("config/approved-layout.json")
    if not obs_path.exists() or not app_path.exists():
        return
    layout = DesiredLayout.model_validate_json(app_path.read_text())
    observed = json.loads(obs_path.read_text())
    assert plan_reconciliation_ops(layout, observed) == []


def test_usage_stats_parser_and_most_used_left_page():
    from automation.device.usage_stats import parse_dumpsys_usagestats, select_top_used_apps
    from automation.planner.layout_planner import add_most_used_left_page

    sample_dump = """
      package=com.google.android.youtube totalTime="02:15:10" lastTimeActive="2026-11-25"
      package=com.microsoft.teams totalTimeUsed="45:30"
      package=com.rapido.passenger totalTime=900000
    """
    parsed = parse_dumpsys_usagestats(sample_dump)
    assert parsed["com.google.android.youtube"] == ((2 * 3600) + (15 * 60) + 10) * 1000
    assert parsed["com.microsoft.teams"] == ((45 * 60) + 30) * 1000
    assert parsed["com.rapido.passenger"] == 900000

    apps = _sample_apps()
    classify_inventory(apps)
    layout, _ = generate_proposed_layout(apps, columns=4, rows=6)
    chosen = select_top_used_apps(apps, parsed, pinned_refs=["YONO SBI", "ChatGPT"], limit=4)
    assert chosen[0]["displayName"] == "YONO SBI"
    assert chosen[1]["displayName"] == "ChatGPT"

    updated = add_most_used_left_page(layout, apps, pinned_refs=["YONO SBI", "ChatGPT"], usage_ms_by_pkg=parsed)
    assert updated.pages[0].id == "most-used"
    assert updated.pages[0].order == 0
    assert updated.pages[0].isDefaultHome is False
    daily = next(p for p in updated.pages if p.id == "daily-essentials")
    assert daily.order == 1
    assert daily.isDefaultHome is True


