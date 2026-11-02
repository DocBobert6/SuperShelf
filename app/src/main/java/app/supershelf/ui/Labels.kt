package app.supershelf.ui

/** Super Shelf is SNES-only, so this is a one-entry label table. */
fun systemLabel(system: String): String = when (system) {
    "snes" -> "Super Nintendo"
    else -> system
}
