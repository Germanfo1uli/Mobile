package com.example.labmob

import android.app.Activity
import android.media.MediaPlayer
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
import androidx.compose.ui.draw.clip
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

private enum class MenuDestination { HOME, PROFILE, RULES, AUTHORS, SETTINGS, MAP, GAME, RESULT, RECORDS }

@Composable
fun MainMenuScreen(player: SavedPlayerProfile, onDeleteSave: () -> Unit = {}, onChangePlayer: () -> Unit = {}, onPlayerUpdated: (SavedPlayerProfile) -> Unit = {}) {
    var destination by rememberSaveable { mutableStateOf(MenuDestination.HOME) }
    var showVelvetRoom by rememberSaveable { mutableStateOf(false) }
    var syncMessage by rememberSaveable { mutableStateOf("Настройки ещё не менялись") }
    var resultScore by rememberSaveable { mutableIntStateOf(0) }
    var resultHits by rememberSaveable { mutableIntStateOf(0) }
    var resultMisses by rememberSaveable { mutableIntStateOf(0) }
    var bestScore by remember { mutableStateOf<Int?>(null) }
    val api = remember { BackendApi() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(destination, player.id) {
        if (destination == MenuDestination.HOME) {
            runCatching { api.playerResults(player.id) }
                .onSuccess { bestScore = it.maxOfOrNull(HuntRecord::score) }
        }
    }

    LoopingMenuMusic(destination == MenuDestination.GAME)
    BackHandler(enabled = showVelvetRoom || (destination != MenuDestination.HOME && destination != MenuDestination.GAME)) {
        if (showVelvetRoom) showVelvetRoom = false else destination = MenuDestination.HOME
    }

    Box(Modifier.fillMaxSize().background(MenuInk).testTag("main_menu")) {
        Image(
            painterResource(R.drawable.althunt_city_collage_v2), null, Modifier.fillMaxSize().alpha(if (destination == MenuDestination.HOME) 0.82f else 0.34f),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.05f), 0.48f to Color.Black.copy(alpha = 0.42f), 1f to MenuInk),
            ),
        )
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            when (destination) {
                MenuDestination.HOME -> MenuDashboard(
                    player,
                    bestScore,
                    { destination = MenuDestination.PROFILE },
                    { destination = MenuDestination.MAP },
                    { destination = MenuDestination.RULES },
                    { destination = MenuDestination.AUTHORS },
                    { destination = MenuDestination.SETTINGS },
                    { destination = MenuDestination.RECORDS },
                    onChangePlayer,
                    { showVelvetRoom = true },
                )
                MenuDestination.PROFILE -> DossierScreen(
                    player = player,
                    onBack = { destination = MenuDestination.HOME },
                    onDeleteSave = onDeleteSave,
                )
                MenuDestination.RULES -> RulesScreen { destination = MenuDestination.HOME }
                MenuDestination.AUTHORS -> AuthorsScreen { destination = MenuDestination.HOME }
                MenuDestination.SETTINGS -> SettingsScreen(player.id, player.difficulty, syncMessage, { destination = MenuDestination.HOME }) { settings ->
                    scope.launch {
                        syncMessage = "Передаём настройки..."
                        runCatching { api.saveSettings(player.id, settings) }
                            .onSuccess {
                                syncMessage = "План сохранён в PostgreSQL"
                                onPlayerUpdated(player.copy(difficulty = settings.difficulty))
                            }
                            .onFailure { syncMessage = "Не удалось сохранить: ${it.message ?: "нет связи"}" }
                    }
                }
                MenuDestination.MAP -> LevelMapScreen(
                    onBack = { destination = MenuDestination.HOME },
                    onStart = { destination = MenuDestination.GAME },
                )
                MenuDestination.GAME -> HuntGameScreen(
                    player = player,
                    onLeave = { destination = MenuDestination.MAP },
                    onFinished = { round ->
                        resultScore = round.score
                        resultHits = round.hits
                        resultMisses = round.misses
                        destination = MenuDestination.RESULT
                    },
                )
                MenuDestination.RESULT -> HuntResultScreen(
                    resultScore, resultHits, resultMisses,
                    onMap = { destination = MenuDestination.MAP },
                    onRecords = { destination = MenuDestination.RECORDS },
                    onMenu = { destination = MenuDestination.HOME },
                )
                MenuDestination.RECORDS -> RecordsScreen { destination = MenuDestination.HOME }
            }
        }
    }
    if (showVelvetRoom) VelvetRoomDialog { showVelvetRoom = false }
}

