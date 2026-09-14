package com.example.labmob

import android.text.Html
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CourseInk = Color(0xFF07070A)
private val CourseRed = Color(0xFFE60012)
private val CoursePanel = Color(0xFF17171D)

private enum class CourseTab(val title: String) {
    PROFILE("ДОСЬЕ"),
    RULES("ПРАВИЛА"),
    AUTHORS("АВТОРЫ"),
    SETTINGS("НАСТРОЙКИ"),
}

@Composable
fun CourseHomeScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(CourseTab.PROFILE.ordinal) }
    var playerId by rememberSaveable { mutableStateOf<String?>(null) }
    var syncMessage by rememberSaveable { mutableStateOf("Сначала сохрани досье игрока") }
    val api = remember { BackendApi() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CourseInk)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = CourseInk,
            contentColor = Color.White,
            edgePadding = 8.dp,
        ) {
            CourseTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = tab.title,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                        )
                    },
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (CourseTab.entries[selectedTab]) {
                CourseTab.PROFILE -> RegistrationScreen { profile ->
                    scope.launch {
                        syncMessage = "Сохранение досье..."
                        runCatching { api.createPlayer(profile) }
                            .onSuccess { id ->
                                playerId = id
                                syncMessage = "Досье сохранено в PostgreSQL"
                            }
                            .onFailure { error ->
                                syncMessage = "Не удалось сохранить: ${error.message ?: "нет связи с сервером"}"
                            }
                    }
                }
                CourseTab.RULES -> RulesScreen()
                CourseTab.AUTHORS -> AuthorsScreen()
                CourseTab.SETTINGS -> SettingsScreen(
                    playerId = playerId,
                    syncMessage = syncMessage,
                    onSave = { settings ->
                        playerId?.let { id ->
                            scope.launch {
                                syncMessage = "Сохранение настроек..."
                                runCatching { api.saveSettings(id, settings) }
                                    .onSuccess { syncMessage = "Настройки сохранены в PostgreSQL" }
                                    .onFailure { error ->
                                        syncMessage = "Не удалось сохранить: ${error.message ?: "нет связи с сервером"}"
                                    }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RulesScreen() {
    val context = LocalContext.current
    val rulesHtml = remember {
        context.resources.openRawResource(R.raw.game_rules)
            .bufferedReader()
            .use { it.readText() }
    }

    CourseSection(
        title = "ПРАВИЛА ИГРЫ",
        testTag = "rules_screen",
    ) {
        AndroidView(
            factory = { viewContext ->
                TextView(viewContext).apply {
                    setTextColor(android.graphics.Color.rgb(7, 7, 10))
                    setBackgroundColor(android.graphics.Color.WHITE)
                    textSize = 17f
                    setPadding(36, 28, 36, 28)
                }
            },
            update = { textView ->
                textView.text = Html.fromHtml(rulesHtml, Html.FROM_HTML_MODE_COMPACT)
            },
            modifier = Modifier
                .fillMaxWidth()
                .border(3.dp, Color.White),
        )
    }
}

private data class Author(
    val initials: String,
    val name: String,
    val contribution: String,
    val accent: Color,
)

@Composable
private fun AuthorsScreen() {
    val authors = remember {
        listOf(
            Author(
                initials = "ГП",
                name = "Герман Пырцак",
                contribution = "Основа приложения, регистрация игрока и визуальная тема",
                accent = Color(0xFF1737C7),
            ),
            Author(
                initials = "ЯК",
                name = "Яна Карпова",
                contribution = "Навигация, правила, настройки и подготовка проекта к запуску",
                accent = CourseRed,
            ),
        )
    }

    CourseSection(
        title = "КОМАНДА",
        testTag = "authors_screen",
    ) {
        Text(
            text = "АВТОРЫ ПРОЕКТА",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(10.dp))
        authors.forEach { author ->
            AuthorCard(author)
            Spacer(Modifier.height(14.dp))
        }
        Text(
            text = "Аватары пока оформлены инициалами — реальные фотографии можно заменить в ресурсах проекта.",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun AuthorCard(author: Author) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(3.dp, author.accent)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(author.accent)
                .border(3.dp, CourseInk, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = author.initials,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = author.name,
                color = CourseInk,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = author.contribution,
                color = CourseInk.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    playerId: String?,
    syncMessage: String,
    onSave: (GameSettings) -> Unit,
) {
    var gameSpeed by rememberSaveable { mutableFloatStateOf(1f) }
    var maxInsects by rememberSaveable { mutableIntStateOf(8) }
    var bonusInterval by rememberSaveable { mutableIntStateOf(15) }
    var roundDuration by rememberSaveable { mutableIntStateOf(60) }

    CourseSection(
        title = "НАСТРОЙКИ ИГРЫ",
        testTag = "settings_screen",
    ) {
        SettingSlider(
            title = "СКОРОСТЬ ИГРЫ",
            valueText = String.format("%.1fx", gameSpeed),
            value = gameSpeed,
            onValueChange = { gameSpeed = it },
            valueRange = 0.5f..2f,
            steps = 5,
        )
        SettingSlider(
            title = "МАКСИМУМ НАСЕКОМЫХ",
            valueText = maxInsects.toString(),
            value = maxInsects.toFloat(),
            onValueChange = { maxInsects = it.roundToInt() },
            valueRange = 3f..15f,
            steps = 11,
        )
        SettingSlider(
            title = "БОНУС КАЖДЫЕ",
            valueText = "$bonusInterval сек",
            value = bonusInterval.toFloat(),
            onValueChange = { bonusInterval = it.roundToInt() },
            valueRange = 5f..30f,
            steps = 24,
        )
        SettingSlider(
            title = "ДЛИТЕЛЬНОСТЬ РАУНДА",
            valueText = "$roundDuration сек",
            value = roundDuration.toFloat(),
            onValueChange = { roundDuration = it.roundToInt() },
            valueRange = 30f..180f,
            steps = 14,
        )
        Button(
            onClick = {
                onSave(
                    GameSettings(
                        gameSpeed = gameSpeed,
                        maxInsects = maxInsects,
                        bonusIntervalSeconds = bonusInterval,
                        roundDurationSeconds = roundDuration,
                    ),
                )
            },
            enabled = playerId != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = CourseRed,
                contentColor = Color.White,
            ),
        ) {
            Text("СОХРАНИТЬ НА СЕРВЕРЕ", fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = syncMessage,
            color = Color.White.copy(alpha = 0.82f),
            modifier = Modifier.testTag("backend_sync_status"),
        )
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(3.dp, CourseRed)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = CourseInk,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                color = CourseRed,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.End,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun CourseSection(
    title: String,
    testTag: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CourseInk)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag(testTag),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 34.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .fillMaxWidth()
                .background(CoursePanel)
                .border(3.dp, Color.White)
                .padding(14.dp),
        )
        Spacer(Modifier.height(18.dp))
        content()
    }
}
