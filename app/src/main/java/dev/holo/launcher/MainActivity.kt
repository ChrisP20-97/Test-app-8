package dev.holo.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.holo.launcher.ui.HoloRoot
import dev.holo.launcher.ui.theme.HoloTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Fires when the user presses Home while the launcher is already showing. */
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Counts requests (from the keyboard's gear) to open keyboard settings. */
    private val keyboardSettings = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
        val container = (application as HoloApplication).container
        HoloTheme.apply(container.settings.state.value)
        if (intent?.action == ACTION_KEYBOARD_SETTINGS) keyboardSettings.value++
        setContent { HoloRoot(container = container, homePresses = homePresses, keyboardSettings = keyboardSettings) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePresses.tryEmit(Unit)
        }
        if (intent.action == ACTION_KEYBOARD_SETTINGS) keyboardSettings.value++
    }

    companion object {
        const val ACTION_KEYBOARD_SETTINGS = "dev.holo.launcher.KEYBOARD_SETTINGS"
    }
}
