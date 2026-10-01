package dev.holo.launcher.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HoloNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val clearable: Boolean,
    val autoCancel: Boolean,
    val contentIntent: PendingIntent?,
)

/**
 * Mirrors the phone's notifications into the launcher. Needs "Notification access",
 * which the user grants in system settings (sideloaded apps first need
 * App info > menu > Allow restricted settings).
 */
class HoloNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _connected.value = true
        publish()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        _connected.value = false
        _items.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        publish()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        publish()
    }

    private fun publish() {
        val active: Array<StatusBarNotification> =
            runCatching { activeNotifications }.getOrNull() ?: emptyArray()
        _items.value = active.asSequence()
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .filter { !it.isOngoing }
            .map { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty()
                HoloNotification(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    title = title,
                    text = text,
                    postTime = sbn.postTime,
                    clearable = sbn.isClearable,
                    autoCancel = (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0,
                    contentIntent = sbn.notification.contentIntent,
                )
            }
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .sortedByDescending { it.postTime }
            .toList()
    }

    companion object {
        private val _items = MutableStateFlow<List<HoloNotification>>(emptyList())
        val items: StateFlow<List<HoloNotification>> = _items.asStateFlow()

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        @Volatile
        private var instance: HoloNotificationListener? = null

        fun isGranted(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun dismiss(key: String) {
            runCatching { instance?.cancelNotification(key) }
        }

        fun dismissAll() {
            runCatching { instance?.cancelAllNotifications() }
        }

        /** Opens the notification's target, as tapping it in the shade would. */
        fun open(context: Context, n: HoloNotification) {
            val pi = n.contentIntent ?: return
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
            runCatching { pi.send(context, 0, null, null, null, null, options) }
            if (n.autoCancel && n.clearable) dismiss(n.key)
        }
    }
}

data class NowPlaying(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val art: ImageBitmap?,
    val packageName: String,
    val controller: MediaController,
)

/** Reads the active media session. Uses the notification-access grant, so no extra permission. */
object MediaHub {
    fun current(context: Context): NowPlaying? {
        if (!HoloNotificationListener.isGranted(context)) return null
        val msm = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val sessions: List<MediaController> = try {
            msm.getActiveSessions(ComponentName(context, HoloNotificationListener::class.java))
        } catch (e: SecurityException) {
            return null
        }
        val c = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
            ?: return null
        val md = c.metadata
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        return NowPlaying(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown track",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?: "",
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            art = art?.asImageBitmap(),
            packageName = c.packageName,
            controller = c,
        )
    }
}
