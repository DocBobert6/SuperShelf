package app.supershelf.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import app.supershelf.data.AppStore
import app.supershelf.emu.BoxArt
import app.supershelf.emu.CoreDownload
import app.supershelf.emu.CoreOptions
import app.supershelf.emu.CoreStore
import app.supershelf.emu.GameActivity
import app.supershelf.emu.GameEntry
import app.supershelf.emu.GameLayout
import app.supershelf.emu.GameLibrary
import app.supershelf.emu.OutlineTheme
import app.supershelf.emu.OverlayStyleStore
import app.supershelf.emu.SaveStates
import app.supershelf.emu.ShaderPack
import app.supershelf.emu.ZipRomImport
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Super Shelf's home screen — the SNES library, nothing else.
 * No browser, no other consoles. Import copies ROMs into app
 * storage (originals never touched); delete removes only the
 * copy, its saves and its art. Covers come from libretro
 * thumbnails in v1 (plus your own imported cover, which server
 * fetches never overwrite); multi-source search is Phase 2 and
 * the picker below is built so a second source can be added
 * without changing the tile or detail UI. The Snes9x core and
 * the Mega Bezel (adapted) preset pack are both downloadable,
 * never bundled; a missing core shows a plain download card
 * and never a dead Play button.
 */
@Composable
fun LibraryScreen(store: AppStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var games by remember { mutableStateOf(GameLibrary.load(store)) }
    var query by remember { mutableStateOf("") }
    var favOnly by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf("Title") }
    var grid by remember { mutableStateOf(true) }
    var sortMenu by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<GameEntry?>(null) }
    var showLayout by remember { mutableStateOf(false) }
    var layoutGameId by remember { mutableStateOf<String?>(null) }
    var coreStatus by remember {
        mutableStateOf(CoreDownload.status(context, "snes"))
    }
    var coreBusy by remember { mutableStateOf(false) }
    var coreMsg by remember { mutableStateOf("") }
    var shaderBusy by remember { mutableStateOf(false) }
    var shaderMsg by remember { mutableStateOf("") }
    var shaderPresets by remember {
        mutableStateOf(ShaderPack.installed(context))
    }
    var shaderTick by remember { mutableStateOf(0) }
    var artTick by remember { mutableStateOf(0) }
    // Bulk cover search state (see startBulkCovers): one run,
    // sequential, cancellable, with a summary at the end.
    var bulkRunning by remember { mutableStateOf(false) }
    var bulkDone by remember { mutableStateOf(0) }
    var bulkTotal by remember { mutableStateOf(0) }
    var bulkCurrent by remember { mutableStateOf("") }
    var bulkSummary by remember { mutableStateOf("") }
    val bulkCancelled = remember { AtomicBoolean(false) }
    // Shown when Play is tapped without a core: the core
    // card may be scrolled off-screen, so a missing core
    // must never be a silent dead tap.
    var coreDownloadPrompt by remember { mutableStateOf(false) }

    fun refresh() {
        games = GameLibrary.load(store)
        coreStatus = CoreDownload.status(context, "snes")
        shaderPresets = ShaderPack.installed(context)
    }

    fun displayName(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0) c.getString(i) else null
                    } else null
                }
        }.getOrNull() ?: uri.lastPathSegment ?: "game"
    }

    fun importBytes(
        bytes: ByteArray,
        display: String,
        ext: String
    ): String? {
        if (ext.lowercase() !in listOf("smc", "sfc")) return null
        val fileName = GameLibrary.safeFileName(display)
        val dir = File(CoreStore.romDir(context), "snes").apply { mkdirs() }
        if (games.any { it.fileName == fileName }) {
            return "Already in library: $display"
        }
        File(dir, fileName).writeBytes(bytes)
        GameLibrary.add(
            store,
            title = display.substringBeforeLast('.'),
            system = "snes",
            fileName = fileName
        )
        return null
    }

    fun importUri(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val name = displayName(uri)
                val ext = name.substringAfterLast('.', "").lowercase()
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Could not open $name")
                val msgs = mutableListOf<String>()
                if (ext == "zip") {
                    ZipRomImport.forEachEntry(input, "snes") { fn, disp, _, bytes ->
                        importBytes(bytes, disp, disp.substringAfterLast('.'))
                            ?.let { msgs += it }
                    }
                } else {
                    val bytes = input.use { it.readBytes() }
                    importBytes(bytes, name, ext)?.let { msgs += it }
                }
                withContext(Dispatchers.Main) {
                    refresh()
                    if (msgs.isNotEmpty()) {
                        Toast.makeText(context, msgs.first(), Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, "Import failed: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> uris.forEach { importUri(it) } }
    val pickCover = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val game = detail ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() } ?: return@runCatching
                // Store as the custom cover: server fetches write
                // -box.png only, so this file is never overwritten.
                BoxArt.customFile(context, game).writeBytes(bytes)
            }
            withContext(Dispatchers.Main) { artTick++ }
        }
    }
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            fun walk(dir: DocumentFile, depth: Int) {
                if (depth > 4) return
                for (child in dir.listFiles()) {
                    if (child.isDirectory) {
                        walk(child, depth + 1)
                    } else {
                        val name = child.name ?: continue
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in listOf("smc", "sfc", "zip")) {
                            runCatching {
                                val input = context.contentResolver
                                    .openInputStream(child.uri) ?: return@runCatching
                                if (ext == "zip") {
                                    ZipRomImport.forEachEntry(input, "snes") { _, disp, _, bytes ->
                                        importBytes(bytes, disp, disp.substringAfterLast('.'))
                                    }
                                } else {
                                    importBytes(input.use { it.readBytes() }, name, ext)
                                }
                            }
                        }
                    }
                }
            }
            val root = DocumentFile.fromTreeUri(context, uri)
            if (root != null) walk(root, 0)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun play(game: GameEntry) {
        if (coreStatus != CoreDownload.Status.DOWNLOADED) {
            coreMsg = "Download the Snes9x core to play."
            coreDownloadPrompt = true
            return
        }
        GameLibrary.touchPlayed(store, game.id)
        val intent = Intent(context, GameActivity::class.java)
            .putExtra(GameActivity.EXTRA_ROM_PATH, game.romFile(context).absolutePath)
            .putExtra(GameActivity.EXTRA_GAME_ID, game.id)
            .putExtra(GameActivity.EXTRA_GAME_TITLE, game.title)
        context.startActivity(intent)
        refresh()
    }

    fun downloadCore() {
        if (coreBusy) return
        coreBusy = true
        coreMsg = ""
        scope.launch {
            val result = runCatching {
                val manifest = CoreDownload.fetchManifest().getOrThrow()
                val entry = CoreDownload.entryFor(manifest, "snes")
                    ?: throw IllegalStateException(
                        "No Snes9x core in the cores manifest yet. " +
                            "The cores release has not been published — " +
                            "this is expected until the first cores " +
                            "release is signed and published."
                    )
                CoreDownload.download(context, entry).getOrThrow()
            }
            withContext(Dispatchers.Main) {
                coreBusy = false
                if (result.isFailure) {
                    coreMsg = result.exceptionOrNull()?.message
                        ?: "Core download failed."
                }
                refresh()
            }
        }
    }

    fun downloadShaders() {
        if (shaderBusy) return
        shaderBusy = true
        shaderMsg = ""
        scope.launch {
            val result = ShaderPack.downloadTo(context)
            withContext(Dispatchers.Main) {
                shaderBusy = false
                if (result.isFailure) {
                    shaderMsg = result.exceptionOrNull()?.message
                        ?: "Shader pack download failed."
                }
                refresh()
            }
        }
    }

    fun startBulkCovers() {
        if (bulkRunning) return
        val snapshot = games.toList()
        if (snapshot.isEmpty()) {
            bulkSummary = "No games yet — import a ROM first."
            return
        }
        bulkCancelled.set(false)
        bulkRunning = true
        bulkDone = 0
        bulkTotal = snapshot.size
        bulkCurrent = ""
        bulkSummary = ""
        scope.launch(Dispatchers.IO) {
            val result = BoxArt.fetchAll(
                context,
                snapshot,
                onProgress = { done, total, title ->
                    // fetchAll calls back off the main thread;
                    // Compose state must update on Main.
                    scope.launch(Dispatchers.Main) {
                        bulkDone = done
                        bulkTotal = total
                        bulkCurrent = title
                    }
                },
                isCancelled = { bulkCancelled.get() }
            )
            withContext(Dispatchers.Main) {
                bulkRunning = false
                bulkSummary = result.summary()
                artTick++
                refresh()
            }
        }
    }

    fun cancelBulkCovers() {
        // Flag only, never job.cancel(): cancelling the job
        // would kill the summary block below, leaving the
        // UI stuck on "running". The flag stops fetchAll
        // before the next game and the summary still runs.
        bulkCancelled.set(true)
    }

    val favIds = GameLibrary.favorites(store)
    val shown = games.filter { game ->
        (!favOnly || game.id in favIds) &&
            (query.isBlank() || game.title.contains(query, ignoreCase = true))
    }.let { list ->
        when (sort) {
            "Added" -> list.sortedByDescending { it.addedAt }
            "Last played" -> list.sortedByDescending { it.lastPlayedAt }
            else -> list.sortedBy { it.title.lowercase() }
        }
    }

    // Single scrollable home list: header, search, all
    // Downloads & settings cards, and the library live in
    // ONE LazyColumn, so the whole home scrolls together
    // in portrait and landscape. Never nest a grid/list
    // inside a scrolling Column again — that was the bug:
    // a plain Column with no verticalScroll, followed by a
    // fillMaxSize library list with no weight, clipped the
    // library and left the settings cards unscrollable.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridColumns = maxOf(1, (maxWidth / 140.dp).toInt())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
        item {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Super Shelf", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Super Nintendo library • ${games.size} games • Plays .smc, .sfc, .zip",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(onClick = { grid = !grid }) {
                Text(if (grid) "List" else "Grid")
            }
        }
        }
        item {
        // Search + sort + favourites
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search library") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = favOnly,
                onClick = { favOnly = !favOnly },
                label = { Text("★") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                OutlinedButton(onClick = { sortMenu = true }) { Text(sort) }
                DropdownMenu(
                    expanded = sortMenu,
                    onDismissRequest = { sortMenu = false }
                ) {
                    listOf("Title", "Added", "Last played").forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s) },
                            onClick = { sort = s; sortMenu = false }
                        )
                    }
                }
            }
        }
        }
        item {
        Column {
        // Downloads & settings — one grouped section, in the
        // order a new user needs them: import, then the two
        // downloads that gate play and shaders, then covers,
        // then the global overlay. Per-game versions of the
        // shader and overlay controls live in the game detail
        // dialog and use the same components, so the two
        // places can never disagree about behaviour.
        Text(
            "Downloads & settings",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Import row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { pickFiles.launch("*/*") }) { Text("Import ROMs") }
            OutlinedButton(onClick = { pickFolder.launch(null) }) {
                Text("Import folder")
            }
        }
        }
        }
        item {
        // Core card (never a dead Play button)
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    when (coreStatus) {
                        CoreDownload.Status.DOWNLOADED ->
                            "Snes9x core: installed"
                        CoreDownload.Status.CORRUPT ->
                            "Snes9x core: corrupt — re-download it"
                        else -> "Snes9x core: not downloaded"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "The core is downloaded, verified, and never bundled " +
                        "in the app. Without it, games cannot launch.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (coreBusy) LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (coreMsg.isNotBlank()) {
                    Text(coreMsg, style = MaterialTheme.typography.bodySmall)
                }
                if (coreStatus != CoreDownload.Status.DOWNLOADED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { downloadCore() }, enabled = !coreBusy) {
                        Text("Download the core to play")
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        CoreDownload.removeDownloaded(context, "snes")
                        refresh()
                    }) { Text("Delete core") }
                }
            }
        }
        }
        item {
        // Shader pack card
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    if (shaderPresets.isEmpty()) {
                        "Mega Bezel (adapted): not downloaded"
                    } else {
                        "Mega Bezel (adapted): ${shaderPresets.size} presets"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Reflective presets, adapted for this app's native " +
                        "CRT chain — not RetroArch, and not bundled. " +
                        "Without a pack, games use the built-in CRT look.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (shaderBusy) LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (shaderMsg.isNotBlank()) {
                    Text(shaderMsg, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { downloadShaders() }, enabled = !shaderBusy) {
                        Text(
                            if (shaderPresets.isEmpty()) "Download presets"
                            else "Re-download presets"
                        )
                    }
                    if (shaderPresets.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            ShaderPack.remove(context)
                            refresh()
                        }) { Text("Delete pack") }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // The app-wide default: the same picker the
                // per-game dialog uses, with gameId = null.
                // Keyed on shaderTick so a tap here redraws
                // with the new selection immediately.
                key(shaderTick) {
                    ShaderPickerSection(
                        store = store,
                        presets = shaderPresets,
                        gameId = null,
                        shaderBusy = shaderBusy,
                        onDownloadShaders = { downloadShaders() },
                        onChanged = { shaderTick++; refresh() }
                    )
                }
            }
        }
        }
        item {
        // Covers card — bulk search lives here, and only
        // here, so there is exactly one place to run it.
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Covers",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Find covers for every game in one run, from " +
                        "libretro thumbnails. Your own custom covers " +
                        "are never overwritten — custom-covered games " +
                        "are skipped and counted in the summary. " +
                        "One game at a time, same as a single search.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (bulkRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "$bulkDone of $bulkTotal" +
                            if (bulkCurrent.isNotBlank()) " — $bulkCurrent"
                            else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (bulkSummary.isNotBlank()) {
                    Text(
                        bulkSummary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { startBulkCovers() },
                        enabled = !bulkRunning && games.isNotEmpty()
                    ) { Text("Find all covers") }
                    if (bulkRunning) {
                        OutlinedButton(onClick = { cancelBulkCovers() }) {
                            Text("Cancel")
                        }
                    }
                }
                if (games.isEmpty()) {
                    Text(
                        "No games yet — import a ROM first.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        }
        item {
        // Global controller overlay — the same controls
        // as the per-game overlay section, with no game
        // id, so this is the all-games default. Per-game
        // overrides in the detail dialog win over this.
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Controller overlay (all games)",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Exactly two themes: SNES or Flat (white). " +
                        "Opacity and stroke are adjustable here; " +
                        "a per-game override in the game dialog " +
                        "wins over this default.",
                    style = MaterialTheme.typography.bodySmall
                )
                OverlaySettings(store = store, gameId = null)
            }
        }
        }
        // Library — inside the same LazyColumn: grid mode
        // chunks games into rows (gridColumns per row, from
        // the 140dp tile width the old grid used), list mode
        // is one row per game. No nested scrollable.
        if (shown.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (games.isEmpty()) {
                            "No games yet. Import a .smc, .sfc, or .zip " +
                                "ROM you already own — it is copied into " +
                                "the app, and your original is never touched."
                        } else {
                            "Nothing matches that search."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else if (grid) {
            items(
                shown.chunked(gridColumns),
                key = { row -> row.first().id }
            ) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { game ->
                        GameTile(
                            modifier = Modifier.weight(1f),
                            game = game,
                            artTick = artTick,
                            isFav = game.id in favIds,
                            onPlay = { play(game) },
                            onDetail = { detail = game },
                            onFav = {
                                GameLibrary.setFavorite(
                                    store, game.id, game.id !in favIds
                                )
                                refresh()
                            }
                        )
                    }
                    repeat(gridColumns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            items(shown, key = { it.id }) { game ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClick = { play(game) },
                            onLongClick = { detail = game }
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        game.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = {
                        GameLibrary.setFavorite(
                            store, game.id, game.id !in favIds
                        )
                        refresh()
                    }) {
                        Icon(
                            if (game.id in favIds) {
                                Icons.Filled.Star
                            } else {
                                Icons.Outlined.StarBorder
                            },
                            contentDescription = "Favourite"
                        )
                    }
                }
            }
        }
        } // close LazyColumn
    } // close BoxWithConstraints

    // Detail dialog
    val d = detail
    if (d != null) {
        GameDetailDialog(
            game = d,
            store = store,
            shaderPresets = shaderPresets,
            shaderBusy = shaderBusy,
            onDownloadShaders = { downloadShaders() },
            onShaderChanged = { shaderTick++; refresh() },
            onDismiss = { detail = null },
            onPlay = { play(d); detail = null },
            onRename = { title ->
                GameLibrary.rename(store, d.id, title)
                refresh()
            },
            onDelete = {
                GameLibrary.remove(context, store, d.id)
                runCatching {
                    SaveStates.dirFor(context.filesDir, d.id)
                        .deleteRecursively()
                }
                detail = null
                refresh()
            },
            onFindCover = {
                scope.launch(Dispatchers.IO) {
                    BoxArt.fetch(context, d, force = true)
                    withContext(Dispatchers.Main) { artTick++ }
                }
            },
            onImportCover = { pickCover.launch("image/*") },
            onClearCustomCover = {
                BoxArt.customFile(context, d).delete()
                artTick++
            },
            onLayout = { layoutGameId = d.id; showLayout = true }
        )
    }
    // Core-download prompt: Play with no core is never a
    // dead tap, wherever the core card is scrolled to.
    if (coreDownloadPrompt) {
        AlertDialog(
            onDismissRequest = { coreDownloadPrompt = false },
            title = { Text("Snes9x core needed") },
            text = {
                Text(
                    "Download the Snes9x core to play. It is " +
                        "downloaded and verified, never bundled."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    coreDownloadPrompt = false
                    downloadCore()
                }) { Text("Download core") }
            },
            dismissButton = {
                TextButton(onClick = { coreDownloadPrompt = false }) {
                    Text("Not now")
                }
            }
        )
    }
    if (showLayout) {
        Dialog(onDismissRequest = { showLayout = false }) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                GameLayoutEditor(
                    store = store,
                    console = "snes",
                    consoleLabel = "Super Nintendo",
                    onDone = { showLayout = false },
                    gameId = layoutGameId
                )
            }
        }
    }
}

