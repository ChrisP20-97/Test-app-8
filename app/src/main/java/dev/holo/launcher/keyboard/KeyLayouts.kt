package dev.holo.launcher.keyboard

/** What a key does. */
sealed interface KeyAction {
    data class Text(val text: String) : KeyAction
    data object Shift : KeyAction
    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    data object Globe : KeyAction
    data class Mode(val target: KbMode) : KeyAction
}

/** One key in a row. [width] is in key units; a row is 10 units wide. */
data class KeySpec(
    val action: KeyAction,
    val label: String,
    val width: Float = 1f,
    /** Typed on long press, and shown as a small hint in the corner. */
    val alt: String? = null,
) {
    val isText: Boolean get() = action is KeyAction.Text
    val isFunction: Boolean get() = !isText && action != KeyAction.Space
}

/** A row: leading/trailing gaps in units plus its keys. */
data class KeyRow(val keys: List<KeySpec>, val lead: Float = 0f, val trail: Float = 0f)

enum class KbMode { ALPHA, SYMBOLS, SYMBOLS2, NUMBER, PHONE }

/** Variations of the main layout depending on the field being edited. */
enum class FieldKind { TEXT, EMAIL, URL, PASSWORD }

object KeyLayouts {
    private fun t(c: String, alt: String? = null) = KeySpec(KeyAction.Text(c), c, alt = alt)
    private fun chars(s: String, alts: String? = null): List<KeySpec> =
        s.mapIndexed { i, ch -> t(ch.toString(), alts?.getOrNull(i)?.toString()) }

    private val shift = KeySpec(KeyAction.Shift, "", 1.5f)
    private val back = KeySpec(KeyAction.Backspace, "", 1.5f)

    private fun bottomRow(modeKey: KeySpec, kind: FieldKind): KeyRow {
        val left = when (kind) {
            FieldKind.EMAIL -> t("@")
            FieldKind.URL -> t("/")
            else -> t(",")
        }
        val right = if (kind == FieldKind.URL) t(".", alt = ",") else t(".", alt = ",")
        return KeyRow(
            listOf(
                modeKey,
                left,
                KeySpec(KeyAction.Globe, "", 1f),
                KeySpec(KeyAction.Space, "", 4f),
                right,
                KeySpec(KeyAction.Enter, "", 1.5f),
            )
        )
    }

    private val numberRow = KeyRow(chars("1234567890"))

    fun rows(mode: KbMode, kind: FieldKind, numberRowOn: Boolean): List<KeyRow> = when (mode) {
        KbMode.ALPHA -> buildList {
            if (numberRowOn) add(numberRow)
            add(KeyRow(chars("qwertyuiop", if (numberRowOn) null else "1234567890")))
            add(KeyRow(chars("asdfghjkl", "@#\$_&-+()"), lead = 0.5f, trail = 0.5f))
            add(KeyRow(listOf(shift) + chars("zxcvbnm", "*\"':;!?") + back))
            add(bottomRow(KeySpec(KeyAction.Mode(KbMode.SYMBOLS), "?123", 1.5f), kind))
        }
        KbMode.SYMBOLS -> listOf(
            KeyRow(chars("1234567890")),
            KeyRow(chars("@#\$_&-+()/")),
            KeyRow(listOf(KeySpec(KeyAction.Mode(KbMode.SYMBOLS2), "=\\<", 1.5f)) + chars("*\"':;!?") + back),
            bottomRow(KeySpec(KeyAction.Mode(KbMode.ALPHA), "ABC", 1.5f), kind),
        )
        KbMode.SYMBOLS2 -> listOf(
            KeyRow(chars("~`|•√π÷×¶∆")),
            KeyRow(chars("£€¥^°={}\\%")),
            KeyRow(listOf(KeySpec(KeyAction.Mode(KbMode.SYMBOLS), "?123", 1.5f)) + chars("[]<>™©✓") + back),
            bottomRow(KeySpec(KeyAction.Mode(KbMode.ALPHA), "ABC", 1.5f), kind),
        )
        KbMode.NUMBER -> pad(
            "123-", "456,", "789",
            listOf(KeySpec(KeyAction.Mode(KbMode.ALPHA), "ABC", 2.5f), w("0"), w("."), KeySpec(KeyAction.Enter, "", 2.5f)),
        )
        KbMode.PHONE -> pad(
            "123-", "456+", "789",
            listOf(w("*"), w("0"), w("#"), KeySpec(KeyAction.Enter, "", 2.5f)),
        )
    }

    private fun w(c: String) = KeySpec(KeyAction.Text(c), c, 2.5f)

    private fun pad(r0: String, r1: String, r2: String, r3: List<KeySpec>): List<KeyRow> = listOf(
        KeyRow(r0.map { w(it.toString()) }),
        KeyRow(r1.map { w(it.toString()) }),
        KeyRow(r2.map { w(it.toString()) } + KeySpec(KeyAction.Backspace, "", 2.5f)),
        KeyRow(r3),
    )
}
