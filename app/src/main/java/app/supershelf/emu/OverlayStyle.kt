package app.supershelf.emu

import androidx.compose.ui.graphics.Color
import app.supershelf.data.AppStore

/**
 * Touch-overlay style for Super Shelf. Exactly two outline themes,
 * per Robert's 2026-11-01 correction — no third option:
 *  - SNES: outlines in the real US SNES controller's colours
 *    (purple for A/B, lavender for X/Y, grey for the rest).
 *  - FLAT: outlines in white only, nothing else.
 * Opacity is adjustable (Robert, same day): one slider, persisted
 * per layout, applying to the outline stroke and its label together,
 * from near-invisible (0.05) to fully solid (1.0). Stroke width is
 * adjustable alongside it. A per-game override wins over the global
 * style; otherwise the global style applies. The overlay is always
 * outline-only: transparent fill, never a solid button.
 */
enum class OutlineTheme { SNES, FLAT }

data class OverlayStyle(
    val theme: OutlineTheme = OutlineTheme.SNES,
    /** 0.05 = near-invisible, 1.0 = fully solid. */
    val opacity: Float = 1.0f,
    /** Stroke width in dp. */
    val strokeDp: Float = 2.5f,
    val labelsOn: Boolean = true
) {
    /** Colour for one control id under this theme, at full strength
     * (callers multiply alpha by [opacity]). */
    fun colorFor(id: String): Color = when (theme) {
        OutlineTheme.FLAT -> Color.White
        OutlineTheme.SNES -> when (id) {
            "a", "b" -> Color(0xFF6B4FA1) // US SNES purple
            "x", "y" -> Color(0xFFB7A6D9) // US SNES lavender
            "l", "r" -> Color(0xFF8A8A8A)
            "select", "start" -> Color(0xFF8A8A8A)
            else -> Color(0xFF3A3A3A) // D-pad: dark grey
        }
    }
}

object OverlayStyleStore {
    private const val THEME_KEY = "overlayTheme"
    private const val OPACITY_KEY = "overlayOpacity"
    private const val STROKE_KEY = "overlayStrokeDp"
    private const val LABELS_KEY = "overlayLabels"

    fun global(store: AppStore): OverlayStyle {
        val theme = when (store.setting(THEME_KEY, "SNES")) {
            "FLAT" -> OutlineTheme.FLAT
            else -> OutlineTheme.SNES
        }
        val opacity = store.setting(OPACITY_KEY, "1.0")
            .toFloatOrNull()?.coerceIn(0.05f, 1.0f) ?: 1.0f
        val stroke = store.setting(STROKE_KEY, "2.5")
            .toFloatOrNull()?.coerceIn(1f, 6f) ?: 2.5f
        val labels = store.setting(LABELS_KEY, "true") != "false"
        return OverlayStyle(theme, opacity, stroke, labels)
    }

    fun forGame(store: AppStore, gameId: String?): OverlayStyle {
        if (gameId == null) return global(store)
        val themeRaw = store.setting("$THEME_KEY:$gameId", "")
        val opacityRaw = store.setting("$OPACITY_KEY:$gameId", "")
        val strokeRaw = store.setting("$STROKE_KEY:$gameId", "")
        if (themeRaw.isBlank() && opacityRaw.isBlank() && strokeRaw.isBlank()) {
            return global(store)
        }
        val base = global(store)
        return base.copy(
            theme = when (themeRaw) {
                "FLAT" -> OutlineTheme.FLAT
                "SNES" -> OutlineTheme.SNES
                else -> base.theme
            },
            opacity = opacityRaw.toFloatOrNull()?.coerceIn(0.05f, 1.0f)
                ?: base.opacity,
            strokeDp = strokeRaw.toFloatOrNull()?.coerceIn(1f, 6f)
                ?: base.strokeDp
        )
    }

    fun saveGlobal(store: AppStore, style: OverlayStyle) {
        store.putSetting(THEME_KEY, style.theme.name)
        store.putSetting(OPACITY_KEY, style.opacity.toString())
        store.putSetting(STROKE_KEY, style.strokeDp.toString())
        store.putSetting(LABELS_KEY, style.labelsOn.toString())
    }

    fun saveForGame(store: AppStore, gameId: String, style: OverlayStyle) {
        store.putSetting("$THEME_KEY:$gameId", style.theme.name)
        store.putSetting("$OPACITY_KEY:$gameId", style.opacity.toString())
        store.putSetting("$STROKE_KEY:$gameId", style.strokeDp.toString())
    }

    fun clearForGame(store: AppStore, gameId: String) {
        store.removeSetting("$THEME_KEY:$gameId")
        store.removeSetting("$OPACITY_KEY:$gameId")
        store.removeSetting("$STROKE_KEY:$gameId")
    }
}
