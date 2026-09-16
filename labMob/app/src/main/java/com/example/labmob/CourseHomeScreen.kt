package com.example.labmob

import android.app.Activity
import android.media.MediaPlayer
import android.util.Log
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val MenuInk = Color(0xFF08080B)
private val MenuRed = Color(0xFFE60012)
private val MenuDeepRed = Color(0xFF71000C)
private val MenuPaper = Color(0xFFF4F1E9)
private val MenuMuted = Color(0xFF24242A)
private val VelvetBlue = Color(0xFF101D56)

@Composable
fun RegistrationFlowScreen(onRegistered: (String, PlayerProfile) -> Unit) {
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var submissionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val api = remember { BackendApi() }
    val scope = rememberCoroutineScope()
    RegistrationScreen(
        isSubmitting = isSubmitting,
        submissionMessage = submissionMessage,
        onProfileSubmitted = { profile ->
            if (!isSubmitting) {
                isSubmitting = true
                submissionMessage = "Проверяем сведения и открываем дело..."
                scope.launch {
                    runCatching { api.createPlayer(profile) }
                        .onSuccess { onRegistered(it, profile) }
                        .onFailure { submissionMessage = "Сервер не принял досье: ${it.message ?: "проверь соединение"}" }
                    isSubmitting = false
                }
            }
        },
    )
}

private enum class MenuDestination { HOME, PROFILE, RULES, AUTHORS, SETTINGS }

@Composable
fun MainMenuScreen(player: SavedPlayerProfile, onDeleteSave: () -> Unit = {}) {
    var destination by rememberSaveable { mutableStateOf(MenuDestination.HOME) }
    var showVelvetRoom by rememberSaveable { mutableStateOf(false) }
    var syncMessage by rememberSaveable { mutableStateOf("Настройки ещё не менялись") }
    val api = remember { BackendApi() }
    val scope = rememberCoroutineScope()

    LoopingMenuMusic()
    BackHandler(enabled = showVelvetRoom || destination != MenuDestination.HOME) {
        if (showVelvetRoom) showVelvetRoom = false else destination = MenuDestination.HOME
    }

    Box(Modifier.fillMaxSize().background(MenuInk).testTag("main_menu")) {
        Image(
            painterResource(R.drawable.althunt_menu_art), null, Modifier.fillMaxSize().alpha(if (destination == MenuDestination.HOME) 0.52f else 0.18f),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.12f), 0.55f to Color.Black.copy(alpha = 0.73f), 1f to MenuInk),
            ),
        )
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            when (destination) {
                MenuDestination.HOME -> MenuDashboard(
                    player,
                    { destination = MenuDestination.PROFILE },
                    { destination = MenuDestination.RULES },
                    { destination = MenuDestination.AUTHORS },
                    { destination = MenuDestination.SETTINGS },
                    { showVelvetRoom = true },
                )
                MenuDestination.PROFILE -> DossierScreen(
                    player = player,
                    onBack = { destination = MenuDestination.HOME },
                    onDeleteSave = onDeleteSave,
                )
                MenuDestination.RULES -> RulesScreen { destination = MenuDestination.HOME }
                MenuDestination.AUTHORS -> AuthorsScreen { destination = MenuDestination.HOME }
                MenuDestination.SETTINGS -> SettingsScreen(syncMessage, { destination = MenuDestination.HOME }) { settings ->
                    scope.launch {
                        syncMessage = "Передаём настройки..."
                        runCatching { api.saveSettings(player.id, settings) }
                            .onSuccess { syncMessage = "План сохранён в PostgreSQL" }
                            .onFailure { syncMessage = "Не удалось сохранить: ${it.message ?: "нет связи"}" }
                    }
                }
            }
        }
    }
    if (showVelvetRoom) VelvetRoomDialog { showVelvetRoom = false }
}

