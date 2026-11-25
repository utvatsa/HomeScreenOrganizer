"""Reconciliation planner and checkpointed execution journal."""

from __future__ import annotations

import json
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal, Optional

from automation.models import DesiredLayout, PageItemSpec, PageSpec


OpType = Literal[
    "ensure_grid",
    "create_page",
    "set_default_page",
    "add_header_widget",
    "configure_header_widget",
    "resize_widget",
    "add_shortcut",
    "create_folder",
    "add_to_folder",
    "rename_folder",
    "add_widget",
    "add_placeholder_widget",
    "remove_obsolete_item",
    "remove_empty_page",
    "reorder_page",
]


@dataclass
class OperationRecord:
    opId: str
    opType: OpType
    pageId: str
    targetLabel: str
    packageName: Optional[str] = None
    activityName: Optional[str] = None
    profileId: int = 0
    folderTitle: Optional[str] = None
    position: Optional[dict[str, int]] = None
    dependsOn: list[str] = field(default_factory=list)
    risk: Literal["low", "medium", "high"] = "low"
    requiresManualConfirm: bool = False
    status: Literal["pending", "completed", "skipped", "failed", "blocked"] = "pending"
    attempts: int = 0
    timestamp: Optional[str] = None
    error: Optional[str] = None


def _norm_comp(comp: str | None) -> str:
    if not comp or "/" not in comp:
        return comp or ""
    pkg, act = comp.split("/", 1)
    if act.startswith("."):
        act = pkg + act
    return f"{pkg}/{act}"


