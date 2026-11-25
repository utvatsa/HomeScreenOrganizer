"""Realistic local Pillow visual preview generator for proposed home screen pages."""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw, ImageFont

from automation.models import DesiredLayout, PageItemSpec, PageSpec


# Exact geometry observed on SM-S721B (1080x2340, 480dpi)
SCREEN_W = 1080
SCREEN_H = 2340
WS_LEFT = 41
WS_TOP = 125
WS_RIGHT = 1039
WS_BOTTOM = 1763
WS_W = WS_RIGHT - WS_LEFT   # 998
WS_H = WS_BOTTOM - WS_TOP   # 1638


def _load_font(size: int, weight: str = "regular") -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = {
        "light": [
            "/System/Library/Fonts/SFNS.ttf",
            "/System/Library/Fonts/HelveticaNeue.ttc",
            "/System/Library/Fonts/Supplemental/Arial.ttf",
        ],
        "regular": [
            "/System/Library/Fonts/SFNS.ttf",
            "/System/Library/Fonts/HelveticaNeue.ttc",
            "/System/Library/Fonts/Supplemental/Arial.ttf",
        ],
    }
    for path in candidates.get(weight, candidates["regular"]):
        if Path(path).exists():
            try:
                return ImageFont.truetype(path, size=size)
            except OSError:
                continue
    return ImageFont.load_default()


def _truncate_label(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont, max_w: int) -> str:
    if draw.textlength(text, font=font) <= max_w:
        return text
    out = text
    while len(out) > 1 and draw.textlength(out + "…", font=font) > max_w:
        out = out[:-1]
    return out + "…"


