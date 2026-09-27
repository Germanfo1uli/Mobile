package com.example.labmob

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt
import kotlin.math.sin

private val Paper = Color(0xFFF4F1E9)
private val Ink = Color(0xFF09090D)
private val Red = Color(0xFFE60012)

@Composable
internal fun LevelMapScreen(progress: PlayerProgress?, onBack: () -> Unit, onStart: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("level_map")) {
        HuntBackButton(onBack)
        RansomTitle("КАРТА ОХОТЫ", size = 32)
        Text("ПОЛИЦИЯ ОТМЕТИЛА ТРИ РАЙОНА. НОВЫЕ МАРШРУТЫ ОТКРЫВАЮТСЯ ЗА РЕЗУЛЬТАТ.",
            color = Color.White, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            LevelNode("01", "КРАСНЫЙ КВАРТАЛ", "ПЕРВАЯ НАВОДКА • ДОСТУП ОТКРЫТ", true) { onStart(1) }
            LevelNode(
                "02", "ПОДЗЕМНЫЙ ПЕРЕХОД",
                if (progress?.levelTwoUnlocked == true) "ЗОЛОТАЯ НАВОДКА • ДОСТУП ОТКРЫТ"
                else "ЗАМОК • НУЖНО БОЛЬШЕ ${progress?.levelTwoUnlockScore ?: 6000} УЛИК НА УРОВНЕ 1",
                progress?.levelTwoUnlocked == true,
            ) { onStart(2) }
            LevelNode("03", "ЧЁРНАЯ БАШНЯ", "ДАЖЕ ПОСЛЕ ВТОРОЙ ВЫЛАЗКИ ПОД ЗАМКОМ", false, {})
        }
        Text("СЛЕДУЮЩИЕ МАРШРУТЫ ОТКРОЮТСЯ В ДРУГИХ ГЛАВАХ.", color = Paper.copy(alpha = .7f),
            fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun LevelNode(number: String, title: String, subtitle: String, unlocked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().rotate(if (unlocked) -1f else .5f)
        .background(if (unlocked) Red else Color(0xFF2B2B31), SlashShape)
        .clickable(enabled = unlocked, onClick = onClick)
        .testTag(if (unlocked) "level_${number.toInt()}" else "level_locked_$number")
        .padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number, color = if (unlocked) Ink else Paper.copy(alpha = .5f), fontSize = 35.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            Text(subtitle, color = Color.White.copy(alpha = .8f), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(if (unlocked) "→" else "×", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun LevelTwoBriefingScreen(onBack: () -> Unit, onFinished: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    Box(
        Modifier.fillMaxSize().background(if (page == 1) Color(0xFF061638) else Ink)
            .clickable { if (page < 2) page++ else onFinished() }
            .testTag("level_two_briefing"),
    ) {
        Image(
            painterResource(R.drawable.althunt_city_collage_v2), null,
            Modifier.fillMaxSize().alpha(if (page == 1) .18f else .34f), contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                if (page == 1) Color(0xFF123D89).copy(alpha = .42f) else Color.Black.copy(alpha = .38f),
            ),
        )
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            HuntBackButton(onBack)
            RansomTitle(if (page == 1) "ТИШИНА..." else "РАЙОН 02", size = 34)
            Spacer(Modifier.weight(1f))
            when (page) {
                0 -> CharacterDialogue(
                    R.drawable.interrogator_dialogue_v2,
                    "Следователь",
                    "В этом районе появилась славная добыча. Сумеешь поймать её, здорово поможешь следствию...",
                )
                1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painterResource(R.drawable.mysterious_butterfly),
                        contentDescription = "Светящаяся бабочка",
                        modifier = Modifier.fillMaxWidth(.78f).aspectRatio(1f),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        "ТЫ ДОЛЖЕН ВСТРЕТИТЬСЯ С НИМ...\nМЫ ДОЛГО ИСКАЛИ ТЕБЯ...",
                        color = Color(0xFFC8F7FF), fontSize = 17.sp, lineHeight = 23.sp,
                        fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().background(Color(0xCC030916), SlashShape).padding(18.dp),
                    )
                }
                else -> CharacterDialogue(
                    R.drawable.interrogator_dialogue_v2,
                    "Следователь",
                    "Эй. Чего застыл? Вперёд в бой!",
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (page < 2) "КОСНИСЬ, ЧТОБЫ ПРОДОЛЖИТЬ" else "НАЧАТЬ ВЫЛАЗКУ  →",
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.End)
                    .background(if (page == 1) Color(0xFF1955A6) else Red, ReverseSlashShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
internal fun HuntGameScreen(
    player: SavedPlayerProfile,
    level: Int,
    onLeave: () -> Unit,
    onFinished: (HuntRound) -> Unit,
) {
    val viewModel: HuntGameViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val round = uiState.round
    val appContext = LocalContext.current.applicationContext
    var spriteFrames by remember { mutableStateOf<HuntSpriteFrames?>(null) }
    var frameElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val theurgyActive = (round?.theurgyRemainingMilliseconds ?: 0L) > 0L

    LaunchedEffect(player.id, level) { viewModel.begin(player.id, level) }
    LaunchedEffect(appContext) { spriteFrames = HuntSpriteCache.load(appContext) }
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (true) withFrameNanos { frameNanos ->
            if (frameNanos - lastFrameNanos >= 33_000_000L) {
                lastFrameNanos = frameNanos
                frameElapsed = SystemClock.elapsedRealtime()
            }
        }
    }
    LaunchedEffect(round?.finished, round?.id) {
        round?.takeIf(HuntRound::finished)?.let(onFinished)
    }
    LaunchedEffect(round?.tiltActive, round?.bonusesCollected) {
        if (round?.tiltActive == true) playHuntSound(appContext, R.raw.alt_slide)
    }
    TiltSensorEffect(enabled = round?.tiltActive == true && !theurgyActive) { x, y ->
        viewModel.updateTilt(x, y)
    }
    BackHandler(enabled = !uiState.pending && !theurgyActive) {
        if (round == null) onLeave() else viewModel.finish()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).testTag("hunt_game")) {
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("РАЙОН 0$level", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.background(if (level == 2) Color(0xFF9B6500) else Red, SlashShape)
                        .padding(horizontal = 13.dp, vertical = 8.dp))
                Spacer(Modifier.weight(1f))
                Text("${((round?.remainingMilliseconds ?: 0L) + 999) / 1000} СЕК", color = Ink,
                    fontSize = 18.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.background(Paper, ReverseSlashShape).padding(horizontal = 13.dp, vertical = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("УЛИКИ  ${round?.score ?: 0}", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("ПОЙМАНО ${round?.hits ?: 0}  /  МИМО ${round?.misses ?: 0}", color = Paper, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).background(Ink, SlashShape).clip(SlashShape)
                    .testTag("hunt_playfield")
                    .pointerInput(round?.id, uiState.pending) {
                        detectTapGestures { position ->
                            viewModel.tap(
                                (position.x / size.width).coerceIn(0f, 1f),
                                (position.y / size.height).coerceIn(0f, 1f),
                            )
                        }
                    },
            ) {
                Image(painterResource(R.drawable.althunt_city_collage_v2), null, Modifier.fillMaxSize().alpha(.32f), contentScale = ContentScale.Crop)
                val current = round
                val sprites = spriteFrames
                if (current != null && sprites != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        val now = frameElapsed
                        val elapsed = if (theurgyActive) 0f else ((now - current.sampledAtElapsedMs).coerceIn(0L, 900L) / 1000f)
                        current.targets.forEach { target ->
                            val projectedX = target.x + target.vx * elapsed
                            val projectedY = target.y + target.vy * elapsed
                            val x = if (current.tiltActive) projectedX.coerceIn(target.radius, 1f - target.radius)
                                else reflectedTargetPosition(projectedX, target.radius)
                            val y = if (current.tiltActive) projectedY.coerceIn(target.radius, 1f - target.radius)
                                else reflectedTargetPosition(projectedY, target.radius)
                            val frames = when (target.type) {
                                "alt_silver" -> sprites.silver
                                "alt_gold" -> sprites.gold
                                else -> sprites.red
                            }
                            val runningFrame = if (theurgyActive) 0 else ((now / 95L + (target.id.hashCode() and 3)) % frames.size).toInt()
                            val image = frames[runningFrame]
                            val targetSize = minOf(size.width, size.height) * target.radius * 2f
                            val fit = targetSize / maxOf(image.width, image.height)
                            val drawWidth = image.width * fit
                            val drawHeight = image.height * fit
                            val centerX = size.width * x
                            val bob = if (theurgyActive) 0f else sin(now / 380.0 * Math.PI * 2.0).toFloat() * 1.5.dp.toPx()
                            val centerY = size.height * y - bob
                            val flip = if (target.type == "alt_silver") target.vx > 0f else target.vx < 0f
                            withTransform({ if (flip) scale(-1f, 1f, Offset(centerX, centerY)) }) {
                                drawImage(image, srcSize = IntSize(image.width, image.height),
                                    dstOffset = IntOffset((centerX - drawWidth / 2f).roundToInt(), (centerY - drawHeight / 2f).roundToInt()),
                                    dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()), filterQuality = FilterQuality.Low)
                            }
                        }
                    }
                }
                current?.targets?.firstOrNull { it.type == "alt_gold" }?.let { golden ->
                    Text(
                        "ЗОЛОТАЯ ЦЕЛЬ • ${golden.points} УЛИК • КУРС ЦБ ${golden.goldRate?.roundToInt() ?: 0} ₽/Г",
                        color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 9.dp)
                            .background(Color(0xFFF6D45C), ReverseSlashShape).padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
                current?.bonus?.let { bonus ->
                    Text("ТЕУРГИЯ", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.offset(x = maxWidth * bonus.x - 35.dp, y = maxHeight * bonus.y - 20.dp)
                            .background(Red, SlashShape).padding(10.dp))
                }
                if (current?.tiltActive == true) {
                    Text("НАКЛОНЯЙ ТЕЛЕФОН", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 44.dp)
                            .background(Paper, ReverseSlashShape).padding(horizontal = 14.dp, vertical = 7.dp))
                }
                if (current == null || uiState.error != null) {
                    Column(Modifier.align(Alignment.Center).background(Paper, SlashShape).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.error ?: "ПОЛУЧАЕМ НАВОДКУ...", color = Ink, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        if (uiState.error != null) {
                            Spacer(Modifier.height(10.dp))
                            Text("ПОВТОРИТЬ", color = Color.White, modifier = Modifier.background(Red).clickable { viewModel.retry(player.id, level) }.padding(10.dp))
                            Text("НАЗАД К КАРТЕ", color = Ink, modifier = Modifier.clickable(onClick = onLeave).padding(10.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("КАСАЙСЯ ЦЕЛЕЙ • ПРОМАХ −${round?.missPenalty ?: 10}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f))
                Text("ЗАВЕРШИТЬ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.background(Red, ReverseSlashShape)
                        .clickable(enabled = round != null && !uiState.pending && !theurgyActive) { viewModel.finish() }
                        .padding(12.dp))
            }
        }
        if (theurgyActive) round?.let { TheurgySequence(it, frameElapsed) }
    }
}

private fun playHuntSound(context: Context, resource: Int) {
    MediaPlayer.create(context.applicationContext, resource)?.apply {
        setVolume(0.9f, 0.9f)
        setOnCompletionListener { it.release() }
        setOnErrorListener { failed, _, _ -> failed.release(); true }
        start()
    }
}

private data class HuntSpriteFrames(
    val red: List<ImageBitmap>,
    val silver: List<ImageBitmap>,
    val gold: List<ImageBitmap>,
)

private object HuntSpriteCache {
    @Volatile private var cached: HuntSpriteFrames? = null

    suspend fun load(context: Context): HuntSpriteFrames = withContext(Dispatchers.IO) {
        cached ?: synchronized(this@HuntSpriteCache) {
            cached ?: HuntSpriteFrames(
                red = listOf(R.drawable.alt_target_red_v2, R.drawable.alt_target_red_pass,
                    R.drawable.alt_target_red_opposite, R.drawable.alt_target_red_push).map { decode(context, it) },
                silver = listOf(R.drawable.alt_target_silver_v2, R.drawable.alt_target_silver_pass,
                    R.drawable.alt_target_silver_opposite, R.drawable.alt_target_silver_push).map { decode(context, it) },
                gold = listOf(R.drawable.alt_target_gold_run1, R.drawable.alt_target_gold_run2,
                    R.drawable.alt_target_gold_run3).map { decode(context, it) },
            ).also { cached = it }
        }
    }

    private fun decode(context: Context, resource: Int): ImageBitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4
            inScaled = false
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        return requireNotNull(BitmapFactory.decodeResource(context.resources, resource, options)).asImageBitmap()
    }
}

private fun reflectedTargetPosition(value: Float, radius: Float): Float {
    val span = 1f - 2f * radius
    val cycle = 2f * span
    val phase = ((value - radius) % cycle + cycle) % cycle
    return radius + if (phase <= span) phase else cycle - phase
}

@Composable
internal fun HuntResultScreen(score: Int, hits: Int, misses: Int, onMap: () -> Unit, onRecords: () -> Unit, onMenu: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("hunt_result")) {
        Spacer(Modifier.height(25.dp))
        RansomTitle("ОТЧЁТ О ВЫЛАЗКЕ", size = 30)
        Spacer(Modifier.height(18.dp))
        Text("$score", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.background(Red, SlashShape).padding(horizontal = 30.dp, vertical = 8.dp))
        Text("УЛИКИ В ДЕЛЕ  •  ПОЙМАНО $hits  •  ПРОМАХИ $misses", color = Paper,
            fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp, bottom = 20.dp))
        CharacterDialogue(R.drawable.interrogator_dialogue_v2, "Следователь", when {
            score < 1_000 -> "Слабо. С такой добычей начальство даже папку не откроет. Соберись и попробуй ещё раз."
            score < 3_000 -> "Уже лучше. Но этого пока мало, чтобы заинтересовать тех, кто решает твою судьбу."
            score < 6_000 -> "Неплохо справился. Но чтобы заинтересовать рыбку покрупнее, тебе придётся ещё поработать."
            else -> "Вот теперь вижу потенциал. Продолжай в том же духе, и разговор о твоей свободе станет серьёзным."
        })
        Spacer(Modifier.weight(1f))
        HuntAction("СНОВА НА КАРТУ", onMap)
        HuntAction("ПОСМОТРЕТЬ РЕКОРДЫ", onRecords)
        HuntAction("В ГЛАВНОЕ МЕНЮ", onMenu)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
internal fun RecordsScreen(onBack: () -> Unit) {
    var records by remember { mutableStateOf<List<HuntRecord>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true
        runCatching { BackendApi().records() }
            .onSuccess { records = it; error = null }
            .onFailure { error = it.message ?: "Сервер недоступен" }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("records_screen")) {
        HuntBackButton(onBack)
        RansomTitle("РЕКОРДЫ", size = 34)
        Text("ПОДТВЕРЖДЁННЫЕ РЕЗУЛЬТАТЫ ИЗ POSTGRESQL", color = Color.White,
            fontSize = 10.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(14.dp))
        if (loading) Text("ЗАГРУЖАЕМ АРХИВ...", color = Color.White)
        if (error != null) {
            Text("Нет связи: $error", color = Color.White)
            HuntAction("ПОВТОРИТЬ", { refresh++ })
        }
        if (!loading && error == null && records.isEmpty()) Text("Рекордов пока нет. Первый выход за тобой.", color = Color.White)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            itemsIndexed(records) { index, record ->
                Row(Modifier.fillMaxWidth().background(if (index == 0) Red else Paper, SlashShape)
                    .padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(record.playerName.uppercase(), color = if (index == 0) Color.White else Ink,
                            fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Text("СЛОЖНОСТЬ ${record.difficulty}  •  ${record.hits} ПОПАДАНИЙ", color = if (index == 0) Color.White else Ink,
                            fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${record.score}", color = if (index == 0) Color.White else Red,
                        fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun HuntBackButton(onClick: () -> Unit) {
    Text("← НАЗАД", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 14.dp, bottom = 12.dp).background(Red, SlashShape)
            .clickable(onClick = onClick).testTag("menu_back").padding(horizontal = 16.dp, vertical = 9.dp))
}

@Composable
private fun HuntAction(label: String, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().padding(bottom = 8.dp).height(50.dp), shape = SlashShape,
        colors = ButtonDefaults.buttonColors(containerColor = Red)) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}
