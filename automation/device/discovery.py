"""Device capability and profile discovery."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from automation.adb.client import AdbClient


def discover_device_profile(adb: AdbClient) -> dict[str, Any]:
    """Collect read-only device and launcher metadata."""
    props = {
        "model": adb.get_prop("ro.product.model"),
        "manufacturer": adb.get_prop("ro.product.manufacturer"),
        "device": adb.get_prop("ro.product.device"),
        "androidRelease": adb.get_prop("ro.build.version.release"),
        "sdk": int(adb.get_prop("ro.build.version.sdk") or "0"),
        "securityPatch": adb.get_prop("ro.build.version.security_patch"),
        "oneUiVersion": adb.get_prop("ro.build.version.oneui"),
        "buildId": adb.get_prop("ro.build.display.id"),
    }
    wm_size = adb.shell("wm size")
    wm_density = adb.shell("wm density")
    font_scale = adb.shell("settings get system font_scale").strip()
    nav_mode = adb.shell("settings get secure navigation_mode").strip()
    screen_zoom = adb.shell("settings get secure display_density_forced").strip()
    current_user = int((adb.shell("am get-current-user").strip() or "0"))
    home_resolved = adb.shell(
        "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    ).strip().splitlines()[-1]

    def _pkg_ver(pkg: str) -> dict[str, Any]:
        out = adb.shell(f"dumpsys package {pkg} | grep -E 'versionName=|versionCode=|enabled=' | head -n 4", check=False)
        vn = re.search(r"versionName=(\S+)", out)
        vc = re.search(r"versionCode=(\d+)", out)
        return {
            "installed": bool(vn or vc),
            "versionName": vn.group(1) if vn else None,
            "versionCode": int(vc.group(1)) if vc else None,
        }

    size_m = re.search(r"(\d+)x(\d+)", wm_size)
    phys_d = re.search(r"Physical density:\s*(\d+)", wm_density)
    over_d = re.search(r"Override density:\s*(\d+)", wm_density)

    users_raw = adb.shell("pm list users")
    users = []
    for line in users_raw.splitlines():
        um = re.search(r"UserInfo\{(\d+):([^:}]+):([0-9a-fA-F]+)\}", line)
        if um:
            users.append({"id": int(um.group(1)), "name": um.group(2), "flags": um.group(3)})

    return {
        **props,
        "display": {
            "widthPx": int(size_m.group(1)) if size_m else 1080,
            "heightPx": int(size_m.group(2)) if size_m else 2340,
            "physicalDensityDpi": int(phys_d.group(1)) if phys_d else None,
            "overrideDensityDpi": int(over_d.group(1)) if over_d else None,
            "screenZoom": screen_zoom,
            "fontScale": float(font_scale) if font_scale not in ("null", "") else 1.0,
            "navigationMode": int(nav_mode) if nav_mode.isdigit() else nav_mode,
        },
        "currentUser": current_user,
        "users": users,
        "activeLauncher": home_resolved,
        "companionPackages": {
            "oneUiHome": _pkg_ver("com.sec.android.app.launcher"),
            "goodLock": _pkg_ver("com.samsung.android.goodlock"),
            "homeUp": _pkg_ver("com.samsung.android.app.homestar"),
            "smartSwitch": _pkg_ver("com.sec.android.easyMover"),
            "kwgt": _pkg_ver("org.kustom.widget"),
            "homeStudio": _pkg_ver("com.homestudio.local"),
        },
    }


def save_device_profile(profile: dict[str, Any], path: Path = Path("config/device-profile.json")) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(profile, indent=2) + "\n", encoding="utf-8")
    return path