private object MenuMusicController {
    private var player: MediaPlayer? = null
    fun play(activity: Activity) {
        val wasCreated = player == null
        val active = player ?: MediaPlayer.create(activity.applicationContext, R.raw.menu_theme)?.apply {
            isLooping = true
            setVolume(0.34f, 0.34f)
        }?.also { player = it }
        if (active?.isPlaying == false) active.start()
        Log.d("AlthuntMusic", "play created=$wasCreated positionMs=${active?.currentPosition ?: -1}")
    }
    fun pause() { if (player?.isPlaying == true) player?.pause() }
    fun release() { player?.release(); player = null }
}

@Composable
private fun LoopingMenuMusic() {
    val activity = LocalContext.current as? Activity
    val lifecycleOwner = LocalContext.current as? LifecycleOwner
    DisposableEffect(activity, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> activity?.let(MenuMusicController::play)
                Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) MenuMusicController.pause()
                Lifecycle.Event.ON_DESTROY -> if (activity?.isChangingConfigurations != true) MenuMusicController.release()
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) activity?.let(MenuMusicController::play)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) MenuMusicController.pause()
        }
    }
}

@Composable
private fun MenuDashboard(
    player: SavedPlayerProfile,
    onProfile: () -> Unit,
    onRules: () -> Unit,
    onAuthors: () -> Unit,
    onSettings: () -> Unit,
    onVelvetRoom: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("menu_dashboard"),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            PlayerStrip(player, onProfile)
            Spacer(Modifier.height(18.dp))
            Text("ALTHUNT", color = Color.White, fontSize = 45.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = (-2).sp)
            Text(
                "ЕЩЁ НЕ ВРЕМЯ ДЛЯ ОХОТЫ.", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                modifier = Modifier.rotate(-1f).background(MenuRed).border(2.dp, Color.White).padding(horizontal = 9.dp, vertical = 6.dp),
            )
        }
        item { MenuActionCard("НА ОХОТУ", "ОПЕРАЦИЯ ЕЩЁ НЕ ГОТОВА", MenuIcon.LOCK, false, MenuMuted, "play_locked") {} }
        item { MenuActionCard("ИНСТРУКТАЖ", "СЛЕДОВАТЕЛЬ ОБЪЯСНИТ ЗАДАЧУ", MenuIcon.BRIEFING, true, MenuRed, "rules_menu_item", onClick = onRules) }
        item { MenuActionCard("КОМАНДА", "ТЕ, КТО ОСТАЛСЯ В ТЕНИ", MenuIcon.CREW, true, Color.White, "authors_menu_item", true, onAuthors) }
        item { MenuActionCard("ПОДГОТОВКА", "НАСТРОИТЬ УСЛОВИЯ ОХОТЫ", MenuIcon.SETTINGS, true, MenuDeepRed, "settings_menu_item", onClick = onSettings) }
        item { MenuActionCard("БАРХАТНАЯ КОМНАТА", "ДВЕРЬ, КОТОРОЙ НЕ ДОЛЖНО БЫТЬ", MenuIcon.DOOR, true, VelvetBlue, "velvet_room_menu_item", onClick = onVelvetRoom) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PlayerStrip(player: SavedPlayerProfile, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().rotate(-1f).background(MenuPaper).border(3.dp, MenuInk).clickable(onClick = onClick).testTag("profile_menu_item").padding(13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(54.dp).background(MenuInk).border(3.dp, MenuRed), contentAlignment = Alignment.Center) {
            Text(player.fullName.trim().take(1).uppercase(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text(player.fullName.uppercase(), color = MenuInk, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text("${player.course} КУРС  •  ${player.zodiac.uppercase()}", color = MenuRed, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Text("ОТКРЫТЬ\nДОСЬЕ  →", color = MenuInk, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
    }
}

private enum class MenuIcon { LOCK, BRIEFING, CREW, SETTINGS, DOOR }

@Composable
private fun MenuActionCard(
    title: String, subtitle: String, icon: MenuIcon, enabled: Boolean, accent: Color, testTag: String,
    darkText: Boolean = false, onClick: () -> Unit,
) {
    val foreground = if (darkText) MenuInk else Color.White
    Row(
        Modifier.fillMaxWidth().rotate(if (title.length % 2 == 0) 0.7f else -0.6f).alpha(if (enabled) 1f else 0.58f)
            .background(accent).border(3.dp, Color.White).clickable(enabled = enabled, onClick = onClick).testTag(testTag).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = foreground, fontSize = 22.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            Text(subtitle, color = foreground.copy(alpha = 0.75f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
        }
        Box(
            Modifier.size(48.dp).rotate(3f).background(if (darkText) MenuInk else Color.White),
            contentAlignment = Alignment.Center,
        ) {
            MenuGlyph(icon, if (darkText) Color.White else MenuInk)
        }
    }
}

@Composable
private fun MenuGlyph(icon: MenuIcon, color: Color) {
    Canvas(Modifier.size(29.dp)) {
        val stroke = Stroke(width = size.minDimension * 0.09f)
        when (icon) {
            MenuIcon.LOCK -> {
                drawArc(color, 200f, 140f, false, topLeft = androidx.compose.ui.geometry.Offset(size.width * .25f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width * .5f, size.height * .62f), style = stroke)
                drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * .18f, size.height * .42f),
                    size = androidx.compose.ui.geometry.Size(size.width * .64f, size.height * .48f), style = stroke)
            }
            MenuIcon.BRIEFING -> {
                val bubble = Path().apply {
                    moveTo(size.width * .08f, size.height * .14f); lineTo(size.width * .92f, size.height * .14f)
                    lineTo(size.width * .92f, size.height * .7f); lineTo(size.width * .56f, size.height * .7f)
                    lineTo(size.width * .35f, size.height * .9f); lineTo(size.width * .38f, size.height * .7f)
                    lineTo(size.width * .08f, size.height * .7f); close()
                }
                drawPath(bubble, color, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .24f, size.height * .36f), androidx.compose.ui.geometry.Offset(size.width * .76f, size.height * .36f), stroke.width)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .24f, size.height * .52f), androidx.compose.ui.geometry.Offset(size.width * .62f, size.height * .52f), stroke.width)
            }
            MenuIcon.CREW -> {
                drawCircle(color, size.width * .16f, androidx.compose.ui.geometry.Offset(size.width * .35f, size.height * .32f), style = stroke)
                drawCircle(color, size.width * .14f, androidx.compose.ui.geometry.Offset(size.width * .69f, size.height * .38f), style = stroke)
                drawArc(color, 190f, 160f, false, topLeft = androidx.compose.ui.geometry.Offset(size.width * .08f, size.height * .45f),
                    size = androidx.compose.ui.geometry.Size(size.width * .58f, size.height * .48f), style = stroke)
                drawArc(color, 190f, 160f, false, topLeft = androidx.compose.ui.geometry.Offset(size.width * .45f, size.height * .52f),
                    size = androidx.compose.ui.geometry.Size(size.width * .48f, size.height * .38f), style = stroke)
            }
            MenuIcon.SETTINGS -> {
                listOf(.25f to .68f, .5f to .35f, .75f to .58f).forEach { (y, knob) ->
                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height * y), androidx.compose.ui.geometry.Offset(size.width, size.height * y), stroke.width)
                    drawCircle(color, size.width * .1f, androidx.compose.ui.geometry.Offset(size.width * knob, size.height * y), style = Stroke(stroke.width))
                }
            }
            MenuIcon.DOOR -> {
                drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * .2f, size.height * .08f),
                    size = androidx.compose.ui.geometry.Size(size.width * .58f, size.height * .84f), style = stroke)
                drawCircle(color, size.width * .045f, androidx.compose.ui.geometry.Offset(size.width * .62f, size.height * .52f))
            }
        }
    }
}

