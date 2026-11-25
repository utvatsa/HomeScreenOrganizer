"""Local interactive HTML layout editor and HTTP backend for HomeScreenOrganizer."""

from __future__ import annotations

import json
from dataclasses import asdict
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

from automation.adb.client import AdbClient, DeviceTarget
from automation.executor.planner_ops import plan_reconciliation_ops
from automation.launcher.dump_parser import parse_oneui_dump
from automation.models import DesiredLayout
from automation.planner.validator import LayoutValidationError, validate_layout
from automation.reporting.previews import generate_all_previews


def build_editor_html(
    initial_layout_json: str,
    apps_json: str,
    observed_json: str,
) -> str:
    """Build a self-contained dark-mode interactive layout editor HTML."""
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<title>HomeScreenOrganizer — Interactive Layout Editor</title>
<style>
  * {{ box-sizing: border-box; }}
  body {{ margin: 0; font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif; background: #0c0d11; color: #eceef4; }}
  header {{ display: flex; flex-wrap: wrap; gap: 10px; align-items: center; justify-content: space-between; padding: 14px 22px; background: #14161e; border-bottom: 1px solid #262938; position: sticky; top: 0; z-index: 20; }}
  h1 {{ font-size: 18px; font-weight: 600; margin: 0; }}
  .btn {{ background: #262a3a; color: #eceef4; border: 1px solid #383e54; border-radius: 8px; padding: 7px 12px; font-size: 13px; cursor: pointer; }}
  .btn:hover {{ background: #31374d; }}
  .btn.primary {{ background: #3b66d4; border-color: #4d78e6; }}
  .btn.danger {{ background: #6b262c; border-color: #8f353d; }}
  .layout {{ display: grid; grid-template-columns: 280px 1fr 360px; min-height: calc(100vh - 58px); }}
  .panel {{ border-right: 1px solid #222634; padding: 16px; overflow-y: auto; }}
  .panel:last-child {{ border-right: none; border-left: 1px solid #222634; }}
  .page-pill {{ display: flex; justify-content: space-between; align-items: center; padding: 9px 11px; border-radius: 9px; background: #161923; border: 1px solid #252938; margin-bottom: 8px; cursor: pointer; font-size: 13px; }}
  .page-pill.active {{ border-color: #4d78e6; background: #1c243a; }}
  .badge {{ font-size: 11px; padding: 2px 6px; border-radius: 999px; background: #283044; color: #b7c5eb; }}
  .badge.warn {{ background: #5b3b18; color: #ffd49e; }}
  .phone-frame {{ width: 380px; margin: 12px auto; background: #0d0d0e; border: 2px solid #2d3142; border-radius: 28px; padding: 18px 14px 20px; box-shadow: 0 18px 50px rgba(0,0,0,.55); }}
  .phone-header {{ min-height: 54px; display: flex; align-items: flex-end; padding: 6px 10px; color: #fff; font-weight: 300; letter-spacing: 0.4px; }}
  .phone-spacer {{ height: 140px; display: flex; align-items: center; justify-content: center; color: #6c7285; font-size: 12px; border: 1px dashed #222633; border-radius: 10px; margin: 8px 0 12px; }}
  .grid-4x2 {{ display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }}
  .slot {{ min-height: 82px; background: #161923; border: 1px solid #282c3c; border-radius: 12px; padding: 7px 5px; text-align: center; cursor: pointer; display: flex; flex-direction: column; justify-content: space-between; align-items: center; font-size: 11px; }}
  .slot.selected {{ border-color: #5b8cff; box-shadow: 0 0 0 2px rgba(91,140,255,.3); }}
  .slot-kind {{ font-size: 10px; color: #8da2d6; text-transform: uppercase; }}
  .slot-title {{ font-weight: 500; word-break: break-word; }}
  .field {{ margin-bottom: 12px; }}
  .field label {{ display: block; font-size: 12px; color: #9da4ba; margin-bottom: 4px; }}
  input, select {{ width: 100%; background: #151822; color: #eceef4; border: 1px solid #2f3548; border-radius: 7px; padding: 7px 9px; font-size: 13px; }}
  .app-list {{ max-height: 240px; overflow-y: auto; border: 1px solid #262a3a; border-radius: 8px; padding: 6px; margin-top: 6px; }}
  .app-row {{ display: flex; justify-content: space-between; align-items: center; padding: 5px 6px; border-bottom: 1px solid #1e2230; font-size: 12px; }}
  .muted {{ color: #8c93a8; font-size: 12px; }}
  .status-bar {{ font-size: 12px; color: #8fe3b4; }}
</style>
</head>
<body>
<header>
  <div>
    <h1>HomeScreenOrganizer — Interactive Layout Editor</h1>
    <div id="status" class="status-bar">Loaded layout &amp; 356-app catalog.</div>
  </div>
  <div style="display:flex; gap:8px; flex-wrap:wrap;">
    <button class="btn" onclick="loadFromObserved()">Load Current Phone Layout</button>
    <label class="btn" style="cursor:pointer;">Import JSON<input type="file" accept=".json" style="display:none" onchange="importJsonFile(event)"></label>
    <button class="btn" onclick="downloadJson()">Export JSON</button>
    <button class="btn" onclick="saveToServer()">Save to Workspace</button>
    <button class="btn" onclick="dryRunServer()">Dry Run</button>
    <button class="btn primary" onclick="applyToPhone()">Apply to Phone</button>
  </div>
</header>

<div class="layout">
  <div class="panel">
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
      <strong>Pages (<span id="pageCount">0</span>)</strong>
      <button class="btn" onclick="addPage()">+ Page</button>
    </div>
    <div id="pagesList"></div>
    <hr style="border-color:#222634; margin:16px 0;" />
    <strong>Global Header Theme</strong>
    <div class="field" style="margin-top:8px;">
      <label>Font Size (sp): <span id="lblFontSize"></span></label>
      <input id="inpFontSize" type="range" min="16" max="36" step="1" oninput="updateTheme()" />
    </div>
    <div class="field">
      <label>Opacity: <span id="lblOpacity"></span></label>
      <input id="inpOpacity" type="range" min="0.4" max="1" step="0.05" oninput="updateTheme()" />
    </div>
    <div class="field">
      <label>Font Weight</label>
      <select id="inpFontWeight" onchange="updateTheme()">
        <option value="200">200 (Extra Light)</option>
        <option value="300">300 (Light)</option>
        <option value="400">400 (Regular)</option>
        <option value="500">500 (Medium)</option>
        <option value="700">700 (Bold)</option>
      </select>
    </div>
  </div>

  <div class="panel">
    <div style="display:flex; justify-content:space-between; align-items:center;">
      <strong id="activePageHeading">Page</strong>
      <div style="display:flex; gap:6px;">
        <button class="btn" onclick="movePage(-1)">◀ Move Left</button>
        <button class="btn" onclick="movePage(1)">Move Right ▶</button>
        <button class="btn" onclick="setDefaultPage()">Set Default</button>
        <button class="btn danger" onclick="deletePage()">Delete Page</button>
      </div>
    </div>
    <div class="field" style="margin-top:10px;">
      <label>Category Header Title (leave empty for Wallpaper page)</label>
      <input id="inpPageTitle" type="text" oninput="updatePageTitle()" />
    </div>

    <div class="phone-frame">
      <div id="previewHeader" class="phone-header"></div>
      <div id="previewSpacer" class="phone-spacer">Open upper canvas (1-handed layout)</div>
      <div id="slotsGrid" class="grid-4x2"></div>
    </div>
    <p class="muted" style="text-align:center;">Up to 8 slots in the bottom 2 rows for comfortable one-hand reachability. Click any slot to edit or swap.</p>
  </div>

  <div class="panel">
    <strong>Slot &amp; Suite Inspector</strong>
    <div id="slotEditor" style="margin-top:10px;" class="muted">Select a slot on the phone preview to edit, add an app, or build a Suite folder.</div>
    <hr style="border-color:#222634; margin:16px 0;" />
    <strong>Installed App Catalog</strong>
    <input id="appSearch" type="text" placeholder="Search 356 installed apps..." oninput="renderAppCatalog()" style="margin-top:8px;" />
    <div id="appCatalog" class="app-list"></div>
  </div>
</div>

<script>
const INITIAL_LAYOUT = {initial_layout_json};
const APPS_CATALOG = {apps_json};
const OBSERVED_LAYOUT = {observed_json};

let layout = structuredClone(INITIAL_LAYOUT);
let selectedPageIdx = 0;
let selectedSlotIdx = 0;

function setStatus(msg, isErr=false) {{
  const el = document.getElementById('status');
  el.textContent = msg;
  el.style.color = isErr ? '#ff8b8b' : '#8fe3b4';
}}

function renderAll() {{
  layout.pages.sort((a,b) => a.order - b.order);
  document.getElementById('pageCount').textContent = layout.pages.length;
  const list = document.getElementById('pagesList');
  list.innerHTML = '';
  layout.pages.forEach((p, idx) => {{
    const interactiveCount = p.items.filter(i => i.type === 'app' || i.type === 'folder').length;
    const div = document.createElement('div');
    div.className = 'page-pill' + (idx === selectedPageIdx ? ' active' : '');
    div.onclick = () => {{ selectedPageIdx = idx; selectedSlotIdx = 0; renderAll(); }};
    const warn = interactiveCount > 8 ? ' warn' : '';
    div.innerHTML = `<span>${{idx+1}}. ${{p.title || 'Wallpaper'}}${{p.isDefaultHome ? ' ★' : ''}}</span><span class="badge${{warn}}">${{interactiveCount}}/8</span>`;
    list.appendChild(div);
  }});

  const th = layout.theme.header;
  document.getElementById('inpFontSize').value = th.fontSizeSp;
  document.getElementById('lblFontSize').textContent = th.fontSizeSp;
  document.getElementById('inpOpacity').value = th.opacity;
  document.getElementById('lblOpacity').textContent = th.opacity;
  document.getElementById('inpFontWeight').value = String(th.fontWeight);

  const page = layout.pages[selectedPageIdx];
  document.getElementById('activePageHeading').textContent = `Page ${{selectedPageIdx+1}}: ${{page.title || 'Wallpaper'}}`;
  document.getElementById('inpPageTitle').value = page.title || '';

  const hdr = document.getElementById('previewHeader');
  hdr.textContent = page.header.enabled && page.title ? page.title : '';
  hdr.style.fontSize = `${{th.fontSizeSp * 0.85}}px`;
  hdr.style.fontWeight = th.fontWeight;
  hdr.style.opacity = th.opacity;

  const hasWeather = page.items.some(i => i.type === 'widget');
  document.getElementById('previewSpacer').textContent = hasWeather
    ? 'Weather Widget (4x1) + Open One-Hand Buffer'
    : 'Open upper canvas (1-handed layout)';

  const grid = document.getElementById('slotsGrid');
  grid.innerHTML = '';
  const interactive = page.items.filter(i => i.type === 'app' || i.type === 'folder');
  for (let s = 0; s < 8; s++) {{
    const item = interactive[s];
    const cell = document.createElement('div');
    cell.className = 'slot' + (s === selectedSlotIdx ? ' selected' : '');
    cell.onclick = () => {{ selectedSlotIdx = s; renderAll(); }};
    if (item) {{
      const kind = item.type === 'folder' ? `Suite (${{item.children.length}})` : 'App';
      cell.innerHTML = `<span class="slot-kind">${{kind}}</span><span class="slot-title">${{item.title || item.packageName}}</span>`;
    }} else {{
      cell.innerHTML = `<span class="slot-kind">Empty</span><span class="muted">+ Assign</span>`;
    }}
    grid.appendChild(cell);
  }}
  renderSlotEditor();
  renderAppCatalog();
}}

function renderSlotEditor() {{
  const page = layout.pages[selectedPageIdx];
  const interactive = page.items.filter(i => i.type === 'app' || i.type === 'folder');
  const item = interactive[selectedSlotIdx];
  const el = document.getElementById('slotEditor');
  if (!item) {{
    el.innerHTML = `<p class="muted">Slot #${{selectedSlotIdx+1}} is empty. Click "+ Add" on any app below, or create a new Suite folder:</p>
    <button class="btn" onclick="createFolderInSlot()">+ Create Suite Folder in Slot #${{selectedSlotIdx+1}}</button>`;
    return;
  }}
  if (item.type === 'app') {{
    el.innerHTML = `
      <div class="field"><label>App Label</label><input value="${{item.title || ''}}" readonly /></div>
      <div class="muted" style="margin-bottom:8px;">${{item.packageName}}</div>
      <div style="display:flex; gap:6px;">
        <button class="btn" onclick="moveSlot(-1)">◀ Left</button>
        <button class="btn" onclick="moveSlot(1)">Right ▶</button>
        <button class="btn danger" onclick="removeSlotItem()">Remove</button>
      </div>`;
  }} else if (item.type === 'folder') {{
    const rows = item.children.map((c, ci) => `
      <div class="app-row">
        <span>${{c.label}}</span>
        <button class="btn danger" style="padding:2px 7px;" onclick="removeFolderChild(${{ci}})">×</button>
      </div>`).join('');
    el.innerHTML = `
      <div class="field"><label>Suite Folder Name</label>
        <input value="${{item.title || ''}}" oninput="renameFolder(this.value)" />
      </div>
      <div style="display:flex; gap:6px; margin-bottom:8px;">
        <button class="btn" onclick="moveSlot(-1)">◀ Left</button>
        <button class="btn" onclick="moveSlot(1)">Right ▶</button>
        <button class="btn danger" onclick="removeSlotItem()">Delete Folder</button>
      </div>
      <label class="muted">Apps inside Suite (${{item.children.length}}) — click "+ Add" below to append:</label>
      <div class="app-list">${{rows}}</div>`;
  }}
}}

function renderAppCatalog() {{
  const q = (document.getElementById('appSearch').value || '').toLowerCase();
  const list = document.getElementById('appCatalog');
  const matches = APPS_CATALOG.filter(a => a.displayName.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q)).slice(0, 60);
  list.innerHTML = matches.map(a => `
    <div class="app-row">
      <div><strong>${{a.displayName}}</strong><div class="muted">${{a.packageName}}</div></div>
      <button class="btn" onclick='assignApp(${{JSON.stringify(a.id)}})'>+ Add</button>
    </div>`).join('');
}}

function reflowPositions(page) {{
  const cols = layout.launcher.grid.columns || 4;
  const rows = layout.launcher.grid.rows || 6;
  const startRow = rows - 2;
  const widgets = page.items.filter(i => i.type === 'widget' || i.type === 'placeholder_widget');
  const interactive = page.items.filter(i => i.type === 'app' || i.type === 'folder');
  interactive.forEach((it, idx) => {{
    it.position = {{ row: startRow + Math.floor(idx / cols), column: idx % cols, rowSpan: 1, columnSpan: 1 }};
  }});
  page.items = [...widgets, ...interactive];
}}

function assignApp(appId) {{
  const app = APPS_CATALOG.find(a => a.id === appId);
  if (!app) return;
  const page = layout.pages[selectedPageIdx];
  const interactive = page.items.filter(i => i.type === 'app' || i.type === 'folder');
  const cur = interactive[selectedSlotIdx];
  if (cur && cur.type === 'folder') {{
    cur.children.push({{ id: `${{cur.id}}:${{app.id}}`, packageName: app.packageName, activityName: app.activityName, label: app.displayName, profileId: app.profileId }});
    setStatus(`Added ${{app.displayName}} to folder ${{cur.title}}`);
  }} else {{
    if (interactive.length >= 8 && !cur) {{ setStatus('Max 8 items per page reached!', true); return; }}
    const newItem = {{
      id: `${{page.id}}:app:${{Date.now()}}:${{app.id}}`,
      type: 'app', title: app.displayName, packageName: app.packageName,
      activityName: app.activityName, profileId: app.profileId,
      position: {{ row: 4, column: 0, rowSpan: 1, columnSpan: 1 }}
    }};
    if (cur) {{
      const realIdx = page.items.indexOf(cur);
      page.items[realIdx] = newItem;
    }} else {{
      page.items.push(newItem);
    }}
    reflowPositions(page);
    setStatus(`Placed ${{app.displayName}} on ${{page.title}}`);
  }}
  renderAll();
}}

function createFolderInSlot() {{
  const page = layout.pages[selectedPageIdx];
  const interactive = page.items.filter(i => i.type === 'app' || i.type === 'folder');
  if (interactive.length >= 8) {{ setStatus('Max 8 items per page reached!', true); return; }}
  page.items.push({{
    id: `${{page.id}}:folder:${{Date.now()}}`,
    type: 'folder', title: 'New Suite',
    position: {{ row: 4, column: 0, rowSpan: 1, columnSpan: 1 }},
    children: []
  }});
  reflowPositions(page);
  selectedSlotIdx = page.items.filter(i => i.type === 'app' || i.type === 'folder').length - 1;
  renderAll();
}}

function renameFolder(val) {{
  const page = layout.pages[selectedPageIdx];
  const item = page.items.filter(i => i.type === 'app' || i.type === 'folder')[selectedSlotIdx];
  if (item && item.type === 'folder') item.title = val;
  renderAll();
}}

function removeFolderChild(ci) {{
  const page = layout.pages[selectedPageIdx];
  const item = page.items.filter(i => i.type === 'app' || i.type === 'folder')[selectedSlotIdx];
  if (item && item.type === 'folder') item.children.splice(ci, 1);
  renderAll();
}}

function removeSlotItem() {{
  const page = layout.pages[selectedPageIdx];
  const interactive = page.items.filter(i => i.type === 'app' || i.type === 'folder');
  const item = interactive[selectedSlotIdx];
  if (!item) return;
  page.items = page.items.filter(i => i !== item);
  reflowPositions(page);
  renderAll();
}}

function moveSlot(delta) {{
  const page = layout.pages[selectedPageIdx];
  const widgets = page.items.filter(i => i.type === 'widget' || i.type === 'placeholder_widget');
  const interactive = page.items.filter(i => i.type === 'app' || i.type === 'folder');
  const target = selectedSlotIdx + delta;
  if (target < 0 || target >= interactive.length) return;
  [interactive[selectedSlotIdx], interactive[target]] = [interactive[target], interactive[selectedSlotIdx]];
  page.items = [...widgets, ...interactive];
  reflowPositions(page);
  selectedSlotIdx = target;
  renderAll();
}}

function updatePageTitle() {{
  const page = layout.pages[selectedPageIdx];
  const val = document.getElementById('inpPageTitle').value;
  page.title = val.trim() ? val : null;
  page.header.enabled = !!page.title;
  renderAll();
}}

function updateTheme() {{
  layout.theme.header.fontSizeSp = Number(document.getElementById('inpFontSize').value);
  layout.theme.header.opacity = Number(document.getElementById('inpOpacity').value);
  layout.theme.header.fontWeight = Number(document.getElementById('inpFontWeight').value);
  renderAll();
}}

function addPage() {{
  const order = layout.pages.length;
  layout.pages.push({{
    id: `custom-${{Date.now()}}`, title: 'New Category', order, isDefaultHome: false,
    header: {{ enabled: true, inheritTheme: true, position: {{ row: 0, column: 0, rowSpan: 1, columnSpan: 4 }} }},
    items: []
  }});
  selectedPageIdx = layout.pages.length - 1;
  renderAll();
}}

function movePage(delta) {{
  const t = selectedPageIdx + delta;
  if (t < 0 || t >= layout.pages.length) return;
  [layout.pages[selectedPageIdx], layout.pages[t]] = [layout.pages[t], layout.pages[selectedPageIdx]];
  layout.pages.forEach((p, i) => p.order = i);
  selectedPageIdx = t;
  renderAll();
}}

function setDefaultPage() {{
  layout.pages.forEach((p, i) => p.isDefaultHome = (i === selectedPageIdx));
  renderAll();
}}

function deletePage() {{
  if (layout.pages.length <= 1) return;
  layout.pages.splice(selectedPageIdx, 1);
  layout.pages.forEach((p, i) => p.order = i);
  selectedPageIdx = Math.max(0, selectedPageIdx - 1);
  renderAll();
}}

function loadFromObserved() {{
  if (!OBSERVED_LAYOUT || !OBSERVED_LAYOUT.pages) {{ setStatus('No observed layout embedded', true); return; }}
  setStatus('Loaded observed layout reference into preview (use Sync from Phone when server is running).');
}}

function importJsonFile(ev) {{
  const f = ev.target.files[0];
  if (!f) return;
  f.text().then(txt => {{
    layout = JSON.parse(txt);
    selectedPageIdx = 0;
    selectedSlotIdx = 0;
    renderAll();
    setStatus('Imported JSON layout file.');
  }});
}}

function downloadJson() {{
  const blob = new Blob([JSON.stringify(layout, null, 2)], {{ type: 'application/json' }});
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'desired-layout.json';
  a.click();
}}

async function apiPost(url, body) {{
  const res = await fetch(url, {{ method: 'POST', headers: {{ 'Content-Type': 'application/json' }}, body: JSON.stringify(body || {{}}) }});
  return await res.json();
}}

async function saveToServer() {{
  try {{
    const r = await apiPost('/api/save', layout);
    setStatus(r.message || 'Saved!', !r.ok);
  }} catch {{
    setStatus('Run `.venv/bin/homescreenorganizer serve` to enable direct Save & Apply from browser, or use Export JSON.', true);
  }}
}}

async function dryRunServer() {{
  try {{
    const r = await apiPost('/api/dry-run', layout);
    setStatus(r.message || 'Dry-run ok', !r.ok);
  }} catch {{
    setStatus('Start local server with `.venv/bin/homescreenorganizer serve` for live Dry Run.', true);
  }}
}}

async function applyToPhone() {{
  try {{
    const r = await apiPost('/api/apply', layout);
    setStatus(r.message || 'Applied!', !r.ok);
  }} catch {{
    setStatus('Start local server with `.venv/bin/homescreenorganizer serve` for one-click Apply to Phone.', true);
  }}
}}

renderAll();
</script>
</body>
</html>
"""


def write_static_editor(out_path: Path = Path("reports/layout-editor.html")) -> Path:
    desired_p = Path("config/approved-layout.json")
    if not desired_p.exists():
        desired_p = Path("config/desired-layout.json")
    desired_txt = desired_p.read_text(encoding="utf-8") if desired_p.exists() else "{}"
    apps_txt = Path("inventory/applications.json").read_text(encoding="utf-8") if Path("inventory/applications.json").exists() else "[]"
    obs_txt = Path("config/observed-layout.json").read_text(encoding="utf-8") if Path("config/observed-layout.json").exists() else "{}"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(build_editor_html(desired_txt, apps_txt, obs_txt), encoding="utf-8")
    return out_path


def serve_editor(host: str = "127.0.0.1", port: int = 8765) -> None:
    """Serve interactive HomeScreenOrganizer layout editor with live Save / Dry-Run / Apply."""
    write_static_editor()

    class Handler(BaseHTTPRequestHandler):
        def _send_json(self, payload: dict[str, Any], status: int = 200) -> None:
            data = json.dumps(payload).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self) -> None:
            if self.path in ("/", "/index.html", "/layout-editor.html"):
                html_path = write_static_editor()
                body = html_path.read_bytes()
                self.send_response(HTTPStatus.OK)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            else:
                self._send_json({"ok": False, "message": "Not found"}, 404)

        def do_POST(self) -> None:
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length).decode("utf-8") if length else "{}"
            try:
                data = json.loads(raw)
                layout = DesiredLayout.model_validate(data)
                apps = json.loads(Path("inventory/applications.json").read_text(encoding="utf-8"))
                validate_layout(layout, installed_ids={a["id"] for a in apps}, allow_duplicates=True)
                if self.path == "/api/save":
                    Path("config/desired-layout.json").write_text(layout.model_dump_json(indent=2) + "\n", encoding="utf-8")
                    generate_all_previews(layout, apps, Path("assets/wallpaper/reconstructed_wallpaper.png"), Path("screenshots/before/page_05.png"))
                    self._send_json({"ok": True, "message": "Saved config/desired-layout.json and updated previews."})
                elif self.path == "/api/dry-run":
                    obs = json.loads(Path("config/observed-layout.json").read_text(encoding="utf-8"))
                    ops = plan_reconciliation_ops(layout, obs)
                    self._send_json({"ok": True, "message": f"Dry run valid: {len(ops)} operations planned."})
                elif self.path == "/api/apply":
                    from automation.executor.live_applier import apply_layout_live
                    Path("config/approved-layout.json").write_text(layout.model_dump_json(indent=2) + "\n", encoding="utf-8")
                    res = apply_layout_live(layout)
                    self._send_json({"ok": True, "message": f"Applied to phone: {res['summary']}"})
                else:
                    self._send_json({"ok": False, "message": "Unknown endpoint"}, 404)
            except (LayoutValidationError, Exception) as exc:
                self._send_json({"ok": False, "message": str(exc)}, 400)

    server = ThreadingHTTPServer((host, port), Handler)
    print(f"HomeScreenOrganizer Interactive Editor serving at http://{host}:{port}")
    server.serve_forever()
