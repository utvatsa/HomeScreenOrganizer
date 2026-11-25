"""Strict validator for DesiredLayout configurations."""

from __future__ import annotations

from typing import Any
from automation.models import DesiredLayout


class LayoutValidationError(ValueError):
    """Raised when a declarative layout fails structural or capability checks."""


def validate_layout(
    layout: DesiredLayout,
    *,
    installed_ids: set[str] | None = None,
    allow_duplicates: bool = True,
) -> list[str]:
    """Validate layout uniqueness, grid bounds, collisions, and references.

    Returns a list of non-fatal warnings; raises LayoutValidationError on hard errors.
    """
    errors: list[str] = []
    warnings: list[str] = []

    cols = layout.launcher.grid.columns
    rows = layout.launcher.grid.rows or 6

    page_ids: set[str] = set()
    item_ids: set[str] = set()
    orders: set[int] = set()
    default_pages = 0

    for page in layout.pages:
        if page.id in page_ids:
            errors.append(f"Duplicate page ID: {page.id}")
        page_ids.add(page.id)

        if page.order in orders:
            errors.append(f"Duplicate page order {page.order} on page {page.id}")
        orders.add(page.order)

        if page.isDefaultHome:
            default_pages += 1

        occupied: dict[tuple[int, int], str] = {}

        def _occupy(r: int, c: int, rs: int, cs: int, label: str) -> None:
            if r < 0 or c < 0 or r + rs > rows or c + cs > cols:
                errors.append(
                    f"Page {page.id}: item {label} out of grid bounds "
                    f"(pos=({r},{c}) span=({rs},{cs}) grid=({rows},{cols}))"
                )
                return
            for rr in range(r, r + rs):
                for cc in range(c, c + cs):
                    if (rr, cc) in occupied:
                        errors.append(
                            f"Page {page.id}: cell collision at ({rr},{cc}) between "
                            f"{occupied[(rr, cc)]} and {label}"
                        )
                    occupied[(rr, cc)] = label

        if page.header.enabled:
            hp = page.header.position
            _occupy(hp.row, hp.column, hp.rowSpan, hp.columnSpan, f"header:{page.id}")

        for item in page.items:
            if item.id in item_ids:
                errors.append(f"Duplicate item ID: {item.id}")
            item_ids.add(item.id)

            pos = item.position
            _occupy(pos.row, pos.column, pos.rowSpan, pos.columnSpan, item.id)

            if item.type in ("app", "pinned_shortcut"):
                if not item.packageName or not item.activityName:
                    errors.append(f"Page {page.id}: app item {item.id} missing packageName/activityName")
                elif installed_ids is not None:
                    comp_id = f"u{item.profileId}:{item.packageName}/{item.activityName}"
                    if comp_id not in installed_ids:
                        errors.append(f"Page {page.id}: app {comp_id} not found in installed inventory")
            elif item.type == "folder":
                if not item.title:
                    errors.append(f"Page {page.id}: folder {item.id} missing title")
                if len(item.children) < 2:
                    warnings.append(f"Page {page.id}: folder {item.id} ({item.title}) has {len(item.children)} child(ren)")
                for ch in item.children:
                    if installed_ids is not None:
                        comp_id = f"u{ch.profileId}:{ch.packageName}/{ch.activityName}"
                        if comp_id not in installed_ids:
                            errors.append(f"Page {page.id}: folder child {comp_id} not in installed inventory")

    if default_pages != 1:
        warnings.append(f"Expected exactly 1 default home page, found {default_pages}")

    if not allow_duplicates:
        seen_apps: dict[str, str] = {}
        for page in layout.pages:
            for item in page.items:
                if item.type == "app" and item.packageName:
                    key = f"u{item.profileId}:{item.packageName}/{item.activityName}"
                    if key in seen_apps:
                        errors.append(f"Duplicate shortcut {key} on {seen_apps[key]} and {page.id}")
                    seen_apps[key] = page.id

    if errors:
        raise LayoutValidationError("\n".join(errors))
    return warnings