@Composable
private fun DossierScreen(player: SavedPlayerProfile, onBack: () -> Unit, onDeleteSave: () -> Unit) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("dossier_screen"), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeading("ДОСЬЕ ЗАКЛЮЧЁННОГО", "МАТЕРИАЛЫ СЛЕДСТВИЯ // ДОПУСК ОГРАНИЧЕН", onBack) }
        item {
            Row(
                Modifier.fillMaxWidth().rotate(-0.7f).background(MenuRed).border(3.dp, Color.White).padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(74.dp).background(MenuInk).border(3.dp, Color.White), contentAlignment = Alignment.Center) {
                    Text(player.fullName.trim().take(1).uppercase(), color = Color.White, fontSize = 35.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text("ПОД НАБЛЮДЕНИЕМ", color = MenuInk, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                        modifier = Modifier.background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(player.fullName.uppercase(), color = Color.White, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
                    Text("ДЕЛО № ${player.id.take(8).uppercase()}", color = Color.White.copy(alpha = .76f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item { DossierField("ПОЛ", player.gender, MenuInk) }
        item { DossierField("КУРС ОБУЧЕНИЯ", "${player.course} курс", MenuMuted) }
        item { DossierField("УРОВЕНЬ РИСКА", "${player.difficulty}/5", MenuDeepRed) }
        item { DossierField("ДАТА РОЖДЕНИЯ", formatPlayerDate(player.birthDateMillis), MenuInk) }
        item { DossierField("ЗНАК ЗОДИАКА", player.zodiac, MenuRed) }
        item {
            Text(
                "УДАЛИТЬ ЛОКАЛЬНОЕ СОХРАНЕНИЕ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(MenuInk).border(2.dp, MenuRed).clickable { showDeleteDialog = true }
                    .testTag("delete_save_from_profile").padding(vertical = 13.dp),
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
    if (showDeleteDialog) DeleteSaveDialog(onConfirm = onDeleteSave, onDismiss = { showDeleteDialog = false })
}

@Composable
private fun DossierField(label: String, value: String, accent: Color) {
    Row(
        Modifier.fillMaxWidth().rotate(if (label.length % 2 == 0) .4f else -.35f).background(accent).border(2.dp, Color.White).padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White.copy(alpha = .68f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp, modifier = Modifier.weight(.42f))
        Text(value.uppercase(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(.58f))
    }
}

private fun formatPlayerDate(value: Long) = if (value <= 0L) "Не указана" else SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(value))

@Composable
private fun RulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val html = remember { context.resources.openRawResource(R.raw.game_rules).bufferedReader().use { it.readText() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("rules_screen")) {
        SectionHeading("ИНСТРУКТАЖ", "СЛЕДОВАТЕЛЬ ГОВОРИТ ТОЛЬКО ОДИН РАЗ", onBack)
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1737C7)).border(3.dp, Color.White).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.police_curator), "Следователь полиции", Modifier.size(86.dp), contentScale = ContentScale.Fit)
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text("СЛЕДОВАТЕЛЬ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("Запоминай. Повторять правила на улице никто не будет.", color = Color.White, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        AndroidView(
            factory = { WebView(it).apply { settings.javaScriptEnabled = false; setBackgroundColor(android.graphics.Color.TRANSPARENT); loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) } },
            modifier = Modifier.fillMaxWidth().weight(1f).border(3.dp, Color.White),
        )
        Spacer(Modifier.height(14.dp))
    }
}

private data class Author(val initials: String, val name: String, val contribution: String, val accent: Color, val alignment: Alignment)

@Composable
private fun AuthorsScreen(onBack: () -> Unit) {
    val authors = remember {
        listOf(
            Author("ГП", "Герман Пырцак", "Интерфейс, досье игрока и визуальная система", MenuRed, Alignment.CenterStart),
            Author("ЯК", "Яна Карпова", "Backend, PostgreSQL и сетевое взаимодействие", Color.White, Alignment.CenterEnd),
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("authors_screen")) {
        SectionHeading("КОМАНДА", "ТЕ, КТО ОСТАЛСЯ В ТЕНИ", onBack)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(authors) { AuthorCard(it) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AuthorCard(author: Author) {
    val dark = author.accent != Color.White
    Row(
        Modifier.fillMaxWidth().rotate(if (author.initials == "ГП") -1f else 1f).background(author.accent).border(3.dp, Color.White).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(96.dp).background(MenuInk).border(3.dp, if (dark) Color.White else MenuInk)) {
            Image(painterResource(R.drawable.phantom_author_duo), "Масочный аватар ${author.name}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = author.alignment)
            Text(author.initials, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                modifier = Modifier.align(Alignment.BottomStart).background(MenuInk.copy(alpha = 0.88f)).padding(horizontal = 6.dp, vertical = 3.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(author.name.uppercase(), color = if (dark) Color.White else MenuInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text(author.contribution, color = if (dark) Color.White.copy(alpha = 0.82f) else MenuInk.copy(alpha = 0.76f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsScreen(syncMessage: String, onBack: () -> Unit, onSave: (GameSettings) -> Unit) {
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var targets by rememberSaveable { mutableIntStateOf(8) }
    var leads by rememberSaveable { mutableIntStateOf(15) }
    var duration by rememberSaveable { mutableIntStateOf(60) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).testTag("settings_screen")) {
        SectionHeading("ПОДГОТОВКА", "НАСТРОЙ УСЛОВИЯ ДО ВЫХОДА В ГОРОД", onBack)
        SettingCard("СКОРОСТЬ ОХОТЫ", "Темп движения целей", String.format(Locale.US, "%.1fx", speed), speed, { speed = it }, 0.5f..2f, 5)
        SettingCard("МАКСИМУМ АЛЬТУШЕК", "Одновременно в зоне операции", targets.toString(), targets.toFloat(), { targets = it.roundToInt() }, 3f..15f, 11)
        SettingCard("ИНТЕРВАЛ НАВОДОК", "Как часто полиция даёт бонус", "$leads сек", leads.toFloat(), { leads = it.roundToInt() }, 5f..30f, 24)
        SettingCard("ДЛИТЕЛЬНОСТЬ РАУНДА", "Время одной операции", "$duration сек", duration.toFloat(), { duration = it.roundToInt() }, 30f..180f, 14)
        Button(
            { onSave(GameSettings(speed, targets, leads, duration)) },
            Modifier.fillMaxWidth().height(60.dp).rotate(-1f), shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MenuRed, contentColor = Color.White),
        ) { Text("ПОДТВЕРДИТЬ ПЛАН  →", fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 16.sp) }
        Text(syncMessage, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 26.dp).testTag("backend_sync_status"), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingCard(title: String, hint: String, shown: String, value: Float, change: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, steps: Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).background(MenuPaper).border(3.dp, MenuInk).padding(horizontal = 14.dp, vertical = 13.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MenuInk, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(hint, color = MenuInk.copy(alpha = 0.62f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(shown, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.rotate(2f).background(MenuRed).padding(horizontal = 10.dp, vertical = 7.dp))
        }
        Slider(value, change, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = MenuRed, activeTrackColor = MenuInk, inactiveTrackColor = Color(0xFFBDB9B1), activeTickColor = Color.White, inactiveTickColor = MenuInk))
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String, onBack: () -> Unit) {
    Column(Modifier.padding(top = 14.dp, bottom = 14.dp)) {
        Text("← НАЗАД", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.background(MenuRed).border(2.dp, Color.White).clickable(onClick = onBack).testTag("menu_back").padding(horizontal = 12.dp, vertical = 8.dp))
        Spacer(Modifier.height(10.dp))
        Box {
            Text(title, color = Color.Black, fontSize = 29.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                modifier = Modifier.offset(4.dp, 4.dp).background(MenuRed).padding(horizontal = 9.dp, vertical = 5.dp))
            Text(title, color = Color.White, fontSize = 29.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
    }
}

@Composable
private fun VelvetRoomDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().rotate(-1f).background(VelvetBlue).border(4.dp, Color.White).testTag("velvet_room_dialog").padding(22.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("БАРХАТНАЯ КОМНАТА", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text("Вы ещё не готовы предстать перед Игорем.", color = MenuInk, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().background(Color.White).padding(18.dp))
                Spacer(Modifier.height(14.dp))
                Text("ЗАКРЫТЬ ДВЕРЬ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.background(MenuInk).border(2.dp, Color.White).clickable(onClick = onDismiss).padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
    }
}
