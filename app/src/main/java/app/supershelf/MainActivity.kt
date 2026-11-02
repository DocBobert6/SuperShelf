package app.supershelf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.supershelf.data.AppStore
import app.supershelf.ui.LibraryScreen
import app.supershelf.ui.SuperShelfTheme

/**
 * Super Shelf's only launcher activity: the SNES library.
 * There is no browser, no tabs, no start page in this app —
 * LibraryScreen is the home screen, and GameActivity is the
 * only other screen (the player).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = AppStore.get(this)
        setContent {
            SuperShelfTheme(store) {
                LibraryScreen(store = store)
            }
        }
    }
}
