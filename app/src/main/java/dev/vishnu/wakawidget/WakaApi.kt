package dev.vishnu.wakawidget

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class AuthException : IOException("WakaTime rejected the API key")

object WakaApi {
    private const val BASE = "https://wakatime.com/api/v1/users/current"

    /** Blocking; call from a background thread. */
    fun fetchStats(apiKey: String): Stats {
        val today = get("/status_bar/today", apiKey).getJSONObject("data")
        val grandTotal = today.optJSONObject("grand_total")

        val end = LocalDate.now()
        val start = end.minusDays(6)
        val summaries = get("/summaries?start=$start&end=$end", apiKey).getJSONArray("data")
        val week = (0 until summaries.length()).map { i ->
            val d = summaries.getJSONObject(i)
            val range = d.optJSONObject("range")
            val date = range?.optString("date").orEmpty()
                .ifEmpty { range?.optString("start").orEmpty().take(10) }
            Day(date, d.optJSONObject("grand_total")?.optDouble("total_seconds", 0.0) ?: 0.0)
        }

        return Stats(
            todaySeconds = grandTotal?.optDouble("total_seconds", 0.0) ?: 0.0,
            languages = items(today.optJSONArray("languages")),
            projects = items(today.optJSONArray("projects")),
            week = week,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun items(arr: JSONArray?): List<Item> =
        if (arr == null) emptyList()
        else (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .map { Item(it.optString("name"), it.optDouble("total_seconds", 0.0)) }
            .filter { it.seconds > 0 }
            .sortedByDescending { it.seconds }

    private fun get(path: String, apiKey: String): JSONObject {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            val token = Base64.encodeToString(apiKey.toByteArray(), Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $token")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code == 401 || code == 403) throw AuthException()
            if (code !in 200..299) throw IOException("WakaTime returned HTTP $code")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
