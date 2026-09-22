package com.example.labmob

import android.content.Context

data class SavedPlayerProfile(
    val id: String,
    val fullName: String,
    val gender: String,
    val course: Int,
    val difficulty: Int,
    val birthDateMillis: Long,
    val zodiac: String,
)

private const val PLAYER_PREFERENCES = "althunt_local_save"

fun Context.loadLocalPlayer(): SavedPlayerProfile? {
    val preferences = getSharedPreferences(PLAYER_PREFERENCES, Context.MODE_PRIVATE)
    val id = preferences.getString("id", null)?.takeIf { it.isNotBlank() } ?: return null
    val fullName = preferences.getString("full_name", null)?.takeIf { it.isNotBlank() } ?: return null
    return SavedPlayerProfile(
        id = id,
        fullName = fullName,
        gender = preferences.getString("gender", "Не указан").orEmpty(),
        course = preferences.getInt("course", 1),
        difficulty = preferences.getInt("difficulty", 3),
        birthDateMillis = preferences.getLong("birth_date", 0L),
        zodiac = preferences.getString("zodiac", "Не определён").orEmpty(),
    )
}

fun Context.saveLocalPlayer(id: String, profile: PlayerProfile): SavedPlayerProfile {
    val saved = SavedPlayerProfile(
        id = id,
        fullName = profile.fullName,
        gender = profile.gender,
        course = profile.course,
        difficulty = profile.difficulty,
        birthDateMillis = profile.birthDateMillis,
        zodiac = profile.zodiac.title,
    )
    saveLocalPlayer(saved)
    return saved
}

fun Context.saveLocalPlayer(saved: SavedPlayerProfile) {
    getSharedPreferences(PLAYER_PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putString("id", saved.id)
        .putString("full_name", saved.fullName)
        .putString("gender", saved.gender)
        .putInt("course", saved.course)
        .putInt("difficulty", saved.difficulty)
        .putLong("birth_date", saved.birthDateMillis)
        .putString("zodiac", saved.zodiac)
        .apply()
}

fun Context.deleteLocalPlayer() {
    getSharedPreferences(PLAYER_PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
}
