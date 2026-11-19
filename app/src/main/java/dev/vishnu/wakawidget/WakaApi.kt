package dev.vishnu.wakawidget

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Base64

class AuthException : IOException("WakaTime rejected the API key or token")

/** HTTP 403. WakaTime limits API keys to plugin endpoints and returns 403 for the rest. */
private class ForbiddenException : IOException("WakaTime refused access (HTTP 403)")

object WakaApi {
    private const val BASE = "https://wakatime.com/api/v1/users/current"
    private const val OAUTH_TOKEN_PREFIX = "waka_tok_"

    /** True for a personal OAuth token (wakatime.com/oauth), which can also read the 7-day summaries. */
    fun isOAuthToken(credential: String): Boolean = credential.startsWith(OAUTH_TOKEN_PREFIX)

    /**
     * OAuth tokens go in a Bearer header. Secret API keys use Basic auth with the
     * base64 of the raw key, and WakaTime only lets them read plugin endpoints.
     */
    fun authorizationHeader(credential: String): String =
        if (isOAuthToken(credential)) "Bearer $credential"
        else "Basic " + Base64.getEncoder().encodeToString(credential.toByteArray())

    /** Blocking; call from a background thread. [apiKey] is an API key or a personal OAuth token. */
    fun fetchStats(apiKey: String): Stats {
        // status_bar/today is a plugin endpoint, so a 403 here really means a bad key.
        val today = try {
            get("/status_bar/today", apiKey).getJSONObject("data")
        } catch (e: ForbiddenException) {
            throw AuthException()
        }
        val grandTotal = today.optJSONObject("grand_total")

        // summaries answers API keys with 403 ("API Keys are only for plugins"):
        // keep today's stats and show no chart instead of failing the refresh.
        val week = try {
            fetchWeek(apiKey)
        } catch (e: ForbiddenException) {
            emptyList()
        }

        return Stats(
            todaySeconds = grandTotal?.optDouble("total_seconds", 0.0) ?: 0.0,
            languages = items(today.optJSONArray("languages")),
            projects = items(today.optJSONArray("projects")),
            week = week,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun fetchWeek(apiKey: String): List<Day> {
        val end = LocalDate.now()
        val start = end.minusDays(6)
        val summaries = get("/summaries?start=$start&end=$end", apiKey).getJSONArray("data")
        return (0 until summaries.length()).map { i ->
            val d = summaries.getJSONObject(i)
            val range = d.optJSONObject("range")
            val date = range?.optString("date").orEmpty()
                .ifEmpty { range?.optString("start").orEmpty().take(10) }
            Day(date, d.optJSONObject("grand_total")?.optDouble("total_seconds", 0.0) ?: 0.0)
        }
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
            conn.setRequestProperty("Authorization", authorizationHeader(apiKey))
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code == 401) throw AuthException()
            if (code == 403) throw ForbiddenException()
            if (code !in 200..299) throw IOException("WakaTime returned HTTP $code")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
