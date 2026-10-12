package com.example.labmob

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
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
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var rating by remember { mutableStateOf<PvpProfile?>(null) }
    var leaderboard by remember { mutableStateOf<List<PvpRating>>(emptyList()) }

    LaunchedEffect(player.id, match?.status) {
        runCatching { api.pvpProfile(player.id) }.onSuccess { rating = it }
        runCatching { api.pvpLeaderboard() }.onSuccess { leaderboard = it }
    }

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

    Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF39000A), NetworkInk, NetworkInk)))
        .testTag("multiplayer_screen")
        .padding(horizontal = 18.dp, vertical = 18.dp)) {
        HuntBackButton(::leave)
        RansomTitle("СЕТЕВАЯ ОХОТА", size = 29)
        Text("ОДИН РАЙОН. ДВА ОХОТНИКА. ТОЛЬКО ОДИН ПОБЕДИТЕЛЬ.",
            color = NetworkPaper.copy(alpha = .78f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        PvpRatingBanner(rating)
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("ДУЭЛЬ", "ДОРОГА СЛАВЫ", "РЕЙТИНГ").forEachIndexed { index, title ->
                Button(onClick = { selectedTab = index }, modifier = Modifier.weight(1f).height(44.dp),
                    shape = SlashShape,
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedTab == index) NetworkRed else Color(0xFF292930)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)) {
                    Text(title, fontSize = if (index == 1) 9.sp else 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        val current = match
        if (selectedTab == 1) {
            PvpRoadPanel(rating, Modifier.weight(1f))
        } else if (selectedTab == 2) {
            PvpLeaderboardPanel(player.id, leaderboard, Modifier.weight(1f))
        } else if (current == null) {
            Text("ВЫХОД НА СЛЕД", color = NetworkRed, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("Поиск займёт до 20 секунд. Как только найдём соперника, вылазка начнётся сама.",
                color = Color.White, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(10.dp))
            Spacer(Modifier.height(15.dp))
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
                shape = SlashShape,
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
            current.rules?.let { rules ->
                Text("РАНГ МАТЧА: ${rules.rankName.uppercase()}  •  ${rules.roundDurationSeconds} СЕК  •  СКОРОСТЬ ${rules.gameSpeed}",
                    color = NetworkPaper.copy(alpha = .78f), fontSize = 10.sp,
                    fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 8.dp))
            }
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
                        Text(if (finished && item.ratingDelta != null)
                            "${item.score} УЛИК  /  ${if (item.ratingDelta > 0) "+" else ""}${item.ratingDelta} РЕЙТИНГА"
                            else if (item.submitted) "${item.score} УЛИК" else if (waiting) "В ПОИСКЕ"
                            else if (expired) "ПОИСК ЗАВЕРШЁН" else "В БОЮ",
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (waiting) Button(onClick = ::leave, modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !busy, shape = SlashShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34343C))) {
                Text("ОТМЕНИТЬ ПОИСК", fontWeight = FontWeight.Black)
            }
            if (finished) Button(onClick = {
                match = null; code = ""; onMatchChanged(null)
            }, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = SlashShape,
                colors = ButtonDefaults.buttonColors(containerColor = NetworkRed)) {
                Text("НОВЫЙ ПОИСК", fontWeight = FontWeight.Black)
            }
            if (expired) Button(onClick = {
                match = null; code = ""; onMatchChanged(null)
            }, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = SlashShape,
                colors = ButtonDefaults.buttonColors(containerColor = NetworkRed)) {
                Text("ПОПРОБОВАТЬ СНОВА", fontWeight = FontWeight.Black)
            }
        }
        error?.let { Text(it, color = Color(0xFFFF9A9A), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
    }
}

@Composable
private fun PvpRatingBanner(profile: PvpProfile?) {
    val rating = profile?.rating
    Column(Modifier.fillMaxWidth().background(NetworkPaper, SlashShape)
        .border(2.dp, NetworkRed, SlashShape).padding(horizontal = 34.dp, vertical = 24.dp)) {
        Text("ТВОЙ СЛЕД В ГОРОДЕ", color = NetworkRed, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(rating?.rankName?.uppercase() ?: "ЗАГРУЖАЕМ РАНГ", color = NetworkInk,
                    fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("${rating?.wins ?: 0} ПОБЕД  /  ${rating?.losses ?: 0} ПОРАЖЕНИЙ",
                    color = NetworkInk.copy(alpha = .7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text("${rating?.points ?: 0}", color = NetworkRed, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(progress = { if (rating?.points == 1000) 1f else ((rating?.points ?: 0) % 100) / 100f },
            modifier = Modifier.fillMaxWidth().height(5.dp), color = NetworkRed,
            trackColor = Color(0xFFBDB8B2))
        Text(if (rating?.nextRankAt == null) "ВЕРШИНА ДОРОГИ СЛАВЫ • 1000 / 1000"
            else "ДО СЛЕДУЮЩЕГО РАНГА: ${rating.nextRankAt - rating.points}",
            color = NetworkInk, fontSize = 10.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 5.dp))
        Text("ПОБЕДА +10  /  ПОРАЖЕНИЕ −5  /  НИЧЬЯ 0",
            color = NetworkInk.copy(alpha = .7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PvpRoadPanel(profile: PvpProfile?, modifier: Modifier) {
    val road = profile?.road.orEmpty()
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item {
            Text("ДОРОГА СЛАВЫ", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text("Награда за новый ранг выдаётся один раз. Потеряешь ранг и вернёшься — повторной выплаты не будет.",
                color = NetworkPaper, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(6.dp))
        }
        if (profile == null) item { Text("ЗАГРУЖАЕМ ДОРОГУ СЛАВЫ...", color = NetworkPaper) }
        items(road) { step ->
            val reached = (profile?.rating?.points ?: 0) >= step.at
            Row(Modifier.fillMaxWidth().background(if (reached) NetworkRed else Color(0xFF24242B), SlashShape)
                .padding(horizontal = 32.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("${step.at} / 1000  •  ${step.name.uppercase()}", color = Color.White,
                        fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(if (step.claimed) "НАГРАДА ПОЛУЧЕНА" else "НАГРАДА: +${step.rewardClues} УЛИК",
                        color = NetworkPaper.copy(alpha = .82f), fontSize = 10.sp)
                }
                Text(if (reached) "✓" else "◇", color = Color.White,
                    fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun PvpLeaderboardPanel(playerId: String, entries: List<PvpRating>, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("ЛУЧШИЕ ОХОТНИКИ", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text("Отдельный рейтинг сетевой охоты", color = NetworkPaper, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
        }
        if (entries.isEmpty()) item { Text("ПОКА НЕТ УЧАСТНИКОВ", color = NetworkPaper) }
        items(entries) { entry ->
            val own = entry.playerId == playerId
            Row(Modifier.fillMaxWidth().background(if (own) NetworkRed else Color(0xFF24242B), SlashShape)
                .padding(horizontal = 30.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#${entry.place ?: 0}", color = Color.White, fontSize = 18.sp,
                    fontWeight = FontWeight.Black, modifier = Modifier.width(45.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (own) "ТЫ • ${entry.playerName}" else entry.playerName,
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(entry.rankName.uppercase(), color = NetworkPaper.copy(alpha = .78f), fontSize = 10.sp)
                }
                Text("${entry.points}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
