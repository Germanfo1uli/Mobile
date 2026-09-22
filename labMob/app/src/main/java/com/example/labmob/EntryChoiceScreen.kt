package com.example.labmob

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@Composable
fun EntryChoiceScreen(
    savedPlayer: SavedPlayerProfile?,
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onDeleteSave: () -> Unit = {},
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
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
