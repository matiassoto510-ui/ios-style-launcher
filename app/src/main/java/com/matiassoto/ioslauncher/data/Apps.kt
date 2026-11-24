package com.matiassoto.ioslauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val key: String,
    val packageName: String,
    val component: ComponentName,
    val label: String,
    val icon: ImageBitmap,
    val category: String,
    val installTime: Long,
    val isSystem: Boolean,
)

class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val user: UserHandle = Process.myUserHandle()
    private val iconCache = HashMap<String, ImageBitmap>()

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.Default) {
        launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != context.packageName }
            .mapNotNull { runCatching { toAppInfo(it) }.getOrNull() }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    }

    private fun toAppInfo(info: LauncherActivityInfo): AppInfo {
        val ai = info.applicationInfo
        val key = info.componentName.flattenToString()
        val label = info.label?.toString()?.takeIf { it.isNotBlank() } ?: info.componentName.packageName
        val installTime = runCatching {
            context.packageManager.getPackageInfo(ai.packageName, 0).firstInstallTime
        }.getOrDefault(0L)
        val cached = synchronized(iconCache) { iconCache[key] }
        val icon = cached ?: run {
            val d = runCatching { info.getIcon(DisplayMetrics.DENSITY_XXHIGH) }.getOrNull()
                ?: context.packageManager.defaultActivityIcon
            renderIcon(d, 168).also { synchronized(iconCache) { iconCache[key] = it } }
        }
        val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        return AppInfo(
            key = key,
            packageName = ai.packageName,
            component = info.componentName,
            label = label,
            icon = icon,
            category = categoryOf(ai, ai.packageName, label),
            installTime = installTime,
            isSystem = isSystem,
        )
    }

    fun invalidate(pkg: String?) {
        synchronized(iconCache) {
            if (pkg == null) iconCache.clear() else iconCache.keys.removeAll { it.startsWith("$pkg/") }
        }
    }

    fun registerChanges(onChange: () -> Unit): LauncherApps.Callback {
        val cb = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) { invalidate(packageName); onChange() }
            override fun onPackageAdded(packageName: String, user: UserHandle) { invalidate(packageName); onChange() }
            override fun onPackageChanged(packageName: String, user: UserHandle) { invalidate(packageName); onChange() }
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { invalidate(null); onChange() }
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { onChange() }
        }
        launcherApps.registerCallback(cb, Handler(Looper.getMainLooper()))
        return cb
    }

    fun unregister(cb: LauncherApps.Callback) = launcherApps.unregisterCallback(cb)

    fun launch(app: AppInfo, bounds: android.graphics.Rect?, opts: Bundle?) {
        try {
            launcherApps.startMainActivity(app.component, user, bounds, opts)
        } catch (e: Exception) {
            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(it, opts) }
            }
        }
    }

    fun openAppInfo(app: AppInfo, bounds: android.graphics.Rect?, opts: Bundle?) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, user, bounds, opts) }
    }
}

fun renderIcon(d: Drawable, sizePx: Int): ImageBitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (d is AdaptiveIconDrawable) {
        // Adaptive icons are 108dp with a 72dp safe zone; show the safe zone full-bleed.
        val inset = (sizePx * 0.25f).toInt()
        d.background?.let { it.setBounds(-inset, -inset, sizePx + inset, sizePx + inset); it.draw(c) }
        d.foreground?.let { it.setBounds(-inset, -inset, sizePx + inset, sizePx + inset); it.draw(c) }
    } else {
        c.drawColor(Color.WHITE)
        val pad = (sizePx * 0.14f).toInt()
        d.setBounds(pad, pad, sizePx - pad, sizePx - pad)
        d.draw(c)
    }
    return bmp.asImageBitmap()
}

private val heuristics: List<Pair<Regex, String>> = listOf(
    Regex("camera|photo|gallery|snapseed|lightroom|picsart") to "Photo & Video",
    Regex("bank|pay|wallet|finance|coinbase|webull|robinhood|cash|venmo|zelle|crypto|money") to "Finance",
    Regex("whatsapp|messag|mms|sms|telegram|facebook|instagram|twitter|snapchat|tiktok|discord|signal|messenger|threads|reddit|\\.x\\.|linkedin") to "Social",
    Regex("music|spotify|youtube|netflix|video|tv|podcast|hulu|disney|prime|twitch|player|radio") to "Entertainment",
    Regex("maps|uber|lyft|travel|airbnb|booking|waze|expedia|airline|transit") to "Travel",
    Regex("shop|amazon|ebay|store|cart|walmart|target|temu|shein|etsy|doordash|ubereats|instacart") to "Shopping & Food",
    Regex("health|fit|workout|step|sleep|run") to "Health & Fitness",
    Regex("docs|sheets|slides|office|drive|notes|keep|word|excel|outlook|gmail|mail|calendar|todo|task|notion|teams|slack|zoom") to "Productivity",
    Regex("game|play\\.games|puzzle|clash|craft|roblox") to "Games",
    Regex("news|weather|book|kindle|read") to "Information & Reading",
    Regex("settings|clock|calc|file|contacts|phone|dialer|launcher|android|samsung|sec\\.|google|chrome|browser|vending|recorder|compass|flashlight|torch") to "Utilities",
)

fun categoryOf(ai: ApplicationInfo, pkg: String, label: String): String {
    when (ai.category) {
        ApplicationInfo.CATEGORY_GAME -> return "Games"
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> return "Entertainment"
        ApplicationInfo.CATEGORY_IMAGE -> return "Photo & Video"
        ApplicationInfo.CATEGORY_SOCIAL -> return "Social"
        ApplicationInfo.CATEGORY_NEWS -> return "Information & Reading"
        ApplicationInfo.CATEGORY_MAPS -> return "Travel"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "Productivity"
    }
    val hay = (pkg + " " + label).lowercase()
    for ((re, cat) in heuristics) if (re.containsMatchIn(hay)) return cat
    return "Other"
}
