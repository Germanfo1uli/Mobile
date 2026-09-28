package com.example.labmob

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val VelvetNight = Color(0xFF030716)
private val VelvetCobalt = Color(0xFF122B83)
private val VelvetElectric = Color(0xFF356DFF)
private val VelvetPale = Color(0xFFEAF1FF)

@Composable
internal fun VelvetRoomScreen(
    player: SavedPlayerProfile,
    onBack: () -> Unit,
    onBalanceChanged: (Long) -> Unit,
) {
    var dialoguePage by rememberSaveable { mutableIntStateOf(0) }
    var fullIntroduction by rememberSaveable(player.id) { mutableStateOf<Boolean?>(null) }
    var room by remember { mutableStateOf<VelvetRoomState?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val api = remember { BackendApi() }
    val scope = rememberCoroutineScope()
    val firstName = remember(player.fullName) {
        player.fullName.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifBlank { "гость" }
    }
    val dialogue = if (fullIntroduction == true) {
        listOf(
            "Добро пожаловать в бархатную комнату.",
            "Я давно ждал вас, $firstName.",
            "Эта комната существует меж сном и явью, разумом и материей. Сюда попадают лишь те, кому предстоит сделать выбор.",
            "Я готов помочь вам обрести новую силу. Разумеется, если вы готовы заплатить за неё собранными уликами.",
            "Но будьте внимательны. Почти каждая сила потребует встречной платы прямо во время боя. Читайте условия сделки. Только Вектор падения и Армагедон не скрывают обратной стороны.",
        )
    } else {
        listOf("Добро пожаловать в бархатную комнату.")
    }

    LaunchedEffect(player.id) {
        runCatching { api.velvetRoom(player.id) }
            .onSuccess {
                room = it
                if (fullIntroduction == null) fullIntroduction = !it.introSeen
                onBalanceChanged(it.totalClues)
            }
            .onFailure {
                error = it.message ?: "Не удалось открыть каталог"
                if (fullIntroduction == null) fullIntroduction = false
            }
    }

    Box(Modifier.fillMaxSize().background(VelvetNight).testTag("velvet_room_screen")) {
        Image(
            painterResource(R.drawable.velvet_room_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, VelvetNight.copy(alpha = .28f), VelvetNight.copy(alpha = .82f)))))

        if (fullIntroduction == null) {
            CircularProgressIndicator(color = VelvetPale, modifier = Modifier.align(Alignment.Center))
        } else if (dialoguePage < dialogue.size) {
            Image(
                painterResource(R.drawable.igor_velvet),
                contentDescription = "Игорь",
                modifier = Modifier.align(Alignment.BottomEnd).fillMaxHeight(.84f).fillMaxWidth(1.18f).offset(y = (-140).dp),
                alignment = Alignment.BottomEnd,
                contentScale = ContentScale.Fit,
            )
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                VelvetBack(onBack)
                Spacer(Modifier.height(10.dp))
                RansomTitle("БАРХАТНАЯ КОМНАТА", Modifier.fillMaxWidth(.88f), 30)
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Box(Modifier.matchParentSize().offset(7.dp, 7.dp).background(VelvetElectric, ReverseSlashShape))
                    Column(
                        Modifier.fillMaxWidth().background(VelvetPale, SlashShape).border(2.dp, VelvetNight, SlashShape)
                            .clickable {
                                if (dialoguePage == dialogue.lastIndex && fullIntroduction == true) {
                                    scope.launch { runCatching { api.markVelvetIntroSeen(player.id) } }
                                }
                                dialoguePage++
                            }.testTag("velvet_dialogue").padding(20.dp),
                    ) {
                        Text("ИГОРЬ", color = VelvetPale, fontSize = 11.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.rotate(-2f).background(VelvetCobalt, ReverseSlashShape).padding(horizontal = 13.dp, vertical = 5.dp))
                        Spacer(Modifier.height(9.dp))
                        Text(dialogue[dialoguePage], color = VelvetNight, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                        Text(if (dialoguePage == dialogue.lastIndex) "ОТКРЫТЬ КАТАЛОГ  →" else "ДАЛЕЕ  →",
                            color = VelvetCobalt, fontSize = 10.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.fillMaxWidth().padding(top = 9.dp), textAlign = TextAlign.End)
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                item {
                    Spacer(Modifier.height(12.dp))
                    VelvetBack(onBack)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            RansomTitle("КАТАЛОГ ТЕУРГИЙ", size = 27)
                            Text("СИЛА ИМЕЕТ ЦЕНУ", color = VelvetPale, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                        }
                        Image(painterResource(R.drawable.igor_velvet), "Игорь", Modifier.size(112.dp), contentScale = ContentScale.Fit)
                    }
                    Text(
                        "УЛИКИ  /  ${formatClues(room?.totalClues ?: 0)}",
                        color = VelvetNight, fontSize = 15.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.rotate(-1f).background(VelvetPale, ReverseSlashShape).padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                    if (error != null) {
                        Text(error.orEmpty(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp).background(Color(0xFF760B24), SlashShape).padding(12.dp))
                    }
                }
                if (room == null && error == null) {
                    item { Box(Modifier.fillParentMaxHeight(.55f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VelvetPale)
                    } }
                }
                items(room?.theurgies.orEmpty(), key = { it.id }) { item ->
                    TheurgyCard(
                        item = item,
                        balance = room?.totalClues ?: 0,
                        busy = busyId != null,
                        onAction = {
                            busyId = item.id
                            error = null
                            scope.launch {
                                val result = if (item.owned) {
                                    runCatching { api.selectTheurgy(player.id, item.id) }
                                } else {
                                    runCatching { api.purchaseTheurgy(player.id, item.id) }
                                }
                                result.onSuccess {
                                    room = it
                                    onBalanceChanged(it.totalClues)
                                }.onFailure { error = it.message ?: "Сделка не состоялась" }
                                busyId = null
                            }
                        },
                    )
                }
                item {
                    Text("Вернитесь, когда пожелаете изменить заключённый контракт.", color = VelvetPale.copy(alpha = .72f),
                        fontSize = 11.sp, lineHeight = 16.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp))
                }
            }
        }
    }
}

