package app.mathlab.data

import android.content.Context
import androidx.compose.runtime.*
import app.mathlab.math.AngleMode
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val id: Long, val expr: String, val result: String, val time: Long, val kind: String)

class AppState(context: Context) {
    private val prefs = context.getSharedPreferences("mathlab", Context.MODE_PRIVATE)

    var palette by mutableStateOf(prefs.getString("palette", "dynamic") ?: "dynamic")
        private set
    var customHue by mutableFloatStateOf(prefs.getFloat("hue", 265f))
        private set
    var themeMode by mutableIntStateOf(prefs.getInt("themeMode", 0)) // 0 system, 1 light, 2 dark
        private set
    var pureBlack by mutableStateOf(prefs.getBoolean("pureBlack", false))
        private set
    var angle by mutableStateOf(if (prefs.getString("angle", "DEG") == "RAD") AngleMode.RAD else AngleMode.DEG)
        private set

    val history = mutableStateListOf<HistoryEntry>().also { it.addAll(loadHistory()) }

    fun setPalette(v: String) { palette = v; prefs.edit().putString("palette", v).apply() }
    fun setCustomHue(v: Float) { customHue = v; prefs.edit().putFloat("hue", v).apply() }
    fun setThemeMode(v: Int) { themeMode = v; prefs.edit().putInt("themeMode", v).apply() }
    fun setPureBlack(v: Boolean) { pureBlack = v; prefs.edit().putBoolean("pureBlack", v).apply() }
    fun setAngle(v: AngleMode) { angle = v; prefs.edit().putString("angle", v.name).apply() }

    private fun loadHistory(): List<HistoryEntry> = try {
        val arr = JSONArray(prefs.getString("history", "[]") ?: "[]")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryEntry(o.getLong("id"), o.getString("e"), o.getString("r"), o.getLong("t"), o.optString("k", "calc"))
        }
    } catch (e: Exception) { emptyList() }

    private fun saveHistory() {
        val arr = JSONArray()
        history.forEach {
            arr.put(JSONObject().put("id", it.id).put("e", it.expr).put("r", it.result).put("t", it.time).put("k", it.kind))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun addHistory(expr: String, result: String, kind: String = "calc") {
        history.add(0, HistoryEntry(System.nanoTime(), expr, result, System.currentTimeMillis(), kind))
        while (history.size > 500) history.removeAt(history.lastIndex)
        saveHistory()
    }

    fun removeHistory(id: Long) { history.removeAll { it.id == id }; saveHistory() }
    fun clearHistory() { history.clear(); saveHistory() }
}
