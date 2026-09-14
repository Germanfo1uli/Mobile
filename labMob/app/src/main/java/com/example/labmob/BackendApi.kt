package com.example.labmob

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GameSettings(
    val gameSpeed: Float,
    val maxInsects: Int,
    val bonusIntervalSeconds: Int,
    val roundDurationSeconds: Int,
)

class BackendApi(private val baseUrl: String = BuildConfig.API_BASE_URL) {
    suspend fun createPlayer(profile: PlayerProfile): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("fullName", profile.fullName)
            .put("gender", profile.gender)
            .put("course", profile.course)
            .put("difficulty", profile.difficulty)
            .put(
                "birthDate",
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(profile.birthDateMillis)),
            )
            .put("zodiac", profile.zodiac.title)

        request("players", "POST", body).getJSONObject("player").getString("id")
    }

    suspend fun saveSettings(playerId: String, settings: GameSettings) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("gameSpeed", settings.gameSpeed.toDouble())
            .put("maxInsects", settings.maxInsects)
            .put("bonusIntervalSeconds", settings.bonusIntervalSeconds)
            .put("roundDurationSeconds", settings.roundDurationSeconds)
        request("players/$playerId/settings", "PUT", body)
    }

    private fun request(path: String, method: String, body: JSONObject): JSONObject {
        val connection = URL(baseUrl.trimEnd('/') + "/" + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(payload).optString("error") }.getOrNull()
                error(message?.takeIf { it.isNotBlank() } ?: "API error $status")
            }
            JSONObject(payload)
        } finally {
            connection.disconnect()
        }
    }
}
