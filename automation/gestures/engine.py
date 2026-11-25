"""Grid-aware gesture engine and launcher state navigation for Samsung One UI Home."""

from __future__ import annotations

import re
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from automation.adb.client import AdbClient, AdbError


@dataclass(frozen=True)
class LauncherGeometry:
    width_px: int = 1080
    height_px: int = 2340
    ws_left: int = 41
    ws_top: int = 125
    ws_right: int = 1039
    ws_bottom: int = 1763
    columns: int = 5
    rows: int = 6

    def cell_bounds(self, row: int, column: int, row_span: int = 1, col_span: int = 1) -> tuple[int, int, int, int]:
        cw = (self.ws_right - self.ws_left) / self.columns
        ch = (self.ws_bottom - self.ws_top) / self.rows
        x1 = int(self.ws_left + column * cw)
        y1 = int(self.ws_top + row * ch)
        x2 = int(self.ws_left + (column + col_span) * cw)
        y2 = int(self.ws_top + (row + row_span) * ch)
        return (x1, y1, x2, y2)

    def cell_center(self, row: int, column: int) -> tuple[int, int]:
        x1, y1, x2, y2 = self.cell_bounds(row, column)
        return ((x1 + x2) // 2, (y1 + y2) // 2)


def parse_bounds(bounds_str: str) -> Optional[tuple[int, int, int, int]]:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str.strip())
    if not m:
        return None
    return (int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4)))


class GestureEngine:
    """Safe, state-verified launcher gesture abstraction."""

    SENSITIVE_DIALOG_KEYWORDS = (
        "uninstall",
        "delete data",
        "clear data",
        "factory reset",
        "payment",
        "sign in",
    )

    def __init__(self, adb: AdbClient, geometry: LauncherGeometry | None = None) -> None:
        self.adb = adb
        self.geometry = geometry or LauncherGeometry()

    def get_active_marker_index(self) -> tuple[int, int]:
        """Return (selected_marker_index, total_markers) from One UI PageIndicatorView."""
        fg = self.adb.get_foreground_info()
        if "GoogleDiscoverWindow" in str(fg["current_focus"]):
            return 0, 14
        out = self.adb.shell("dumpsys activity top | grep -A 75 'workspace_page_indicator'", check=False)
        markers = re.findall(r"PageIndicatorMarker\{[^\}]+\s+([^\s\}]+)\s+\d+,\d+-\d+,\d+\}", out)
        selected = [i for i, flags in enumerate(markers) if "S" in flags]
        return (selected[0] if selected else -1), len(markers)

    def wait_for_ui_stability(self, delay_s: float = 0.65) -> None:
        time.sleep(delay_s)

    def return_home_safe(self) -> None:
        self.adb.keyevent("KEYCODE_HOME")
        self.wait_for_ui_stability(0.8)

    def swipe_page_next(self) -> None:
        self.adb.swipe(850, 420, 220, 420, 300)
        self.wait_for_ui_stability(0.6)

    def swipe_page_prev(self) -> None:
        self.adb.swipe(220, 420, 850, 420, 300)
        self.wait_for_ui_stability(0.6)

    def navigate_to_page(self, target_page_1based: int, max_steps: int = 20) -> int:
        """Navigate reliably to a 1-based One UI home page verified by PageIndicatorMarker."""
        fg = self.adb.get_foreground_info()
        if "com.sec.android.app.launcher" not in str(fg["current_focus"]):
            self.return_home_safe()
        for _ in range(max_steps):
            idx, total = self.get_active_marker_index()
            if idx == target_page_1based:
                return idx
            if idx == -1:
                self.return_home_safe()
            elif idx < target_page_1based:
                self.swipe_page_next()
            else:
                self.swipe_page_prev()
        idx, _ = self.get_active_marker_index()
        if idx != target_page_1based:
            raise AdbError(f"Failed to navigate to page {target_page_1based}; stuck at {idx}")
        return idx

    def tap_coordinate(self, x: int, y: int) -> None:
        self.adb.tap(x, y)
        self.wait_for_ui_stability(0.5)

    def long_press_coordinate(self, x: int, y: int, duration_ms: int = 900) -> None:
        self.adb.swipe(x, y, x, y, duration_ms)
        self.wait_for_ui_stability(0.9)

    def enter_home_edit_mode(self, neutral_x: int = 540, neutral_y: int = 300) -> None:
        self.long_press_coordinate(neutral_x, neutral_y, 900)

    def exit_home_edit_mode(self) -> None:
        self.return_home_safe()

    def open_app_drawer(self) -> None:
        self.adb.swipe(540, 1500, 540, 500, 300)
        self.wait_for_ui_stability(0.7)

    def check_for_unexpected_dialog(self, xml_path: Path) -> Optional[str]:
        if not xml_path.exists():
            return None
        tree = ET.parse(xml_path)
        for node in tree.iter("node"):
            txt = (node.attrib.get("text") or "").lower()
            for kw in self.SENSITIVE_DIALOG_KEYWORDS:
                if kw in txt:
                    return node.attrib.get("text")
        return None
