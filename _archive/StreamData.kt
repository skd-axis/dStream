package com.lifestreams.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Stream(
    val id: String,
    val name: String,
    val color: String,   // hex e.g. "#7c6af7"
    val hours: Int,
    val log: List<String>
)

object StreamData {

    private const val PREFS_NAME = "lifestreams_data"
    private const val KEY_STREAMS = "streams_json"

    /**
     * Load streams from SharedPreferences.
     * The HTML app writes to localStorage — Capacitor bridges localStorage
     * to SharedPreferences automatically when you call:
     *   Preferences.set({ key: 'ls_v13', value: JSON.stringify(streams) })
     * via @capacitor/preferences plugin.
     *
     * Key used by the HTML app: "ls_v13"
     */
    fun load(context: Context): List<Stream> {
        // Capacitor stores Preferences in a prefs file named "CAPPreferences"
        val prefs = context.getSharedPreferences("CAPPreferences", Context.MODE_PRIVATE)
        val raw = prefs.getString("ls_v13", null) ?: return emptyList()
        return parse(raw)
    }

    private fun parse(json: String): List<Stream> {
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Parse a CSS hex color string to android Color int */
    fun parseColor(hex: String): Int {
        return try {
            android.graphics.Color.parseColor(hex)
        } catch (e: Exception) {
            android.graphics.Color.parseColor("#7c6af7")
        }
    }

    /** Darken a color by factor (0–1) */
    fun darken(color: Int, factor: Float = 0.6f): Int {
        val r = (android.graphics.Color.red(color)   * factor).toInt().coerceIn(0,255)
        val g = (android.graphics.Color.green(color) * factor).toInt().coerceIn(0,255)
        val b = (android.graphics.Color.blue(color)  * factor).toInt().coerceIn(0,255)
        return android.graphics.Color.rgb(r, g, b)
    }
}
