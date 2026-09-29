package com.example.labmob

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
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
    val difficulty: Int,
)

data class HuntTarget(
    val id: String, val type: String, val x: Float, val y: Float, val radius: Float,
    val vx: Float, val vy: Float, val points: Int, val goldRate: Double? = null,
)
data class HuntBonus(val id: String, val x: Float, val y: Float, val title: String)
data class HuntRound(
    val id: String, val finished: Boolean, val remainingMilliseconds: Long,
    val score: Int, val hits: Int, val misses: Int, val missPenalty: Int,
    val targets: List<HuntTarget>, val bonus: HuntBonus?,
    val sampledAtElapsedMs: Long, val tiltActive: Boolean, val theurgyRemainingMilliseconds: Long,
    val bonusesCollected: Int, val level: Int = 1,
    val selectedTheurgy: String = "gravity", val effectType: String? = null,
    val effectMultiplier: Int = 1,
)
data class HuntRecord(
    val playerName: String, val score: Int, val difficulty: Int,
    val hits: Int, val misses: Int, val playedAt: String, val level: Int,
)
data class PlayerProgress(
    val totalClues: Long,
    val bestScore: Int,
    val bestLevelOneScore: Int,
    val levelTwoUnlocked: Boolean,
    val levelTwoUnlockScore: Int,
    val velvetRoomUnlocked: Boolean,
)

data class TheurgyItem(
    val id: String,
    val title: String,
    val description: String,
    val price: Long,
    val effectType: String,
    val owned: Boolean,
    val selected: Boolean,
)

data class VelvetRoomState(
    val unlocked: Boolean,
    val introSeen: Boolean,
    val totalClues: Long,
    val selectedTheurgy: String,
    val theurgies: List<TheurgyItem>,
)

private fun JSONObject.toHuntRound(): HuntRound {
    val targetsJson = getJSONArray("targets")
    return HuntRound(
        id = getString("id"), level = optInt("level", 1), finished = getString("status") == "finished",
        remainingMilliseconds = getLong("remainingMilliseconds"), score = getInt("score"),
        hits = getInt("hits"), misses = getInt("misses"), missPenalty = optInt("missPenalty", 10),
        targets = (0 until targetsJson.length()).map { index ->
            targetsJson.getJSONObject(index).let {
                HuntTarget(it.getString("id"), it.getString("type"), it.getDouble("x").toFloat(),
                    it.getDouble("y").toFloat(), it.getDouble("radius").toFloat(),
                    it.getJSONObject("velocity").getDouble("x").toFloat(),
                    it.getJSONObject("velocity").getDouble("y").toFloat(), it.getInt("points"),
                    it.optDouble("goldRate").takeUnless(Double::isNaN))
            }
        },
        bonus = optJSONObject("bonus")?.let {
            HuntBonus(it.getString("id"), it.getDouble("x").toFloat(), it.getDouble("y").toFloat(), it.optString("title", "Теургия"))
        },
        sampledAtElapsedMs = SystemClock.elapsedRealtime(),
        tiltActive = optJSONObject("effect")?.optString("type") == "tilt",
        theurgyRemainingMilliseconds = optLong("theurgyRemainingMilliseconds", 0L),
        bonusesCollected = optInt("bonusesCollected", 0),
        selectedTheurgy = optString("selectedTheurgy", "gravity"),
        effectType = optJSONObject("effect")?.optString("type")?.takeIf { it.isNotBlank() },
        effectMultiplier = optJSONObject("effect")?.optInt("multiplier", 1) ?: 1,
    )
}

class BackendApi(private val baseUrl: String = BuildConfig.API_BASE_URL) {
    suspend fun listPlayers(): List<SavedPlayerProfile> = withContext(Dispatchers.IO) {
        val players = request("players").getJSONArray("players")
        (0 until players.length()).map { index ->
            val item = players.getJSONObject(index)
            SavedPlayerProfile(
                id = item.getString("id"), fullName = item.getString("fullName"),
                gender = item.getString("gender"), course = item.getInt("course"),
                difficulty = item.getInt("difficulty"),
                birthDateMillis = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(item.getString("birthDate"))?.time ?: 0L,
                zodiac = item.getString("zodiac"),
            )
        }
    }

    suspend fun startRound(playerId: String, level: Int): HuntRound = withContext(Dispatchers.IO) {
        request("rounds", "POST", JSONObject().put("playerId", playerId).put("level", level)).getJSONObject("round").toHuntRound()
    }

    suspend fun getRound(roundId: String): HuntRound = withContext(Dispatchers.IO) {
        request("rounds/$roundId").getJSONObject("round").toHuntRound()
    }

    suspend fun tap(roundId: String, x: Float, y: Float, eventId: String, targetId: String? = null): HuntRound = withContext(Dispatchers.IO) {
        val event = JSONObject().put("eventId", eventId).put("type", "tap")
            .put("x", x.toDouble()).put("y", y.toDouble())
        if (targetId != null) event.put("targetId", targetId)
        request("rounds/$roundId/events", "POST", event).getJSONObject("round").toHuntRound()
    }

    suspend fun collectBonus(roundId: String, bonusId: String, eventId: String): HuntRound = withContext(Dispatchers.IO) {
        request("rounds/$roundId/events", "POST", JSONObject().put("eventId", eventId).put("type", "collect_bonus")
            .put("bonusId", bonusId)).getJSONObject("round").toHuntRound()
    }

