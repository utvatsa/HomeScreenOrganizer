"""Idempotent, checkpointed live execution engine for Samsung One UI Home."""

from __future__ import annotations

import json
import re
import time
import xml.etree.ElementTree as ET
from dataclasses import asdict
from pathlib import Path
from typing import Any, Optional

from automation.adb.client import AdbClient, AdbError, DeviceTarget
from automation.executor.planner_ops import ExecutionJournal, OperationRecord
from automation.gestures.engine import GestureEngine, LauncherGeometry, parse_bounds
from automation.launcher.dump_parser import LauncherSnapshot, parse_oneui_dump
from automation.models import DesiredLayout, PageItemSpec, PageSpec


def _norm_comp(comp: str | None) -> str:
    if not comp or "/" not in comp:
        return comp or ""
    pkg, act = comp.split("/", 1)
    if act.startswith("."):
        act = pkg + act
    return f"{pkg}/{act}"


def _finder_query_for_label(label: str) -> str:
    # Keep alphanumeric words only so shell & punctuation never break Finder query
    cleaned = re.sub(r"[^A-Za-z0-9 ]+", " ", label).strip()
    words = [w for w in cleaned.split() if w]
    if not words:
        return label
    return "%s".join(words[:3])


def _escape_input_text(text: str) -> str:
    # Escape characters significant to /system/bin/sh and Android input text
    out = []
    for ch in text:
        if ch == " ":
            out.append("%s")
        elif ch in ("&", "'", '"', "(", ")", ";", "<", ">", "|", "$", "`", "\\"):
            out.append("\\" + ch)
        else:
            out.append(ch)
    return "".join(out)