def _paste_rounded_icon(
    canvas: Image.Image,
    icon_path: Path | None,
    cx: int,
    cy: int,
    size: int,
    fallback_label: str = "?",
) -> None:
    if icon_path and icon_path.exists():
        icon = Image.open(icon_path).convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
        # Apply gentle squircle-like rounded mask
        mask = Image.new("L", (size, size), 0)
        mdraw = ImageDraw.Draw(mask)
        mdraw.rounded_rectangle((0, 0, size, size), radius=int(size * 0.28), fill=255)
        canvas.paste(icon, (cx - size // 2, cy - size // 2), mask)
    else:
        draw = ImageDraw.Draw(canvas)
        r = size // 2
        draw.rounded_rectangle(
            (cx - r, cy - r, cx + r, cy + r),
            radius=int(size * 0.28),
            fill=(45, 48, 58, 255),
            outline=(90, 95, 110, 255),
            width=2,
        )
        f = _load_font(int(size * 0.4))
        ch = fallback_label[:1].upper()
        tw = draw.textlength(ch, font=f)
        draw.text((cx - tw / 2, cy - size * 0.22), ch, fill=(230, 230, 240, 255), font=f)


def _draw_folder_icon(
    canvas: Image.Image,
    children_icons: list[Path | None],
    cx: int,
    cy: int,
    size: int,
) -> None:
    overlay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    odraw = ImageDraw.Draw(overlay)
    odraw.rounded_rectangle(
        (0, 0, size, size),
        radius=int(size * 0.30),
        fill=(28, 30, 36, 200),
        outline=(75, 78, 90, 140),
        width=2,
    )
    mini = int(size * 0.24)
    pad = int(size * 0.11)
    gap = (size - 2 * pad - 3 * mini) // 2
    for idx in range(min(9, len(children_icons))):
        row = idx // 3
        col = idx % 3
        mx = pad + col * (mini + gap) + mini // 2
        my = pad + row * (mini + gap) + mini // 2
        _paste_rounded_icon(overlay, children_icons[idx], mx, my, mini)
    canvas.paste(overlay, (cx - size // 2, cy - size // 2), overlay)


def render_page_preview(
    layout: DesiredLayout,
    page: PageSpec,
    apps_by_id: dict[str, dict[str, Any]],
    wallpaper_path: Path,
    dock_template_screenshot: Path,
    out_path: Path,
) -> Path:
    """Render a realistic 1080x2340 preview image for a single proposed home screen page."""
    base = Image.open(wallpaper_path).convert("RGBA").resize((SCREEN_W, SCREEN_H), Image.Resampling.LANCZOS)

    # Copy actual status bar (top 110px) and bottom dock/QSB/nav bar (y >= 1780) from reference screenshot
    if dock_template_screenshot.exists():
        ref = Image.open(dock_template_screenshot).convert("RGBA").resize((SCREEN_W, SCREEN_H))
        top_bar = ref.crop((0, 0, SCREEN_W, 110))
        bottom_bar = ref.crop((0, 1780, SCREEN_W, SCREEN_H))
        base.paste(top_bar, (0, 0))
        base.paste(bottom_bar, (0, 1780))

    draw = ImageDraw.Draw(base)
    cols = layout.launcher.grid.columns
    rows = layout.launcher.grid.rows or 6
    cell_w = WS_W / cols
    cell_h = WS_H / rows

    # Draw subtle page indicator dots near y=1750
    total_pages = len(layout.pages)
    dot_r = 5
    dot_gap = 20
    dots_w = total_pages * dot_gap
    start_dx = (SCREEN_W - dots_w) // 2
    for i in range(total_pages):
        dx = start_dx + i * dot_gap + dot_gap // 2
        dy = 1758
        color = (255, 255, 255, 230) if i == page.order else (255, 255, 255, 80)
        draw.ellipse((dx - dot_r, dy - dot_r, dx + dot_r, dy + dot_r), fill=color)

    # Render Category Header if enabled
    if page.header.enabled and page.title:
        hp = page.header.position
        hx = WS_LEFT + int(hp.column * cell_w) + 24
        hy = WS_TOP + int(hp.row * cell_h) + int(cell_h * 0.45)
        h_font = _load_font(int(layout.theme.header.fontSizeSp * 2.85), weight="light")
        alpha = int(layout.theme.header.opacity * 255)
        draw.text((hx, hy), page.title, fill=(255, 255, 255, alpha), font=h_font)
        if layout.theme.header.showSeparator:
            line_y = hy + int(layout.theme.header.fontSizeSp * 3.3)
            draw.line((hx, line_y, WS_RIGHT - 24, line_y), fill=(255, 255, 255, 55), width=2)

    icon_size = 132 if cols >= 5 else 148
    label_font = _load_font(31 if cols >= 5 else 34)

    for item in page.items:
        p = item.position
        x0 = WS_LEFT + int(p.column * cell_w)
        y0 = WS_TOP + int(p.row * cell_h)
        w = int(p.columnSpan * cell_w)
        h = int(p.rowSpan * cell_h)

        if item.type == "widget":
            # Weather widget stylized preview
            pad = 16
            draw.rounded_rectangle(
                (x0 + pad, y0 + 18, x0 + w - pad, y0 + h - 18),
                radius=32,
                fill=(24, 26, 32, 130),
                outline=(255, 255, 255, 30),
                width=2,
            )
            wf = _load_font(44)
            sf = _load_font(26)
            draw.text((x0 + 42, y0 + 48), "22°  Clear / Minimal Weather", fill=(255, 255, 255, 230), font=wf)
            draw.text((x0 + 44, y0 + 112), "Samsung Weather Widget (4×1)", fill=(200, 205, 215, 170), font=sf)
            continue

        if item.type == "placeholder_widget":
            # Completely invisible by design!
            continue

        cx = x0 + w // 2
        cy = y0 + int(h * 0.42)

        if item.type == "folder":
            ch_icons: list[Path | None] = []
            for ch in item.children:
                cid = f"u{ch.profileId}:{ch.packageName}/{ch.activityName}"
                app_entry = apps_by_id.get(cid)
                ip = Path(app_entry["iconPath"]) if app_entry and app_entry.get("iconPath") else None
                ch_icons.append(ip)
            _draw_folder_icon(base, ch_icons, cx, cy, icon_size)
            lbl = _truncate_label(draw, item.title or "Folder", label_font, int(w - 14))
            tw = draw.textlength(lbl, font=label_font)
            draw.text((cx - tw / 2, y0 + int(h * 0.77)), lbl, fill=(242, 242, 246, 240), font=label_font)
        else:
            cid = f"u{item.profileId}:{item.packageName}/{item.activityName}"
            app_entry = apps_by_id.get(cid)
            ip = Path(app_entry["iconPath"]) if app_entry and app_entry.get("iconPath") else None
            _paste_rounded_icon(base, ip, cx, cy, icon_size, fallback_label=item.title or "?")
            lbl = _truncate_label(draw, item.title or "", label_font, int(w - 14))
            tw = draw.textlength(lbl, font=label_font)
            draw.text((cx - tw / 2, y0 + int(h * 0.77)), lbl, fill=(242, 242, 246, 240), font=label_font)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    base.convert("RGB").save(out_path, quality=95)
    return out_path


def generate_all_previews(
    layout: DesiredLayout,
    apps: list[dict[str, Any]],
    wallpaper_path: Path,
    dock_template_screenshot: Path,
    out_dir: Path = Path("assets/previews"),
) -> dict[str, Any]:
    apps_by_id = {a["id"]: a for a in apps}
    page_paths: list[Path] = []
    for page in sorted(layout.pages, key=lambda p: p.order):
        fname = f"preview_{page.order + 1:02d}_{page.id}.png"
        p = render_page_preview(
            layout, page, apps_by_id, wallpaper_path, dock_template_screenshot, out_dir / fname
        )
        page_paths.append(p)

    # Contact sheet (4 columns)
    cols_sheet = 4
    rows_sheet = math.ceil(len(page_paths) / cols_sheet)
    thumb_w, thumb_h = 360, 780
    pad = 24
    sheet_w = cols_sheet * thumb_w + (cols_sheet + 1) * pad
    sheet_h = rows_sheet * thumb_h + (rows_sheet + 1) * pad
    sheet = Image.new("RGB", (sheet_w, sheet_h), (18, 18, 22))
    sdraw = ImageDraw.Draw(sheet)
    lbl_font = _load_font(22)
    for idx, p in enumerate(page_paths):
        r = idx // cols_sheet
        c = idx % cols_sheet
        x = pad + c * (thumb_w + pad)
        y = pad + r * (thumb_h + pad)
        im = Image.open(p).resize((thumb_w, thumb_h), Image.Resampling.LANCZOS)
        sheet.paste(im, (x, y))
        sdraw.rectangle((x, y, x + thumb_w, y + thumb_h), outline=(65, 68, 78), width=2)
    contact_path = out_dir / "contact_sheet.png"
    sheet.save(contact_path, quality=92)
    return {
        "pagePreviews": [str(p) for p in page_paths],
        "contactSheet": str(contact_path),
    }