def plan_reconciliation_ops(
    desired: DesiredLayout,
    observed: dict[str, Any],
) -> list[OperationRecord]:
    """Compare desired layout with observed layout and emit dependency-ordered operations."""
    ops: list[OperationRecord] = []
    op_counter = 0

    def _next_id(prefix: str) -> str:
        nonlocal op_counter
        op_counter += 1
        return f"{prefix}-{op_counter:04d}"

    # Check grid setting
    oneui_settings = observed.get("oneUiSettings", "")
    desired_grid = f"{desired.launcher.grid.columns} X {desired.launcher.grid.rows or 6}"
    if f"Home Grid : {desired_grid}" not in oneui_settings:
        ops.append(
            OperationRecord(
                opId=_next_id("grid"),
                opType="ensure_grid",
                pageId="__launcher__",
                targetLabel=f"Set Home Grid to {desired_grid}",
                risk="medium",
            )
        )

    observed_pages = observed.get("pages", [])
    existing_page_count = len(observed_pages)

    # Index observed items by page_index for idempotency checks
    for page in sorted(desired.pages, key=lambda p: p.order):
        obs_page = observed_pages[page.order] if page.order < existing_page_count else None
        obs_items = obs_page.get("items", []) if obs_page else []
        page_deps: list[str] = []
        if page.order >= existing_page_count:
            cp_id = _next_id("page")
            ops.append(
                OperationRecord(
                    opId=cp_id,
                    opType="create_page",
                    pageId=page.id,
                    targetLabel=f"Create page {page.order + 1} ({page.title or 'Wallpaper'})",
                )
            )
            page_deps.append(cp_id)

        if page.isDefaultHome and f"Default Home Page : {page.order}" not in oneui_settings:
            ops.append(
                OperationRecord(
                    opId=_next_id("default"),
                    opType="set_default_page",
                    pageId=page.id,
                    targetLabel=f"Set {page.title or page.id} as Default Home Page",
                    dependsOn=list(page_deps),
                )
            )

        if page.header.enabled and page.title:
            has_hdr = any(
                it.get("type") == "widget"
                and "CategoryHeaderWidgetProvider" in str(it.get("component", ""))
                and it.get("position_y") == page.header.position.row
                for it in obs_items
            )
            if not has_hdr:
                hw_id = _next_id("hdr")
                ops.append(
                    OperationRecord(
                        opId=hw_id,
                        opType="add_header_widget",
                        pageId=page.id,
                        targetLabel=f"Place Category Header: {page.title}",
                        packageName="com.homestudio.local",
                        activityName="com.homestudio.local.widget.CategoryHeaderWidgetProvider",
                        position=page.header.position.model_dump(),
                        dependsOn=list(page_deps),
                    )
                )
                cfg_id = _next_id("hdrcfg")
                ops.append(
                    OperationRecord(
                        opId=cfg_id,
                        opType="configure_header_widget",
                        pageId=page.id,
                        targetLabel=f"Configure Header: {page.title}",
                        dependsOn=[hw_id],
                    )
                )

        for item in page.items:
            tx, ty = item.position.column, item.position.row
            if item.type == "app":
                exp_comp = _norm_comp(f"{item.packageName}/{item.activityName}")
                has_app = any(
                    it.get("type") == "app"
                    and _norm_comp(it.get("component")) == exp_comp
                    and int(it.get("profile_id", 0)) == item.profileId
                    and it.get("position_x") == tx
                    and it.get("position_y") == ty
                    for it in obs_items
                )
                if not has_app:
                    ops.append(
                        OperationRecord(
                            opId=_next_id("app"),
                            opType="add_shortcut",
                            pageId=page.id,
                            targetLabel=item.title or (item.packageName or ""),
                            packageName=item.packageName,
                            activityName=item.activityName,
                            profileId=item.profileId,
                            position=item.position.model_dump(),
                            dependsOn=list(page_deps),
                        )
                    )
            elif item.type == "folder":
                obs_fld = next(
                    (
                        it for it in obs_items
                        if it.get("type") == "folder"
                        and it.get("position_x") == tx
                        and it.get("position_y") == ty
                    ),
                    None,
                )
                f_deps = list(page_deps)
                if obs_fld is None:
                    f_id = _next_id("fld")
                    ops.append(
                        OperationRecord(
                            opId=f_id,
                            opType="create_folder",
                            pageId=page.id,
                            targetLabel=f"Folder: {item.title}",
                            folderTitle=item.title,
                            position=item.position.model_dump(),
                            dependsOn=list(page_deps),
                        )
                    )
                    f_deps = [f_id]
                elif (obs_fld.get("title") or "") != (item.title or ""):
                    ops.append(
                        OperationRecord(
                            opId=_next_id("fren"),
                            opType="rename_folder",
                            pageId=page.id,
                            targetLabel=f"Rename Folder -> {item.title}",
                            folderTitle=item.title,
                            position=item.position.model_dump(),
                            dependsOn=list(page_deps),
                        )
                    )
                obs_children = {
                    (_norm_comp(c.get("component")), int(c.get("profile_id", 0)))
                    for c in (obs_fld.get("children", []) if obs_fld else [])
                }
                for ch in item.children:
                    exp_ch = (_norm_comp(f"{ch.packageName}/{ch.activityName}"), ch.profileId)
                    if exp_ch not in obs_children:
                        ops.append(
                            OperationRecord(
                                opId=_next_id("fadd"),
                                opType="add_to_folder",
                                pageId=page.id,
                                targetLabel=ch.label,
                                packageName=ch.packageName,
                                activityName=ch.activityName,
                                profileId=ch.profileId,
                                folderTitle=item.title,
                                dependsOn=list(f_deps),
                            )
                        )
            elif item.type == "widget":
                has_wgt = any(
                    it.get("type") == "widget"
                    and it.get("position_y") == ty
                    and (not item.provider or item.provider.split("/")[0] in str(it.get("component", "")))
                    for it in obs_items
                )
                if not has_wgt:
                    ops.append(
                        OperationRecord(
                            opId=_next_id("wgt"),
                            opType="add_widget",
                            pageId=page.id,
                            targetLabel=item.title or (item.provider or "Widget"),
                            packageName=(item.provider or "").split("/")[0] or None,
                            position=item.position.model_dump(),
                            dependsOn=list(page_deps),
                            requiresManualConfirm=False,
                        )
                    )
            elif item.type == "placeholder_widget":
                has_ph = any(
                    it.get("type") == "widget"
                    and "Wallpaper" in str(it.get("component", ""))
                    for it in obs_items
                )
                if not has_ph:
                    ops.append(
                        OperationRecord(
                            opId=_next_id("ph"),
                            opType="add_placeholder_widget",
                            pageId=page.id,
                            targetLabel="Place Wallpaper Spacer",
                            packageName="com.homestudio.local",
                            position=item.position.model_dump(),
                            dependsOn=list(page_deps),
                        )
                    )

    return ops


class ExecutionJournal:
    """Append-only JSONL execution journal and checkpoint persistence."""

    def __init__(
        self,
        journal_path: Path = Path("logs/execution-journal.jsonl"),
        checkpoint_path: Path = Path("checkpoints/latest-checkpoint.json"),
    ) -> None:
        self.journal_path = journal_path
        self.checkpoint_path = checkpoint_path
        self.journal_path.parent.mkdir(parents=True, exist_ok=True)
        self.checkpoint_path.parent.mkdir(parents=True, exist_ok=True)

    def append(self, op: OperationRecord, event: str) -> None:
        rec = {
            "ts": datetime.now(timezone.utc).isoformat(),
            "event": event,
            "op": asdict(op),
        }
        with self.journal_path.open("a", encoding="utf-8") as f:
            f.write(json.dumps(rec) + "\n")

    def save_checkpoint(self, phase: str, ops: list[OperationRecord], extra: dict[str, Any] | None = None) -> None:
        payload = {
            "updatedAt": datetime.now(timezone.utc).isoformat(),
            "phase": phase,
            "completedCount": sum(1 for o in ops if o.status == "completed"),
            "totalCount": len(ops),
            "operations": [asdict(o) for o in ops],
            "extra": extra or {},
        }
        tmp = self.checkpoint_path.with_suffix(".tmp")
        tmp.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        tmp.replace(self.checkpoint_path)
