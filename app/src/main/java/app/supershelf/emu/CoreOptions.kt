package app.supershelf.emu

import app.supershelf.data.AppStore

/**
 * Core options (Phase J, J7): the settings each libretro
 * core actually accepts, taken from the cores' own option
 * tables (2026-10-24 research, scratch/j7/CORE_OPTIONS.md).
 * Values are the exact strings the cores expect via
 * GET_VARIABLE; [labels] holds display text only where it
 * differs from the value. Options are stored per CONSOLE
 * (a GB page and a GBA page can set different mGBA color
 * correction) under coreOpt:<console>:<key>, and apply
 * from the next game launched. The tables here are the
 * meaningful options, not every static key: visibility
 * gates, runtime-populated lists, Sega CD keys and
 * light-gun/mouse options are left out by design, and the
 * tables are structured to grow. The PlayStation table
 * (2026-10-25) is curated from the pinned SwanStation
 * clone's src/libretro/libretro_core_options.h (113 keys):
 * only options that work through this frontend's software
 * framebuffer path are mapped. Left out by design:
 * GPU_Renderer (a hardware renderer would ask the shim
 * for a hardware context it does not provide), MSAA /
 * downsampling / shader precompilation (hardware-only),
 * BIOS path choices (the app owns BIOS import), memory
 * card types (the app owns the .srm card), multitap /
 * per-controller fine-tuning (no such inputs here),
 * texture replacements (no pack import flow), run-ahead,
 * recompiler internals, log level, and the 50-100 entry
 * numeric lists (custom aspect, offsets, axis scales),
 * which are trimmed to a short sensible subset where
 * the option itself is worth keeping. The DS table
 * (2026-10-25) is curated from the pinned melonDS
 * clone's src/libretro/libretro_core_options.h: only
 * options that work through this frontend's software
 * framebuffer path are mapped. Forced, never
 * user-set (see forcedPairs): console mode (DS-only),
 * touch mode (Touch), screen layout (Top/Bottom),
 * screen gap (0). Left out by design: DSi-only
 * options, the OpenGL renderer/resolution (no
 * hardware context in this shim), hybrid layouts
 * (the frontend owns arrangement), and swap-screen
 * mode (the frontend owns swap).
 */
data class CoreOption(
    val key: String,
    val label: String,
    val values: List<String>,
    val labels: Map<String, String> = emptyMap(),
    val default: String
) {
    fun display(value: String): String = labels[value] ?: value
}

object CoreOptions {
    private fun range(a: Int, b: Int, step: Int): List<String> =
        (a..b step step).map { it.toString() }

    private fun opt(
        key: String,
        label: String,
        values: List<String>,
        default: String,
        labels: Map<String, String> = emptyMap()
    ) = CoreOption(key, label, values, labels, default)

