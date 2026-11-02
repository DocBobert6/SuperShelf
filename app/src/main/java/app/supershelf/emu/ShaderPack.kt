package app.supershelf.emu

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Mega Bezel reflective presets, adapted — NOT bundled, NOT RetroArch.
 *
 * Mega Bezel is a RetroArch slang shader pack; its .slangp presets do
 * not run in this app's native CRT chain. What Super Shelf ships is
 * an adapted layer: a downloadable preset pack (shaders.json on a
 * dedicated `shaders` release in this repo) whose presets carry Mega
 * Bezel's own preset names, credited to upstream, and whose values
 * drive (a) the native CRT chain (scan / blur / curve / round, the
 * same values the built-in Violet preset uses as a base) and
 * (b) a Compose bezel frame + reflection around the game picture
 * in GameActivity. The UI must label these "Mega Bezel (adapted)"
 * and must never claim RetroArch-identical output.
 *
 * Trust mirrors CoreDownload exactly: the manifest is installed
 * only after its detached ECDSA P-256 signature verifies against
 * the pinned shaders public key, and the installed manifest's
 * SHA-256 is recorded so a later load can re-check it. A missing
 * or invalid signature fails closed — the app falls back to the
 * built-in Violet CRT look and never refuses to launch a game
 * over a shader. The private shaders-signing key is a repo
 * secret only; it is never in this repo.
 *
 * Licence gate (must be settled before a download URL ships):
 * verify the Mega Bezel pack's redistribution terms. If upstream
 * does not permit re-hosting, this manifest must point at
 * upstream's own distribution instead of a re-hosted copy.
 * The pack in v1 contains preset *values* (names + numbers),
 * not upstream's shader code or bezel artwork — the bezel
 * frame and reflection are drawn by this app.
 */
object ShaderPack {

    const val MANIFEST_URL =
        "https://github.com/DocBobert6/SuperShelf" +
            "/releases/download/shaders/shaders.json"
    const val MANIFEST_SIGNATURE_URL =
        "https://github.com/DocBobert6/SuperShelf" +
            "/releases/download/shaders/shaders.json.sig"

    /** Pinned shaders public key (X.509 DER, base64). Generated
     * for Super Shelf; the private half is a repo secret. */
    const val MANIFEST_PUBLIC_KEY_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAERCPX1ghJuJPpPyZF9cg6wityBlg3stfMdom2TSY84an6tmeDUaJ1UQUYPKJHJForVcsgJXl4oCwfO9tgro5Fzg=="

    data class ReflectivePreset(
        val name: String,
        /** Upstream credit line shown in the picker, e.g.
         * "Mega Bezel preset, adapted for Super Shelf". */
        val credit: String,
        /** Bezel frame colour as ARGB hex, e.g. "#FF101010". */
        val bezelColor: String,
        /** Reflection strength 0..1 (how visible the flipped
         * reflection in the bezel is). */
        val reflection: Float,
        /** CRT chain values, same meaning as ShaderPreset. */
        val scan: Float,
        val blur: Float,
        val curveOn: Boolean,
        val curveAmount: Int,
        val roundOn: Boolean,
        val roundAmount: Int
    )

    fun packDir(context: Context): File =
        File(context.filesDir, "shaders-downloaded").apply { mkdirs() }

    fun manifestFile(context: Context): File =
        File(packDir(context), "shaders.json")

    fun manifestShaFile(context: Context): File =
        File(packDir(context), "shaders.json.sha256")

