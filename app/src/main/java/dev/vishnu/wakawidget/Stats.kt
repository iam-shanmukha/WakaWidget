package dev.vishnu.wakawidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Item(val name: String, val seconds: Double)
data class Day(val date: String, val seconds: Double)

data class Stats(
    val todaySeconds: Double,
    val languages: List<Item>,
    val projects: List<Item>,
    val week: List<Day>,
    val updatedAt: Long,
) {
    fun toJson(): String = JSONObject().apply {
        put("todaySeconds", todaySeconds)
        put("languages", items(languages))
        put("projects", items(projects))
        put("week", JSONArray().apply {
            week.forEach { put(JSONObject().put("date", it.date).put("seconds", it.seconds)) }
        })
        put("updatedAt", updatedAt)
    }.toString()

    companion object {
        private fun items(list: List<Item>) = JSONArray().apply {
            list.forEach { put(JSONObject().put("name", it.name).put("seconds", it.seconds)) }
        }

        private fun parseItems(arr: JSONArray?): List<Item> =
            if (arr == null) emptyList()
            else (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Item(o.optString("name"), o.optDouble("seconds", 0.0))
            }

        fun fromJson(json: String): Stats? = try {
            val o = JSONObject(json)
            val weekArr = o.optJSONArray("week") ?: JSONArray()
            Stats(
                todaySeconds = o.optDouble("todaySeconds", 0.0),
                languages = parseItems(o.optJSONArray("languages")),
                projects = parseItems(o.optJSONArray("projects")),
                week = (0 until weekArr.length()).map {
                    val d = weekArr.getJSONObject(it)
                    Day(d.optString("date"), d.optDouble("seconds", 0.0))
                },
                updatedAt = o.optLong("updatedAt", 0L),
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** "3h 12m", "45m", "0m" */
fun formatDuration(seconds: Double): String {
    val totalMin = (seconds / 60).toInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Last successful fetch, so a newly added widget has something to show straight away. */
object StatsCache {
    private const val PREFS = "stats_cache"
    private const val KEY = "stats"

    fun save(context: Context, stats: Stats) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, stats.toJson()).apply()
    }

    fun loadJson(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
}
