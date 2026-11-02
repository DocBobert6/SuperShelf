package app.supershelf.emu

import android.content.Context
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import app.supershelf.data.AppStore
import app.supershelf.ui.Frost
import app.supershelf.ui.SaveStateDialog
import app.supershelf.ui.SuperShelfTheme
import app.supershelf.ui.frostBar
import app.supershelf.ui.GameLayoutEditor
import app.supershelf.ui.systemLabel

/**
 * The game surface (Phase J): a fullscreen libretro
 * session in the video player's chrome language -
 * floating translucent controls that auto-hide, over a
 * letterboxed GLES picture. Touch controls are real
 * multi-touch (each control tracks its own pointer), and
 * every press ticks with the adjustable haptic strength
 * (setting `gameHaptics:<console>`, falling back to the old shared key, Light by default).
 */
class GameActivity : ComponentActivity() {

    /** Set by GameScreen; the activity forwards pad
     * keys and axes to it. Touch input is untouched
     * and stays live alongside. */
    var gameKeyRouter: GameKeyRouter? = null
    private val seenGamepads = mutableSetOf<Int>()

    private fun announceGamepad(deviceId: Int) {
        if (!seenGamepads.add(deviceId)) return
        val name = InputDevice.getDevice(deviceId)?.name
            ?: "Controller"
        Toast.makeText(
            this, "Controller connected: $name",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD ==
            InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_JOYSTICK ==
            InputDevice.SOURCE_JOYSTICK
        ) {
            announceGamepad(event.deviceId)
            if (gameKeyRouter?.onKey(keyCode, true) == true) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD ==
            InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_JOYSTICK ==
            InputDevice.SOURCE_JOYSTICK
        ) {
            if (gameKeyRouter?.onKey(keyCode, false) == true) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK ==
            InputDevice.SOURCE_JOYSTICK &&
            event.action == MotionEvent.ACTION_MOVE &&
            gameKeyRouter != null
        ) {
            announceGamepad(event.deviceId)
            gameKeyRouter?.onAxes(
                event.getAxisValue(MotionEvent.AXIS_HAT_X),
                event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                event.getAxisValue(MotionEvent.AXIS_X),
                event.getAxisValue(MotionEvent.AXIS_Y),
                event.getAxisValue(MotionEvent.AXIS_Z),
                event.getAxisValue(MotionEvent.AXIS_RZ)
            )
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    companion object {
        const val EXTRA_ROM_PATH = "supershelf.emu.ROM_PATH"
        const val EXTRA_GAME_ID = "supershelf.emu.GAME_ID"
        const val EXTRA_LOAD_SLOT = "supershelf.emu.LOAD_SLOT"
        const val EXTRA_LOAD_PATH = "supershelf.emu.LOAD_PATH"
        const val EXTRA_GAME_TITLE = "supershelf.emu.GAME_TITLE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val romPath = intent.getStringExtra(EXTRA_ROM_PATH) ?: ""
        val gameId = intent.getStringExtra(EXTRA_GAME_ID)
        val gameTitle = intent.getStringExtra(EXTRA_GAME_TITLE)
        val loadSlot = intent.getIntExtra(
            EXTRA_LOAD_SLOT, SaveStates.NO_SLOT
        )
        val loadPath = intent.getStringExtra(EXTRA_LOAD_PATH)
        // Themed exactly like the player (Robert's rule:
        // the controls match the player's UI, theme
        // included) - SuperShelfTheme over the shared store.
        val store = AppStore.get(this)
        intent.getStringExtra(EXTRA_GAME_ID)?.let { gameId ->
            runCatching { GameLibrary.touchPlayed(store, gameId) }
        }
        setContent {
            SuperShelfTheme(store) {
                GameScreen(
                    romPath = romPath,
                    gameId = gameId,
                    gameTitle = gameTitle,
                    loadSlot = loadSlot,
                    loadPath = loadPath,
                    onExit = { finish() }
                )
            }
        }
    }
}

@Composable
private fun GameScreen(
    romPath: String,
    gameId: String?,
    gameTitle: String?,
    loadSlot: Int,
    loadPath: String?,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    // Clean title (J9): the library's display title,
    // never the raw filename. The filename is only
    // a fallback, prettified, for launches without
    // a library entry.
    val romName = remember(romPath, gameTitle) {
        gameTitle?.takeIf { it.isNotBlank() }
            ?: File(romPath).name.substringBeforeLast('.')
                .replace('_', ' ').ifBlank { "Game" }
    }
    // Super Shelf is SNES-only: library launches are
    // always SNES, with no extension fallback.
    val consoleKey = "snes"
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    // J7: in portrait the picture is top-anchored
    // just below the camera cutout (the native side
    // reads this inset); in landscape it stays
    // centred and the inset is zero.
    val density = LocalDensity.current
    val cutoutTopPx = WindowInsets.displayCutout
        .getTop(density)
    val cutoutTop = with(density) { cutoutTopPx.toDp() }
    val topInsetPx = cutoutTopPx + with(density) {
        8.dp.toPx().toInt()
    }
    val portraitTopInsetPx = topInsetPx
    val portraitTopInsetDp = cutoutTop
    LaunchedEffect(portraitTopInsetPx, landscape) {
        RetroNative.nativeSetTopInset(
            if (landscape) 0 else portraitTopInsetPx
        )
    }
    LaunchedEffect(consoleKey) {
        val preset = ShaderPresets.forGame(
            store::setting, consoleKey, gameId
        )
        val shadersOn =
            store.setting(
                "gameShaders:$consoleKey",
                store.setting("gameShaders", "false")
            ) == "true"
        if (preset.lcd) {
            RetroNative.nativeSetLcdShader(
                shadersOn, preset.brightScan, preset.brightLcd
            )
        } else if (preset.crt) {
            RetroNative.nativeSetCrtShader(shadersOn)
        } else {
            RetroNative.nativeSetShader(
                shadersOn, preset.scan, preset.grid, preset.blur
            )
        }
        // Per-console color profile (beta.22): rides
        // with the shader toggle, like the other
        // preset values, and is read at session start.
        RetroNative.nativeSetColorProfile(
            preset.color.saturation,
            preset.color.warm,
            preset.color.gamma,
            preset.color.bright,
            preset.color.black,
            preset.color.segaFix
        )
        // Display curvature (beta.4, defaults beta.6):
        // the per-console toggle plus strength
        // slider, read at session start like the
        // shader choice, falling back to the
        // preset's gentle defaults where unset.
        RetroNative.nativeSetCurve(
            ShaderPresets.curvePull(
                preset,
                ShaderPresets.curveOnGame(
                    store::setting, consoleKey, gameId
                ),
                ShaderPresets.curveAmountGame(
                    store::setting, consoleKey, gameId
                )
            )
        )
        // Rounded corners (beta.5, defaults beta.6):
        // the same mesh path, per console,
        // independent of the curve toggle and the
        // shaders toggle.
        RetroNative.nativeSetRound(
            if (ShaderPresets.roundOnGame(
                    store::setting, consoleKey, gameId
                ))
                ShaderPresets.roundAmountGame(
                    store::setting, consoleKey, gameId
                ) / 100f
            else 0f
        )
        // Mega Bezel (adapted) override, applied last
        // at session start so it wins over Violet:
        // the preset's scan/blur/curve/round drive the
        // picture, and its frame + reflection are drawn
        // by the bezel composable below. Two calls are
        // deliberate: nativeSetShader sets the shared
        // scan/blur globals but also switches the mode
        // to the legacy pass, so nativeSetCrtShader
        // restores the CRT chain mode while keeping
        // those globals. Colour is neutral here — a
        // Mega Bezel preset must not inherit Violet's
        // warm grade. A shader change made in the
        // library applies on the next launch, never
        // mid-session (the surface must not move).
        val bezel = ShaderPack.effectivePreset(
            store, gameId, ShaderPack.installed(context)
        )
        if (bezel != null) {
            RetroNative.nativeSetShader(
                shadersOn, bezel.scan, 0f, bezel.blur
            )
            RetroNative.nativeSetCrtShader(shadersOn)
            RetroNative.nativeSetColorProfile(
                1f, 0f, 1f, 1f, 0f, false
            )
            RetroNative.nativeSetCurve(
                if (bezel.curveOn) {
                    0.020f * bezel.curveAmount.coerceIn(0, 100) / 100f
                } else {
                    0f
                }
            )
            RetroNative.nativeSetRound(
                if (bezel.roundOn) {
                    bezel.roundAmount.coerceIn(0, 100) / 100f
                } else {
                    0f
                }
            )
        }
    }
    // Flat-outline overlay style: the game's own
    // style wins, else the global style. Keyed on
    // store.revision so editor saves apply live.
    val overlayStyle = remember(store.revision) {
        OverlayStyleStore.forGame(store, gameId)
    }
    // Mega Bezel (adapted) preset for this game:
    // per-game choice wins, else the app-wide default,
    // else (pack installed) the first reflective preset;
    // null means the built-in Violet look, with no frame.
    // A missing or corrupt pack yields null here, so
    // rendering falls back to Violet silently and the
    // setting itself is left untouched. Only reflective
    // presets (reflection > 0) ever become effective.
    val installedPresets = remember(store.revision) {
        ShaderPack.installed(context)
    }
    val bezelPresetName = ShaderPack.effectivePresetName(
        store, gameId, installedPresets
    ) ?: ""
    val bezelPreset = remember(bezelPresetName, store.revision) {
        if (bezelPresetName.isBlank()) {
            null
        } else {
            installedPresets.firstOrNull { it.name == bezelPresetName }
        }
    }
    // Keyed on store.revision too, so a layout
    // saved from the in-game editor applies live
    // instead of waiting for a rotation or relaunch.
    val layout = remember(consoleKey, landscape, store.revision) {
        GameLayout.load(store, consoleKey, landscape)
    }
    val controlsAlpha =
        (store.setting(
            "gameControlsAlpha:$consoleKey",
            store.setting("gameControlsAlpha", "100")
        ).toIntOrNull() ?: 100) / 100f
    var error by remember { mutableStateOf<String?>(null) }
    var created by remember { mutableStateOf(false) }
    var stateLoaded by remember { mutableStateOf(false) }
    var showStates by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    // The title pill shows at launch only (Robert):
    // visible for the first seconds of the session,
    // then gone even when the controls come back.
    var titleVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(4000)
        titleVisible = false
    }
    // Collapsed chrome (restored, beta.32): the
    // expand pill toggles the top pills; Back opens
    // the pause menu, as it did in beta.31.
    var chromeExpanded by remember { mutableStateOf(false) }
    // FPS readout (per-console setting, off by
    // default): toggled on the console's page before
    // launching; in-game the number is its own
    // always-on overlay, top-right. The readout diffs
    // fresh-frame counts twice a second, so it shows
    // the game's real output rate, not redraws.
    var fpsOn by remember {
        mutableStateOf(
            store.setting(
                "gameFpsCounter:$consoleKey", "false"
            ) == "true"
        )
    }
    var fpsText by remember { mutableStateOf("--") }
    // Fast-forward (J9): per-console setting, off by
    // default; the chrome pill toggles it, audio
    // silences while it runs.
    val ffEnabled = remember {
        store.setting("gameFastForward:$consoleKey", "false") ==
            "true"
    }
    var ffOn by remember { mutableStateOf(false) }
    LaunchedEffect(ffOn) {
        RetroNative.nativeSetSpeed(if (ffOn) 4 else 1)
    }
    // Integer scaling (J9): per-console setting,
    // off by default; applied at session start.
    LaunchedEffect(consoleKey) {
        RetroNative.nativeSetIntegerScale(
            store.setting(
                "gameIntegerScale:$consoleKey", "false"
            ) == "true"
        )
    }
    // No portrait-layout migration: Super Shelf is a
    // fresh app, so saved layouts are never wiped.
    // Pause menu (J9): the game idles while the menu
    // is open, and stays idle if save states were
    // opened from it.
    var showPauseMenu by remember { mutableStateOf(false) }
    var showStatesFromMenu by remember {
        mutableStateOf(false)
    }
    var showEditor by remember { mutableStateOf(false) }
    // Plain showStates pauses too: the chrome
    // States button used to open over a game that
    // kept running underneath the dialog.
    val paused = showPauseMenu ||
        showStates ||
        showEditor
    LaunchedEffect(paused) {
        RetroNative.nativeSetPaused(paused)
    }
    // Screen-on follows play state (audit P-M3):
    // a paused game no longer holds the screen on.
    LaunchedEffect(paused) {
        val activity = context as? GameActivity
            ?: return@LaunchedEffect
        if (paused) {
            activity.window.clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        } else {
            activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
    // Crash autosave (beta.7): every 5 minutes of
    // live play, the current run's auto-state is
    // rewritten, so a crash loses at most ~5
    // minutes. Pausing restarts the wait (the
    // effect re-runs on `paused`); leaving the game
    // saves on dispose as before.
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (true) {
            delay(5 * 60 * 1000L)
            if (gameId != null) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        SaveStates.autoSave(context, gameId)
                    }
                }
            }
        }
    }
    // FPS sampler (moved below `paused`: locals
    // resolve in declaration order). While paused
    // no new frames arrive, so the readout freezes
    // on its last live value instead of dropping
    // to 0; `prev` stays fresh so resume is clean.
    val pausedNow = rememberUpdatedState(paused)
    LaunchedEffect(fpsOn) {
        if (!fpsOn) return@LaunchedEffect
        var prev = RetroNative.nativeGetPresentedFrames()
        while (true) {
            delay(500)
            val cur = RetroNative.nativeGetPresentedFrames()
            if (!pausedNow.value) {
                fpsText = "${(cur - prev) * 2}"
            }
            prev = cur
        }
    }
    // P-M1: backgrounding pauses the game even when no
    // menu is open; returning resumes only if no menu
    // (or other pause source) is still holding the pause.
    val gameLifecycleOwner =
        androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(gameLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                RetroNative.nativeSetPaused(true)
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                RetroNative.nativeSetPaused(pausedNow.value)
            }
        }
        gameLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { gameLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Hide touch controls while a gamepad is
    // connected (J9): tracked from the system's
    // input-device listener, so disconnects bring
    // the buttons straight back.
    var padConnected by remember { mutableStateOf(false) }
    val inputManager = remember {
        context.getSystemService(
            Context.INPUT_SERVICE
        ) as? InputManager
    }
    DisposableEffect(inputManager) {
        if (inputManager == null) {
            return@DisposableEffect onDispose {}
        }
        fun refresh() {
            padConnected = inputManager.inputDeviceIds.any { id ->
                val dev = InputDevice.getDevice(id)
                dev != null && (
                    (dev.sources and
                        InputDevice.SOURCE_GAMEPAD) ==
                        InputDevice.SOURCE_GAMEPAD ||
                    (dev.sources and
                        InputDevice.SOURCE_JOYSTICK) ==
                        InputDevice.SOURCE_JOYSTICK
                )
            }
        }
        refresh()
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                refresh()
            }
            override fun onInputDeviceChanged(deviceId: Int) {
                refresh()
            }
            override fun onInputDeviceRemoved(deviceId: Int) {
                refresh()
            }
        }
        inputManager.registerInputDeviceListener(listener, null)
        onDispose {
            inputManager.unregisterInputDeviceListener(listener)
        }
    }
    var lastTouchMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val inputMask = remember { mutableStateOf(0) }

    val vibrator = remember {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    val hapticLevel = remember {
        store.setting(
            "gameHaptics:$consoleKey",
            store.setting("gameHaptics", "Light")
        )
    }
    fun tick() {
        val v = vibrator ?: return
        val (ms, amp) = when (hapticLevel) {
            "Off" -> return
            "Medium" -> 18L to 140
            "Strong" -> 26L to 255
            else -> 12L to 60
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }
    fun poke() {
        lastTouchMs = System.currentTimeMillis()
        controlsVisible = true
    }
    // Back opens the pause menu (beta.31, Robert):
    // system Back (gesture or key) while playing
    // opens the menu; the game already idles while
    // it is open. The pill row itself expands and
    // collapses only by tapping the expand pill, as
    // it used to. Back inside the menu or any dialog
    // is handled by the dialog itself, so this stays
    // disabled while anything is open.
    BackHandler(
        enabled = !showPauseMenu && !showStates &&
            !showEditor && error == null
    ) {
        showPauseMenu = true
    }
    // One-time Back-menu hint: a transient toast on
    // the first game launch after this change, then
    // never again (flagged in settings).
    LaunchedEffect(Unit) {
        if (store.setting("gameBackHintShown", "false") != "true") {
            Toast.makeText(
                context, "Back opens the pause menu",
                Toast.LENGTH_LONG
            ).show()
            store.putSetting("gameBackHintShown", "true")
        }
    }
    val router = remember {
        GameKeyRouter(
            setBit = { bit, on ->
                inputMask.value = if (on) {
                    inputMask.value or (1 shl bit)
                } else {
                    inputMask.value and (1 shl bit).inv()
                }
                RetroNative.nativeSetInput(inputMask.value)
            },
            onActivity = { poke() },
            onTick = { tick() }
        )
    }
    DisposableEffect(router) {
        (context as? GameActivity)?.gameKeyRouter = router
        onDispose {
            (context as? GameActivity)?.gameKeyRouter = null
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (controlsVisible &&
                System.currentTimeMillis() - lastTouchMs > 10000
            ) {
                controlsVisible = false
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (gameId != null) {
                runCatching {
                    SaveStates.autoSave(context, gameId)
                }
            }
            RetroNative.nativeStop()
            RetroNative.nativeDestroy()
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        // One surface composable, shared by the
        // plain and bezel-framed branches below
        // so the factory code lives in one place.
        val gameSurface: @Composable () -> Unit = {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            if (!created) {
                                created = true
                                // The status check is the SHA-256
                                // pass: a corrupt download is refused,
                                // not loaded, and removed; a verified
                                // one resolves straight to its file —
                                // and is verified once more just
                                // below, immediately before loading
                                // (audit S-m6), so a file swapped in
                                // between the check and the load is
                                // refused, not loaded.
                                val coreStatus = CoreDownload.status(
                                    ctx, consoleKey
                                )
                                if (coreStatus ==
                                    CoreDownload.Status.CORRUPT
                                ) {
                                    Toast.makeText(
                                        ctx,
                                        "Downloaded core failed " +
                                            "verification and was " +
                                            "removed - download the " +
                                            "core again from the " +
                                            "console page.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    CoreDownload.removeDownloaded(
                                        ctx, consoleKey
                                    )
                                }
                                val corePath = if (coreStatus ==
                                    CoreDownload.Status.DOWNLOADED
                                ) {
                                    CoreDownload
                                        .downloadedFileIfPresent(
                                            ctx, consoleKey
                                        )?.absolutePath
                                } else {
                                    null
                                }
                                if (corePath == null) {
                                    error = "No core downloaded for " +
                                        "this console yet - download " +
                                        "it from the console page."
                                    return
                                }
                                // Audit S-m6: re-verify immediately
                                // before loading — the file above was
                                // resolved after the status hash, so
                                // a swap in between must be refused.
                                if (CoreDownload.status(
                                        ctx, consoleKey
                                    ) != CoreDownload.Status.DOWNLOADED
                                ) {
                                    error = "Downloaded core failed " +
                                        "verification - download the core " +
                                        "again from the console page."
                                    return
                                }
                                val err = RetroNative.nativeCreate(
                                    corePath, romPath,
                                    CoreStore.systemDir(ctx).absolutePath,
                                    CoreStore.saveDir(ctx).absolutePath,
                                    CoreOptions.storedPairs(
                                        store, consoleKey
                                    )
                                )
                                if (err != null) {
                                    error = err
                                    return
                                }
                            }
                            RetroNative.nativeSetSurface(holder.surface)
                            RetroNative.nativeStart()
                            if (!stateLoaded) {
                                stateLoaded = true
                                // A path wins over a slot
                                // (beta.7: archived auto-saves
                                // are files, not slots).
                                val loadFile = loadPath
                                    ?.let { File(it) }
                                    ?.takeIf { it.exists() }
                                    ?: if (loadSlot !=
                                        SaveStates.NO_SLOT &&
                                        gameId != null
                                    ) {
                                        SaveStates.stateFile(
                                            ctx, gameId, loadSlot
                                        ).takeIf { it.exists() }
                                    } else {
                                        null
                                    }
                                if (loadFile != null) {
                                    RetroNative.nativeLoadState(
                                        loadFile.absolutePath
                                    )
                                }
                            }
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int, width: Int, height: Int
                        ) {
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            RetroNative.nativeStop()
                            RetroNative.nativeSetSurface(null)
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        }
        if (bezelPreset != null) {
            // Mega Bezel (adapted) frame: a dark
            // frame in the preset's bezel colour
            // around the picture, with a reflection
            // strip below it. Adapted, not a
            // RetroArch-identical reproduction.
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(bezelFrameColor(bezelPreset))
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    gameSurface()
                }
                BezelReflection(
                    preset = bezelPreset,
                    modifier = Modifier.fillMaxWidth()
                        .height(64.dp)
                )
            }
        } else {
            gameSurface()
        }

        // A centre tap toggles the controls; it sits under
        // them, so buttons always win their own touches.
        Box(
            modifier = Modifier.fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember {
                        androidx.compose.foundation.interaction
                            .MutableInteractionSource()
                    }
                ) { poke() }
        )

        if (error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Couldn't start the game",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onExit) { Text("Close") }
            }
        } else if (controlsVisible) {
            GameControls(
                titleVisible = titleVisible,
                topInsetDp =
                    if (landscape) 0.dp else portraitTopInsetDp,
                romName = romName,
                layout = layout,
                controlsAlpha = controlsAlpha,
                overlayStyle = overlayStyle,
                padConnected = padConnected,
                inputMask = inputMask,
                onPoke = { poke() },
                onTick = { tick() },
                onStates = {
                    showStatesFromMenu = false
                    showStates = true
                },
                onMenu = { showPauseMenu = true },
                chromeExpanded = chromeExpanded,
                onToggleChrome = { chromeExpanded = !chromeExpanded },
                ffEnabled = ffEnabled,
                ffOn = ffOn,
                onToggleFf = { ffOn = !ffOn },
            )
        }
        // FPS status overlay (beta.31): when the
        // per-console FPS setting is on, the
        // number sits top-right, always, never
        // auto-hiding with the controls.
        // Display-only, never tappable.
        if (fpsOn && !paused && error == null
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd
            ) {
                Row(
                    modifier = Modifier.padding(
                        top = 14.dp + portraitTopInsetDp,
                        end = 14.dp
                    ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    if (fpsOn) {
                        Text(
                            fpsText,
                            style = MaterialTheme.typography
                                .titleSmall.copy(
                                    color = Color.White,
                                    shadow = Shadow(
                                        color = Color.Black,
                                        offset = Offset(0f, 1f),
                                        blurRadius = 4f
                                    )
                                )
                        )
                    }
                }
            }
        }
        if (showStates && gameId != null) {
            SaveStateDialog(
                gameId = gameId,
                showSave = true,
                batteryExists = false,
                onDeleteBattery = null,
                // Safe in-game form (C10): the card row
                // shows status only. Import/export live
                // on the console page - importing into a
                // running core would be overwritten by
                // the exit autosave.
                isPs1 = false,
                cardStatusOnly = true,
                onLoad = { slot ->
                    if (SaveStates.load(context, gameId, slot)) {
                        showStates = false
                        if (showStatesFromMenu) {
                            showStatesFromMenu = false
                            showPauseMenu = true
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Couldn't load that save state.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onLoadPath = { path ->
                    if (RetroNative.nativeLoadState(path)) {
                        showStates = false
                        if (showStatesFromMenu) {
                            showStatesFromMenu = false
                            showPauseMenu = true
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Couldn't load that save state.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onDismiss = {
                    showStates = false
                    if (showStatesFromMenu) {
                        showStatesFromMenu = false
                        showPauseMenu = true
                    }
                }
            )
        }
        if (showPauseMenu) {
            PauseMenuDialog(
                ffEnabled = ffEnabled,
                ffOn = ffOn,
                onToggleFf = { ffOn = !ffOn },
                onResume = { showPauseMenu = false },
                onSaveStates = {
                    showStatesFromMenu = true
                    showStates = true
                    showPauseMenu = false
                },
                onEditLayout = {
                    showPauseMenu = false
                    showEditor = true
                },
                onReset = {
                    RetroNative.nativeReset()
                    showPauseMenu = false
                },
                onExit = onExit,
                onDismiss = { showPauseMenu = false },
                romName = romName,
                bezelName = bezelPreset?.name,
                bezelCredit = bezelPreset?.credit
            )
        }
        if (showEditor) {
            // The editor sits over the paused game;
            // Done (or back) returns to the pause
            // menu, and the game stays paused throughout.
            GameLayoutEditor(
                store = store,
                console = consoleKey,
                consoleLabel = systemLabel(consoleKey),
                onDone = {
                    showEditor = false
                    showPauseMenu = true
                },
                restoreStatusBar = false
            )
        }
    }
}

@Composable
private fun GameControls(
    titleVisible: Boolean,
    topInsetDp: Dp,
    romName: String,
    layout: List<ControlSpec>,
    controlsAlpha: Float,
    padConnected: Boolean,
    inputMask: MutableState<Int>,
    onPoke: () -> Unit,
    onTick: () -> Unit,
    onStates: () -> Unit,
    onMenu: () -> Unit,
    chromeExpanded: Boolean,
    onToggleChrome: () -> Unit,
    ffEnabled: Boolean,
    ffOn: Boolean,
    onToggleFf: () -> Unit,
    overlayStyle: OverlayStyle
) {
    // Collapsed chrome (restored, beta.32; Robert,
    // beta.16): while playing, only the expand pill
    // shows; it opens the save / fast-forward /
    // menu pills, and the same pill closes them
    // again. Exit lives in the pause menu, not the
    // top row. The row renders before the
    // padConnected early-return below, so it stays
    // reachable with a gamepad connected. The FPS
    // number is NOT a pill here: it lives in
    // beta.31's always-on status overlay.
    Box(modifier = Modifier.fillMaxSize()) {
        // Title + expand, top edge, player-capsule style.
        Row(
            modifier = Modifier.align(Alignment.TopStart)
                .padding(
                    start = 14.dp, end = 14.dp,
                    top = 14.dp + topInsetDp,
                    bottom = 14.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp)
                    .graphicsLayer { this.alpha = controlsAlpha }
                    .frostBar(CircleShape)
                    .clickable { onToggleChrome() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (chromeExpanded) Icons.Filled.ChevronLeft
                    else Icons.Filled.ChevronRight,
                    contentDescription = if (chromeExpanded) {
                        "Hide game buttons"
                    } else {
                        "Show game buttons"
                    },
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (titleVisible) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .frostBar(RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        romName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (chromeExpanded) {
                Spacer(Modifier.width(10.dp))
                // States, first in the expanded row,
                // with a floppy-disk icon.
                Box(
                    modifier = Modifier.size(48.dp)
                        .frostBar(CircleShape)
                        .clickable { onStates() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = "Save states",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Fast-forward: only when enabled for
                // this console; lit in the accent colour
                // while it runs.
                if (ffEnabled) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .frostBar(RoundedCornerShape(50))
                            .clickable { onToggleFf() }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "FF",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (ffOn) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
                // Pause menu, last in the row.
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier.size(48.dp)
                        .frostBar(CircleShape)
                        .clickable { onMenu() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = "Pause menu",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // The controls themselves (J4): the pad renders
        // as ONE pointer surface; everything else keeps
        // its own pointer. Positions and sizes come from
        // the console's saved layout for this orientation.
        // With a gamepad connected (J9) the touch layer
        // hides entirely - the pad is the input now.
        if (padConnected) return@Box
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            val padSpecs = layout.filter {
                GameLayout.groupOf(it.id) == "pad"
            }
            if (padSpecs.size == 4) {
                val centres = padSpecs.map { spec ->
                    spec to (w * spec.cx to h * spec.cy)
                }
                val maxR = padSpecs.maxOf { it.sizeDp }.dp / 2
                val minX = centres.minOf { it.second.first } -
                    maxR * 1.3f
                val minY = centres.minOf { it.second.second } -
                    maxR * 1.3f
                val maxX = centres.maxOf { it.second.first } +
                    maxR * 1.3f
                val maxY = centres.maxOf { it.second.second } +
                    maxR * 1.3f
                // Axis pivot (dpad fix): the pad's
                // vertical axis is the up/down centre
                // line and its horizontal axis is the
                // left/right row - never the mean of
                // all four centres, which skewed the
                // pad whenever a layout was asymmetric.
                val upC = centres.firstOrNull {
                    it.first.id == "up"
                }?.second
                val downC = centres.firstOrNull {
                    it.first.id == "down"
                }?.second
                val leftC = centres.firstOrNull {
                    it.first.id == "left"
                }?.second
                val rightC = centres.firstOrNull {
                    it.first.id == "right"
                }?.second
                val pivot = (
                    if (upC != null && downC != null) {
                        (upC.first + downC.first) / 2
                    } else {
                        centres.fold(0.dp) {
                            a, p -> a + p.second.first
                        } / 4
                    }
                    ) to (
                    if (leftC != null && rightC != null) {
                        (leftC.second + rightC.second) / 2
                    } else {
                        centres.fold(0.dp) {
                            a, p -> a + p.second.second
                        } / 4
                    }
                    )
                DpadSurface(
                    visuals = centres.map { (spec, c) ->
                        PadVisual(spec, c.first - minX, c.second - minY)
                    },
                    pivotX = pivot.first - minX,
                    pivotY = pivot.second - minY,
                    inputMask = inputMask,
                    onPoke = onPoke,
                    onTick = onTick,
                    overlayStyle = overlayStyle,
                    modifier = Modifier.offset(x = minX, y = minY)
                        .size(maxX - minX, maxY - minY)
                )
            }
            for (spec in layout) {
                if (GameLayout.groupOf(spec.id) == "pad") continue
                val widthDp = spec.sizeDp.dp
                val heightDp = if (spec.pill) {
                    (spec.sizeDp * 0.46f).dp
                } else {
                    spec.sizeDp.dp
                }
                val x = w * spec.cx - widthDp / 2
                val y = h * spec.cy - heightDp / 2
                // No graphicsLayer alpha here: the
                // outline style carries the only
                // opacity, so it is never doubled.
                val controlModifier = Modifier.offset(x = x, y = y)
                // Analogue sticks are gone in this
                // SNES-only build: a saved layout
                // carrying one simply skips it.
                if (spec.analog >= 0) continue
                if (spec.pill) {
                    PadPill(
                        spec.id, spec.label, spec.bit,
                        widthDp, heightDp,
                        inputMask, onPoke, onTick, overlayStyle,
                        modifier = controlModifier
                    )
                } else {
                    PadButton(
                        spec.id, spec.label, spec.bit,
                        spec.sizeDp.dp,
                        inputMask, onPoke, onTick, overlayStyle,
                        modifier = controlModifier
                    )
                }
            }
        }
    }
}

/** The pause menu (J9): resume, save states,
 * fast-forward (when enabled for this console),
 * reset, exit. Opening it idles the game; closing
 * it without resuming keeps it idle. */
@Composable
private fun PauseMenuDialog(
    ffEnabled: Boolean,
    ffOn: Boolean,
    onToggleFf: () -> Unit,
    onResume: () -> Unit,
    onSaveStates: () -> Unit,
    onEditLayout: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit,
    romName: String = "",
    bezelName: String? = null,
    bezelCredit: String? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Kit dialog material (Robert, 2026-10-24:
        // the plain white card looked basic and old):
        // the sheets' 28dp shape + sheet colour, and
        // icon rows instead of centred text buttons.
        Card(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = Frost.DialogShape,
            colors = CardDefaults.cardColors(
                containerColor = Frost.sheetColor()
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "Paused",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(
                        horizontal = 20.dp, vertical = 8.dp
                    )
                )
                if (romName.isNotBlank()) {
                    Text(
                        romName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme
                            .onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            horizontal = 20.dp
                        )
                    )
                }
                PauseRow(
                    Icons.Filled.PlayArrow, "Resume", onResume
                )
                PauseRow(
                    Icons.Filled.Save, "Save states", onSaveStates
                )
                PauseRow(
                    Icons.Filled.Tune, "Edit touch layout", onEditLayout
                )
                if (ffEnabled) {
                    PauseRow(
                        Icons.Filled.FastForward,
                        "Fast-forward",
                        onToggleFf,
                        trailing = {
                            Switch(
                                checked = ffOn,
                                onCheckedChange = { onToggleFf() }
                            )
                        }
                    )
                }
                PauseRow(
                    Icons.Filled.Refresh, "Reset game", onReset
                )
                if (bezelName != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(
                            "Mega Bezel (adapted): $bezelName",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (bezelCredit != null) {
                            Text(
                                bezelCredit,
                                style = MaterialTheme.typography
                                    .bodySmall,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                    }
                }
                PauseRow(
                    Icons.Filled.Close, "Exit game", onExit
                )
            }
        }
    }
}

/** One pause-menu row: leading icon + label, full
 * width, left aligned - the kit's row language, not
 * a centred stock-dialog button. */
@Composable
private fun PauseRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/** One pad button's position inside the pad surface. */
private data class PadVisual(
    val spec: ControlSpec,
    val centreX: Dp,
    val centreY: Dp
)

/**
 * The D-pad as ONE surface (J4): the four pads render at
 * their layout positions, but a single pointer owns the
 * surface, so the thumb can slide between directions and
 * diagonals register. Direction comes from the angle
 * around the pads' centre pivot (8 sectors, a small dead
 * zone in the middle), which keeps working when the
 * directions are moved apart in the editor. Dpad fix:
 * the pivot is the axes' intersection, not the mean of
 * the centres; sector angles are measured on axes
 * normalised by the pad's own half-spans, so the sector
 * boundaries fall in the visual gaps between buttons
 * even when the pad is a wide rectangle; and the
 * gesture keys on a stable identity, reading live
 * centres, so a recomposition mid-slide cannot drop
 * it. The connecting cross bars do NOT draw here any
 * more - play shows only the four circles, and the
 * cross lives in the layout editor as a guide.
 */
@Composable
private fun DpadSurface(
    visuals: List<PadVisual>,
    pivotX: Dp,
    pivotY: Dp,
    inputMask: MutableState<Int>,
    onPoke: () -> Unit,
    onTick: () -> Unit,
    overlayStyle: OverlayStyle,
    modifier: Modifier = Modifier
) {
    val upBit = visuals.firstOrNull { it.spec.id == "up" }?.spec?.bit ?: 4
    val downBit =
        visuals.firstOrNull { it.spec.id == "down" }?.spec?.bit ?: 5
    val leftBit =
        visuals.firstOrNull { it.spec.id == "left" }?.spec?.bit ?: 6
    val rightBit =
        visuals.firstOrNull { it.spec.id == "right" }?.spec?.bit ?: 7
    val padBits = visuals.fold(0) { a, v -> a or (1 shl v.spec.bit) }
    var activeBits by remember { mutableStateOf(0) }
    // Stable gesture identity (dpad fix): the block
    // never restarts on a recomposition, and reads
    // the live pivot and centres, so a slide cannot
    // be dropped by a layout recomposition mid-drag.
    val currentPivotX by rememberUpdatedState(pivotX)
    val currentPivotY by rememberUpdatedState(pivotY)
    val currentVisuals by rememberUpdatedState(visuals)
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                fun bitsFor(pos: Offset): Int {
                    // Live pivot (audit F-M3): read
                    // here, not once when the gesture
                    // block starts, so an in-game
                    // layout edit takes effect mid-drag.
                    val pivot =
                        Offset(currentPivotX.toPx(), currentPivotY.toPx())
                    val dx = pos.x - pivot.x
                    val dy = pos.y - pivot.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < 14.dp.toPx()) return 0
                    // Elliptical correction (dpad
                    // fix): normalise by the pad's own
                    // half-spans, so a wide rectangular
                    // pad's sectors match its buttons.
                    val upV = currentVisuals.firstOrNull {
                        it.spec.id == "up"
                    }
                    val downV = currentVisuals.firstOrNull {
                        it.spec.id == "down"
                    }
                    val leftV = currentVisuals.firstOrNull {
                        it.spec.id == "left"
                    }
                    val rightV = currentVisuals.firstOrNull {
                        it.spec.id == "right"
                    }
                    val spanX = if (leftV != null &&
                        rightV != null
                    ) {
                        (rightV.centreX - leftV.centreX)
                            .toPx() / 2f
                    } else {
                        0f
                    }
                    val spanY = if (upV != null &&
                        downV != null
                    ) {
                        (downV.centreY - upV.centreY)
                            .toPx() / 2f
                    } else {
                        0f
                    }
                    val ndx = if (spanX > 0f) dx / spanX else dx
                    val ndy = if (spanY > 0f) dy / spanY else dy
                    val ang = Math.toDegrees(
                        atan2(ndy.toDouble(), ndx.toDouble())
                    )
                    return when {
                        ang >= -22.5 && ang < 22.5 -> 1 shl rightBit
                        ang >= 22.5 && ang < 67.5 ->
                            (1 shl downBit) or (1 shl rightBit)
                        ang >= 67.5 && ang < 112.5 -> 1 shl downBit
                        ang >= 112.5 && ang < 157.5 ->
                            (1 shl downBit) or (1 shl leftBit)
                        ang >= 157.5 || ang < -157.5 -> 1 shl leftBit
                        ang >= -157.5 && ang < -112.5 ->
                            (1 shl upBit) or (1 shl leftBit)
                        ang >= -112.5 && ang < -67.5 -> 1 shl upBit
                        else -> (1 shl upBit) or (1 shl rightBit)
                    }
                }
                fun apply(bits: Int) {
                    if (bits != activeBits) {
                        activeBits = bits
                        inputMask.value =
                            (inputMask.value and padBits.inv()) or bits
                        RetroNative.nativeSetInput(inputMask.value)
                        if (bits != 0) onTick()
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onPoke()
                    apply(bitsFor(down.position))
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change =
                                event.changes.firstOrNull {
                                    it.id == down.id
                                } ?: break
                            if (!change.pressed) break
                            apply(bitsFor(change.position))
                        }
                    } finally {
                        apply(0)
                    }
                }
            }
    ) {
        // Dpad fix: no cross bars in play - only the
        // four direction circles draw here. The cross
        // lives in the layout editor as a guide, and
        // the diagonal function is untouched: it is
        // the angle-sector code above, not the bars.
        for (v in visuals) {
            val size = v.spec.sizeDp.dp
            val pressed = activeBits and (1 shl v.spec.bit) != 0
            // Flat outline only: transparent fill
            // with a themed stroke, never a fill.
            // Pressed feedback is stroke-only:
            // a thicker, fully bright stroke.
            val themeColor = overlayStyle.colorFor(v.spec.id)
            val strokeColor = themeColor.copy(
                alpha = if (pressed) 1f else overlayStyle.opacity
            )
            val strokeWidth = if (pressed) {
                overlayStyle.strokeDp.dp + 1.dp
            } else {
                overlayStyle.strokeDp.dp
            }
            Box(
                modifier = Modifier.offset(
                    x = v.centreX - size / 2,
                    y = v.centreY - size / 2
                ).size(size)
                    .border(strokeWidth, strokeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (overlayStyle.labelsOn) {
                    Text(
                        v.spec.label,
                        color = strokeColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PadButton(
    id: String,
    label: String,
    bit: Int,
    size: Dp,
    inputMask: MutableState<Int>,
    onPoke: () -> Unit,
    onTick: () -> Unit,
    overlayStyle: OverlayStyle,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    fun setBit(on: Boolean) {
        inputMask.value = if (on) {
            inputMask.value or (1 shl bit)
        } else {
            inputMask.value and (1 shl bit).inv()
        }
        RetroNative.nativeSetInput(inputMask.value)
    }
    // Flat outline only: transparent fill with a
    // themed stroke; pressed feedback is stroke
    // brightening/thickening only, never a fill.
    val themeColor = overlayStyle.colorFor(id)
    val strokeColor = themeColor.copy(
        alpha = if (pressed) 1f else overlayStyle.opacity
    )
    val strokeWidth = if (pressed) {
        overlayStyle.strokeDp.dp + 1.dp
    } else {
        overlayStyle.strokeDp.dp
    }
    Box(
        modifier = modifier.size(size)
            .border(strokeWidth, strokeColor, CircleShape)
            .pointerInput(bit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    setBit(true)
                    onTick()
                    onPoke()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any {
                                it.id == down.id && it.pressed
                            }
                            if (!anyPressed) break
                        } while (true)
                    } finally {
                        pressed = false
                        setBit(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (overlayStyle.labelsOn) {
            Text(
                label,
                color = strokeColor,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun PadPill(
    id: String,
    label: String,
    bit: Int,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    inputMask: MutableState<Int>,
    onPoke: () -> Unit,
    onTick: () -> Unit,
    overlayStyle: OverlayStyle,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    fun setBit(on: Boolean) {
        inputMask.value = if (on) {
            inputMask.value or (1 shl bit)
        } else {
            inputMask.value and (1 shl bit).inv()
        }
        RetroNative.nativeSetInput(inputMask.value)
    }
    // Flat outline only: transparent fill with a
    // themed stroke; pressed feedback is stroke
    // brightening/thickening only, never a fill.
    val themeColor = overlayStyle.colorFor(id)
    val strokeColor = themeColor.copy(
        alpha = if (pressed) 1f else overlayStyle.opacity
    )
    val strokeWidth = if (pressed) {
        overlayStyle.strokeDp.dp + 1.dp
    } else {
        overlayStyle.strokeDp.dp
    }
    Box(
        modifier = modifier.width(width).height(height)
            .border(
                strokeWidth, strokeColor,
                RoundedCornerShape(50)
            )
            .pointerInput(bit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    setBit(true)
                    onTick()
                    onPoke()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any {
                                it.id == down.id && it.pressed
                            }
                            if (!anyPressed) break
                        } while (true)
                    } finally {
                        pressed = false
                        setBit(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (overlayStyle.labelsOn) {
            Text(
                label,
                color = strokeColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** The bezel frame colour for an adapted preset:
 * its own bezelColor hex, or a near-black default
 * when the value does not parse. */
private fun bezelFrameColor(
    preset: ShaderPack.ReflectivePreset
): Color = runCatching {
    Color(android.graphics.Color.parseColor(preset.bezelColor))
}.getOrDefault(Color(0xFF101010))

/**
 * Mega Bezel (adapted) reflection strip: a
 * vertically flipped, faded copy of the latest
 * native frame grab, at most ~2 grabs/second.
 * The grab format is an 8-byte little-endian
 * width/height header followed by RGBA bytes
 * (the same format the save-state thumbnail
 * reads). When no frame is available yet, only
 * a glossy gradient draws — play is never
 * blocked waiting for a frame. This is an
 * adapted approximation, not a RetroArch-
 * identical reproduction.
 */
@Composable
private fun BezelReflection(
    preset: ShaderPack.ReflectivePreset,
    modifier: Modifier = Modifier
) {
    var frameBitmap by remember {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            runCatching {
                val grab = RetroNative.nativeFrameGrab()
                    ?: return@runCatching
                if (grab.size < 8) return@runCatching
                val buf = java.nio.ByteBuffer.wrap(grab)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val w = buf.int
                val h = buf.int
                if (w <= 0 || h <= 0 ||
                    grab.size < 8 + w * h * 4
                ) {
                    return@runCatching
                }
                val pixels = IntArray(w * h)
                var i = 8
                for (p in pixels.indices) {
                    val r = grab[i].toInt() and 0xFF
                    val g = grab[i + 1].toInt() and 0xFF
                    val b = grab[i + 2].toInt() and 0xFF
                    pixels[p] = (0xFF shl 24) or
                        (r shl 16) or (g shl 8) or b
                    i += 4
                }
                frameBitmap = android.graphics.Bitmap.createBitmap(
                    pixels, w, h,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
            }
        }
    }
    val frameColor = bezelFrameColor(preset)
    Box(
        modifier = modifier.background(frameColor)
    ) {
        val bmp = frameBitmap
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        scaleY = -1f
                        alpha = preset.reflection
                    }
            )
            // Fade the reflection out toward the bottom.
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush
                            .verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    frameColor
                                )
                            )
                    )
            )
        } else {
            // No frame yet: glossy gradient only.
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush
                            .verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.14f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.30f)
                                )
                            )
                    )
            )
        }
    }
}