@Composable
private fun TheurgyCard(item: TheurgyItem, balance: Long, busy: Boolean, onAction: () -> Unit) {
    val active = item.selected
    val canBuy = item.owned || balance >= item.price
    val accent = if (active) VelvetElectric else VelvetCobalt
    Row(
        Modifier.fillMaxWidth().rotate(if (item.id == "chrono") .55f else -.45f)
            .background(VelvetPale, if (item.id == "scarlet") ReverseSlashShape else SlashShape)
            .border(if (active) 4.dp else 2.dp, accent, if (item.id == "scarlet") ReverseSlashShape else SlashShape)
            .padding(horizontal = 17.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(48.dp).background(accent, ReverseSlashShape), contentAlignment = Alignment.Center) {
            Text(when {
                item.id == "moonfall" -> "×3"
                item.effectType == "freeze" -> "Ⅱ"
                item.effectType == "multiplier" -> "×2"
                item.effectType == "shield" -> "0"
                item.effectType == "armageddon" -> "X"
                else -> "↘"
            },
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text(item.title.uppercase(), color = VelvetNight, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(item.description, color = VelvetNight.copy(alpha = .68f), fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            val label = when {
                item.selected -> "ВЫБРАНО"
                item.owned -> "ВЫБРАТЬ"
                else -> "КУПИТЬ  /  ${formatClues(item.price)}"
            }
            Text(
                label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(if (canBuy || item.selected) 1f else .45f)
                    .background(if (item.selected) VelvetNight else accent, SlashShape)
                    .clickable(enabled = !busy && !item.selected && canBuy, onClick = onAction)
                    .testTag("theurgy_${item.id}").padding(horizontal = 13.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun VelvetBack(onClick: () -> Unit) {
    Text("← ЗАКРЫТЬ ДВЕРЬ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.background(VelvetCobalt, SlashShape).clickable(onClick = onClick)
            .testTag("velvet_back").padding(horizontal = 15.dp, vertical = 9.dp))
}

private fun formatClues(value: Long): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU")).format(value)
