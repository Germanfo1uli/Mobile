package com.example.labmob

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val EntryInk = Color(0xFF07070A)
private val EntryRed = Color(0xFFE60012)
private val EntryPaper = Color(0xFFF4F1E9)

@Composable
fun EntryChoiceScreen(
    savedPlayer: SavedPlayerProfile?,
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onDeleteSave: () -> Unit = {},
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EntryInk)
            .testTag("entry_menu"),
    ) {
        Image(
            painter = painterResource(R.drawable.althunt_menu_art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.6f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.22f),
                        0.44f to Color.Black.copy(alpha = 0.64f),
                        1f to EntryInk,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = "ALTHUNT",
                color = Color.White,
                fontSize = 52.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-2).sp,
            )
            Text(
                text = "ПОЛИЦЕЙСКАЯ ОПЕРАЦИЯ // ДОСТУП ОГРАНИЧЕН",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp,
                modifier = Modifier
                    .rotate(-1f)
                    .background(EntryRed)
                    .border(2.dp, Color.White)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(22.dp))
            EntryButton(
                title = "НОВАЯ ИГРА",
                subtitle = "НАЧАТЬ ДОПРОС И СОЗДАТЬ ДОСЬЕ",
                enabled = true,
                testTag = "new_game",
                onClick = onNewGame,
            )
            Spacer(Modifier.height(12.dp))
            EntryButton(
                title = "ЛОКАЛЬНОЕ СОХРАНЕНИЕ",
                subtitle = savedPlayer?.let { "ПРОДОЛЖИТЬ КАК ${it.fullName.uppercase()}" }
                    ?: "СОХРАНЕНИЕ ПОКА НЕ НАЙДЕНО",
                enabled = savedPlayer != null,
                testTag = "continue_game",
                onClick = onContinue,
            )
            if (savedPlayer != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(Color.Black.copy(alpha = 0.82f))
                        .border(2.dp, Color.White)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${savedPlayer.course} КУРС  •  ${savedPlayer.zodiac.uppercase()}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "СОХРАНЕНО НА УСТРОЙСТВЕ",
                        color = EntryRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.End,
                    )
                }
                Text(
                    text = "УДАЛИТЬ ЛОКАЛЬНОЕ СОХРАНЕНИЕ",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 10.dp)
                        .background(EntryInk.copy(alpha = 0.9f))
                        .border(2.dp, EntryRed)
                        .clickable { showDeleteDialog = true }
                        .testTag("delete_save")
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
    }
    if (showDeleteDialog) {
        DeleteSaveDialog(
            onConfirm = {
                showDeleteDialog = false
                onDeleteSave()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun EntryButton(
    title: String,
    subtitle: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(if (enabled) -0.7f else 0.5f)
            .alpha(if (enabled) 1f else 0.54f)
            .background(if (enabled) EntryRed else Color(0xFF29292F))
            .border(3.dp, Color.White)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 17.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
            )
            Text(
                text = subtitle,
                color = if (enabled) EntryPaper.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.55f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp,
            )
        }
        Text(
            text = if (enabled) "→" else "×",
            color = EntryInk,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.background(Color.White).padding(horizontal = 11.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun DeleteSaveDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .rotate(-1f)
                .background(EntryInk)
                .border(4.dp, Color.White)
                .testTag("delete_save_dialog")
                .padding(20.dp),
        ) {
            Text("УДАЛИТЬ СОХРАНЕНИЕ?", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            Spacer(Modifier.height(10.dp))
            Text(
                "Досье исчезнет с этого устройства. Вернуть его будет нельзя.",
                color = EntryInk,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(14.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "ОТМЕНА", color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).border(2.dp, Color.White).clickable(onClick = onDismiss).padding(vertical = 11.dp),
                )
                Text(
                    "УДАЛИТЬ", color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).background(EntryRed).border(2.dp, Color.White).clickable(onClick = onConfirm).testTag("confirm_delete_save").padding(vertical = 11.dp),
                )
            }
        }
    }
}