    fun parseManifest(json: String): List<ReflectivePreset> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("presets") ?: return emptyList()
        val out = ArrayList<ReflectivePreset>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += ReflectivePreset(
                name = o.getString("name"),
                credit = o.optString(
                    "credit",
                    "Mega Bezel preset, adapted for Super Shelf"
                ),
                bezelColor = o.optString("bezelColor", "#FF101010"),
                reflection = o.optDouble("reflection", 0.5)
                    .toFloat().coerceIn(0f, 1f),
                scan = o.optDouble("scan", 0.30).toFloat(),
                blur = o.optDouble("blur", 0.10).toFloat(),
                curveOn = o.optBoolean("curveOn", true),
                curveAmount = o.optInt("curveAmount", 30),
                roundOn = o.optBoolean("roundOn", true),
                roundAmount = o.optInt("roundAmount", 20)
            )
        }
        return out
    }

    /** Installed presets, or empty when no verified pack is
     * installed. Never throws. */
    fun installed(context: Context): List<ReflectivePreset> {
        val file = manifestFile(context)
        if (!file.exists()) return emptyList()
        val expected = runCatching {
            manifestShaFile(context).readText().trim()
        }.getOrNull() ?: return emptyList()
        return runCatching {
            if (CoreDownload.sha256Hex(file) != expected) return emptyList()
            parseManifest(file.readText())
        }.getOrDefault(emptyList())
    }

    fun presetByName(context: Context, name: String): ReflectivePreset? =
        installed(context).firstOrNull { it.name == name }

    /** App-wide default preset setting. Blank = not set by the
     * user; [VIOLET_KEY] = the user explicitly chose the built-in
     * Violet CRT fallback instead of a Mega Bezel preset. */
    const val DEFAULT_KEY = "defaultShaderPreset"

    /** Sentinel stored when the user explicitly picks the built-in
     * Violet CRT look. It is never a preset name and never shown
     * as one; the UI labels it "Violet (built-in CRT fallback)". */
    const val VIOLET_KEY = "VIOLET"

    /** Per-game shader setting key (blank = use the default). */
    fun perGameKey(gameId: String): String = "gameShaderPreset:$gameId"

    /** Only presets with a reflection actually count as
     * reflective. A preset with reflection 0 is never offered
     * as a Mega Bezel (adapted) — with reflections choice, and
     * never becomes a default. */
    fun reflectivePresets(
        installed: List<ReflectivePreset>
    ): List<ReflectivePreset> = installed.filter { it.reflection > 0f }

    /** The default's effective preset name, or null for Violet:
     * an explicit default wins when it is still an installed
     * reflective preset; otherwise, when a pack is installed,
     * the first reflective preset is the default; with no pack,
     * the fallback is Violet. An explicit Violet choice is
     * honoured even when a pack is installed. */
    fun defaultEffectiveName(
        store: app.supershelf.data.AppStore,
        installed: List<ReflectivePreset>
    ): String? {
        val refl = reflectivePresets(installed)
        val def = store.setting(DEFAULT_KEY, "")
        if (def == VIOLET_KEY) return null
        if (def.isNotBlank() && refl.any { it.name == def }) return def
        return refl.firstOrNull()?.name
    }

    /** Effective preset name for one game, or null for Violet:
     * a per-game choice wins when it is still an installed
     * reflective preset; a per-game choice naming a preset that
     * is no longer installed (or is non-reflective) falls
     * through to the default rather than silently to Violet;
     * blank per-game means "use the default". */
    fun effectivePresetName(
        store: app.supershelf.data.AppStore,
        gameId: String?,
        installed: List<ReflectivePreset>
    ): String? {
        if (gameId == null) return defaultEffectiveName(store, installed)
        val refl = reflectivePresets(installed)
        val per = store.setting(perGameKey(gameId), "")
        if (per == VIOLET_KEY) return null
        if (per.isNotBlank() && refl.any { it.name == per }) return per
        return defaultEffectiveName(store, installed)
    }

    fun effectivePreset(
        store: app.supershelf.data.AppStore,
        gameId: String?,
        installed: List<ReflectivePreset>
    ): ReflectivePreset? {
        val name = effectivePresetName(store, gameId, installed)
            ?: return null
        return installed.firstOrNull { it.name == name }
    }

    /** Download + install into [context]'s pack dir. Returns the
     * installed presets, or a failure naming the refusal reason. */
    suspend fun downloadTo(context: Context): Result<List<ReflectivePreset>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val manifestBytes = fetchBytes(MANIFEST_URL)
                val sigBytes = fetchBytes(MANIFEST_SIGNATURE_URL)
                if (!CoreDownload.verifyManifestSignature(
                        manifestBytes, sigBytes, MANIFEST_PUBLIC_KEY_BASE64
                    )
                ) {
                    throw IllegalStateException(
                        "Shader manifest signature is missing or invalid; " +
                            "refusing to install it"
                    )
                }
                val presets = parseManifest(manifestBytes.decodeToString())
                val dir = packDir(context)
                val tmp = File(dir, "shaders.json.tmp")
                tmp.writeBytes(manifestBytes)
                val dest = manifestFile(context)
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                manifestShaFile(context)
                    .writeText(CoreDownload.sha256Hex(dest))
                presets
            }
        }

    fun remove(context: Context) {
        manifestFile(context).delete()
        manifestShaFile(context).delete()
    }

    private fun fetchBytes(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException(
                    "Shaders release returned ${conn.responseCode} for $url"
                )
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