    suspend fun updateTilt(roundId: String, x: Float, y: Float, eventId: String): HuntRound = withContext(Dispatchers.IO) {
        request("rounds/$roundId/events", "POST", JSONObject().put("eventId", eventId).put("type", "tilt")
            .put("x", x.toDouble()).put("y", y.toDouble())).getJSONObject("round").toHuntRound()
    }

    suspend fun finishRound(roundId: String): HuntRound = withContext(Dispatchers.IO) {
        request("rounds/$roundId/finish", "POST", JSONObject()).getJSONObject("round").toHuntRound()
    }

    suspend fun records(): List<HuntRecord> = withContext(Dispatchers.IO) {
        val records = request("records?limit=50").getJSONArray("records")
        (0 until records.length()).map { index ->
            records.getJSONObject(index).let {
                HuntRecord(it.getString("full_name"), it.getInt("score"), it.getInt("difficulty"),
                    it.getInt("hits"), it.getInt("misses"), it.getString("played_at"), it.optInt("level", 1))
            }
        }
    }

    suspend fun playerResults(playerId: String): List<HuntRecord> = withContext(Dispatchers.IO) {
        val results = request("players/$playerId/results?limit=100").getJSONArray("results")
        (0 until results.length()).mapNotNull { index ->
            results.getJSONObject(index).let {
                if (!it.optBoolean("verified", false)) null else
                    HuntRecord("", it.getInt("score"), it.getInt("difficulty"),
                        it.getInt("hits"), it.getInt("misses"), it.getString("playedAt"), it.optInt("level", 1))
            }
        }
    }

    suspend fun playerProgress(playerId: String): PlayerProgress = withContext(Dispatchers.IO) {
        request("players/$playerId/progress").getJSONObject("progress").let {
            PlayerProgress(
                totalClues = it.getLong("totalClues"),
                bestScore = it.getInt("bestScore"),
                bestLevelOneScore = it.getInt("bestLevelOneScore"),
                levelTwoUnlocked = it.getBoolean("levelTwoUnlocked"),
                levelTwoUnlockScore = it.getInt("levelTwoUnlockScore"),
                velvetRoomUnlocked = it.optBoolean("velvetRoomUnlocked", false),
            )
        }
    }

    suspend fun velvetRoom(playerId: String): VelvetRoomState = withContext(Dispatchers.IO) {
        request("players/$playerId/theurgies").getJSONObject("velvetRoom").toVelvetRoomState()
    }

    suspend fun purchaseTheurgy(playerId: String, theurgyId: String): VelvetRoomState = withContext(Dispatchers.IO) {
        request("players/$playerId/theurgies/$theurgyId/purchase", "POST", JSONObject())
            .getJSONObject("velvetRoom").toVelvetRoomState()
    }

    suspend fun selectTheurgy(playerId: String, theurgyId: String): VelvetRoomState = withContext(Dispatchers.IO) {
        request("players/$playerId/theurgies/selected", "PUT", JSONObject().put("theurgyId", theurgyId))
            .getJSONObject("velvetRoom").toVelvetRoomState()
    }

    suspend fun markVelvetIntroSeen(playerId: String) = withContext(Dispatchers.IO) {
        request("players/$playerId/velvet-intro-seen", "POST", JSONObject())
    }
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

    suspend fun deletePlayer(playerId: String) = withContext(Dispatchers.IO) {
        request("players/$playerId", "DELETE")
    }

    suspend fun saveSettings(playerId: String, settings: GameSettings) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("gameSpeed", settings.gameSpeed.toDouble())
            .put("maxInsects", settings.maxInsects)
            .put("bonusIntervalSeconds", settings.bonusIntervalSeconds)
            .put("roundDurationSeconds", settings.roundDurationSeconds)
            .put("difficulty", settings.difficulty)
        request("players/$playerId/settings", "PUT", body)
    }

    suspend fun loadSettings(playerId: String): GameSettings = withContext(Dispatchers.IO) {
        request("players/$playerId/settings").getJSONObject("settings").let {
            GameSettings(it.getDouble("gameSpeed").toFloat(), it.getInt("maxInsects"),
                it.getInt("bonusIntervalSeconds"), it.getInt("roundDurationSeconds"), it.getInt("difficulty"))
        }
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val connection = URL(baseUrl.trimEnd('/') + "/" + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(payload).optString("error") }.getOrNull()
                error(message?.takeIf { it.isNotBlank() } ?: "API error $status")
            }
            if (payload.isBlank()) JSONObject() else JSONObject(payload)
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONObject.toVelvetRoomState(): VelvetRoomState {
    val items = getJSONArray("theurgies")
    return VelvetRoomState(
        unlocked = getBoolean("unlocked"),
        introSeen = optBoolean("introSeen", false),
        totalClues = getLong("totalClues"),
        selectedTheurgy = getString("selectedTheurgy"),
        theurgies = (0 until items.length()).map { index ->
            items.getJSONObject(index).let {
                TheurgyItem(
                    id = it.getString("id"),
                    title = it.getString("title"),
                    description = it.getString("description"),
                    price = it.getLong("price"),
                    effectType = it.getString("effectType"),
                    owned = it.getBoolean("owned"),
                    selected = it.getBoolean("selected"),
                )
            }
        },
    )
}
