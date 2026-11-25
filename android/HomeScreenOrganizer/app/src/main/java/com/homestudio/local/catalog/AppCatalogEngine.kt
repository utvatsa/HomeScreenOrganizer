package com.homestudio.local.catalog

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.homestudio.local.data.CustomAppDefinition
import org.json.JSONObject

data class CatalogCategoryMeta(
    val id: String,
    val title: String,
    val description: String,
)

data class CatalogAppEntry(
    val packageName: String,
    val label: String,
    val categories: List<String>,
    val suiteFolder: String? = null,
)

data class AppCatalog(
    val categories: List<CatalogCategoryMeta>,
    val appsByPackage: Map<String, CatalogAppEntry>,
)

data class InstalledAppInfo(
    val packageName: String,
    val activityName: String,
    val displayName: String,
    val categories: List<String>,
    val suiteFolder: String? = null,
    val isSystemApp: Boolean = false,
    val isCustomTagged: Boolean = false,
    val isCatalogMatched: Boolean = false,
)

object AppCatalogEngine {

    private val EXCLUDED_LAUNCHER_SELF = setOf(
        "com.sec.android.app.launcher",
        "com.homestudio.local",
    )

    fun loadCatalogFromAssets(context: Context): AppCatalog {
        val raw = runCatching {
            context.assets.open("app_catalog.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return AppCatalog(emptyList(), emptyMap())
        return parseCatalogJson(raw)
    }

    fun parseCatalogJson(rawJson: String): AppCatalog {
        val root = JSONObject(rawJson)
        val catsArr = root.optJSONArray("categories")
        val cats = mutableListOf<CatalogCategoryMeta>()
        if (catsArr != null) {
            for (i in 0 until catsArr.length()) {
                val o = catsArr.getJSONObject(i)
                cats.add(
                    CatalogCategoryMeta(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        description = o.optString("description", ""),
                    ),
                )
            }
        }
        val appsArr = root.optJSONArray("apps")
        val appMap = mutableMapOf<String, CatalogAppEntry>()
        if (appsArr != null) {
            for (i in 0 until appsArr.length()) {
                val o = appsArr.getJSONObject(i)
                val pkg = o.getString("package")
                val cArr = o.optJSONArray("categories")
                val cList = if (cArr != null) {
                    (0 until cArr.length()).map { idx -> cArr.getString(idx) }
                } else {
                    emptyList()
                }
                appMap[pkg] = CatalogAppEntry(
                    packageName = pkg,
                    label = o.optString("label", pkg),
                    categories = cList,
                    suiteFolder = o.optString("suite").takeIf { o.has("suite") && !o.isNull("suite") },
                )
            }
        }
        return AppCatalog(categories = cats, appsByPackage = appMap)
    }

    /** Discover ONLY apps currently installed and launchable on the device, classified cleanly. */
    fun discoverInstalledApps(
        context: Context,
        catalog: AppCatalog,
        customApps: List<CustomAppDefinition>,
    ): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val customByPkg = customApps.associateBy { it.packageName }
        val results = mutableListOf<InstalledAppInfo>()
        val seenKeys = mutableSetOf<String>()

        for (ri in resolveInfos) {
            val ai = ri.activityInfo ?: continue
            val pkg = ai.packageName ?: continue
            if (pkg in EXCLUDED_LAUNCHER_SELF) continue
            val cls = ai.name ?: continue
            val dedupKey = "$pkg/$cls"
            if (!seenKeys.add(dedupKey)) continue

            val label = ri.loadLabel(pm).toString().ifBlank { pkg }
            val isSystem = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val custom = customByPkg[pkg]
            val catEntry = catalog.appsByPackage[pkg]

            val (categories, suite, isCustom) = when {
                custom != null && custom.categories.isNotEmpty() -> {
                    Triple(custom.categories, custom.suiteFolder ?: catEntry?.suiteFolder, true)
                }
                catEntry != null -> {
                    Triple(catEntry.categories, catEntry.suiteFolder, false)
                }
                else -> {
                    val inferred = inferCategoriesHeuristically(
                        pkg = pkg,
                        label = label,
                        appCategory = ai.applicationInfo.category,
                    )
                    Triple(inferred.first, inferred.second, false)
                }
            }

            results.add(
                InstalledAppInfo(
                    packageName = pkg,
                    activityName = cls,
                    displayName = label,
                    categories = categories,
                    suiteFolder = suite,
                    isSystemApp = isSystem,
                    isCustomTagged = isCustom,
                    isCatalogMatched = catEntry != null || isCustom,
                ),
            )
        }

        return results.sortedWith(compareBy({ it.isSystemApp }, { it.displayName.lowercase() }))
    }

    /** Fallback on-device heuristics for apps not listed in app_catalog.json. */
    fun inferCategoriesHeuristically(
        pkg: String,
        label: String,
        appCategory: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    ): Pair<List<String>, String?> {
        val hay = "${pkg.lowercase()} ${label.lowercase()}"
        val matched = mutableListOf<String>()
        var suite: String? = null

        if (listOf("bank", "sbi", "hdfc", "icici", "axis", "kotak", "insur", "lic", "acko").any { it in hay }) {
            matched.add("banking")
            if ("sbi" in hay) suite = "SBI Suite" else if ("hdfc" in hay) suite = "HDFC Suite"
        }
        if (listOf("upi", "pay", "wallet", "bhim", "gpay", "cred", "fastag").any { it in hay }) {
            matched.add("payments")
            if ("upi" in hay) matched.add("upi")
        }
        if (listOf("invest", "stock", "mutual", "kite", "groww", "crypto", "coin", "wealth", "finance").any { it in hay }) {
            matched.add("finance")
        }
        if (listOf("food", "swiggy", "zomato", "grocery", "blinkit", "zepto", "pizza", "cafe", "dining").any { it in hay }) {
            matched.add("food-dining")
        }
        if (listOf("cab", "taxi", "ride", "metro", "rail", "train", "flight", "hotel", "airbnb", "booking", "travel", "rapido", "uber").any { it in hay }) {
            matched.add("travel")
        }
        if (listOf("fit", "health", "workout", "garmin", "strava", "gym", "med", "hospital", "pharma", "smoke").any { it in hay }) {
            matched.add("health")
        }
        if (listOf("home", "smart", "iot", "bulb", "camera", "wifi", "router", "fiber", "wiz").any { it in hay }) {
            matched.add("smart-home")
        }
        if (listOf("shop", "store", "mart", "fashion", "cloth", "shoe", "mall", "amazon", "flipkart").any { it in hay }) {
            matched.add("shopping")
        }
        if (listOf("gpt", "claude", "gemini", "llm", "copilot", "perplexity", "grok", "ai").any { " $it" in " $hay" || ".$it" in hay }) {
            matched.add("ai-suite")
        }
        if (listOf("gov", "aadhaar", "passport", "digilocker", "vahan", "license").any { it in hay }) {
            matched.add("government-docs")
        }
        if (listOf("auth", "otp", "pass", "jira", "git", "slack", "work", "teams", "note", "task", "pdf", "scan", "office").any { it in hay }) {
            matched.add("productivity")
        }

        if (matched.isEmpty()) {
            when (appCategory) {
                ApplicationInfo.CATEGORY_GAME -> matched.add("entertainment") to "Games & Play"
                ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> matched.add("entertainment") to "OTT & Audio"
                ApplicationInfo.CATEGORY_SOCIAL -> matched.add("social-comms") to null
                ApplicationInfo.CATEGORY_MAPS -> matched.add("travel") to null
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> matched.add("productivity") to null
                ApplicationInfo.CATEGORY_NEWS -> matched.add("daily-essentials") to null
                else -> {}
            }
        }
        if (matched.isEmpty()) {
            matched.add("utilities")
        }
        return matched.distinct() to suite
    }
}
