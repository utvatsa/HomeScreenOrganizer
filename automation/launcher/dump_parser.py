"""Parser for Samsung One UI Home LauncherProvider dumpsys output."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any


def _parse_kv_row(inner: str) -> dict[str, str]:
    """Parse key=value pairs separated by 4 spaces or tabs in OneUI dumpsys."""
    tokens = re.split(r"(?:\t|\s{2,})(?=[A-Za-z_][A-Za-z0-9_]*=)", inner.strip())
    row: dict[str, str] = {}
    for tok in tokens:
        if "=" in tok:
            k, v = tok.split("=", 1)
            row[k.strip()] = v.strip()
    return row


def _parse_table(section: str, table_name: str) -> list[dict[str, str]]:
    pattern = rf"table - {re.escape(table_name)}\r?\n(.*?)(?=\r?\n\s*table - |\Z)"
    m = re.search(pattern, section, re.S)
    if not m:
        return []
    rows: list[dict[str, str]] = []
    for line in m.group(1).splitlines():
        if "{" not in line or "}" not in line:
            continue
        inner = line.split("{", 1)[1].rsplit("}", 1)[0]
        row = _parse_kv_row(inner)
        if row:
            rows.append(row)
    return rows


@dataclass
class LauncherSnapshot:
    common_settings: str
    oneui_settings: str
    homeup_settings: str
    pages: list[dict[str, Any]] = field(default_factory=list)
    dock_items: list[dict[str, Any]] = field(default_factory=list)
    folders: dict[int, dict[str, Any]] = field(default_factory=dict)
    raw_items: list[dict[str, str]] = field(default_factory=list)
    raw_groups: list[dict[str, str]] = field(default_factory=list)


def parse_oneui_dump(dump_text: str) -> LauncherSnapshot:
    """Parse the OneUI DB section and settings from LauncherProvider dumpsys."""
    def _grab_block(header: str) -> str:
        m = re.search(rf"{re.escape(header)}\r?\n(.*?)(?=\r?\n\s*\r?\n|\Z)", dump_text, re.S)
        return m.group(1).strip() if m else ""

    common = _grab_block("Home Common Settings")
    oneui_set = _grab_block("OneUI Settings")
    homeup = _grab_block("HomeUp Settings")

    db_section = ""
    if "DB : OneUI" in dump_text:
        db_section = dump_text.split("DB : OneUI", 1)[1].split("DB : ", 1)[0]

    groups = _parse_table(db_section, "item_group")
    items = _parse_table(db_section, "item")

    # Identify workspace pages (container_id=2, type=page) and hotseat (type=hotseat)
    workspace_groups = [g for g in groups if g.get("type") == "workspace"]
    workspace_id = int(workspace_groups[0]["id"]) if workspace_groups else 2
    hotseat_groups = [g for g in groups if g.get("type") == "hotseat"]
    hotseat_id = int(hotseat_groups[0]["id"]) if hotseat_groups else 11

    page_groups = [
        g for g in groups
        if g.get("type") == "page" and str(g.get("container_id")) == str(workspace_id)
    ]
    page_groups.sort(key=lambda g: int(g.get("rank", "0")))

    # Map folders (container_type=0 means inside page/hotseat, type=folder)
    folders_by_id: dict[int, dict[str, Any]] = {}
    for it in items:
        if it.get("type") == "folder":
            fid = int(it["id"])
            folders_by_id[fid] = {
                "id": fid,
                "title": None if it.get("title") == "null" else it.get("title"),
                "container_type": int(it.get("container_type", "0")),
                "container_id": int(it.get("container_id", "-1")),
                "position_x": int(it.get("position_x", "-1")),
                "position_y": int(it.get("position_y", "-1")),
                "rank": int(it.get("rank", "-1")),
                "color": int(it.get("color", "-1")),
                "children": [],
            }

    # Populate folder children (container_type=1 means inside folder)
    for it in items:
        if it.get("container_type") == "1":
            fid = int(it.get("container_id", "-1"))
            if fid in folders_by_id:
                folders_by_id[fid]["children"].append({
                    "id": int(it["id"]),
                    "type": it.get("type"),
                    "title": None if it.get("title") == "null" else it.get("title"),
                    "component": None if it.get("component") == "null" else it.get("component"),
                    "rank": int(it.get("rank", "0")),
                    "profile_id": int(it.get("profile_id", "0")),
                })
    for f in folders_by_id.values():
        f["children"].sort(key=lambda c: c["rank"])

    def _serialize_item(it: dict[str, str]) -> dict[str, Any]:
        iid = int(it["id"])
        base: dict[str, Any] = {
            "id": iid,
            "type": it.get("type"),
            "title": None if it.get("title") == "null" else it.get("title"),
            "component": None if it.get("component") == "null" else it.get("component"),
            "app_widget_id": int(it.get("app_widget_id", "-1")),
            "position_x": int(it.get("position_x", "-1")),
            "position_y": int(it.get("position_y", "-1")),
            "span_x": int(it.get("spanX", "-1")),
            "span_y": int(it.get("spanY", "-1")),
            "rank": int(it.get("rank", "-1")),
            "profile_id": int(it.get("profile_id", "0")),
        }
        if iid in folders_by_id:
            base["children"] = folders_by_id[iid]["children"]
        return base

    pages: list[dict[str, Any]] = []
    for idx, pg in enumerate(page_groups):
        pg_id = int(pg["id"])
        pg_items = [
            _serialize_item(it)
            for it in items
            if it.get("container_type") == "0" and int(it.get("container_id", "-1")) == pg_id
        ]
        pg_items.sort(key=lambda x: (x["position_y"], x["position_x"], x["rank"]))
        pages.append({
            "page_index": idx,
            "group_id": pg_id,
            "rank": int(pg.get("rank", idx)),
            "items": pg_items,
        })

    dock_items = [
        _serialize_item(it)
        for it in items
        if it.get("container_type") == "0" and int(it.get("container_id", "-1")) == hotseat_id
    ]
    dock_items.sort(key=lambda x: x["rank"])

    return LauncherSnapshot(
        common_settings=common,
        oneui_settings=oneui_set,
        homeup_settings=homeup,
        pages=pages,
        dock_items=dock_items,
        folders=folders_by_id,
        raw_items=items,
        raw_groups=groups,
    )