private object MenuMusicController {
    private var player: MediaPlayer? = null
    private var track: Int? = null
    fun play(activity: Activity, game: Boolean) {
        val requestedTrack = if (game) R.raw.game_theme else R.raw.menu_theme
        if (track != requestedTrack) { player?.release(); player = null; track = requestedTrack }
        val active = player ?: MediaPlayer.create(activity.applicationContext, requestedTrack)?.apply {
            isLooping = true
            setVolume(0.34f, 0.34f)
        }?.also { player = it }
        if (active?.isPlaying == false) active.start()
    }
    fun pause() { if (player?.isPlaying == true) player?.pause() }
    fun release() { player?.release(); player = null; track = null }
}

@Composable
private fun LoopingMenuMusic(game: Boolean) {
    val activity = LocalContext.current as? Activity
    val lifecycleOwner = LocalContext.current as? LifecycleOwner
    DisposableEffect(activity, lifecycleOwner, game) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> activity?.let { MenuMusicController.play(it, game) }
                Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) MenuMusicController.pause()
                Lifecycle.Event.ON_DESTROY -> if (activity?.isChangingConfigurations != true) MenuMusicController.release()
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) activity?.let { MenuMusicController.play(it, game) }
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) MenuMusicController.pause()
        }
    }
}

@Composable
private fun MenuDashboard(
    player: SavedPlayerProfile,
    bestScore: Int?,
    onProfile: () -> Unit,
    onPlay: () -> Unit,
    onRules: () -> Unit,
    onAuthors: () -> Unit,
    onSettings: () -> Unit,
    onRecords: () -> Unit,
    onChangePlayer: () -> Unit,
    onVelvetRoom: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 14.dp).testTag("menu_dashboard"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            PlayerStrip(player, onProfile)
            Spacer(Modifier.height(3.dp))
            RansomTitle("ALTHUNT", Modifier.fillMaxWidth(), 50, TextAlign.Center)
            if (bestScore != null) Text("ЛУЧШИЙ РЕЗУЛЬТАТ  /  $bestScore УЛИК", color = Color.White,
                fontSize = 11.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.background(MenuRed, SlashShape).padding(horizontal = 14.dp, vertical = 7.dp))
        }
        item {
            CharacterDialogue(
                R.drawable.interrogator_dialogue_v2,
                "Следователь",
                "Наводка получена. Первый район открыт. Выходи, пока след не остыл.",
            )
        }
        item { SlashMenuItem("→", "НА ВЫЛАЗКУ", "КАРТА ГОРОДА И ПЕРВАЯ НАВОДКА", true, false, "play_menu_item") { onPlay() } }
        item { SlashMenuItem("01", "ИНСТРУКТАЖ", "УСЛОВИЯ СДЕЛКИ", true, true, "rules_menu_item") { onRules() } }
        item { SlashMenuItem("02", "КОМАНДА", "ЛЮДИ ИЗ ТЕНИ", true, false, "authors_menu_item") { onAuthors() } }
        item { SlashMenuItem("03", "ПОДГОТОВКА", "ПАРАМЕТРЫ ОПЕРАЦИИ", true, true, "settings_menu_item") { onSettings() } }
        item { SlashMenuItem("04", "РЕКОРДЫ", "ЛУЧШИЕ ДЕЛА ОПЕРАЦИИ", true, false, "records_menu_item") { onRecords() } }
        item { SlashMenuItem("05", "СМЕНИТЬ ИГРОКА", "НОВОЕ ИЛИ СОХРАНЁННОЕ ДОСЬЕ", true, true, "switch_player_menu_item") { onChangePlayer() } }
        item {
            SlashMenuItem(
                "?", "БАРХАТНАЯ КОМНАТА", "ВЫ ВИДИТЕ СТРАННУЮ СИНЮЮ ДВЕРЬ",
                true, false, "velvet_room_menu_item", accent = VelvetBlue,
            ) { onVelvetRoom() }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PlayerStrip(player: SavedPlayerProfile, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().rotate(-1.4f).background(MenuPaper, ReverseSlashShape).clickable(onClick = onClick).testTag("profile_menu_item").padding(horizontal = 17.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(52.dp).rotate(-4f).background(MenuInk, SlashShape), contentAlignment = Alignment.Center) {
            Text(player.fullName.trim().take(1).uppercase(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text(player.fullName.uppercase(), color = MenuInk, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text("${player.course} КУРС  •  ${player.zodiac.uppercase()}", color = MenuRed, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Text("ДОСЬЕ\n→", color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            modifier = Modifier.rotate(3f).background(MenuRed, SlashShape).padding(horizontal = 12.dp, vertical = 8.dp))
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
                Modifier.fillMaxWidth().rotate(-1.2f).background(MenuRed, SlashShape).padding(horizontal = 20.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(74.dp).rotate(-5f).background(MenuInk, ReverseSlashShape), contentAlignment = Alignment.Center) {
                    Text(player.fullName.trim().take(1).uppercase(), color = Color.White, fontSize = 35.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text("ПОД НАБЛЮДЕНИЕМ", color = MenuInk, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                        modifier = Modifier.rotate(2f).background(Color.White, SlashShape).padding(horizontal = 11.dp, vertical = 5.dp))
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
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).rotate(-1f).background(MenuInk, SlashShape).clickable { showDeleteDialog = true }
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
        Modifier.fillMaxWidth().padding(horizontal = 3.dp)
            .rotate(if (label.length % 2 == 0) .45f else -.4f)
            .background(accent, if (label.length % 2 == 0) SlashShape else ReverseSlashShape)
            .padding(start = 22.dp, end = 25.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label, color = Color.White.copy(alpha = .7f), fontSize = 9.sp, lineHeight = 11.sp,
            fontWeight = FontWeight.Black, letterSpacing = .55.sp, maxLines = 2,
            modifier = Modifier.weight(.48f),
        )
        Text(
            value.uppercase(), color = Color.White, fontSize = 14.sp, lineHeight = 17.sp,
            fontWeight = FontWeight.Black, textAlign = TextAlign.End, maxLines = 2,
            modifier = Modifier.weight(.52f),
        )
    }
}

private fun formatPlayerDate(value: Long) = if (value <= 0L) "Не указана" else SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(value))

@Composable
private fun RulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val html = remember { context.resources.openRawResource(R.raw.game_rules).bufferedReader().use { it.readText() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("rules_screen")) {
        SectionHeading("ИНСТРУКТАЖ", "СЛЕДОВАТЕЛЬ ГОВОРИТ ТОЛЬКО ОДИН РАЗ", onBack)
        CharacterDialogue(
            R.drawable.interrogator_dialogue_v2,
            "Следователь",
            "Смотри на меня, когда я говорю. Это не игра. Ниже всё, что отделяет тебя от новой камеры.",
            Modifier.height(210.dp),
        )
        AndroidView(
            factory = { WebView(it).apply { settings.javaScriptEnabled = false; setBackgroundColor(android.graphics.Color.TRANSPARENT); loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) } },
            modifier = Modifier.fillMaxWidth().weight(1f).rotate(-.4f).clip(ReverseSlashShape),
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
        Modifier.fillMaxWidth().rotate(if (author.initials == "ГП") -1.6f else 1.3f).background(author.accent, if (author.initials == "ГП") SlashShape else ReverseSlashShape).padding(horizontal = 20.dp, vertical = 17.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(108.dp).rotate(if (author.initials == "ГП") -4f else 4f).background(MenuInk, SlashShape)) {
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
private fun SettingsScreen(playerId: String, initialDifficulty: Int, syncMessage: String, onBack: () -> Unit, onSave: (GameSettings) -> Unit) {
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var targets by rememberSaveable { mutableIntStateOf(8) }
    var leads by rememberSaveable { mutableIntStateOf(15) }
    var duration by rememberSaveable { mutableIntStateOf(60) }
    var difficulty by rememberSaveable { mutableIntStateOf(initialDifficulty) }
    LaunchedEffect(playerId) {
        runCatching { BackendApi().loadSettings(playerId) }.onSuccess {
            speed = it.gameSpeed
            targets = it.maxInsects
            leads = it.bonusIntervalSeconds
            duration = it.roundDurationSeconds
            difficulty = it.difficulty
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).testTag("settings_screen")) {
        SectionHeading("ПОДГОТОВКА", "НАСТРОЙ УСЛОВИЯ ДО ВЫХОДА В ГОРОД", onBack)
        SettingCard("СЛОЖНОСТЬ", "Уровень риска и цена каждой цели", "$difficulty / 5", difficulty.toFloat(), { difficulty = it.roundToInt() }, 1f..5f, 3)
        SettingCard("СКОРОСТЬ ОХОТЫ", "Темп движения целей", String.format(Locale.US, "%.1fx", speed), speed, { speed = it }, 0.5f..2f, 5)
        SettingCard("МАКСИМУМ АЛЬТУШЕК", "Одновременно в зоне операции", targets.toString(), targets.toFloat(), { targets = it.roundToInt() }, 3f..15f, 11)
        SettingCard("ИНТЕРВАЛ НАВОДОК", "Как часто полиция даёт бонус", "$leads сек", leads.toFloat(), { leads = it.roundToInt() }, 5f..30f, 24)
        SettingCard("ДЛИТЕЛЬНОСТЬ РАУНДА", "Время одной операции", "$duration сек", duration.toFloat(), { duration = it.roundToInt() }, 30f..180f, 14)
        Button(
            { onSave(GameSettings(speed, targets, leads, duration, difficulty)) },
            Modifier.fillMaxWidth().height(60.dp).rotate(-1f), shape = SlashShape,
            colors = ButtonDefaults.buttonColors(containerColor = MenuRed, contentColor = Color.White),
        ) { Text("ПОДТВЕРДИТЬ ПЛАН  →", fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 16.sp) }
        Text(syncMessage, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 26.dp).testTag("backend_sync_status"), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingCard(title: String, hint: String, shown: String, value: Float, change: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, steps: Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).rotate(if (title.length % 2 == 0) .6f else -.7f)
        .background(MenuPaper, if (title.length % 2 == 0) SlashShape else ReverseSlashShape).padding(horizontal = 20.dp, vertical = 15.dp)) {
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
            modifier = Modifier.rotate(-2f).background(MenuRed, SlashShape).clickable(onClick = onBack).testTag("menu_back").padding(horizontal = 16.dp, vertical = 9.dp))
        Spacer(Modifier.height(10.dp))
        RansomTitle(title, size = if (title.length > 16) 25 else 31)
        Spacer(Modifier.height(7.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
    }
}

@Composable
private fun VelvetRoomDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().rotate(-1f).background(VelvetBlue, SlashShape).testTag("velvet_room_dialog").padding(26.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("БАРХАТНАЯ КОМНАТА", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text("Вы ещё не готовы предстать перед Игорем.", color = MenuInk, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().rotate(1f).background(Color.White, ReverseSlashShape).padding(horizontal = 24.dp, vertical = 24.dp))
                Spacer(Modifier.height(14.dp))
                Text("ЗАКРЫТЬ ДВЕРЬ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.background(MenuInk, ReverseSlashShape).clickable(onClick = onDismiss).padding(horizontal = 20.dp, vertical = 11.dp))
            }
        }
    }
}
