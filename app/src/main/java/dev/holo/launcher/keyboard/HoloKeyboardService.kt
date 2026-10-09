package dev.holo.launcher.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.holo.launcher.HoloApplication
import dev.holo.launcher.MainActivity
import dev.holo.launcher.data.KeyHaptics

/** Observable keyboard state the UI draws from. */
class KbState {
    var mode by mutableStateOf(KbMode.ALPHA)
    var kind by mutableStateOf(FieldKind.TEXT)
    var shift by mutableStateOf(false)
    var caps by mutableStateOf(false)
    /** Text for the enter key, or null for the plain return arrow. */
    var enterLabel by mutableStateOf<String?>(null)
    var fieldLabel by mutableStateOf("TEXT")
    var shown by mutableStateOf(false)
    var bootTick by mutableIntStateOf(0)
}

enum class FeedbackKind { KEY, DELETE, SPACE, ENTER }

/** What the keyboard UI can ask the service to do. */
interface KbActions {
    fun text(s: String)
    fun backspace()
    fun space()
    fun enter()
    fun shift()
    fun mode(target: KbMode)
    fun globe()
    fun picker()
    fun moveCursor(steps: Int)
    fun paste()
    fun hide()
    fun openSettings()
    fun feedback(kind: FeedbackKind)
}

/**
 * The HUD keyboard. A plain [InputMethodService] hosting Compose; it owns its own lifecycle and
 * saved-state registry because Compose needs both and a service provides neither.
 */
class HoloKeyboardService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, KbActions {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    val state = KbState()
    private val store by lazy { (application as HoloApplication).container.settings }

    private var lastShiftTap = 0L
    private var lastSpaceAt = 0L
    private var inputView: View? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@HoloKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@HoloKeyboardService)
            setContent { HoloKeyboard(state = state, store = store, actions = this@HoloKeyboardService) }
        }
        inputView = view
        return view
    }

    /** Never switch to the full-screen extract editor in landscape. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onWindowShown() {
        super.onWindowShown()
        state.shown = true
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        state.shown = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val ei = info ?: EditorInfo()
        val cls = ei.inputType and InputType.TYPE_MASK_CLASS
        val variation = ei.inputType and InputType.TYPE_MASK_VARIATION
        val password = when (cls) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
        state.kind = when {
            password -> FieldKind.PASSWORD
            cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) -> FieldKind.EMAIL
            cls == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
            else -> FieldKind.TEXT
        }
        if (!restarting) {
            state.mode = when (cls) {
                InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> KbMode.NUMBER
                InputType.TYPE_CLASS_PHONE -> KbMode.PHONE
                else -> KbMode.ALPHA
            }
            state.caps = false
            state.bootTick++
        }
        state.fieldLabel = when {
            password -> "SECURE"
            cls == InputType.TYPE_CLASS_PHONE -> "PHONE"
            cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_DATETIME -> "NUMERIC"
            state.kind == FieldKind.EMAIL -> "EMAIL"
            state.kind == FieldKind.URL -> "ADDRESS"
            ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0 -> "MESSAGE"
            else -> "TEXT"
        }
        state.enterLabel = enterLabelFor(ei)
        updateShift()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateShift()
    }

    private fun enterLabelFor(ei: EditorInfo): String? {
        if (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
        ei.actionLabel?.toString()?.takeIf { it.isNotBlank() && it.length <= 6 }?.let { return it.uppercase() }
        return when (ei.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "GO"
            EditorInfo.IME_ACTION_SEARCH -> "SEARCH"
            EditorInfo.IME_ACTION_SEND -> "SEND"
            EditorInfo.IME_ACTION_NEXT -> "NEXT"
            EditorInfo.IME_ACTION_DONE -> "DONE"
            EditorInfo.IME_ACTION_PREVIOUS -> "PREV"
            else -> null
        }
    }

    /** Auto-capitalise at the start of sentences, following the field's own caps flags. */
    private fun updateShift() {
        if (state.caps) return
        val s = store.state.value
        val ic = currentInputConnection
        val ei = currentInputEditorInfo
        state.shift = if (s.kbAutoCaps && state.mode == KbMode.ALPHA && state.kind != FieldKind.PASSWORD &&
            ic != null && ei != null && ei.inputType != InputType.TYPE_NULL
        ) {
            ic.getCursorCapsMode(ei.inputType) != 0
        } else {
            false
        }
    }

    // ---------------------------------------------------------------- actions

    override fun text(s: String) {
        val ic = currentInputConnection ?: return
        val upper = (state.shift || state.caps) && state.mode == KbMode.ALPHA
        ic.commitText(if (upper) s.uppercase() else s, 1)
        if (state.shift && !state.caps) state.shift = false
    }

    override fun backspace() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun space() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        if (store.state.value.kbDoubleSpacePeriod && state.kind == FieldKind.TEXT && now - lastSpaceAt < 450 &&
            before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()
        ) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpaceAt = 0L
        } else {
            ic.commitText(" ", 1)
            lastSpaceAt = now
        }
    }

    override fun enter() {
        if (!sendDefaultEditorAction(true)) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    override fun shift() {
        val now = SystemClock.uptimeMillis()
        when {
            now - lastShiftTap < 300 && !state.caps -> {
                state.caps = true
                state.shift = true
            }
            state.caps -> {
                state.caps = false
                state.shift = false
            }
            else -> state.shift = !state.shift
        }
        lastShiftTap = now
    }

    override fun mode(target: KbMode) {
        state.mode = target
        if (target == KbMode.ALPHA) updateShift() else if (!state.caps) state.shift = false
    }

    override fun globe() {
        if (!switchToNextInputMethod(false)) picker()
    }

    override fun picker() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    override fun moveCursor(steps: Int) {
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
    }

    override fun paste() {
        currentInputConnection?.performContextMenuAction(android.R.id.paste)
    }

    override fun hide() {
        requestHideSelf(0)
    }

    override fun openSettings() {
        requestHideSelf(0)
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_KEYBOARD_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    override fun feedback(kind: FeedbackKind) {
        val s = store.state.value
        if (s.kbHaptics != KeyHaptics.OFF) {
            val effect = if (s.kbHaptics == KeyHaptics.FIRM) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK
            runCatching {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
                    ?.vibrate(VibrationEffect.createPredefined(effect))
            }
        }
        if (s.kbSound) {
            val fx = when (kind) {
                FeedbackKind.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                FeedbackKind.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                FeedbackKind.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                FeedbackKind.KEY -> AudioManager.FX_KEYPRESS_STANDARD
            }
            getSystemService(AudioManager::class.java)?.playSoundEffect(fx, -1f)
        }
    }
}
