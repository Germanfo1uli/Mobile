package com.example.labmob

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.hypot

data class HuntGameUiState(
    val round: HuntRound? = null,
    val loading: Boolean = false,
    val pending: Boolean = false,
    val error: String? = null,
)

class HuntGameViewModel(
    private val api: BackendApi,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HuntGameUiState())
    val uiState: StateFlow<HuntGameUiState> = _uiState.asStateFlow()
    private var pollingJob: Job? = null
    private var tiltPending = false

    fun begin(playerId: String, level: Int) {
        val key = "$playerId:$level"
        val current = _uiState.value.round
        val currentGame = savedStateHandle.get<String>(KEY_GAME)
        if (currentGame == key && current != null && !current.finished) return
        if (currentGame != key || current?.finished == true) {
            savedStateHandle[KEY_ROUND_ID] = null
        }
        savedStateHandle[KEY_GAME] = key
        pollingJob?.cancel()
        _uiState.value = HuntGameUiState(loading = true)
        viewModelScope.launch {
            runCatching {
                val savedRoundId = savedStateHandle.get<String>(KEY_ROUND_ID)
                if (savedRoundId == null) api.startRound(playerId, level) else api.getRound(savedRoundId)
            }.onSuccess { round ->
                savedStateHandle[KEY_ROUND_ID] = round.id
                _uiState.value = HuntGameUiState(round = round)
                startPolling(round.id)
            }.onFailure { failure ->
                _uiState.value = HuntGameUiState(error = failure.message ?: "Нет связи с сервером")
            }
        }
    }

    fun retry(playerId: String, level: Int) {
        savedStateHandle[KEY_GAME] = null
        begin(playerId, level)
    }

    fun tap(x: Float, y: Float, targetId: String? = null) {
        val active = _uiState.value.round ?: return
        if (_uiState.value.pending || active.finished || active.theurgyRemainingMilliseconds > 0L) return
        _uiState.value = _uiState.value.copy(pending = true)
        viewModelScope.launch {
            val bonus = active.bonus
            val operation = if (bonus != null && hypot(x - bonus.x, y - bonus.y) < .11f) {
                runCatching { api.collectBonus(active.id, bonus.id, UUID.randomUUID().toString()) }
            } else {
                runCatching { api.tap(active.id, x, y, UUID.randomUUID().toString(), targetId) }
            }
            operation.onSuccess { _uiState.value = HuntGameUiState(round = it) }
                .onFailure { _uiState.value = _uiState.value.copy(pending = false, error = it.message ?: "Не удалось передать касание") }
        }
    }

    fun updateTilt(x: Float, y: Float) {
        val active = _uiState.value.round ?: return
        if (tiltPending || active.finished) return
        tiltPending = true
        viewModelScope.launch {
            runCatching { api.updateTilt(active.id, x, y, UUID.randomUUID().toString()) }
                .onSuccess { _uiState.value = _uiState.value.copy(round = it, error = null) }
            tiltPending = false
        }
    }

    fun finish() {
        val active = _uiState.value.round ?: return
        if (_uiState.value.pending || active.finished) return
        _uiState.value = _uiState.value.copy(pending = true)
        viewModelScope.launch {
            runCatching { api.finishRound(active.id) }
                .onSuccess { _uiState.value = HuntGameUiState(round = it) }
                .onFailure { _uiState.value = _uiState.value.copy(pending = false, error = it.message ?: "Не удалось завершить вылазку") }
        }
    }

    fun consumeFinishedRound(roundId: String) {
        val current = _uiState.value.round
        if (current?.id != roundId || !current.finished) return
        pollingJob?.cancel()
        savedStateHandle[KEY_ROUND_ID] = null
        savedStateHandle[KEY_GAME] = null
        _uiState.value = HuntGameUiState()
    }

    private fun startPolling(roundId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(250)
                if (!_uiState.value.pending) {
                    runCatching { api.getRound(roundId) }
                        .onSuccess { _uiState.value = _uiState.value.copy(round = it, error = null) }
                        .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "Связь потеряна") }
                }
                if (_uiState.value.round?.finished == true) break
            }
        }
    }

    companion object {
        private const val KEY_GAME = "hunt_game_key"
        private const val KEY_ROUND_ID = "hunt_round_id"
    }
}
