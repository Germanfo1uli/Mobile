package com.example.labmob

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
    onBack: () -> Unit,
    onStartGame: (String) -> Unit,
    onMatchChanged: (MultiplayerMatch?) -> Unit,
) {
    val api = remember { BackendApi() }
    val scope = rememberCoroutineScope()
    var match by remember { mutableStateOf(initialMatch) }
    var code by rememberSaveable { mutableStateOf(initialMatch?.code.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }

    fun launchRequest(block: suspend () -> MultiplayerMatch) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { block() }.onSuccess {
                match = it; code = it.code; error = null; onMatchChanged(it)
            }.onFailure { error = it.message ?: "Нет связи с сервером" }
            busy = false
        }
    }

    LaunchedEffect(match?.code, match?.status) {
        while (match != null && match?.status != "finished") {
            delay(2_000)
            runCatching { api.getMatch(match!!.code) }.onSuccess {
                match = it; onMatchChanged(it)
            }
        }
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(NetworkInk).padding(horizontal = 18.dp, vertical = 18.dp)) {
        HuntBackButton(onBack)
        RansomTitle("СЕТЕВАЯ ОХОТА", size = 31)
        Text("ОДНА КОМНАТА • ОБЩИЙ РЕЗУЛЬТАТ • ПОБЕДИТЕЛЬ ОПРЕДЕЛЯЕТСЯ СЕРВЕРОМ",
            color = NetworkPaper.copy(alpha = .78f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        if (match == null) {
            Button({ launchRequest { api.createMatch(player.id) } }, Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = NetworkRed), enabled = !busy) {
                Text("СОЗДАТЬ КОМНАТУ", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(code, { code = it.uppercase().take(6) }, Modifier.fillMaxWidth(), label = { Text("КОД КОМНАТЫ") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NetworkRed, unfocusedBorderColor = NetworkPaper))
            Spacer(Modifier.height(8.dp))
            Button({ launchRequest { api.joinMatch(code, player.id) } }, Modifier.fillMaxWidth().height(54.dp), enabled = code.length == 6 && !busy, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3D9C))) {
                Text("ПОДКЛЮЧИТЬСЯ", fontWeight = FontWeight.Black)
            }
        } else {
            val current = match!!
            Text("КОД ДЛЯ ДРУГИХ ИГРОКОВ", color = NetworkPaper.copy(alpha = .7f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(current.code, color = NetworkRed, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp)
            Text(if (current.status == "waiting") "ОЖИДАНИЕ ИГРОКОВ" else if (current.status == "running") "МАТЧ ИДЁТ" else "МАТЧ ЗАВЕРШЁН", color = Color.White, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(current.players) { item ->
                    Row(Modifier.fillMaxWidth().background(if (item.playerId == current.hostPlayerId) NetworkRed else Color(0xFF23232B), SlashShape).padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.playerName, color = Color.White, fontWeight = FontWeight.Black)
                        Text(if (item.submitted) "${item.score} очков" else "в комнате", color = NetworkPaper, fontWeight = FontWeight.Bold)
                    }
                }
                current.winner?.let { winner -> item { Text("ПОБЕДИТЕЛЬ: ${winner.playerName} • ${winner.score}", color = NetworkRed, fontSize = 18.sp, fontWeight = FontWeight.Black) } }
            }
            if (current.status == "waiting" && current.hostPlayerId == player.id) Button({ launchRequest { api.startMatch(current.code, player.id) } }, Modifier.fillMaxWidth().height(54.dp), enabled = current.players.size >= 2 && !busy, colors = ButtonDefaults.buttonColors(containerColor = NetworkRed)) { Text("НАЧАТЬ МАТЧ", fontWeight = FontWeight.Black) }
            if (current.status == "running") Button({ onStartGame(current.code) }, Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = NetworkRed)) { Text("НАЧАТЬ ВЫЛАЗКУ", fontWeight = FontWeight.Black) }
            if (current.status == "finished") Button({ match = null; onMatchChanged(null) }, Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = NetworkRed)) { Text("НОВАЯ КОМНАТА", fontWeight = FontWeight.Black) }
        }
        error?.let { Text(it, color = Color(0xFFFF9A9A), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
    }
}
