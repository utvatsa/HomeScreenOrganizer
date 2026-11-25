"""Wallpaper metadata discovery and screenshot-based clean background reconstruction."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

from PIL import Image

from automation.adb.client import AdbClient


def inspect_active_wallpaper(adb: AdbClient) -> dict[str, Any]:
    """Inspect active wallpaper service metadata via dumpsys wallpaper (read-only)."""
    dump = adb.shell("dumpsys wallpaper")
    home_cmp = None
    lock_cmp = None
    in_home = False
    in_lock = False
    for line in dump.splitlines():
        if "mLastWallpaper state:" in line:
            in_home, in_lock = True, False
        elif "mLastLockWallpaper state:" in line:
            in_home, in_lock = False, True
        elif "Display state:" in line:
            in_home = in_lock = False
        elif "mInfo.component=ComponentInfo{" in line:
            cmp_val = line.split("ComponentInfo{", 1)[1].split("}", 1)[0]
            if in_home:
                home_cmp = cmp_val
            elif in_lock:
                lock_cmp = cmp_val

    is_layered = bool(home_cmp and "LayeredWallpaperService" in home_cmp)
    return {
        "homeWallpaperComponent": home_cmp,
        "lockWallpaperComponent": lock_cmp,
        "wallpaperType": "samsung-layered-live" if is_layered else "static-or-other",
        "originalFileAccessibleViaAdb": False,
        "notes": (
            "Active wallpaper is served by Samsung LayeredWallpaperService inside protected system/app storage. "
            "No direct ADB read is attempted per safety policy. Empty home page screenshot (page_05.png) "
            "provides exact pixel rendering of the active wallpaper for design and preview generation."
        ),
    }


def build_reconstructed_wallpaper(
    empty_page_screenshot: Path,
    output_path: Path = Path("assets/wallpaper/reconstructed_wallpaper.png"),
) -> dict[str, Any]:
    """Create a clean 1080x2340 preview background from an empty home page screenshot.

    Masks out only the status bar, navigation bar, edge handle, Google search bar, and dock
    using the solid background sample from the empty workspace area so previews accurately
    reflect the wallpaper's colors and center quote.
    """
    img = Image.open(empty_page_screenshot).convert("RGB")
    w, h = img.size
    # Sample upper-left empty workspace pixel (e.g. x=80, y=200)
    bg_color = img.getpixel((80, 200))
    px = img.load()

    # Clear status bar (y < 115)
    for y in range(0, min(115, h)):
        for x in range(w):
            px[x, y] = bg_color
    # Clear right-edge Edge Panel handle strip (x >= 1055)
    for y in range(0, h):
        for x in range(max(0, w - 25), w):
            px[x, y] = bg_color
    # Clear Google QSB + Dock + Nav bar (y >= 1775)
    for y in range(1775, h):
        for x in range(w):
            px[x, y] = bg_color

    output_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(output_path)
    sha256 = hashlib.sha256(output_path.read_bytes()).hexdigest()
    return {
        "sourceScreenshot": str(empty_page_screenshot),
        "reconstructedPath": str(output_path),
        "dimensions": [w, h],
        "sampledBackgroundRgb": list(bg_color),
        "sha256": sha256,
        "isExactOriginal": False,
    }