    private val snes9x = listOf(
        opt(
            "snes9x_region", "Console Region",
            listOf("auto", "ntsc", "pal"), "auto",
            mapOf("auto" to "Auto", "ntsc" to "NTSC", "pal" to "PAL")
        ),
        opt(
            "snes9x_aspect", "Preferred Aspect Ratio",
            listOf("4:3", "4:3 scaled", "uncorrected", "auto", "ntsc", "pal"),
            "4:3",
            mapOf(
                "4:3 scaled" to "4:3 (Preserved)",
                "uncorrected" to "Uncorrected",
                "auto" to "Auto", "ntsc" to "NTSC", "pal" to "PAL"
            )
        ),
        opt(
            "snes9x_overscan", "Crop Overscan",
            listOf("enabled", "disabled", "auto"), "enabled",
            mapOf("auto" to "Auto")
        ),
        opt(
            "snes9x_gfx_hires", "Enable Hi-Res Mode",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_hires_blend", "Hi-Res Blending",
            listOf("disabled", "merge", "blur"), "disabled",
            mapOf("merge" to "Merge", "blur" to "Blur")
        ),
        opt(
            "snes9x_blargg", "Blargg NTSC Filter",
            listOf(
                "disabled", "monochrome", "rf", "composite",
                "s-video", "rgb"
            ),
            "disabled",
            mapOf("s-video" to "S-Video")
        ),
        opt(
            "snes9x_audio_interpolation", "Audio Interpolation",
            listOf("gaussian", "cubic", "sinc", "none", "linear"),
            "gaussian",
            mapOf(
                "gaussian" to "Gaussian", "cubic" to "Cubic",
                "sinc" to "Sinc", "none" to "None",
                "linear" to "Linear"
            )
        ),
        opt(
            "snes9x_up_down_allowed", "Allow Opposing Directions",
            listOf("disabled", "enabled"), "disabled"
        ),
        opt(
            "snes9x_overclock_superfx", "SuperFX Overclocking",
            listOf(
                "50%", "60%", "70%", "80%", "90%", "100%",
                "150%", "200%", "250%", "300%", "350%", "400%",
                "450%", "500%"
            ),
            "100%"
        ),
        opt(
            "snes9x_overclock_cycles", "Reduce Slowdown (Hack, Unsafe)",
            listOf("disabled", "light", "compatible", "max"), "disabled",
            mapOf(
                "light" to "Light", "compatible" to "Compatible",
                "max" to "Max"
            )
        ),
        opt(
            "snes9x_reduce_sprite_flicker",
            "Reduce Flickering (Hack, Unsafe)",
            listOf("disabled", "enabled"), "disabled"
        ),
        opt(
            "snes9x_randomize_memory", "Randomize Memory (Unsafe)",
            listOf("disabled", "enabled"), "disabled"
        ),
        opt(
            "snes9x_block_invalid_vram_access",
            "Block Invalid VRAM Access",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_echo_buffer_hack", "Echo Buffer Hack (Unsafe)",
            listOf("disabled", "enabled"), "disabled"
        ),
        opt(
            "snes9x_layer_1", "Show Layer 1",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_layer_2", "Show Layer 2",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_layer_3", "Show Layer 3",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_layer_4", "Show Layer 4",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_layer_5", "Show Sprite Layer",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_gfx_clip", "Enable Graphic Clip Windows",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_gfx_transp", "Enable Transparency Effects",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_sndchan_1", "Enable Sound Channel 1",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_sndchan_2", "Enable Sound Channel 2",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_sndchan_3", "Enable Sound Channel 3",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_sndchan_4", "Enable Sound Channel 4",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_sndchan_5", "Enable Sound Channel 5",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_sndchan_6", "Enable Sound Channel 6",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_sndchan_7", "Enable Sound Channel 7",
            listOf("enabled", "disabled"), "enabled"
        ),
        opt(
            "snes9x_sndchan_8", "Enable Sound Channel 8",
            listOf("enabled", "disabled"), "enabled"
        )
    )

    fun forConsole(console: String): List<CoreOption> = when (console) {
        "snes" -> snes9x
        else -> emptyList()
    }

    private fun settingKey(console: String, key: String) =
        "coreOpt:$console:$key"

    fun storedValue(
        store: AppStore,
        console: String,
        option: CoreOption
    ): String {
        val raw = store.setting(settingKey(console, option.key), "")
        return if (raw in option.values) raw else option.default
    }

    fun setValue(
        store: AppStore,
        console: String,
        key: String,
        value: String
    ) {
        store.putSetting(settingKey(console, key), value)
    }

    /** "key=value" pairs for the native side, which answers
     * GET_VARIABLE from exactly this map. */
    fun storedPairs(store: AppStore, console: String): Array<String> =
        (forConsole(console).map { option ->
            option.key + "=" + storedValue(store, console, option)
        } + forcedPairs(console)).toTypedArray()

    /** Pairs the frontend owns outright for a
     * console, appended after the user-facing ones.
     * DS forces the core onto its DS-only,
     * frontend-arranged path: console mode DS (DSi
     * deferred), Touch pointer mode (the default
     * Mouse would ignore the touch screen),
     * Top/Bottom layout, and zero screen gap (the
     * frontend draws the hinge gap itself). These
     * are never user-set, so no row exposes them. */
    private fun forcedPairs(console: String): List<String> = emptyList()
}
