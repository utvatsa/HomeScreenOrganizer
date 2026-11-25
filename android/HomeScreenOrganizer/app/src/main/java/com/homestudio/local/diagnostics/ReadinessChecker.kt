package com.homestudio.local.diagnostics

import android.app.AppOpsManager
import android.appwidget.AppWidgetManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.homestudio.local.widget.CategoryHeaderWidgetProvider
import com.homestudio.local.widget.WallpaperPlaceholderWidgetProvider
import com.homestudio.local.widget.WallpaperSpacer1x1Provider

enum class CheckStatus {
    READY,
    OPTIONAL_MISSING,
    ACTION_NEEDED,
}

enum class ChecklistAction {
    NONE,
    OPEN_HOME_SETTINGS,
    OPEN_USAGE_ACCESS,
    PIN_HEADER_WIDGET,
    REFRESH,
}

data class ReadinessItem(
    val id: String,
    val title: String,
    val required: Boolean,
    val status: CheckStatus,
    val detail: String,
    val actionLabel: String? = null,
    val action: ChecklistAction = ChecklistAction.NONE,
)

data class UsageAppInfo(
    val packageName: String,
    val displayName: String,
    val totalForegroundMinutes: Long,
)

data class ReadinessReport(
    val items: List<ReadinessItem>,
    val topUsedApps: List<UsageAppInfo>,
    val usageAccessGranted: Boolean,
    val headerWidgetCount: Int,
    val spacerWidgetCount: Int,
) {
    val requiredItems: List<ReadinessItem>
        get() = items.filter { it.required }

    val readyRequiredCount: Int
        get() = requiredItems.count { it.status == CheckStatus.READY }

    val allRequiredReady: Boolean
        get() = requiredItems.all { it.status == CheckStatus.READY }
}

object ReadinessChecker {

    private const val PKG_ONEUI_HOME = "com.sec.android.app.launcher"
    private const val PKG_GOOD_LOCK = "com.samsung.android.goodlock"
    private const val PKG_HOME_UP = "com.samsung.android.app.homestar"
    private const val PKG_SMART_SWITCH = "com.sec.android.easyMover"
    private const val PKG_KWGT = "org.kustom.widget"

