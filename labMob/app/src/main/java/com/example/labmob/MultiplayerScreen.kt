package com.example.labmob

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val NetworkInk = Color(0xFF09090D)
private val NetworkRed = Color(0xFFE60012)
private val NetworkPaper = Color(0xFFF4F1E9)

@Composable
internal fun MultiplayerScreen(
    player: SavedPlayerProfile,
    initialMatch: MultiplayerMatch? = null,
    initialCode: String? = null,
    alreadyPlayedMatchCode: String? = null,
    onBack: () -> Unit,
    onStartGame: (String) -> Unit,
    onMatchChanged: (MultiplayerMatch?) -> Unit,
) {
    val api = remember { BackendApi() }
    val scope = rememberCoroutineScope()
    var match by remember { mutableStateOf(initialMatch) }
    var code by rememberSaveable { mutableStateOf(initialMatch?.code ?: initialCode.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var searchRemainingMs by rememberSaveable { mutableStateOf(initialMatch?.searchRemainingMilliseconds ?: 20_000L) }
    var freshMatchCode by remember { mutableStateOf<String?>(null) }

    fun leave() {
        if (match?.status != "waiting") {
            onBack()
            return
        }
        if (busy) return
        busy = true
        scope.launch {
            runCatching { api.cancelMatchSearch(player.id) }
                .onSuccess { cancelled ->
                    if (cancelled) { match = null; code = ""; onMatchChanged(null); onBack() }
                    else runCatching { api.getMatch(code) }
                        .onSuccess { match = it; onMatchChanged(it) }
                        .onFailure { error = it.message ?: "Не удалось проверить матч" }
                }
                .onFailure { error = "Не удалось отменить поиск: ${it.message ?: "нет связи"}" }
            busy = false
        }
    }

    LaunchedEffect(code) {
        if (code.isBlank()) return@LaunchedEffect
        while (true) {
            runCatching { api.getMatch(code) }
                .onSuccess {
                    match = it; freshMatchCode = it.code
                    searchRemainingMs = it.searchRemainingMilliseconds; error = null; onMatchChanged(it)
                }
                .onFailure { error = it.message ?: "Связь с сервером потеряна" }
            if (match?.status == "finished" || match?.status == "expired") break
            delay(1_000)
        }
    }

    val ownResult = match?.players?.firstOrNull { it.playerId == player.id }
    LaunchedEffect(match?.status, ownResult?.submitted, match?.code, freshMatchCode, alreadyPlayedMatchCode) {
        val ready = match
        if (ready?.status == "running" && ready.code == freshMatchCode &&
            ownResult?.submitted == false && ready.code != alreadyPlayedMatchCode) {
            onStartGame(ready.code)
        }
    }
    BackHandler(onBack = ::leave)

    Column(Modifier.fillMaxSize().background(NetworkInk).testTag("multiplayer_screen")
        .padding(horizontal = 18.dp, vertical = 18.dp)) {
        HuntBackButton(::leave)
        RansomTitle("СЕТЕВАЯ ОХОТА", size = 31)
        Text("ДВА ОХОТНИКА • ОДНА СЕТЬ • ПОБЕДИТ БОЛЬШИЙ СЧЁТ",
            color = NetworkPaper.copy(alpha = .78f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(22.dp))

        val current = match
        if (current == null) {
            Text("Поиск займёт до 20 секунд. Как только найдём соперника, вылазка начнётся сама.",
                color = Color.White, fontSize = 17.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(22.dp))
            Button(onClick = {
                busy = true
                scope.launch {
                    runCatching { api.searchMatch(player.id) }
                        .onSuccess {
                            match = it; code = it.code; freshMatchCode = it.code
                            searchRemainingMs = it.searchRemainingMilliseconds
                            error = null; onMatchChanged(it)
                        }
                        .onFailure { error = it.message ?: "Нет связи с сервером" }
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth().height(58.dp), enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = NetworkRed)) {
                Text(if (busy) "ПОДКЛЮЧАЕМСЯ..." else "ИСКАТЬ СОПЕРНИКА", fontWeight = FontWeight.Black)
            }
        } else if (freshMatchCode != current.code) {
            Text("СВЕРЯЕМ СОСТОЯНИЕ МАТЧА...", color = Color.White,
                fontSize = 20.sp, fontWeight = FontWeight.Black)
            CircularProgressIndicator(color = NetworkRed, modifier = Modifier.padding(top = 18.dp))
        } else {
            val waiting = current.status == "waiting"
            val finished = current.status == "finished"
            val expired = current.status == "expired"
            Text(when {
                waiting -> "ИЩЕМ СОПЕРНИКА • ${(searchRemainingMs + 999) / 1000} СЕК"
                expired -> "НИКОГО НЕ НАШЛИ"
                finished -> if (current.draw) "НИЧЬЯ" else if (current.winner?.playerId == player.id) "ТЫ ПОБЕДИЛ" else "ПОБЕДИЛ СОПЕРНИК"
                ownResult?.submitted == true -> "ЖДЁМ ИТОГ СОПЕРНИКА"
                else -> "СОПЕРНИК НАЙДЕН"
            }, color = if (finished) NetworkRed else Color.White,
                fontSize = 23.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            if (waiting) {
                CircularProgressIndicator(color = NetworkRed, modifier = Modifier.padding(vertical = 12.dp))
            }
            Text(when {
                waiting -> "Проверяем, кто ещё вышел на охоту..."
                expired -> "За 20 секунд никто не подключился. Можно попробовать снова."
                finished -> "Оба раунда завершены. Сервер сравнил подтверждённые очки."
                ownResult?.submitted == true -> "Твой счёт сохранён. Ждём окончания второй вылазки."
                else -> "Переносим тебя в район..."
            }, color = NetworkPaper, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(current.players) { item ->
                    Row(Modifier.fillMaxWidth().background(
                        if (finished && item.playerId == current.winner?.playerId) NetworkRed else Color(0xFF26262D),
                        SlashShape,
                    ).padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (item.playerId == player.id) "ТЫ" else item.playerName,
                            color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text(if (item.submitted) "${item.score} УЛИК" else if (waiting) "В ПОИСКЕ"
                            else if (expired) "ПОИСК ЗАВЕРШЁН" else "В БОЮ",
                            color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (waiting) Button(onClick = ::leave, modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34343C))) {
                Text("ОТМЕНИТЬ ПОИСК", fontWeight = FontWeight.Black)
            }
            if (finished) Button(onClick = {
                match = null; code = ""; onMatchChanged(null)
            }, modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NetworkRed)) {
                Text("НОВЫЙ ПОИСК", fontWeight = FontWeight.Black)
            }
            if (expired) Button(onClick = {
                match = null; code = ""; onMatchChanged(null)
            }, modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NetworkRed)) {
                Text("ПОПРОБОВАТЬ СНОВА", fontWeight = FontWeight.Black)
            }
        }
        error?.let { Text(it, color = Color(0xFFFF9A9A), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
    }
}
