"""Serial-pinned ADB client with safety preflight checks."""

from __future__ import annotations

import json
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


@dataclass(frozen=True)
class DeviceTarget:
    serial: str
    adb_path: str = "/Users/utsav.v/Library/Android/sdk/platform-tools/adb"
    expected_model: str = "SM-S721B"
    user_id: int = 0

    @classmethod
    def from_private_config(cls, path: Path | None = None) -> "DeviceTarget":
        cfg_path = path or Path("config/private-device.json")
        data = json.loads(cfg_path.read_text(encoding="utf-8"))
        return cls(
            serial=data["serial"],
            adb_path=data.get("adbPath", "/Users/utsav.v/Library/Android/sdk/platform-tools/adb"),
            expected_model=data.get("model", "SM-S721B"),
            user_id=int(data.get("userId", 0)),
        )


class AdbError(RuntimeError):
    """Raised when an ADB command fails or a safety check blocks execution."""


class AdbClient:
    """Safe, serial-pinned ADB wrapper. Never issues destructive device commands."""

    PROHIBITED_SUBSTRINGS = (
        "pm clear",
        "pm disable",
        "pm uninstall",
        "recovery --wipe_data",
        "reboot bootloader",
        "rm -rf /sdcard",
        "rm -rf /data",
    )

    def __init__(self, target: DeviceTarget) -> None:
        self.target = target

    def _base_cmd(self) -> list[str]:
        return [self.target.adb_path, "-s", self.target.serial]

    def run(
        self,
        args: Sequence[str],
        *,
        timeout: float = 30.0,
        check: bool = True,
        text: bool = True,
    ) -> subprocess.CompletedProcess:
        cmd_str = " ".join(args)
        for bad in self.PROHIBITED_SUBSTRINGS:
            if bad in cmd_str:
                raise AdbError(f"Blocked prohibited ADB command pattern: {bad}")
        proc = subprocess.run(
            [*self._base_cmd(), *args],
            capture_output=True,
            timeout=timeout,
            text=text,
            check=False,
        )
        if check and proc.returncode != 0:
            stderr = proc.stderr if text else proc.stderr.decode("utf-8", errors="replace")
            raise AdbError(f"ADB command failed ({proc.returncode}): {' '.join(args)}\n{stderr}")
        return proc

    def shell(self, command: str, *, timeout: float = 30.0, check: bool = True) -> str:
        res = self.run(["shell", command], timeout=timeout, check=check, text=True)
        return res.stdout

    def exec_out_bytes(self, command: str, *, timeout: float = 30.0) -> bytes:
        res = self.run(["exec-out", command], timeout=timeout, check=True, text=False)
        return res.stdout

    def screencap(self, dest_path: Path) -> Path:
        dest_path.parent.mkdir(parents=True, exist_ok=True)
        data = self.exec_out_bytes("screencap -p")
        if not data.startswith(b"\x89PNG"):
            raise AdbError(f"Invalid PNG received from screencap ({len(data)} bytes)")
        dest_path.write_bytes(data)
        return dest_path

    def uiautomator_dump(self, local_path: Path) -> Path:
        remote_tmp = "/sdcard/home-studio-window.xml"
        local_path.parent.mkdir(parents=True, exist_ok=True)
        last_err = ""
        try:
            for attempt in range(3):
                flag = "--compressed " if attempt >= 1 else ""
                proc = self.run(
                    ["shell", f"uiautomator dump {flag}{remote_tmp}"],
                    timeout=18.0,
                    check=False,
                )
                out = (proc.stdout or "") + " " + (proc.stderr or "")
                if "dumped" in out.lower():
                    self.run(["pull", remote_tmp, str(local_path)], timeout=18.0)
                    return local_path
                last_err = out.strip()
            raise AdbError(f"Unexpected uiautomator dump output: {last_err}")
        finally:
            self.shell(f"rm -f {remote_tmp}", check=False)

    def pull(self, remote_path: str, local_path: Path) -> Path:
        local_path.parent.mkdir(parents=True, exist_ok=True)
        self.run(["pull", remote_path, str(local_path)], timeout=60.0)
        return local_path

    def get_prop(self, prop: str) -> str:
        return self.shell(f"getprop {prop}").strip()

    def is_connected_and_authorized(self) -> bool:
        proc = subprocess.run(
            [self.target.adb_path, "devices"],
            capture_output=True,
            text=True,
            timeout=10.0,
            check=False,
        )
        for line in proc.stdout.splitlines()[1:]:
            parts = line.split()
            if len(parts) >= 2 and parts[0] == self.target.serial and parts[1] == "device":
                return True
        return False

    def get_foreground_info(self) -> dict[str, str | bool]:
        win_out = self.shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp|isKeyguardLocked|mAwake'")
        power_out = self.shell("dumpsys power | grep -E 'mWakefulness='")
        focus_m = re.search(r"mCurrentFocus=Window\{[^}]*\s+(\S+)\}", win_out)
        focused_app_m = re.search(r"mFocusedApp=ActivityRecord\{[^}]*\s+(\S+)\s+t\d+\}", win_out)
        wake_m = re.search(r"mWakefulness=(\w+)", power_out)
        return {
            "current_focus": focus_m.group(1) if focus_m else "",
            "focused_app": focused_app_m.group(1) if focused_app_m else "",
            "wakefulness": wake_m.group(1) if wake_m else "",
            "awake": "mAwake=true" in win_out,
            "keyguard_locked": "isKeyguardLocked=true" in win_out,
        }

    def preflight(self, *, require_launcher: bool = False) -> dict[str, object]:
        if not self.is_connected_and_authorized():
            raise AdbError("Target device is not connected or not authorized via ADB.")
        model = self.get_prop("ro.product.model")
        if model != self.target.expected_model:
            raise AdbError(f"Connected model {model!r} does not match expected {self.target.expected_model!r}")
        fg = self.get_foreground_info()
        if fg["wakefulness"] != "Awake" or not fg["awake"] or fg["keyguard_locked"]:
            raise AdbError(f"Device appears asleep or locked: {fg}")
        if require_launcher and "com.sec.android.app.launcher" not in str(fg["current_focus"]):
            raise AdbError(f"Expected Samsung One UI Home in foreground, got: {fg['current_focus']}")
        bat = self.shell("dumpsys battery | grep 'level:' | head -n 1")
        bat_m = re.search(r"level:\s*(\d+)", bat)
        battery_level = int(bat_m.group(1)) if bat_m else -1
        if 0 <= battery_level < 15:
            raise AdbError(f"Battery too low for safe execution: {battery_level}%")
        return {
            "model": model,
            "battery_level": battery_level,
            **fg,
        }

    def tap(self, x: int, y: int) -> None:
        self.shell(f"input tap {int(x)} {int(y)}")

    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 350) -> None:
        self.shell(f"input swipe {int(x1)} {int(y1)} {int(x2)} {int(y2)} {int(duration_ms)}")

    def keyevent(self, keycode: str | int) -> None:
        self.shell(f"input keyevent {keycode}")
