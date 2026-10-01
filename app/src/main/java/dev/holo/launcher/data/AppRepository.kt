package dev.holo.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.ContactsContract
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.LruCache
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val category: Int,
    val info: LauncherActivityInfo,
)

/** [mono] is the app's themed (monochrome) icon layer when it provides one. */
class AppIcon(val full: ImageBitmap, val mono: ImageBitmap?)

/** Default handlers for the COMMS quick links. Any of them can be missing. */
data class CommsTargets(
    val dialer: String? = null,
    val sms: String? = null,
    val email: String? = null,
    val contacts: String? = null,
) {
    val all: Set<String> get() = setOfNotNull(dialer, sms, email, contacts)
}

class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val icons = LruCache<String, AppIcon>(400)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) { changed(packageName) }
        override fun onPackageAdded(packageName: String?, user: UserHandle?) { changed(packageName) }
        override fun onPackageChanged(packageName: String?, user: UserHandle?) { changed(packageName) }
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { refresh() }
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { refresh() }
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun changed(packageName: String?) {
        if (packageName != null) {
            icons.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach { icons.remove(it) }
        }
        refresh()
    }

    fun refresh() {
        scope.launch {
            val list = launcherApps.profiles.flatMap { user ->
                launcherApps.getActivityList(null, user).map { info ->
                    AppEntry(
                        key = info.componentName.flattenToShortString() + "#" + user.hashCode(),
                        label = info.label?.toString().orEmpty(),
                        packageName = info.componentName.packageName,
                        component = info.componentName,
                        user = user,
                        category = info.applicationInfo.category,
                        info = info,
                    )
                }
            }
                .filter { it.packageName != context.packageName }
                .sortedBy { it.label.lowercase() }
            _apps.value = list
        }
    }

    suspend fun icon(app: AppEntry, sizePx: Int): AppIcon {
        icons.get(app.key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val drawable = app.info.getIcon(0)
            val full = drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
            val mono = (drawable as? AdaptiveIconDrawable)?.monochrome?.let { m ->
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                // Adaptive layers are 1.5x the visible icon; expand bounds so the glyph lands centred.
                val inset = sizePx / 4
                m.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
                m.draw(Canvas(bmp))
                bmp.asImageBitmap()
            }
            AppIcon(full, mono).also { icons.put(app.key, it) }
        }
    }

    fun launch(app: AppEntry) {
        try {
            launcherApps.startMainActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchPackage(packageName: String?) {
        if (packageName == null) return
        val entry = _apps.value.firstOrNull { it.packageName == packageName }
        if (entry != null) {
            launch(entry)
            return
        }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun appInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun commsTargets(): CommsTargets = CommsTargets(
        dialer = runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull(),
        sms = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(),
        email = defaultHandler(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))),
        contacts = defaultHandler(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)),
    )

    @Suppress("DEPRECATION")
    private fun defaultHandler(intent: Intent): String? {
        val info = runCatching {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull() ?: return null
        val pkg = info.activityInfo?.packageName ?: return null
        return if (pkg == "android" || pkg.contains("resolver")) null else pkg
    }

    companion object {
        val MEDIA_CATEGORIES = setOf(
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE,
        )
        val UTILITY_CATEGORIES = setOf(
            ApplicationInfo.CATEGORY_PRODUCTIVITY,
            ApplicationInfo.CATEGORY_MAPS,
            ApplicationInfo.CATEGORY_NEWS,
        )
    }
}

/** Ranked app search: prefix, then word-prefix, then substring, then loose subsequence. */
fun searchApps(apps: List<AppEntry>, query: String): List<AppEntry> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return apps.mapNotNull { app ->
        val label = app.label.lowercase()
        val score = when {
            label.startsWith(q) -> 0
            label.split(' ', '-', '_', '.').any { it.startsWith(q) } -> 1
            label.contains(q) -> 2
            isSubsequence(q, label) -> 3
            else -> -1
        }
        if (score < 0) null else Pair(score, app)
    }
        .sortedWith(compareBy<Pair<Int, AppEntry>>({ it.first }, { it.second.label.length }))
        .map { it.second }
}

private fun isSubsequence(needle: String, hay: String): Boolean {
    var i = 0
    for (c in hay) {
        if (i < needle.length && needle[i] == c) i++
    }
    return i == needle.length
}
