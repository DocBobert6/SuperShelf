package app.supershelf.emu

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Box art from the libretro thumbnail server (Phase J
 * amendment, Robert 2026-10-24): the public distribution
 * RetroArch itself uses - per-system folders, box art in
 * Named_Boxarts and title screens in Named_Titles, PNGs
 * named by the No-Intro game name. Fetched lazily, one
 * game at a time, cached in app storage beside nothing
 * else; never bulk-mirrored. A ROM whose name doesn't
 * match the No-Intro form simply gets no art (the tile
 * falls back to title art, then a styled placeholder) -
 * renaming the library entry to the proper title is the
 * fix, and J2's rename is right there.
 */
object BoxArt {
    private const val SERVER = "https://thumbnails.libretro.com"
    private const val MAX_ART_BYTES = 4 * 1024 * 1024

    /** The server's per-system folder names. */
    fun folderFor(system: String): String? = when (system) {
        "snes" -> "Nintendo - Super Nintendo Entertainment System"
        else -> null
    }

    /** The server's file-name form of a game title: the
     * libretro convention replaces filename-hostile
     * characters with underscores. */
    fun serverName(title: String): String =
        title.replace(Regex("[&*/:`<>?\\\\|\"]"), "_").trim()

    fun artDir(context: Context): File =
        File(context.filesDir, "boxart").apply { mkdirs() }

    fun artFile(context: Context, game: GameEntry): File =
        File(artDir(context), game.id + "-box.png")

    /** A user-chosen cover (beta.7): shown first, and
     * never touched by server fetches, which write
     * -box.png only. */
    fun customFile(context: Context, game: GameEntry): File =
        File(artDir(context), game.id + "-custom.png")

    fun titleFile(context: Context, game: GameEntry): File =
        File(artDir(context), game.id + "-title.png")

    private fun urlFor(folder: String, kind: String, name: String): String {
        fun enc(s: String): String =
            URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        return "$SERVER/${enc(folder)}/$kind/${enc(name)}.png"
    }

    /** One download's outcome, so bulk search can tell "no
     * match" (the server said 404) apart from "failed" (a
     * network or bad-data error). Single fetch keeps its old
     * Boolean shape by mapping OK to true and the rest to
     * false. */
    private enum class DownloadStatus { OK, NOT_FOUND, FAILED }

    /** Downloads one image; OK when it landed and is a
     * plausible PNG. Blocking - call off the main thread. */
    private fun download(url: String, dest: File): DownloadStatus {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "SuperShelf")
            if (conn.responseCode == 404) return DownloadStatus.NOT_FOUND
            if (conn.responseCode != 200) return DownloadStatus.FAILED
            var total = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_ART_BYTES) {
                            throw IllegalStateException("art too large")
                        }
                        out.write(buf, 0, n)
                    }
                }
            }
            val bytes = tmp.readBytes()
            val isPng = bytes.size > 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()
            if (!isPng) {
                tmp.delete()
                return DownloadStatus.FAILED
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
            DownloadStatus.OK
        }.getOrElse {
            runCatching { tmp.delete() }
            DownloadStatus.FAILED
        }
    }

    /**
     * Fetches whatever art exists for [game]: box art
     * first, then the title screen. Returns which kinds
     * are now on disk. Blocking - call off the main
     * thread. Already-cached files are kept, not
     * re-fetched, unless [force].
     */
    fun fetch(
        context: Context,
        game: GameEntry,
        force: Boolean = false
    ): Pair<Boolean, Boolean> {
        val folder = folderFor(game.system) ?: return false to false
        val name = serverName(game.title)
        val box = artFile(context, game)
        val title = titleFile(context, game)
        val hasBox = if (force || !box.exists()) {
            download(urlFor(folder, "Named_Boxarts", name), box) ==
                DownloadStatus.OK
        } else {
            true
        }
        val hasTitle = if (force || !title.exists()) {
            download(urlFor(folder, "Named_Titles", name), title) ==
                DownloadStatus.OK
        } else {
            true
        }
        return (hasBox || box.exists()) to (hasTitle || title.exists())
    }

    /** Bulk cover search, one run over every game in [games].
     * Sequential, one game at a time, same per-image cap as
     * single fetch — never a bulk mirror of the source.
     * Blocking - call off the main thread; [onProgress] is
     * called off the main thread too, before each game and
     * once more at the end (done == total). [isCancelled]
     * is checked before each game; a cancel finishes the
     * current game, stops, and still returns a summary with
     * [BulkResult.cancelled] set. Rules that never change:
     * a game with a user custom cover is skipped and never
     * overwritten (its server box art is not even fetched);
     * a game that already has box art is counted as
     * already-had and not re-fetched, so a run is
     * resumable and re-runnable without duplicating art. */
    data class BulkResult(
        val total: Int,
        val processed: Int,
        val found: Int,
        val alreadyHad: Int,
        val skippedCustom: Int,
        val titleOnly: Int,
        val noMatch: Int,
        val failed: Int,
        val failedTitles: List<String>,
        val cancelled: Boolean
    ) {
        fun summary(): String {
            val head = if (cancelled) {
                "Cancelled after $processed of $total. "
            } else {
                "Done: $processed of $total. "
            }
            return head +
                "Found $found, already had $alreadyHad, " +
                "skipped $skippedCustom custom, " +
                "title screen only $titleOnly, " +
                "no match $noMatch, failed $failed." +
                if (failedTitles.isEmpty()) {
                    ""
                } else {
                    " Failed: " + failedTitles.joinToString(", ") + "."
                }
        }
    }

    fun fetchAll(
        context: Context,
        games: List<GameEntry>,
        onProgress: (done: Int, total: Int, title: String) -> Unit,
        isCancelled: () -> Boolean
    ): BulkResult {
        var found = 0
        var alreadyHad = 0
        var skippedCustom = 0
        var titleOnly = 0
        var noMatch = 0
        var failed = 0
        val failedTitles = ArrayList<String>()
        var processed = 0
        var cancelled = false
        for (game in games) {
            if (isCancelled()) {
                cancelled = true
                break
            }
            onProgress(processed, games.size, game.title)
            if (customFile(context, game).exists()) {
                skippedCustom++
                processed++
                continue
            }
            val box = artFile(context, game)
            if (box.exists()) {
                alreadyHad++
                processed++
                continue
            }
            val folder = folderFor(game.system)
            if (folder == null) {
                noMatch++
                processed++
                continue
            }
            val name = serverName(game.title)
            when (download(urlFor(folder, "Named_Boxarts", name), box)) {
                DownloadStatus.OK -> found++
                DownloadStatus.FAILED -> {
                    failed++
                    failedTitles += game.title
                }
                DownloadStatus.NOT_FOUND -> {
                    // Box art missing: a title screen is still
                    // useful tile art, but it is not a cover.
                    // Its own failure does not turn a no-match
                    // into a failure.
                    val title = titleFile(context, game)
                    val titleOk = if (title.exists()) {
                        true
                    } else {
                        download(
                            urlFor(folder, "Named_Titles", name), title
                        ) == DownloadStatus.OK
                    }
                    if (titleOk) titleOnly++ else noMatch++
                }
            }
            processed++
        }
        onProgress(processed, games.size, "")
        return BulkResult(
            total = games.size,
            processed = processed,
            found = found,
            alreadyHad = alreadyHad,
            skippedCustom = skippedCustom,
            titleOnly = titleOnly,
            noMatch = noMatch,
            failed = failed,
            failedTitles = failedTitles,
            cancelled = cancelled
        )
    }
}
