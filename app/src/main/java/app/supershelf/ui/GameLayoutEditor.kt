package app.supershelf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import app.supershelf.data.AppStore
import app.supershelf.emu.ControlSpec
import app.supershelf.emu.GameLayout
import app.supershelf.emu.OutlineTheme
import app.supershelf.emu.OverlayStyleStore
import kotlin.math.roundToInt

/**
 * Super Shelf's layout editor (SNES only, replacing Mariner's
 * multi-console editor): the replica controls on a dark canvas,
 * drag any control to move it, tap one to select it, then use
 * the size slider. Below the canvas: outline theme (SNES / Flat),
 * opacity slider, and stroke-width slider — the same style
 * controls as the library's overlay settings, so a layout saved
 * here and a style saved there never disagree. Landscape and
 * portrait are separate layouts; the editor edits the side the
 * phone is currently held in. Reset restores the replica.
 * Same call signature as Mariner's editor, so GameActivity's
 * pause-menu call needs no change.
 */
@Composable
fun GameLayoutEditor(
    store: AppStore,
    console: String,
    consoleLabel: String,
    onDone: () -> Unit,
    restoreStatusBar: Boolean = false,
    gameId: String? = null
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    var specs by remember(landscape) {
        mutableStateOf(GameLayout.load(store, "snes", landscape, gameId))
    }
    var selected by remember { mutableStateOf<String?>(null) }
    // Style scope matches the layout being edited:
    // a per-game layout edits the per-game style,
    // a global layout edits the global style. The
    // old code always read and saved the global
    // style, so editing a per-game layout silently
    // changed every game's overlay instead.
    var style by remember {
        mutableStateOf(OverlayStyleStore.forGame(store, gameId))
    }
    fun save() {
        GameLayout.save(store, "snes", landscape, specs, gameId)
        if (gameId != null) {
            OverlayStyleStore.saveForGame(store, gameId, style)
        } else {
            OverlayStyleStore.saveGlobal(store, style)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$consoleLabel layout — " +
                    if (landscape) "landscape" else "portrait",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                GameLayout.reset(store, "snes", landscape, gameId)
                specs = GameLayout.load(store, "snes", landscape, gameId)
            }) { Text("Reset", color = Color.White) }
            Button(onClick = { save(); onDone() }) { Text("Done") }
        }
        Text(
            "Drag a control to move it. Tap one, then use the size slider.",
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .background(Color(0xFF111111))
        ) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            for (spec in specs) {
                val isSel = spec.id == selected
                val col = style.colorFor(spec.id)
                    .copy(alpha = style.opacity)
                val x = (spec.cx * wPx - spec.sizeDp * density.density / 2)
                    .roundToInt()
                val y = (spec.cy * hPx - spec.sizeDp * density.density / 2)
                    .roundToInt()
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x, y) }
                        .size(spec.sizeDp.dp)
                        .border(
                            width = style.strokeDp.dp,
                            color = col,
                            shape = if (spec.pill) {
                                RoundedCornerShape(50)
                            } else {
                                CircleShape
                            }
                        )
                        .background(
                            color = if (isSel) {
                                col.copy(alpha = 0.25f)
                            } else {
                                Color.Transparent
                            },
                            shape = if (spec.pill) {
                                RoundedCornerShape(50)
                            } else {
                                CircleShape
                            }
                        )
                        .clickable { selected = spec.id }
                        .pointerInput(spec.id) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val nx = (spec.cx + drag.x / wPx)
                                    .coerceIn(0.03f, 0.97f)
                                val ny = (spec.cy + drag.y / hPx)
                                    .coerceIn(0.03f, 0.97f)
                                specs = specs.map {
                                    if (it.id == spec.id) {
                                        it.copy(cx = nx, cy = ny)
                                    } else it
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (style.labelsOn) {
                        Text(
                            spec.label,
                            color = col,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Theme: ", color = Color.White)
                TextButton(onClick = {
                    style = style.copy(theme = OutlineTheme.SNES)
                }) {
                    Text(
                        "SNES",
                        color = if (style.theme == OutlineTheme.SNES) {
                            Color.Yellow
                        } else {
                            Color.White
                        }
                    )
                }
                TextButton(onClick = {
                    style = style.copy(theme = OutlineTheme.FLAT)
                }) {
                    Text(
                        "Flat (white)",
                        color = if (style.theme == OutlineTheme.FLAT) {
                            Color.Yellow
                        } else {
                            Color.White
                        }
                    )
                }
            }
            val sel = specs.firstOrNull { it.id == selected }
            if (sel != null) {
                Text(
                    "Size: ${sel.sizeDp}dp (${sel.label})",
                    color = Color.White
                )
                Slider(
                    value = sel.sizeDp.toFloat(),
                    onValueChange = { v ->
                        specs = specs.map {
                            if (it.id == sel.id) {
                                it.copy(
                                    sizeDp = v.roundToInt().coerceIn(
                                        GameLayout.MIN_SIZE_DP,
                                        GameLayout.MAX_SIZE_DP
                                    )
                                )
                            } else it
                        }
                    },
                    valueRange = GameLayout.MIN_SIZE_DP.toFloat()..
                        GameLayout.MAX_SIZE_DP.toFloat()
                )
            }
            Text(
                "Opacity: ${(style.opacity * 100).roundToInt()}%",
                color = Color.White
            )
            Slider(
                value = style.opacity,
                onValueChange = { v ->
                    style = style.copy(opacity = v.coerceIn(0.05f, 1f))
                },
                valueRange = 0.05f..1f
            )
            Text(
                "Stroke: ${style.strokeDp}dp",
                color = Color.White
            )
            Slider(
                value = style.strokeDp,
                onValueChange = { v ->
                    style = style.copy(strokeDp = v.coerceIn(1f, 6f))
                },
                valueRange = 1f..6f
            )
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save layout") }
        }
    }
}

// Keep ControlSpec import used (editor works on specs directly).
@Suppress("unused")
private fun unusedSpec(spec: ControlSpec) = spec.id
