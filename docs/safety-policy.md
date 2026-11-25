# Safety Policy

## Core Principle

The target Samsung Galaxy S24 FE (`SM-S721B`) is the user's primary production phone containing banking, financial, communication, and personal apps. Every automation component operates under strict production safety constraints.

## Prohibited Operations

1. **No app uninstallation or disabling**: Never uninstall or disable user or system apps (`pm uninstall`, `adb uninstall`, `pm disable`, `pm disable-user`).
   - Sole exception: Project-created temporary test APKs (`com.homestudio.local.*`) after confirming no active home screen widgets or shortcuts depend on them.
   - Never uninstall `com.homestudio.local` while its widgets or shortcuts are in use.
2. **No data clearing**: Never run `pm clear` on any app, especially `com.sec.android.app.launcher`.
3. **No privilege escalation / root**: Never root, unlock bootloader, flash firmware, or write directly to protected launcher SQLite databases.
4. **No security bypass**: Never bypass lock screen, disable biometrics/encryption/Play Protect, or silently grant sensitive runtime permissions via ADB.
5. **No sensitive data access**: Never open banking/financial apps, read OTPs, passwords, PINs, notifications, personal messages, or initiate transactions.
6. **No external data exfiltration**: Never upload screenshots, wallpapers, app inventories, or device serials to external APIs or remote Git repositories.
7. **Zero monetary cost**: Only free, open-source, locally built software.

## Preflight & Runtime Guards

Before any device command, `AdbClient.preflight()` checks:
- Device serial matches `config/private-device.json`
- Device model matches expected `SM-S721B`
- Screen is awake and keyguard is unlocked (`isKeyguardLocked=false`)
- Battery level >= 15%
- Target foreground package is `com.sec.android.app.launcher` when executing launcher gestures