@Composable
private fun GameTile(
    modifier: Modifier = Modifier,
    game: GameEntry,
    artTick: Int,
    isFav: Boolean,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
    onFav: () -> Unit
) {
    val context = LocalContext.current
    val art = remember(game.id, artTick) {
        val custom = BoxArt.customFile(context, game)
        val box = BoxArt.artFile(context, game)
        val title = BoxArt.titleFile(context, game)
        val file = when {
            custom.exists() -> custom
            box.exists() -> box
            title.exists() -> title
            else -> null
        }
        file?.let {
            runCatching {
                BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Card(
        modifier = modifier.combinedClickable(
            onClick = onPlay,
            onLongClick = onDetail
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .background(Color(0xFF222222)),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) {
                    Image(
                        bitmap = art,
                        contentDescription = game.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        game.title.take(2).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    game.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(onClick = onFav, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (isFav) Icons.Filled.Star
                        else Icons.Outlined.StarBorder,
                        contentDescription = "Favourite"
                    )
                }
            }
        }
    }
}

@Composable
private fun GameDetailDialog(
    game: GameEntry,
    store: AppStore,
    shaderPresets: List<ShaderPack.ReflectivePreset>,
    shaderBusy: Boolean,
    onDownloadShaders: () -> Unit,
    onShaderChanged: () -> Unit,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onFindCover: () -> Unit,
    onImportCover: () -> Unit,
    onClearCustomCover: () -> Unit,
    onLayout: () -> Unit
) {
    var rename by remember { mutableStateOf(game.title) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showCoreOpts by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Current cover source, shown plainly: a custom cover
    // always wins, then server box art, then title art.
    val coverSource = when {
        BoxArt.customFile(context, game).exists() -> "My own cover"
        BoxArt.artFile(context, game).exists() -> "Box art (libretro)"
        BoxArt.titleFile(context, game).exists() -> "Title screen (libretro)"
        else -> "No cover yet"
    }
    val slotsUsed = (0 until SaveStates.SLOT_COUNT).count {
        SaveStates.slotInfo(context, game.id, it).exists
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(game.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Plays .smc, .sfc • File: ${game.fileName}")
                Text(
                    "Battery save: " +
                        if (game.saveFile(context).exists()) "present" else "none"
                )
                Text(
                    "Save states: $slotsUsed of ${SaveStates.SLOT_COUNT} slots used" +
                        if (SaveStates.autoExists(context, game.id)) " + auto-save"
                        else "" +
                        " (manage them from the pause menu while playing)"
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = rename,
                    onValueChange = { rename = it },
                    label = { Text("Title (rename)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Play — one place, always first. If the core
                // is missing, LibraryScreen's play() opens the
                // core-download prompt instead of a dead tap.
                Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                    Text("Play")
                }
                OutlinedButton(
                    onClick = { onRename(rename); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save rename") }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Cover — $coverSource", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = onFindCover,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Find cover (libretro)") }
                OutlinedButton(
                    onClick = onImportCover,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import my own cover") }
                OutlinedButton(
                    onClick = onClearCustomCover,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Clear my custom cover") }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Snes9x options", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = { showCoreOpts = !showCoreOpts },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (showCoreOpts) "Hide Snes9x options" else "Show Snes9x options") }
                if (showCoreOpts) {
                    CoreOptions.forConsole("snes").forEach { opt ->
                        val cur = CoreOptions.storedValue(store, "snes", opt)
                        Text("${opt.label}: $cur")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            opt.values.forEach { v ->
                                FilterChip(
                                    selected = v == cur,
                                    onClick = {
                                        CoreOptions.setValue(store, "snes", opt.key, v)
                                    },
                                    label = { Text(opt.display(v)) }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Shader — always visible, never behind a
                // toggle: the same picker as the home default,
                // scoped to this game. A choice here applies
                // on the next launch, never mid-session.
                ShaderPickerSection(
                    store = store,
                    presets = shaderPresets,
                    gameId = game.id,
                    shaderBusy = shaderBusy,
                    onDownloadShaders = onDownloadShaders,
                    onChanged = onShaderChanged
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Controller overlay", style = MaterialTheme.typography.titleSmall)
                OverlaySettings(store = store, gameId = game.id)
                OutlinedButton(
                    onClick = onLayout,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Edit layout (move / resize)") }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Danger", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete game") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {}
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${game.title}?") },
            text = {
                Text(
                    "This deletes only the app's copy, its saves, " +
                        "and its art. Your original file is never touched."
                )
            },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Keep")
                }
            }
        )
    }
}

@Composable
private fun OverlaySettings(store: AppStore, gameId: String?) {
    var style by remember {
        mutableStateOf(OverlayStyleStore.forGame(store, gameId))
    }
    fun save(s: app.supershelf.emu.OverlayStyle) {
        style = s
        if (gameId != null) {
            OverlayStyleStore.saveForGame(store, gameId, s)
        } else {
            OverlayStyleStore.saveGlobal(store, s)
        }
        // Bridge to the player's existing controls-alpha key,
        // so the player honours opacity even before its
        // overlay renderer is fully rewritten.
        store.putSetting(
            "gameControlsAlpha:snes",
            (s.opacity * 100).toInt().toString()
        )
    }
    Row {
        FilterChip(
            selected = style.theme == OutlineTheme.SNES,
            onClick = { save(style.copy(theme = OutlineTheme.SNES)) },
            label = { Text("SNES") }
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilterChip(
            selected = style.theme == OutlineTheme.FLAT,
            onClick = { save(style.copy(theme = OutlineTheme.FLAT)) },
            label = { Text("Flat (white)") }
        )
    }
    Text("Opacity: ${(style.opacity * 100).toInt()}%")
    Slider(
        value = style.opacity,
        onValueChange = { save(style.copy(opacity = it.coerceIn(0.05f, 1f))) },
        valueRange = 0.05f..1f
    )
    Text("Stroke: ${style.strokeDp}dp")
    Slider(
        value = style.strokeDp,
        onValueChange = { save(style.copy(strokeDp = it.coerceIn(1f, 6f))) },
        valueRange = 1f..6f
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Labels")
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = style.labelsOn,
            onCheckedChange = { save(style.copy(labelsOn = it)) }
        )
    }
    if (gameId != null) {
        OutlinedButton(onClick = {
            OverlayStyleStore.clearForGame(store, gameId)
            style = OverlayStyleStore.global(store)
        }) { Text("Use global overlay style") }
    }
    // Keep GameLayout import used by the editor entry point.
    @Suppress("unused")
    val minSize = GameLayout.MIN_SIZE_DP
}

/**
 * The one Mega Bezel shader picker, used in exactly two
 * places with different scope: the home default (gameId =
 * null) and a per-game override (gameId set). Same labels,
 * same behaviour, so the two can never disagree.
 *
 * Every offered preset is labelled "Mega Bezel (adapted) —
 * with reflections" and only presets with reflection > 0
 * are offered at all; an installed preset with reflection
 * 0 is excluded and counted in a plain note, never in the
 * default list. Nothing here claims RetroArch-identical
 * output — these are adapted values for this app's native
 * CRT chain, and the frame + reflection are drawn by this
 * app. Violet is always offered as the built-in CRT
 * fallback, labelled as such, never as a Mega Bezel preset.
 * A choice applies on the next launch, never mid-session.
 */
@Composable
private fun ShaderPickerSection(
    store: AppStore,
    presets: List<ShaderPack.ReflectivePreset>,
    gameId: String?,
    shaderBusy: Boolean,
    onDownloadShaders: () -> Unit,
    onChanged: () -> Unit
) {
    val refl = ShaderPack.reflectivePresets(presets)
    val effective = ShaderPack.effectivePresetName(store, gameId, presets)
    val heading = if (gameId == null) {
        "Default Mega Bezel shader (all games)"
    } else {
        "Mega Bezel shader (this game)"
    }
    Text(heading, style = MaterialTheme.typography.titleSmall)
    Text(
        "Adapted for this app's native CRT chain — not " +
            "RetroArch, and the output is not RetroArch-identical.",
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        "Currently: " + (
            effective?.let {
                "Mega Bezel (adapted) — with reflections: $it"
            } ?: "Violet (built-in CRT fallback)"
            ),
        style = MaterialTheme.typography.bodySmall
    )
    if (presets.isEmpty()) {
        Text(
            "No preset pack installed — download it to " +
                "choose a Mega Bezel shader. Until then, games " +
                "use Violet.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDownloadShaders, enabled = !shaderBusy) {
            Text("Download Mega Bezel presets")
        }
        // Violet is still explicitly choosable below, so a
        // user who downloaded nothing is never stuck with
        // a dead picker.
    } else if (refl.size < presets.size) {
        Text(
            "${presets.size - refl.size} installed preset(s) " +
                "without reflections are not offered here.",
            style = MaterialTheme.typography.bodySmall
        )
    }
    // FlowRow is not imported in this file; chips wrap
    // naturally in a Column of Rows via a simple Column —
    // each choice is its own row, which also keeps long
    // Mega Bezel labels readable instead of truncated.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (gameId != null) {
            val stored = store.setting(
                ShaderPack.perGameKey(gameId), ""
            )
            FilterChip(
                selected = stored.isBlank(),
                onClick = {
                    store.putSetting(ShaderPack.perGameKey(gameId), "")
                    onChanged()
                },
                label = {
                    Text(
                        "Use default (" +
                            (ShaderPack.defaultEffectiveName(store, presets)
                                ?.let {
                                    "Mega Bezel (adapted) — with reflections: $it"
                                } ?: "Violet (built-in CRT fallback)") +
                            ")"
                    )
                }
            )
        }
        refl.forEach { p ->
            val stored = if (gameId == null) {
                store.setting(ShaderPack.DEFAULT_KEY, "")
            } else {
                store.setting(ShaderPack.perGameKey(gameId), "")
            }
            // Default picker with nothing stored yet still
            // highlights the effective first reflective
            // preset, so the chip matches "Currently" above.
            val selected = stored == p.name ||
                (gameId == null && stored.isBlank() && effective == p.name)
            FilterChip(
                selected = selected,
                onClick = {
                    if (gameId == null) {
                        store.putSetting(ShaderPack.DEFAULT_KEY, p.name)
                    } else {
                        store.putSetting(
                            ShaderPack.perGameKey(gameId), p.name
                        )
                    }
                    onChanged()
                },
                label = {
                    Text(
                        "Mega Bezel (adapted) — with reflections: ${p.name}"
                    )
                }
            )
        }
        val violetSelected = if (gameId == null) {
            store.setting(ShaderPack.DEFAULT_KEY, "") ==
                ShaderPack.VIOLET_KEY
        } else {
            store.setting(ShaderPack.perGameKey(gameId), "") ==
                ShaderPack.VIOLET_KEY
        }
        FilterChip(
            selected = violetSelected,
            onClick = {
                if (gameId == null) {
                    store.putSetting(
                        ShaderPack.DEFAULT_KEY, ShaderPack.VIOLET_KEY
                    )
                } else {
                    store.putSetting(
                        ShaderPack.perGameKey(gameId),
                        ShaderPack.VIOLET_KEY
                    )
                }
                onChanged()
            },
            label = { Text("Violet (built-in CRT fallback)") }
        )
    }
}
