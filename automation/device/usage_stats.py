"""System UsageStats inspection and Top-N Most Used application selection."""

from __future__ import annotations

import re
from typing import Any

from automation.adb.client import AdbClient

_EXCLUDED_SYSTEM_PACKAGES = {
    "com.sec.android.app.launcher",
    "com.homestudio.local",
    "com.android.systemui",
    "com.android.settings",
    "com.samsung.android.honeyboard",
    "com.google.android.inputmethod.latin",
}


def _duration_str_to_ms(raw: str) -> int:
    """Parse Android dumpsys totalTime strings like '02:15:30', '15:30', or integer ms."""
    s = raw.strip().strip('"').strip("'")
    if not s:
        return 0
    if s.isdigit():
        return int(s)
    # Match HH:MM:SS or MM:SS or DdHHhMMmSSs
    m_hms = re.match(r"^(\d+):(\d{2}):(\d{2})$", s)
    if m_hms:
        h, m, sec = int(m_hms.group(1)), int(m_hms.group(2)), int(m_hms.group(3))
        return ((h * 3600) + (m * 60) + sec) * 1000
    m_ms = re.match(r"^(\d+):(\d{2})$", s)
    if m_ms:
        m, sec = int(m_ms.group(1)), int(m_ms.group(2))
        return ((m * 60) + sec) * 1000
    return 0


def parse_dumpsys_usagestats(raw_text: str) -> dict[str, int]:
    """Extract foreground usage milliseconds per package from `adb shell dumpsys usagestats`.

    When multiple interval buckets (daily/weekly/monthly/yearly) exist in the dump,
    keeps the maximum reported foreground duration per package across intervals.
    """
    usage_by_pkg: dict[str, int] = {}
    for line in raw_text.splitlines():
        if "package=" not in line:
            continue
        m_pkg = re.search(r"package=([A-Za-z0-9_.-]+)", line)
        if not m_pkg:
            continue
        pkg = m_pkg.group(1)
        m_time = re.search(
            r"(?:totalTimeUsed|totalTimeInForeground|totalTime)=([^\s,}]+)",
            line,
        )
        if not m_time:
            continue
        ms = _duration_str_to_ms(m_time.group(1))
        if ms > usage_by_pkg.get(pkg, 0):
            usage_by_pkg[pkg] = ms
    return usage_by_pkg


def fetch_device_usage_stats(adb: AdbClient) -> dict[str, int]:
    """Query live foreground usage stats from the connected Android device via ADB."""
    raw = adb.shell("dumpsys usagestats", timeout=30.0, check=False)
    return parse_dumpsys_usagestats(raw)


def select_top_used_apps(
    apps: list[dict[str, Any]],
    usage_ms_by_pkg: dict[str, int] | None = None,
    *,
    pinned_refs: list[str] | None = None,
    limit: int = 8,
) -> list[dict[str, Any]]:
    """Select up to `limit` launcher apps, honoring explicit pinned refs then system usage rank."""
    by_id = {a["id"]: a for a in apps}
    by_pkg: dict[str, list[dict[str, Any]]] = {}
    by_name: dict[str, list[dict[str, Any]]] = {}
    for app in apps:
        by_pkg.setdefault(app["packageName"], []).append(app)
        by_name.setdefault(app["displayName"].strip().lower(), []).append(app)

    selected: list[dict[str, Any]] = []
    seen_ids: set[str] = set()

    def _add(entry: dict[str, Any]) -> None:
        if entry["id"] in seen_ids or len(selected) >= limit:
            return
        seen_ids.add(entry["id"])
        selected.append(entry)

    for ref in pinned_refs or []:
        ref_clean = ref.strip()
        if not ref_clean:
            continue
        if ref_clean in by_id:
            _add(by_id[ref_clean])
            continue
        pkg_hits = by_pkg.get(ref_clean)
        if pkg_hits:
            _add(sorted(pkg_hits, key=lambda x: x.get("profileId", 0))[0])
            continue
        name_hits = by_name.get(ref_clean.lower())
        if name_hits:
            # Prefer non-debug release package when matched by display name
            chosen = sorted(
                name_hits,
                key=lambda x: (x["packageName"].endswith(".debug"), x.get("profileId", 0)),
            )[0]
            _add(chosen)

    if len(selected) < limit and usage_ms_by_pkg:
        ranked_pkgs = sorted(usage_ms_by_pkg.items(), key=lambda kv: kv[1], reverse=True)
        for pkg, fg_ms in ranked_pkgs:
            if fg_ms <= 0 or pkg in _EXCLUDED_SYSTEM_PACKAGES:
                continue
            pkg_hits = by_pkg.get(pkg)
            if not pkg_hits:
                continue
            _add(sorted(pkg_hits, key=lambda x: x.get("profileId", 0))[0])
            if len(selected) >= limit:
                break

    return selected[:limit]
