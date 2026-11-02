package app.supershelf.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import org.json.JSONObject

/**
 * Super Shelf's only persistence: a single JSON settings document.
 * Deliberately small — this app has no browser data, no history, no
 * tabs, no vault. The emulator code only needs setting()/putSetting()
 * plus a revision counter Compose can key on, so that is all this
 * store provides. Writes are synchronous and small; the document
 * lives in filesDir as supershelf_data.json.
 */
class AppStore private constructor(context: Context) {

    private val file = File(context.filesDir, "supershelf_data.json")
    private val lock = Any()
    private val settings = mutableMapOf<String, String>()

    var revision by mutableStateOf(0)
        private set

    init {
        runCatching {
            if (file.exists()) {
                val obj = JSONObject(file.readText())
                for (key in obj.keys()) {
                    settings[key] = obj.optString(key, "")
                }
            }
        }
    }

    private fun saveLocked() {
        runCatching {
            val obj = JSONObject()
            for ((k, v) in settings) obj.put(k, v)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(obj.toString())
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    fun setting(key: String, default: String): String =
        synchronized(lock) { settings[key] ?: default }

    fun putSetting(key: String, value: String) {
        synchronized(lock) {
            settings[key] = value
            saveLocked()
        }
        revision++
    }

    fun removeSetting(key: String) {
        synchronized(lock) {
            if (settings.remove(key) != null) {
                saveLocked()
            }
        }
        revision++
    }

    companion object {
        @Volatile private var instance: AppStore? = null
        fun get(context: Context): AppStore =
            instance ?: synchronized(this) {
                instance ?: AppStore(context.applicationContext).also { instance = it }
            }
    }
}
