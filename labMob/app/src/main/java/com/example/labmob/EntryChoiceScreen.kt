package com.example.labmob

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color

@Composable
fun EntryChoiceScreen(
    savedPlayer: SavedPlayerProfile?,
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onDeleteSave: () -> Unit = {},
    onSelectPlayer: (SavedPlayerProfile) -> Unit = {},
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showPlayerPicker by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(HuntInk).testTag("entry_menu")) {
        CrimeBackdrop(.42f)
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                "СЕКРЕТНАЯ ОПЕРАЦИЯ / 20XX", color = HuntInk, fontSize = 10.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.rotate(-2f).background(HuntPaper, SlashShape).padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(7.dp))
            RansomTitle("ALTHUNT", Modifier.fillMaxWidth(), size = 50)
            Text(
                "ТВОЯ СВОБОДА ТЕПЕРЬ ЗАВИСИТ ОТ НАС.", color = androidx.compose.ui.graphics.Color.White,
                fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                modifier = Modifier.rotate(1f).background(HuntRed, ReverseSlashShape).padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(22.dp))
            SlashMenuItem("01", "НОВАЯ ИГРА", "НАЧАТЬ ДОПРОС", true, true, "new_game") { onNewGame() }
            Spacer(Modifier.height(8.dp))
            SlashMenuItem(
                "02", "ПРОДОЛЖИТЬ", savedPlayer?.fullName?.uppercase() ?: "СОХРАНЕНИЕ НЕ НАЙДЕНО",
                savedPlayer != null, false, "continue_game", onClick = onContinue,
            )
            Spacer(Modifier.height(8.dp))
            SlashMenuItem("03", "ВЫБРАТЬ ДОСЬЕ", "ИГРОКИ ИЗ БАЗЫ ДАННЫХ", true, true, "choose_player") {
                showPlayerPicker = true
            }
            if (savedPlayer != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 9.dp).rotate(-.5f).background(HuntInk, SlashShape).padding(horizontal = 15.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${savedPlayer.course} КУРС", color = HuntRed, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text(" / ${savedPlayer.zodiac.uppercase()}", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "УДАЛИТЬ", color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable { showDeleteDialog = true }.testTag("delete_save").padding(8.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("ВЫБЕРИ СВОЙ СЛЕДУЮЩИЙ ХОД", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .65f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        }
    }
    if (showDeleteDialog) DeleteSaveDialog(
        onConfirm = { showDeleteDialog = false; onDeleteSave() },
        onDismiss = { showDeleteDialog = false },
    )
    if (showPlayerPicker) PlayerPickerDialog(
        onDismiss = { showPlayerPicker = false },
        onSelect = { selected -> showPlayerPicker = false; onSelectPlayer(selected) },
    )
}

@Composable
private fun PlayerPickerDialog(onDismiss: () -> Unit, onSelect: (SavedPlayerProfile) -> Unit) {
    var players by remember { mutableStateOf<List<SavedPlayerProfile>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { BackendApi().listPlayers() }
            .onSuccess { players = it }
            .onFailure { error = it.message ?: "Сервер недоступен" }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(HuntInk, SlashShape).padding(20.dp).testTag("player_picker")) {
            Text("АРХИВ ДОСЬЕ", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Выбери существующего игрока", color = HuntPaper, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            when {
                error != null -> Text("Нет связи с сервером: $error", color = Color.White, fontSize = 13.sp)
                players.isEmpty() -> Text("Загружаем архив или пока нет игроков", color = Color.White, fontSize = 13.sp)
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(players, key = { it.id }) { player ->
                        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).background(HuntPaper, ReverseSlashShape)
                            .clickable { onSelect(player) }.padding(14.dp)) {
                            Text(player.fullName.uppercase(), color = HuntInk, fontSize = 15.sp, fontWeight = FontWeight.Black)
                            Text("${player.course} курс / сложность ${player.difficulty}", color = HuntRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("ЗАКРЫТЬ", color = Color.White, fontWeight = FontWeight.Black,
                modifier = Modifier.background(HuntRed, SlashShape).clickable { onDismiss() }.padding(10.dp))
        }
    }
}

@Composable
fun DeleteSaveDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().rotate(-1f)) {
            Box(Modifier.matchParentSize().offset(8.dp, 8.dp).background(HuntRed, ReverseSlashShape))
            Column(Modifier.fillMaxWidth().background(HuntPaper, SlashShape).padding(24.dp).testTag("delete_save_dialog")) {
                Text("СТЕРЕТЬ ДОСЬЕ?", color = HuntInk, fontSize = 27.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                Text("Локальные данные исчезнут навсегда. Полиция не станет их восстанавливать.", color = HuntInk.copy(alpha = .72f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(17.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DialogChoice("ОТМЕНА", false, Modifier.weight(1f), onDismiss)
                    DialogChoice("УДАЛИТЬ", true, Modifier.weight(1f).testTag("confirm_delete_save"), onConfirm)
                }
            }
        }
    }
}

@Composable
private fun DialogChoice(text: String, danger: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text, color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        modifier = modifier.rotate(if (danger) -1f else 1f).background(if (danger) HuntRed else HuntInk, SlashShape)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}
