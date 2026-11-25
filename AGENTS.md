# AGENTS.md — Durable Project Instructions & Safety Policy

## Project Overview

**HomeScreenOrganizer** is an autonomous, declarative Android home screen management, design, and automation system targeting a primary personal Samsung Galaxy S24 FE (`SM-S721B`) running Samsung One UI Home.

This project is a **production environment** for the user's primary daily phone containing banking, financial, communication, and personal applications.

---

## Non-Negotiable Safety Rules (Prohibited Operations)

**NEVER** perform any of the following on the physical device:
1. Uninstall existing user applications (`adb uninstall`, `pm uninstall`, UI uninstall).
   - **Sole exception**: Project-created temporary test/helper APKs after verifying exact package identity (`com.homestudio.local.*`) and confirming removal will not affect the final home screen.
   - **Never** uninstall `com.homestudio.local` while its widgets or shortcuts are in use.
2. Disable existing applications (`pm disable`, `pm disable-user`).
3. Clear application data or caches (`pm clear`), especially for Samsung One UI Home (`com.sec.android.app.launcher`).
4. Reset Samsung One UI Home.
5. Factory-reset, root, unlock bootloader, flash firmware, install Magisk, or modify system partitions.
6. Bypass lock screen, disable biometric authentication, disable encryption, or disable Play Protect.
7. Open banking/financial applications, read OTPs/passwords/PINs/notifications/conversations/contacts, or initiate financial actions.
8. Delete personal files on device storage (only clean up temporary files created by this project).
9. Upload personal images, screenshots, application inventories, or device serials externally or commit them to Git.
10. Directly modify launcher databases through unsupported privilege escalation.
11. Install paid apps, paid plugins, or introduce any paid services (monetary cost MUST be $0).
12. Switch away from Samsung One UI Home without explicit user approval.

---

## Approval Gate Model

1. **Authorized without asking**: Environment discovery, read-only device inspection, project initialization, app inventory, wallpaper discovery, backup prep, local Python/Android development, emulator testing, installing project-created Home Studio APK, and carefully controlled reversible capability tests on a temporary test page using non-sensitive apps.
2. **Mandatory Approval Gate (Phase 14–15)**: Before executing full home screen restructuring, present ONE consolidated approval package (current inventory, proposed pages, app assignments, banking folders, visual previews, launcher settings changes, backup status, known limitations, recovery strategy, required manual confirmations) and wait for explicit approval.
3. **Post-Approval**: Execute autonomously with checkpointing and verification. Stop and ask only if the approved design must materially change, a privacy-sensitive permission is needed, an irreversible operation is found, backup is insufficient, or the device locks/requires manual confirmation.

---

## Resume Procedure (After Context Reset / Crash / Disconnect)

1. Read `PROJECT_STATE.md` and the latest checkpoint in `checkpoints/`.
2. Verify ADB connectivity and confirm the target device matches the private local serial in `config/private-device.json` (never commit this file).
3. Check device lock state (`dumpsys window` / `dumpsys power`). If locked, pause and ask the user to unlock.
4. Load `config/approved-layout.json` (or `config/desired-layout.json` if pre-approval) and `logs/execution-journal.jsonl`.
5. Run read-only inspection (`homescreenorganizer inspect`) to reconcile `config/observed-layout.json` with the actual launcher state before executing the next pending operation.
6. Never blindly re-run completed operations; verify idempotency first.

---

## Privacy & Git Hygiene

- Keep local Git only; NEVER push to remote without explicit user authorization.
- `.gitignore` MUST exclude: `config/private-device.json`, `backups/`, `checkpoints/`, `screenshots/`, `inventory/`, `assets/wallpaper/`, `assets/previews/`, `reports/`, `logs/`, `dist/`, `.venv/`, `*.keystore`, `*.jks`, `local.properties`, and any device serials or personal data.
