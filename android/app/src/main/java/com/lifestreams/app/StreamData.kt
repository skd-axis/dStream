package com.lifestreams.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Stream(
    val id: String,
    val name: String,
    val color: String,
    val hours: Int,
    val log: List<String>
)

object StreamData {

    // Capacitor Preferences uses "CapacitorStorage" prefs file
    private fun getPrefs(context: Context) =
        context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)

    /** Load streams for the active map */
    fun load(context: Context): List<Stream> {
        val prefs = getPrefs(context)

        // Try new multi-map format first
        val activeMapId = prefs.getString("ds_active_map", null)
        if (activeMapId != null) {
            val mapKey = "ds_map_$activeMapId"
            val raw = prefs.getString(mapKey, null)
            if (raw != null) return parse(raw)
        }

        // Try maps index to find first map
        val mapsRaw = prefs.getString("ds_maps", null)
        if (mapsRaw != null) {
            try {
                val arr = JSONArray(mapsRaw)
                if (arr.length() > 0) {
                    val firstId = arr.getJSONObject(0).optString("id", null)
                    if (firstId != null) {
                        val raw = prefs.getString("ds_map_$firstId", null)
                        if (raw != null) return parse(raw)
                    }
                }
            } catch (e: Exception) {}
        }

        // Legacy fallback
        val legacy = prefs.getString("ls_v13", null) ?: return emptyList()
        return parse(legacy)
    }

    /** Load streams for a specific map id */
    fun loadMap(context: Context, mapId: String): List<Stream> {
        val prefs = getPrefs(context)
        val raw = prefs.getString("ds_map_$mapId", null) ?: return emptyList()
        return parse(raw)
    }

    /** Get active map name */
    fun getActiveMapName(context: Context): String {
        val prefs = getPrefs(context)
        val activeId = prefs.getString("ds_active_map", null) ?: return "dStream"
        val mapsRaw = prefs.getString("ds_maps", null) ?: return "dStream"
        return try {
            val arr = JSONArray(mapsRaw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("id") == activeId)
                    return obj.optString("name", "dStream")
            }
            "dStream"
        } catch (e: Exception) { "dStream" }
    }

    fun parse(json: String): List<Stream> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val logArr = obj.optJSONArray("log") ?: JSONArray()
                Stream(
                    id    = obj.optString("id", i.toString()),
                    name  = obj.optString("name", "Stream"),
                    color = obj.optString("color", "#7c6af7"),
                    hours = obj.optInt("hours", 0),
                    log   = (0 until logArr.length()).map { logArr.getString(it) }
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun parseColor(hex: String): Int {
        return try {
            android.graphics.Color.parseColor(hex)
        } catch (e: Exception) {
            android.graphics.Color.parseColor("#7c6af7")
        }
    }

    fun darken(color: Int, factor: Float = 0.6f): Int {
        val r = (android.graphics.Color.red(color)   * factor).toInt().coerceIn(0,255)
        val g = (android.graphics.Color.green(color) * factor).toInt().coerceIn(0,255)
        val b = (android.graphics.Color.blue(color)  * factor).toInt().coerceIn(0,255)
        return android.graphics.Color.rgb(r, g, b)
    }
}