    fun inspect(context: Context): ReadinessReport {
        val pm = context.packageManager
        val appWidgetMgr = AppWidgetManager.getInstance(context)

        val defaultHomePkg = resolveDefaultHomePackage(pm)
        val oneUiInfo = getPackageInfoCompat(pm, PKG_ONEUI_HOME)
        val goodLockInfo = getPackageInfoCompat(pm, PKG_GOOD_LOCK)
        val homeUpInfo = getPackageInfoCompat(pm, PKG_HOME_UP)
        val smartSwitchInfo = getPackageInfoCompat(pm, PKG_SMART_SWITCH)
        val kwgtInfo = getPackageInfoCompat(pm, PKG_KWGT)

        val pinSupported = appWidgetMgr.isRequestPinAppWidgetSupported
        val headerIds = appWidgetMgr.getAppWidgetIds(
            ComponentName(context, CategoryHeaderWidgetProvider::class.java),
        )
        val spacer4x1Ids = appWidgetMgr.getAppWidgetIds(
            ComponentName(context, WallpaperPlaceholderWidgetProvider::class.java),
        )
        val spacer1x1Ids = appWidgetMgr.getAppWidgetIds(
            ComponentName(context, WallpaperSpacer1x1Provider::class.java),
        )
        val spacerCount = spacer4x1Ids.size + spacer1x1Ids.size
        val usageGranted = hasUsageStatsPermission(context)

        val items = listOf(
            ReadinessItem(
                id = "oneui_home",
                title = "Samsung One UI Home",
                required = true,
                status = if (oneUiInfo != null && defaultHomePkg == PKG_ONEUI_HOME) {
                    CheckStatus.READY
                } else {
                    CheckStatus.ACTION_NEEDED
                },
                detail = when {
                    oneUiInfo == null -> "Not detected on device"
                    defaultHomePkg != PKG_ONEUI_HOME ->
                        "Installed (${oneUiInfo.versionName ?: "?"}), active launcher is ${defaultHomePkg ?: "unknown"}"
                    else -> "Active launcher (v${oneUiInfo.versionName ?: "?"})"
                },
                actionLabel = if (defaultHomePkg != PKG_ONEUI_HOME) "Home Settings" else null,
                action = if (defaultHomePkg != PKG_ONEUI_HOME) ChecklistAction.OPEN_HOME_SETTINGS else ChecklistAction.NONE,
            ),
            ReadinessItem(
                id = "home_up",
                title = "Samsung Home Up (Backup & Tuning)",
                required = true,
                status = if (homeUpInfo != null) CheckStatus.READY else CheckStatus.ACTION_NEEDED,
                detail = if (homeUpInfo != null) {
                    "Installed (v${homeUpInfo.versionName ?: "?"}) — ready for Safe Home backup"
                } else {
                    "Not installed — install free via Good Lock / Galaxy Store before restructuring"
                },
            ),
            ReadinessItem(
                id = "good_lock",
                title = "Samsung Good Lock",
                required = false,
                status = if (goodLockInfo != null) CheckStatus.READY else CheckStatus.OPTIONAL_MISSING,
                detail = if (goodLockInfo != null) {
                    "Installed (v${goodLockInfo.versionName ?: "?"})"
                } else {
                    "Optional — hosts Home Up module"
                },
            ),
            ReadinessItem(
                id = "smart_switch",
                title = "Samsung Smart Switch",
                required = false,
                status = if (smartSwitchInfo != null) CheckStatus.READY else CheckStatus.OPTIONAL_MISSING,
                detail = if (smartSwitchInfo != null) {
                    "Installed (v${smartSwitchInfo.versionName ?: "?"})"
                } else {
                    "Optional — useful for full device backup"
                },
            ),
            ReadinessItem(
                id = "kwgt",
                title = "KWGT (Optional Widgets)",
                required = false,
                status = if (kwgtInfo != null) CheckStatus.READY else CheckStatus.OPTIONAL_MISSING,
                detail = if (kwgtInfo != null) {
                    "Installed (v${kwgtInfo.versionName ?: "?"}) — optional extra"
                } else {
                    "Optional — Home Studio native headers work standalone"
                },
            ),
            ReadinessItem(
                id = "pin_widget",
                title = "Widget Pinning Support",
                required = true,
                status = if (pinSupported) CheckStatus.READY else CheckStatus.ACTION_NEEDED,
                detail = if (pinSupported) {
                    "Supported by launcher (${headerIds.size} header widgets bound)"
                } else {
                    "Launcher reported pinning unsupported — add widgets manually via Long-Press > Widgets"
                },
                actionLabel = if (pinSupported && headerIds.isEmpty()) "Pin Header" else null,
                action = if (pinSupported && headerIds.isEmpty()) ChecklistAction.PIN_HEADER_WIDGET else ChecklistAction.NONE,
            ),
            ReadinessItem(
                id = "usage_access",
                title = "Usage Access (Top-8 Most Used Detection)",
                required = false,
                status = if (usageGranted) CheckStatus.READY else CheckStatus.OPTIONAL_MISSING,
                detail = if (usageGranted) {
                    "Granted — live system Most Used app ranking enabled"
                } else {
                    "Optional — grant Usage Access for on-device Most Used ranking, or use ADB host CLI"
                },
                actionLabel = if (!usageGranted) "Grant Access" else null,
                action = if (!usageGranted) ChecklistAction.OPEN_USAGE_ACCESS else ChecklistAction.NONE,
            ),
        )

        val topUsed = if (usageGranted) queryTopUsedApps(context, limit = 8) else emptyList()

        return ReadinessReport(
            items = items,
            topUsedApps = topUsed,
            usageAccessGranted = usageGranted,
            headerWidgetCount = headerIds.size,
            spacerWidgetCount = spacerCount,
        )
    }

    fun checkPackageInstalled(context: Context, packageName: String): Boolean {
        return getPackageInfoCompat(context.packageManager, packageName.trim()) != null
    }

    fun queryTopUsedApps(context: Context, limit: Int = 8): List<UsageAppInfo> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
        val now = System.currentTimeMillis()
        val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, now - thirtyDaysMs, now)
        }.getOrNull() ?: return emptyList()

        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }.associateBy { it.activityInfo?.packageName }

        val excluded = setOf(PKG_ONEUI_HOME, context.packageName, "com.android.settings")
        val aggregated = mutableMapOf<String, Long>()
        for (item in stats) {
            val pkg = item.packageName ?: continue
            if (pkg in excluded || !launchable.containsKey(pkg)) continue
            aggregated[pkg] = (aggregated[pkg] ?: 0L) + item.totalTimeInForeground
        }

        return aggregated.entries
            .filter { it.value > 0L }
            .sortedByDescending { it.value }
            .take(limit)
            .map { (pkg, fgMs) ->
                val label = launchable[pkg]?.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
                UsageAppInfo(
                    packageName = pkg,
                    displayName = label,
                    totalForegroundMinutes = fgMs / 60_000L,
                )
            }
    }

    @Suppress("DEPRECATION")
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun resolveDefaultHomePackage(pm: PackageManager): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return info?.activityInfo?.packageName
    }

    private fun getPackageInfoCompat(pm: PackageManager, pkg: String): PackageInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
        }.getOrNull()
    }
}