class LiveApplier:
    """Executes an approved DesiredLayout on a physical Samsung One UI Home device."""

    HEADER_PROVIDER = "com.homestudio.local/.widget.CategoryHeaderWidgetProvider"
    SPACER_PROVIDER = "com.homestudio.local/.widget.WallpaperPlaceholderWidgetProvider"
    SPACER_1X1_PROVIDER = "com.homestudio.local/.widget.WallpaperSpacer1x1Provider"
    WEATHER_PROVIDER = "com.sec.android.daemonapp/.appwidget.WeatherAppWidget2x1"

    def __init__(
        self,
        adb: AdbClient,
        layout: DesiredLayout,
        apps_by_id: dict[str, dict[str, Any]],
    ) -> None:
        self.adb = adb
        self.layout = layout
        self.apps_by_id = apps_by_id
        self.geom = LauncherGeometry(
            columns=layout.launcher.grid.columns,
            rows=layout.launcher.grid.rows or 6,
        )
        self.eng = GestureEngine(adb, self.geom)
        self.journal = ExecutionJournal()
        self.tmp_xml = Path("screenshots/execution/_tmp_ui.xml")
        self.tmp_xml.parent.mkdir(parents=True, exist_ok=True)
        # Cache header widget id -> {categoryId, title}
        self.widget_mappings: dict[int, dict[str, str]] = {}
        self.duplicate_name_index_cache: dict[str, int] = {}
        self.failed_items: list[dict[str, Any]] = []

    def dismiss_keyboard_if_open(self) -> None:
        """Dismiss soft keyboard if currently shown so dock/App Drawer taps never hit IME."""
        for _ in range(2):
            out = self.adb.shell("dumpsys input_method | grep 'mInputShown='", check=False)
            if "mInputShown=true" not in out:
                break
            self.adb.keyevent("KEYCODE_BACK")
            time.sleep(0.45)

    def dump_launcher(self) -> LauncherSnapshot:
        return parse_oneui_dump(self.adb.shell("dumpsys activity provider com.sec.android.app.launcher"))

    def ensure_home_page(self, pnum_1based: int) -> None:
        """Ensure we are on the given 1-based home page cleanly via Home Edit Mode."""
        self.adb.preflight(require_launcher=False)
        fg = self.adb.get_foreground_info()
        if "com.sec.android.app.launcher" not in str(fg["current_focus"]):
            self.adb.keyevent("KEYCODE_HOME")
            time.sleep(0.8)

        idx, total = self.eng.get_active_marker_index()
        if idx == pnum_1based:
            return
        if 1 <= idx <= total and abs(pnum_1based - idx) <= 5:
            self.eng.navigate_to_page(pnum_1based)
            return
        # Fast jump via Home Edit Mode page indicator dots
        self._enter_edit_mode_safe()
        out = self.adb.shell("dumpsys activity top | grep -A 120 'workspace_page_indicator'", check=False)
        m_ll = re.search(r"workspace_page_indicator[^\n]*\n\s+android\.widget\.LinearLayout\{[^\}]+\s+(\d+),(\d+)-(\d+),(\d+)[^\}]*\}", out)
        if m_ll:
            ll_left, ll_top, ll_right, ll_bottom = int(m_ll.group(1)), int(m_ll.group(2)), int(m_ll.group(3)), int(m_ll.group(4))
            dot_w = max(30.0, (ll_right - ll_left) / max(1, total + 1))
            dot_x = int(ll_left + (pnum_1based + 0.5) * dot_w)
            dot_y = (ll_top + ll_bottom) // 2 if ll_top > 500 else 1874
            self.adb.tap(dot_x, dot_y)
            time.sleep(0.6)
            self.adb.tap(540, 950)
            time.sleep(0.7)
        idx_now, _ = self.eng.get_active_marker_index()
        if idx_now != pnum_1based:
            self.eng.navigate_to_page(pnum_1based, max_steps=25)

    def _enter_edit_mode_safe(self) -> None:
        """Enter Home Edit Mode by long-pressing an empty cell or outer wallpaper margin."""
        out = self.adb.shell("dumpsys activity top | grep -E 'wallpapaer_button|home_setting_button'", check=False)
        if "wallpapaer_button" in out or "home_setting_button" in out:
            return
        # Pick an empty cell on current page if possible, else outer left margin (20, 900)
        snap = self.dump_launcher()
        idx, _ = self.eng.get_active_marker_index()
        neutral_x, neutral_y = 20, 900
        if 1 <= idx <= len(snap.pages):
            pg = snap.pages[idx - 1]
            occupied = set()
            for it in pg["items"]:
                px, py = max(0, it.get("position_x", 0)), max(0, it.get("position_y", 0))
                sx, sy = max(1, it.get("span_x", 1)), max(1, it.get("span_y", 1))
                for r in range(py, py + sy):
                    for c in range(px, px + sx):
                        occupied.add((c, r))
            for r in range(self.geom.rows):
                found = False
                for c in range(self.geom.columns):
                    if (c, r) not in occupied:
                        neutral_x, neutral_y = self.geom.cell_center(r, c)
                        found = True
                        break
                if found:
                    break
        self.adb.swipe(neutral_x, neutral_y, neutral_x, neutral_y, 950)
        time.sleep(0.85)

    def _tap_xml_node(self, predicate, timeout_retries: int = 2) -> bool:
        for _ in range(timeout_retries):
            try:
                self.adb.uiautomator_dump(self.tmp_xml)
                tree = ET.parse(self.tmp_xml)
                for n in tree.iter("node"):
                    if predicate(n):
                        b = parse_bounds(n.attrib.get("bounds", ""))
                        if b:
                            self.adb.tap((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
                            return True
            except AdbError:
                time.sleep(0.5)
        return False

    def _remove_item_at_cell(
        self, pnum: int, col: int, row: int, span_x: int = 1, span_y: int = 1, item_type: str = "app"
    ) -> bool:
        """Long-press an item/widget at (col, row) and tap Remove (NEVER Uninstall)."""
        self.ensure_home_page(pnum)
        x1, y1, x2, y2 = self.geom.cell_bounds(row, col, max(1, span_y), max(1, span_x))
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        self.adb.swipe(cx, cy, cx, cy, 950)
        time.sleep(0.8)

        def _is_remove(n: ET.Element) -> bool:
            d = n.attrib.get("content-desc", "")
            t = n.attrib.get("text", "")
            # Never match Uninstall!
            if "uninstall" in (d + t).lower():
                return False
            return d in ("Remove, Button", "Remove stack, Button", "Remove all, Button") or t in (
                "Remove",
                "Remove from Home",
                "Remove stack",
                "Remove all",
            )

        if self._tap_xml_node(_is_remove):
            time.sleep(0.6)
            if item_type in ("folder", "stackedwidget"):
                self._tap_xml_node(
                    lambda n: (
                        n.attrib.get("resource-id") in ("android:id/button1", "com.sec.android.app.launcher:id/positive_button")
                        or n.attrib.get("text") in ("Remove", "Remove stack", "Remove all", "Delete")
                    )
                    and "uninstall" not in (n.attrib.get("content-desc", "") + n.attrib.get("text", "")).lower()
                )
                time.sleep(0.4)
            return True
        # Dismiss any accidental menu or Edit Mode
        self.adb.keyevent("KEYCODE_HOME")
        time.sleep(0.4)
        return False

    def drag_cells(self, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 1550) -> None:
        self.adb.shell(f"input draganddrop {int(x1)} {int(y1)} {int(x2)} {int(y2)} {int(duration_ms)}")
        time.sleep(0.9)

    def drag_grid_to_grid(self, c1: int, r1: int, c2: int, r2: int, duration_ms: int = 1550) -> None:
        x1, y1 = self.geom.cell_center(r1, c1)
        x2, y2 = self.geom.cell_center(r2, c2)
        self.drag_cells(x1, y1, x2, y2, duration_ms)

    def create_page(self) -> int:
        """Create a new empty home page in Home Edit Mode and return new page count."""
        before = len(self.dump_launcher().pages)
        self.ensure_home_page(before)
        self._enter_edit_mode_safe()
        # Swipe right inside Home Edit Mode to center the '+' Add page card, then tap center
        self.adb.swipe(850, 1000, 200, 1000, 350)
        time.sleep(0.7)
        self.adb.tap(540, 1000)
        time.sleep(1.0)
        self.adb.keyevent("KEYCODE_HOME")
        time.sleep(0.7)
        after = len(self.dump_launcher().pages)
        return after

    def delete_empty_page(self, pnum: int) -> bool:
        self.ensure_home_page(pnum)
        self._enter_edit_mode_safe()
        ok = self._tap_xml_node(
            lambda n: n.attrib.get("resource-id") == "com.sec.android.app.launcher:id/delete_page_layout"
            or "Remove" in n.attrib.get("content-desc", "")
        )
        time.sleep(0.8)
        self.adb.keyevent("KEYCODE_HOME")
        time.sleep(0.6)
        return ok

    def sync_home_studio_config(self) -> None:
        """Push global theme, categories, and widget_id mappings into Home Studio and refresh."""
        cats = [
            {"id": p.id, "title": p.title}
            for p in sorted(self.layout.pages, key=lambda x: x.order)
            if p.title
        ]
        widgets = [
            {
                "appWidgetId": wid,
                "categoryId": info["categoryId"],
                "title": info["title"],
                "inheritTheme": True,
            }
            for wid, info in sorted(self.widget_mappings.items())
        ]
        cfg = {
            "schemaVersion": 1,
            "globalTheme": {
                "fontFamily": self.layout.theme.header.fontFamily,
                "fontWeight": self.layout.theme.header.fontWeight,
                "fontSizeSp": self.layout.theme.header.fontSizeSp,
                "textColor": self.layout.theme.header.textColor,
                "opacity": self.layout.theme.header.opacity,
                "alignment": self.layout.theme.header.alignment,
                "verticalAlignment": "bottom",
                "backgroundColor": self.layout.theme.header.backgroundColor,
                "paddingDp": self.layout.theme.header.paddingDp,
                "letterSpacing": self.layout.theme.header.letterSpacing,
                "showSeparator": self.layout.theme.header.showSeparator,
                "separatorColor": self.layout.theme.header.separatorColor,
                "shadowEnabled": self.layout.theme.header.shadowEnabled,
            },
            "categories": cats,
            "widgets": widgets,
        }
        raw = json.dumps(cfg, indent=2)
        local_tmp = Path("logs/home_studio_config_push.json")
        local_tmp.write_text(raw, encoding="utf-8")
        # Write safely via run-as stdin
        import subprocess
        subprocess.run(
            [
                self.adb.target.adb_path, "-s", self.adb.target.serial,
                "shell", "run-as com.homestudio.local sh -c 'cat > files/home_studio_config.json'",
            ],
            input=raw.encode("utf-8"),
            check=True,
            timeout=15.0,
        )
        self.adb.shell("am start -S -n com.homestudio.local/.ui.MainActivity --ez refresh_widgets true")
        time.sleep(1.0)
        self.adb.keyevent("KEYCODE_HOME")
        time.sleep(0.7)

    def pin_widget_via_home_studio(self, *, spacer: bool = False, spacer_1x1: bool = False) -> Optional[int]:
        """Trigger requestPinAppWidget via Home Studio debug intent and confirm Add."""
        before_snap = self.dump_launcher()
        before_ids = {
            it["app_widget_id"]
            for p in before_snap.pages
            for it in p["items"]
            if it["type"] == "widget" and it["app_widget_id"] > 0
        }
        if spacer_1x1:
            flag = "--ez pin_spacer_1x1 true"
        elif spacer:
            flag = "--ez pin_spacer true"
        else:
            flag = "--ez pin_header true"
        self.adb.shell(f"am start -S -n com.homestudio.local/.ui.MainActivity {flag}")
        time.sleep(1.3)
        # Tap Samsung's Add button
        added = self._tap_xml_node(
            lambda n: n.attrib.get("resource-id") == "com.sec.android.app.launcher:id/add_button"
            or n.attrib.get("text") == "Add"
        )
        time.sleep(1.2)
        self.adb.keyevent("KEYCODE_HOME")
        time.sleep(0.8)
        if not added:
            return None
        after_snap = self.dump_launcher()
        for p in after_snap.pages:
            for it in p["items"]:
                if it["type"] == "widget" and it["app_widget_id"] not in before_ids:
                    return int(it["app_widget_id"])
        return None

    def add_app_to_first_open_page(
        self,
        *,
        display_name: str,
        package_name: str,
        activity_name: str,
        profile_id: int,
        expected_pnum: int,
    ) -> Optional[dict[str, Any]]:
        """Add an app via Samsung Finder -> Add to Home, verifying exact component & profile."""
        expected_norm = _norm_comp(f"{package_name}/{activity_name}")
        app_id = f"u{profile_id}:{expected_norm}"
        start_idx = self.duplicate_name_index_cache.get(app_id, 0)

        for attempt in range(6):
            self.dismiss_keyboard_if_open()
            before_snap = self.dump_launcher()
            before_ids = {
                it["id"]
                for it in before_snap.pages[expected_pnum - 1]["items"]
            } if expected_pnum <= len(before_snap.pages) else set()

            self.ensure_home_page(expected_pnum)
            self.dismiss_keyboard_if_open()
            # Open App Drawer -> tap Search
            self.adb.tap(939, 2070)
            time.sleep(1.0)
            self.adb.tap(450, 2088)
            time.sleep(0.7)
            q = _finder_query_for_label(display_name)
            self.adb.shell(f"input text {q}")
            time.sleep(1.2)
            self.adb.uiautomator_dump(self.tmp_xml)
            tree = ET.parse(self.tmp_xml)

            matches: list[tuple[int, int]] = []
            exact_matches: list[tuple[int, int]] = []
            for n in tree.iter("node"):
                if n.attrib.get("resource-id") == "com.sec.android.app.launcher:id/tray":
                    desc = n.attrib.get("content-desc", "")
                    b = parse_bounds(n.attrib.get("bounds", ""))
                    if not b:
                        continue
                    pt = ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
                    if desc == display_name:
                        exact_matches.append(pt)
                    elif display_name.lower() in desc.lower():
                        matches.append(pt)

            candidates = exact_matches or matches
            if not candidates:
                self.dismiss_keyboard_if_open()
                self.adb.keyevent("KEYCODE_HOME")
                time.sleep(0.6)
                continue

            pick_idx = (start_idx + attempt) % len(candidates)
            tx, ty = candidates[pick_idx]
            self.adb.swipe(tx, ty, tx, ty, 900)
            time.sleep(0.9)

            ok = self._tap_xml_node(
                lambda n: "Add to Home" in (n.attrib.get("content-desc", "") + n.attrib.get("text", ""))
            )
            time.sleep(1.1)
            if not ok:
                self.dismiss_keyboard_if_open()
                self.adb.keyevent("KEYCODE_HOME")
                time.sleep(0.6)
                continue

            after_snap = self.dump_launcher()
            if expected_pnum <= len(after_snap.pages):
                new_items = [
                    it for it in after_snap.pages[expected_pnum - 1]["items"]
                    if it["id"] not in before_ids and it["type"] == "app"
                ]
                if new_items:
                    added = new_items[0]
                    got_norm = _norm_comp(added.get("component"))
                    got_prof = int(added.get("profile_id", 0))
                    if got_norm == expected_norm and got_prof == profile_id:
                        self.duplicate_name_index_cache[app_id] = pick_idx
                        return added
                    # Wrong sibling added (e.g. Samsung Wallet vs Google Wallet) -> remove & try next candidate!
                    self._remove_item_at_cell(expected_pnum, added["position_x"], added["position_y"])
        print(f"[WARN] Failed to add {display_name} ({package_name}, u{profile_id}) to page {expected_pnum}", flush=True)
        self.failed_items.append({"page": expected_pnum, "label": display_name, "package": package_name, "profileId": profile_id})
        return None

    def rename_folder_at_cell(self, pnum: int, col: int, row: int, title: str) -> bool:
        self.ensure_home_page(pnum)
        self.dismiss_keyboard_if_open()
        cx, cy = self.geom.cell_center(row, col)
        self.adb.tap(cx, cy)
        time.sleep(0.9)
        tapped = self._tap_xml_node(
            lambda n: "EditText" in n.attrib.get("class", "")
            or "folder_name" in n.attrib.get("resource-id", "").lower()
            or "Folder name" in n.attrib.get("text", "")
        )
        if not tapped:
            self.dismiss_keyboard_if_open()
            self.adb.keyevent("KEYCODE_BACK")
            return False
        time.sleep(0.6)
        # Clear any existing title and enter desired folder title
        self.adb.keyevent("KEYCODE_MOVE_END")
        for _ in range(25):
            self.adb.keyevent("KEYCODE_DEL")
        escaped = _escape_input_text(title)
        self.adb.shell(f"input text {escaped}")
        time.sleep(0.4)
        self.adb.keyevent("KEYCODE_ENTER")
        time.sleep(0.4)
        self.dismiss_keyboard_if_open()
        self.adb.keyevent("KEYCODE_HOME")
        time.sleep(0.6)
        self.dismiss_keyboard_if_open()
        return True

    def stage1_clear_to_weather_canvas(self) -> None:
        """Remove obsolete items across pages 1..N while keeping Weather (id=11) on Page 1 (0,1)."""
        snap = self.dump_launcher()
        # Find which page holds Weather id=11
        weather_page_idx = None
        for p in snap.pages:
            for it in p["items"]:
                if it["type"] == "widget" and self.WEATHER_PROVIDER in str(it.get("component", "")):
                    weather_page_idx = p["page_index"]
                    break

        # Remove all non-Weather items from every page
        for _round in range(150):
            snap = self.dump_launcher()
            did_remove = False
            for p in snap.pages:
                pnum = p["page_index"] + 1
                for it in p["items"]:
                    if it["type"] == "widget" and self.WEATHER_PROVIDER in str(it.get("component", "")):
                        continue
                    cx = max(0, it.get("position_x", 0))
                    cy = max(0, it.get("position_y", 0))
                    sx = max(1, it.get("span_x", 1))
                    sy = max(1, it.get("span_y", 1))
                    if self._remove_item_at_cell(pnum, cx, cy, sx, sy, item_type=it["type"]):
                        did_remove = True
                        break
                    # For stackedwidget or offset widget, retry slightly lower inside cell
                    if it["type"] in ("widget", "stackedwidget"):
                        self.ensure_home_page(pnum)
                        x1, y1, x2, y2 = self.geom.cell_bounds(cy, cx, sy, sx)
                        self.adb.swipe((x1 + x2) // 2, (y1 + y2) // 2 + 70, (x1 + x2) // 2, (y1 + y2) // 2 + 70, 950)
                        time.sleep(0.9)
                        if self._tap_xml_node(
                            lambda n: (
                                n.attrib.get("content-desc", "") in ("Remove, Button", "Remove stack, Button", "Remove all, Button")
                                or n.attrib.get("text", "") in ("Remove", "Remove from Home", "Remove stack", "Remove all")
                            )
                            and "uninstall" not in (n.attrib.get("content-desc", "") + n.attrib.get("text", "")).lower()
                        ):
                            time.sleep(0.8)
                            did_remove = True
                            break
                        self.adb.keyevent("KEYCODE_HOME")
                        time.sleep(0.4)
                if did_remove:
                    break
            if not did_remove:
                break

        # Delete all empty pages (leaving only the 1 page holding Weather, or Page 1 if Weather absent)
        for _ in range(20):
            snap = self.dump_launcher()
            if len(snap.pages) <= 1:
                break
            deleted = False
            for p in reversed(snap.pages):
                if len(p["items"]) == 0:
                    if self.delete_empty_page(p["page_index"] + 1):
                        deleted = True
                        break
            if not deleted:
                break

        # Move Weather on Page 1 from (0,0) -> (0,1) so row 0 is open for Daily Essentials Header
        snap = self.dump_launcher()
        for it in snap.pages[0]["items"]:
            if it["type"] == "widget" and self.WEATHER_PROVIDER in str(it.get("component", "")):
                if it["position_y"] != 1:
                    self.ensure_home_page(1)
                    x1, y1 = self.geom.cell_center(it["position_y"], 1)
                    x2, y2 = self.geom.cell_center(1, 1)
                    self.drag_cells(x1, y1, x2, y2, 1300)
                break
        self.journal.save_checkpoint("stage1_clean_canvas", [], {"pagesRemaining": len(self.dump_launcher().pages)})

    def build_page(self, page: PageSpec) -> None:
        """Build a single approved page on the phone, skipping items already verified."""
        pnum = page.order + 1
        print(f"[build_page] Start P{pnum:02d} {page.id} ({page.title})", flush=True)
        self.adb.preflight(require_launcher=False)

        for _ in range(5):
            if len(self.dump_launcher().pages) >= pnum:
                break
            self.create_page()
        if len(self.dump_launcher().pages) < pnum:
            raise AdbError(f"Failed to create home page {pnum}")

        self.ensure_home_page(pnum)
        snap = self.dump_launcher()
        cur_page = snap.pages[pnum - 1]

        # 1. Category Header widget at (0, 0)
        if page.header.enabled and page.title:
            existing_hdr = [it for it in cur_page["items"] if it["type"] == "widget" and "CategoryHeaderWidgetProvider" in str(it.get("component", ""))]
            if not existing_hdr:
                wid = self.pin_widget_via_home_studio(spacer=False)
                if wid is not None:
                    self.widget_mappings[wid] = {"categoryId": page.id, "title": page.title}
                    self.sync_home_studio_config()
                    snap = self.dump_launcher()
                    cur_page = snap.pages[pnum - 1]
                    for it in cur_page["items"]:
                        if it.get("app_widget_id") == wid and (it["position_x"] != 0 or it["position_y"] != 0):
                            x1, y1 = self.geom.cell_center(it["position_y"], min(3, it["position_x"] + 1))
                            x2, y2 = self.geom.cell_center(0, 1)
                            self.drag_cells(x1, y1, x2, y2, 1200)
                            break
            else:
                wid = existing_hdr[0]["app_widget_id"]
                self.widget_mappings[wid] = {"categoryId": page.id, "title": page.title}
                self.sync_home_studio_config()

        # 2. Place apps and folders in rows 4-5
        for item in page.items:
            if item.type in ("widget", "placeholder_widget"):
                continue
            tx = item.position.column
            ty = item.position.row
            if item.type == "app":
                snap = self.dump_launcher()
                cur_page = snap.pages[pnum - 1]
                exp_norm = _norm_comp(f"{item.packageName}/{item.activityName}")
                already = any(
                    it["type"] == "app"
                    and _norm_comp(it.get("component")) == exp_norm
                    and int(it.get("profile_id", 0)) == item.profileId
                    and it["position_x"] == tx and it["position_y"] == ty
                    for it in cur_page["items"]
                )
                if already:
                    continue
                print(f"[P{pnum:02d}] Placing app {item.title} -> ({tx},{ty})", flush=True)
                # If already sitting elsewhere on this page (e.g. from prior partial run), move it directly
                misplaced = next(
                    (
                        it for it in cur_page["items"]
                        if it["type"] == "app"
                        and _norm_comp(it.get("component")) == exp_norm
                        and int(it.get("profile_id", 0)) == item.profileId
                    ),
                    None,
                )
                if misplaced is None:
                    misplaced = self.add_app_to_first_open_page(
                        display_name=item.title or "",
                        package_name=item.packageName or "",
                        activity_name=item.activityName or "",
                        profile_id=item.profileId,
                        expected_pnum=pnum,
                    )
                if misplaced and (misplaced["position_x"] != tx or misplaced["position_y"] != ty):
                    self.ensure_home_page(pnum)
                    self.drag_grid_to_grid(misplaced["position_x"], misplaced["position_y"], tx, ty, duration_ms=1550)
                    # Verify landed; retry once with longer hold if missed
                    snap_chk = self.dump_launcher()
                    still_at = next(
                        (
                            it for it in snap_chk.pages[pnum - 1]["items"]
                            if it.get("id") == misplaced.get("id")
                            and (it["position_x"] != tx or it["position_y"] != ty)
                        ),
                        None,
                    )
                    if still_at:
                        self.ensure_home_page(pnum)
                        self.drag_grid_to_grid(still_at["position_x"], still_at["position_y"], tx, ty, duration_ms=1800)

            elif item.type == "folder":
                standalone_coords = {
                    (it_other.position.column, it_other.position.row)
                    for it_other in page.items
                    if it_other.type == "app"
                }
                for ch in item.children:
                    snap = self.dump_launcher()
                    cur_page = snap.pages[pnum - 1]
                    ch_norm = _norm_comp(f"{ch.packageName}/{ch.activityName}")
                    # Check if child is already at (tx, ty) either as solo item or inside folder at (tx, ty)
                    at_cell = [it for it in cur_page["items"] if it["position_x"] == tx and it["position_y"] == ty]
                    already_in = False
                    for it in at_cell:
                        if it["type"] == "app" and _norm_comp(it.get("component")) == ch_norm and int(it.get("profile_id", 0)) == ch.profileId:
                            already_in = True
                        elif it["type"] == "folder":
                            for fc in it.get("children", []):
                                if _norm_comp(fc.get("component")) == ch_norm and int(fc.get("profile_id", 0)) == ch.profileId:
                                    already_in = True
                    if already_in:
                        continue
                    print(f"[P{pnum:02d}] Placing folder child {item.title}/{ch.label} -> ({tx},{ty})", flush=True)
                    misplaced = next(
                        (
                            it for it in cur_page["items"]
                            if it["type"] == "app"
                            and _norm_comp(it.get("component")) == ch_norm
                            and int(it.get("profile_id", 0)) == ch.profileId
                            and (it["position_x"], it["position_y"]) not in standalone_coords
                        ),
                        None,
                    )
                    if misplaced is None:
                        misplaced = self.add_app_to_first_open_page(
                            display_name=ch.label,
                            package_name=ch.packageName,
                            activity_name=ch.activityName or "",
                            profile_id=ch.profileId,
                            expected_pnum=pnum,
                        )
                    if misplaced and (misplaced["position_x"] != tx or misplaced["position_y"] != ty):
                        self.ensure_home_page(pnum)
                        self.drag_grid_to_grid(misplaced["position_x"], misplaced["position_y"], tx, ty, duration_ms=1550)
                        snap_chk = self.dump_launcher()
                        still_out = next(
                            (
                                it for it in snap_chk.pages[pnum - 1]["items"]
                                if it["type"] == "app"
                                and _norm_comp(it.get("component")) == ch_norm
                                and int(it.get("profile_id", 0)) == ch.profileId
                                and (it["position_x"] != tx or it["position_y"] != ty)
                            ),
                            None,
                        )
                        if still_out:
                            self.ensure_home_page(pnum)
                            self.drag_grid_to_grid(still_out["position_x"], still_out["position_y"], tx, ty, duration_ms=1800)

                # Rename folder if needed
                snap = self.dump_launcher()
                cur_page = snap.pages[pnum - 1]
                for it in cur_page["items"]:
                    if it["type"] == "folder" and it["position_x"] == tx and it["position_y"] == ty:
                        if (it.get("title") or "") != (item.title or ""):
                            self.rename_folder_at_cell(pnum, tx, ty, item.title or "Suite")
                        break

        # 3. Temporarily block remaining upper rows (4x1) + any partial row 4-5 gaps (1x1)
        # so the NEXT page's Add to Home drops onto pnum + 1 automatically!
        if pnum < len(self.layout.pages) - 1:
            rows_needed = [2, 3] if any(it.type == "widget" for it in page.items) else [1, 2, 3]
            for r_fill in rows_needed:
                snap = self.dump_launcher()
                cur_page = snap.pages[pnum - 1]
                occupied_row = any(
                    it["position_y"] <= r_fill < it["position_y"] + max(1, it["span_y"])
                    for it in cur_page["items"]
                )
                if not occupied_row:
                    self.pin_widget_via_home_studio(spacer=True)

            # Fill any remaining empty cells in rows 4-5 (e.g. 5-item Banking Suites or 7-item AI Suite)
            snap = self.dump_launcher()
            cur_page = snap.pages[pnum - 1]
            occupied_cells = set()
            for it in cur_page["items"]:
                px, py = max(0, it.get("position_x", 0)), max(0, it.get("position_y", 0))
                sx, sy = max(1, it.get("span_x", 1)), max(1, it.get("span_y", 1))
                for r in range(py, py + sy):
                    for c in range(px, px + sx):
                        occupied_cells.add((c, r))
            empty_bottom = [
                (c, r)
                for r in range(4, self.geom.rows)
                for c in range(self.geom.columns)
                if (c, r) not in occupied_cells
            ]
            for _ in empty_bottom:
                self.pin_widget_via_home_studio(spacer_1x1=True)

        self.ensure_home_page(pnum)
        self.adb.screencap(Path(f"screenshots/execution/page_{pnum:02d}.png"))
        self.journal.save_checkpoint(f"page_{pnum:02d}_{page.id}", [])

    def remove_all_temporary_spacers(self) -> int:
        removed = 0
        failed_ids: set[int] = set()
        for _ in range(160):
            snap = self.dump_launcher()
            found = None
            for p in snap.pages:
                for it in p["items"]:
                    comp = str(it.get("component", ""))
                    wid = int(it.get("app_widget_id", -1))
                    if it["type"] == "widget" and wid not in failed_ids and (
                        "WallpaperPlaceholderWidgetProvider" in comp
                        or "WallpaperSpacer1x1Provider" in comp
                    ):
                        found = (p["page_index"] + 1, it)
                        break
                if found:
                    break
            if not found:
                break
            pnum, it = found
            if self._remove_item_at_cell(pnum, it["position_x"], it["position_y"], it["span_x"], it["span_y"], item_type="widget"):
                removed += 1
            else:
                # Retry once at slightly offset y center before marking failed
                x1, y1, x2, y2 = self.geom.cell_bounds(it["position_y"], it["position_x"], max(1, it["span_y"]), max(1, it["span_x"]))
                self.ensure_home_page(pnum)
                self.adb.swipe((x1 + x2) // 2, (y1 + y2) // 2 + 40, (x1 + x2) // 2, (y1 + y2) // 2 + 40, 950)
                time.sleep(0.8)
                if self._tap_xml_node(lambda n: n.attrib.get("content-desc", "") == "Remove, Button" or n.attrib.get("text", "") == "Remove"):
                    time.sleep(0.5)
                    removed += 1
                else:
                    self.adb.keyevent("KEYCODE_HOME")
                    failed_ids.add(int(it.get("app_widget_id", -1)))
        return removed

    def seed_existing_widget_mappings(self) -> None:
        """Populate widget_mappings from any CategoryHeader widgets currently on launcher pages."""
        snap = self.dump_launcher()
        pages_by_order = {p.order: p for p in self.layout.pages}
        for sp in snap.pages:
            spec = pages_by_order.get(sp["page_index"])
            if not spec or not spec.title:
                continue
            for it in sp["items"]:
                if it["type"] == "widget" and "CategoryHeaderWidgetProvider" in str(it.get("component", "")):
                    wid = int(it.get("app_widget_id", -1))
                    if wid > 0:
                        self.widget_mappings[wid] = {"categoryId": spec.id, "title": spec.title}

    def set_default_page(self, pnum: int) -> None:
        self.ensure_home_page(pnum)
        self._enter_edit_mode_safe()
        self._tap_xml_node(
            lambda n: n.attrib.get("resource-id") == "com.sec.android.app.launcher:id/default_page_button"
            or "Not selected Default page" in n.attrib.get("content-desc", "")
        )
        time.sleep(0.5)
        self.adb.keyevent("KEYCODE_HOME")
        time.sleep(0.7)

    def _move_last_page_to_first_in_edit_mode(self) -> bool:
        """Attempt to move the newly created last page to position 0 (leftmost) in Home Edit Mode."""
        snap = self.dump_launcher()
        total_pages = len(snap.pages)
        if total_pages <= 1:
            return False
        self.ensure_home_page(total_pages)
        self._enter_edit_mode_safe()
        # Drag centered last-page card leftward past page dots across Edit Mode strip
        for _ in range(total_pages - 1):
            self.adb.shell("input draganddrop 540 1000 60 1000 900")
            time.sleep(0.45)
        self.adb.keyevent("KEYCODE_HOME")
        time.sleep(0.8)
        after = self.dump_launcher()
        # Verify the empty new page landed at index 0 while Weather shifted to index 1
        return len(after.pages) == total_pages and len(after.pages[0]["items"]) == 0

    def apply_left_most_used_page(self, most_used_page: PageSpec) -> dict[str, Any]:
        """Insert/update Most Used as Page 1 (left of Daily Essentials) cleanly."""
        self.adb.preflight(require_launcher=False)
        snap = self.dump_launcher()
        page0_has_weather = any(
            it["type"] == "widget" and self.WEATHER_PROVIDER in str(it.get("component", ""))
            for it in (snap.pages[0]["items"] if snap.pages else [])
        )
        if len(snap.pages) == len(self.layout.pages) - 1 and page0_has_weather:
            # Non-destructive fast path: create empty page at end and move left to index 0
            self.create_page()
            moved = self._move_last_page_to_first_in_edit_mode()
            if moved:
                self.build_page(most_used_page)
                self.seed_existing_widget_mappings()
                default_pnum = next((p.order + 1 for p in self.layout.pages if p.isDefaultHome), 2)
                self.set_default_page(default_pnum)
                self.sync_home_studio_config()
                return {"mode": "non_destructive_left_insert", "pages": len(self.dump_launcher().pages)}
        # Fallback: full idempotent rebuild preserving Weather widget cleanly
        return self.run_full_apply(clean_canvas=True)

    def run_full_apply(self, *, clean_canvas: bool = True) -> dict[str, Any]:
        self.adb.preflight(require_launcher=False)
        if clean_canvas:
            self.stage1_clear_to_weather_canvas()
        else:
            self.seed_existing_widget_mappings()

        for page in sorted(self.layout.pages, key=lambda p: p.order):
            if page.id == "wallpaper":
                for _ in range(5):
                    if len(self.dump_launcher().pages) >= page.order + 1:
                        break
                    self.create_page()
                continue
            self.build_page(page)

        removed_spacers = self.remove_all_temporary_spacers()
        self.seed_existing_widget_mappings()
        default_pnum = next((p.order + 1 for p in self.layout.pages if p.isDefaultHome), 1)
        self.set_default_page(default_pnum)
        self.sync_home_studio_config()

        snap = self.dump_launcher()
        obs_payload = {
            "commonSettings": snap.common_settings,
            "oneUiSettings": snap.oneui_settings,
            "homeUpSettings": snap.homeup_settings,
            "pages": snap.pages,
            "dockItems": snap.dock_items,
        }
        Path("config/observed-layout.json").write_text(json.dumps(obs_payload, indent=2) + "\n", encoding="utf-8")

        return {
            "status": "completed",
            "pagesBuilt": len(self.layout.pages),
            "temporarySpacersRemoved": removed_spacers,
            "headerWidgetsBound": len(self.widget_mappings),
            "failedItems": self.failed_items,
            "summary": f"{len(self.layout.pages)} pages built, {len(self.widget_mappings)} headers bound, {removed_spacers} temporary spacers removed, {len(self.failed_items)} failed items",
        }


def apply_layout_live(layout: DesiredLayout, *, clean_canvas: bool | None = None) -> dict[str, Any]:
    """Execute a DesiredLayout live on the configured physical phone."""
    adb = AdbClient(DeviceTarget.from_private_config())
    adb.preflight(require_launcher=False)
    apps = json.loads(Path("inventory/applications.json").read_text(encoding="utf-8"))
    apps_by_id = {a["id"]: a for a in apps}
    applier = LiveApplier(adb, layout, apps_by_id)
    if clean_canvas is None:
        snap = applier.dump_launcher()
        has_headers = any(
            it["type"] == "widget" and "CategoryHeaderWidgetProvider" in str(it.get("component", ""))
            for p in snap.pages
            for it in p["items"]
        )
        clean_canvas = not has_headers
    return applier.run_full_apply(clean_canvas=clean_canvas)
