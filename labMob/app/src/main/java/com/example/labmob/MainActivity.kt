package com.example.labmob

import android.graphics.Paint
import android.os.Bundle
import android.widget.CalendarView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.example.labmob.ui.theme.LabMobTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Ink = Color(0xFF07070A)
private val RebelRed = Color(0xFFE60012)
private val DeepRed = Color(0xFF7A0010)
private val MutedPaper = Color(0xFF17171D)

data class PlayerProfile(
    val fullName: String,
    val gender: String,
    val course: Int,
    val difficulty: Int,
    val birthDateMillis: Long,
    val zodiac: ZodiacSign,
)

enum class ZodiacSign(val title: String, val symbol: String) {
    ARIES("Овен", "♈︎"),
    TAURUS("Телец", "♉︎"),
    GEMINI("Близнецы", "♊︎"),
    CANCER("Рак", "♋︎"),
    LEO("Лев", "♌︎"),
    VIRGO("Дева", "♍︎"),
    LIBRA("Весы", "♎︎"),
    SCORPIO("Скорпион", "♏︎"),
    SAGITTARIUS("Стрелец", "♐︎"),
    CAPRICORN("Козерог", "♑︎"),
    AQUARIUS("Водолей", "♒︎"),
    PISCES("Рыбы", "♓︎"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            LabMobTheme(dynamicColor = false) {
                LabMobApp()
            }
        }
    }
}

private enum class AppStage {
    SPLASH,
    ENTRY_MENU,
    THIEVES_MESSAGE,
    POLICE_MESSAGE,
    POLICE_DOSSIER_MESSAGE,
    REGISTRATION,
    MAIN_MENU,
}

@Composable
private fun LabMobApp() {
    val context = LocalContext.current
    var stage by rememberSaveable { mutableStateOf(AppStage.SPLASH) }
    var savedPlayer by remember { mutableStateOf(context.loadLocalPlayer()) }

    when (stage) {
        AppStage.SPLASH -> SplashScreen(
            onFinished = { stage = AppStage.ENTRY_MENU },
        )

        AppStage.ENTRY_MENU -> EntryChoiceScreen(
            savedPlayer = savedPlayer,
            onNewGame = { stage = AppStage.THIEVES_MESSAGE },
            onContinue = {
                if (savedPlayer != null) stage = AppStage.MAIN_MENU
            },
            onDeleteSave = {
                context.deleteLocalPlayer()
                savedPlayer = null
            },
            onSelectPlayer = { player ->
                context.saveLocalPlayer(player)
                savedPlayer = player
                stage = AppStage.MAIN_MENU
            },
        )

        AppStage.THIEVES_MESSAGE -> ThievesNoteScreen {
            stage = AppStage.POLICE_MESSAGE
        }

        AppStage.POLICE_MESSAGE -> PoliceInterrogationScreen(
            page = 1,
            onNext = { stage = AppStage.POLICE_DOSSIER_MESSAGE },
        )

        AppStage.POLICE_DOSSIER_MESSAGE -> PoliceInterrogationScreen(
            page = 2,
            onNext = { stage = AppStage.REGISTRATION },
        )

        AppStage.REGISTRATION -> RegistrationFlowScreen(
            onRegistered = { id, profile ->
                savedPlayer = context.saveLocalPlayer(id, profile)
                stage = AppStage.MAIN_MENU
            },
        )

        AppStage.MAIN_MENU -> MainMenuScreen(
            player = requireNotNull(savedPlayer),
            onPlayerUpdated = { updated ->
                context.saveLocalPlayer(updated)
                savedPlayer = updated
            },
            onChangePlayer = { stage = AppStage.ENTRY_MENU },
            onDeleteSave = {
                context.deleteLocalPlayer()
                savedPlayer = null
                stage = AppStage.ENTRY_MENU
            },
        )
    }
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2_300)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .clickable(onClickLabel = "Пропустить заставку", onClick = onFinished)
            .testTag("splash_screen"),
    ) {
        Image(
            painter = painterResource(R.drawable.phantom_crew_splash),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.08f),
                        0.56f to Color.Black.copy(alpha = 0.05f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                ),
        )

        Text(
            text = "ALTHUNT",
            color = Color.Black,
            fontSize = 48.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            letterSpacing = (-1).sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 29.dp, bottom = 105.dp)
                .offset(5.dp, 6.dp),
        )
        Text(
            text = "ALTHUNT",
            color = Color.White,
            fontSize = 48.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            letterSpacing = (-1).sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 29.dp, bottom = 105.dp),
        )
        Text(
            text = "ОХОТА НАЧИНАЕТСЯ",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 31.dp, bottom = 64.dp)
                .background(RebelRed)
                .border(2.dp, Color.White)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ThievesNoteScreen(onNext: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .clickable(onClickLabel = "Следующая карточка", onClick = onNext)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("intro_message"),
    ) {
        CrimeBackdrop(.5f)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 24.dp),
        ) {
            Text(
                "ЗАПИСКА БЕЗ ПОДПИСИ", color = HuntInk, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.rotate(-2f).background(HuntPaper, SlashShape).padding(horizontal = 16.dp, vertical = 7.dp),
            )
            Spacer(Modifier.height(14.dp))
            RansomTitle("ЕЩЁ НЕ\nКОНЕЦ.", size = 38)
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().rotate(-1f).background(Color(0xFFF00A2A), NoteShape)) {
                Canvas(Modifier.matchParentSize().clip(NoteShape)) {
                    val center = androidx.compose.ui.geometry.Offset(size.width * .52f, size.height * .74f)
                    val radius = size.width * .72f
                    drawCircle(Color(0xFF980019), radius, center)
                    drawCircle(Color(0xFFEF0B2C), radius * .75f, center)
                    drawCircle(Color(0xFFA5001D), radius * .5f, center)
                    drawCircle(Color(0xFFF00A2A), radius * .28f, center)
                }
                Column(
                    Modifier.fillMaxWidth()
                        .padding(start = 24.dp, end = 25.dp, top = 28.dp, bottom = 30.dp),
                ) {
                    Text(
                        "ПОДБРОШЕНО В КАМЕРУ // НЕ ПОКАЗЫВАЙ",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = .7.sp,
                        modifier = Modifier.fillMaxWidth().background(HuntInk, SlashShape)
                            .padding(start = 24.dp, end = 18.dp, top = 7.dp, bottom = 7.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    RansomNoteBlock(listOf("НУ И ВЛЯПАЛСЯ ТЫ."), 0)
                    Spacer(Modifier.height(12.dp))
                    RansomNoteBlock(
                        listOf(
                            "СКОРО ПОЛИЦИЯ ПРЕДЛОЖИТ СДЕЛКУ.",
                            "СОГЛАШАЙСЯ И ДЕЛАЙ ВСЁ,",
                            "ЧТО СКАЖУТ.",
                        ),
                        1,
                    )
                    Spacer(Modifier.height(12.dp))
                    RansomNoteBlock(
                        listOf(
                            "НЕ ПОКАЗЫВАЙ, ЧТО ЗНАЕШЬ О НАС.",
                            "НЕ ГЕРОЙСТВУЙ И ЖДИ СИГНАЛА.",
                        ),
                        2,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painterResource(R.drawable.phantom_calling_card_emblem_v2),
                            contentDescription = "Знак Фантомных воров",
                            modifier = Modifier.size(92.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "МЫ РЯДОМ.\nВЫТАЩИМ ТЕБЯ.", color = Color.White, fontSize = 13.sp,
                            lineHeight = 17.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                            modifier = Modifier.weight(1f).background(HuntInk, ReverseSlashShape)
                                .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                        )
                    }
                }
            }
            Text(
                "КОСНИСЬ ЭКРАНА  →", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 18.dp).align(Alignment.End).rotate(-2f).background(HuntRed, ReverseSlashShape).padding(horizontal = 19.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun RansomNoteBlock(lines: List<String>, phase: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        lines.forEachIndexed { lineIndex, line ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                line.split(' ').forEachIndexed { wordIndex, word ->
                    val index = wordIndex + lineIndex + phase
                    val paperChip = index % 3 == 0
                    Text(
                        word,
                        color = if (paperChip) HuntInk else Color.White,
                        fontSize = if (index % 4 == 0) 12.sp else 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = .25.sp,
                        modifier = Modifier.rotate(if (index % 2 == 0) -1.4f else 1.2f)
                            .background(if (paperChip) HuntPaper else HuntInk)
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PoliceInterrogationScreen(page: Int, onNext: () -> Unit) {
    val firstPage = page == 1
    Box(
        modifier = Modifier.fillMaxSize().background(Ink)
            .clickable(
                onClickLabel = if (firstPage) "Продолжить допрос" else "Перейти к досье",
                onClick = onNext,
            )
            .windowInsetsPadding(WindowInsets.safeDrawing).testTag("intro_message"),
    ) {
        CrimeBackdrop(.56f)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 15.dp, vertical = 22.dp),
        ) {
            Text(
                "СЛУЖЕБНЫЙ ДОПРОС", color = HuntInk, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.rotate(-2f).background(HuntPaper, SlashShape).padding(horizontal = 16.dp, vertical = 7.dp),
            )
            Spacer(Modifier.height(12.dp))
            RansomTitle(if (firstPage) "ВЫБОРА НЕТ." else "НАЧНЁМ С ДОСЬЕ.", size = 35)
            Spacer(Modifier.height(10.dp))
            CharacterDialogue(
                character = R.drawable.interrogator_dialogue_v2,
                speaker = "Следователь полиции",
                text = if (firstPage) {
                    "С этого момента я за тобой слежу.\n\nУ тебя два варианта: сотрудничать или остаться здесь надолго."
                } else {
                    "Начнёшь с досье: имя, курс и дата рождения. Заполни всё сам и без пропусков.\n\nТогда закончим быстро. Понял?"
                },
                dialogueFraction = .56f,
            )
            Text(
                if (firstPage) "ДАЛЬШЕ  →" else "ПЕРЕЙТИ К ДОСЬЕ  →",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 8.dp).align(Alignment.End).rotate(-2f).background(HuntRed, ReverseSlashShape)
                    .padding(horizontal = 19.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    isSubmitting: Boolean = false,
    submissionMessage: String? = null,
    onProfileSubmitted: (PlayerProfile) -> Unit = {},
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("Мужской") }
    var course by rememberSaveable { mutableStateOf(1) }
    var difficulty by rememberSaveable { mutableStateOf(3) }
    var birthDateMillis by rememberSaveable { mutableStateOf(todayAtNoon()) }
    var quickDateDigits by rememberSaveable { mutableStateOf("") }
    var quickDateError by rememberSaveable { mutableStateOf(false) }
    var courseMenuExpanded by remember { mutableStateOf(false) }
    var showNameError by rememberSaveable { mutableStateOf(false) }

    val zodiac = remember(birthDateMillis) { zodiacFor(birthDateMillis) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        CrimeBackdrop(dim = 0.58f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            RegistrationHeader()

            ComicPanel {
                SectionLabel("ДАННЫЕ ЗАКЛЮЧЁННОГО")
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        if (it.isNotBlank()) showNameError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("full_name"),
                    label = { Text("ФИО") },
                    placeholder = { Text("Иванов Иван Иванович") },
                    singleLine = true,
                    isError = showNameError,
                    supportingText = {
                        if (showNameError) Text("Введи имя игрока")
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Ink,
                        unfocusedTextColor = Ink,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        errorContainerColor = Color.White,
                        focusedBorderColor = RebelRed,
                        focusedLabelColor = RebelRed,
                        cursorColor = RebelRed,
                        unfocusedBorderColor = Ink,
                        unfocusedLabelColor = Ink,
                        unfocusedPlaceholderColor = Color(0xFF66666C),
                        errorBorderColor = RebelRed,
                    ),
                )

                Spacer(Modifier.height(14.dp))
                FieldCaption("ПОЛ")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Мужской", "Женский").forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            RadioButton(
                                selected = gender == option,
                                onClick = { gender = option },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = RebelRed,
                                    unselectedColor = Ink,
                                ),
                            )
                            Text(
                                text = option,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                FieldCaption("КУРС")
                ExposedDropdownMenuBox(
                    expanded = courseMenuExpanded,
                    onExpandedChange = { courseMenuExpanded = !courseMenuExpanded },
                ) {
                    OutlinedTextField(
                        value = "$course курс",
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("course_dropdown"),
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(courseMenuExpanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Ink,
                            unfocusedTextColor = Ink,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = RebelRed,
                            unfocusedBorderColor = Ink,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded = courseMenuExpanded,
                        onDismissRequest = { courseMenuExpanded = false },
                    ) {
                        (1..6).forEach { value ->
                            DropdownMenuItem(
                                text = { Text("$value курс", fontWeight = FontWeight.Bold) },
                                onClick = {
                                    course = value
                                    courseMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            ComicPanel(background = MutedPaper) {
                SectionLabel("УРОВЕНЬ РИСКА")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Сложность",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                    )
                    Text(
                        text = difficultyTitle(difficulty).uppercase(),
                        color = Ink,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .rotate(-2f)
                            .background(Color.White)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Slider(
                    value = difficulty.toFloat(),
                    onValueChange = { difficulty = it.roundToInt() },
                    modifier = Modifier.testTag("difficulty_slider"),
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = RebelRed,
                        activeTrackColor = RebelRed,
                        inactiveTrackColor = DeepRed,
                        activeTickColor = Color.White,
                        inactiveTickColor = Color.White,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "СПОКОЙНО",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "БЕЗ ПОЩАДЫ",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            ComicPanel {
                SectionLabel("ДАТА РОЖДЕНИЯ")
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatDate(birthDateMillis),
                            modifier = Modifier.testTag("selected_birth_date"),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "ЗНАК: ${zodiac.title.uppercase()}",
                            color = RebelRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    ZodiacImage(zodiac = zodiac, size = 104.dp)
                }

                Spacer(Modifier.height(16.dp))
                FieldCaption("БЫСТРЫЙ ВВОД")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = quickDateDigits,
                    onValueChange = { rawValue ->
                        val digits = rawValue.filter(Char::isDigit).take(8)
                        quickDateDigits = digits
                        quickDateError = false
                        if (digits.length == 8) {
                            val parsedDate = parseDateDigits(digits)
                            if (parsedDate == null) {
                                quickDateError = true
                            } else {
                                birthDateMillis = parsedDate
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_birth_date"),
                    label = { Text("ДД.ММ.ГГГГ") },
                    placeholder = { Text("17.05.2004") },
                    singleLine = true,
                    isError = quickDateError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = DateVisualTransformation(),
                    supportingText = {
                        Text(
                            if (quickDateError) {
                                "Проверь дату: от 01.01.1900 до сегодняшнего дня"
                            } else {
                                "Введи 8 цифр. Точки появятся автоматически"
                            },
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Ink,
                        unfocusedTextColor = Ink,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        errorContainerColor = Color.White,
                        focusedBorderColor = RebelRed,
                        unfocusedBorderColor = Color.White,
                        focusedLabelColor = RebelRed,
                        unfocusedLabelColor = Ink,
                        cursorColor = RebelRed,
                        errorBorderColor = RebelRed,
                    ),
                )

                Text(
                    text = "ИЛИ ВЫБЕРИ В КАЛЕНДАРЕ",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(3.dp, Color.White)
                        .background(Color.White)
                        .padding(2.dp),
                ) {
                    AndroidView(
                        factory = { context ->
                            CalendarView(context).apply {
                                date = birthDateMillis
                                maxDate = System.currentTimeMillis()
                                minDate = minimumBirthDate()
                                firstDayOfWeek = Calendar.MONDAY
                                isFocusable = false
                                isFocusableInTouchMode = false
                                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                setBackgroundColor(android.graphics.Color.WHITE)
                                setOnDateChangeListener { _, year, month, dayOfMonth ->
                                    birthDateMillis = Calendar.getInstance().apply {
                                        clear()
                                        set(year, month, dayOfMonth, 12, 0, 0)
                                    }.timeInMillis
                                    quickDateDigits = formatDate(birthDateMillis).filter(Char::isDigit)
                                    quickDateError = false
                                }
                            }
                        },
                        update = { calendar ->
                            if (calendar.date != birthDateMillis) calendar.date = birthDateMillis
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(310.dp)
                            .testTag("birth_calendar"),
                    )
                }
            }

            Button(
                onClick = {
                    val cleanName = fullName.trim()
                    showNameError = cleanName.isEmpty()
                    if (cleanName.isNotEmpty()) {
                        val profile = PlayerProfile(
                            fullName = cleanName,
                            gender = gender,
                            course = course,
                            difficulty = difficulty,
                            birthDateMillis = birthDateMillis,
                            zodiac = zodiac,
                        )
                        onProfileSubmitted(profile)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .rotate(-1f)
                    .testTag("submit_player"),
                enabled = !isSubmitting,
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RebelRed,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
            ) {
                Text(
                    text = if (isSubmitting) "ПЕРЕДАЁМ ДОСЬЕ..." else "ПЕРЕДАТЬ ДОСЬЕ  →",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                )
            }

            submissionMessage?.let { message ->
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Ink.copy(alpha = 0.86f))
                        .border(2.dp, RebelRed)
                        .padding(12.dp)
                        .testTag("registration_sync_status"),
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RegistrationHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        RansomTitle("ТВОЁ ДОСЬЕ", Modifier.fillMaxWidth(), size = 38)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "ПОЛИЦИИ НУЖНЫ ТВОИ ДАННЫЕ.\nЗАПОЛНИ ВСЁ ЧЁТКО, БЕЗ ПРОПУСКОВ.",
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp,
            modifier = Modifier
                .fillMaxWidth()
                .rotate(.35f)
                .background(RebelRed, SlashShape)
                .padding(start = 20.dp, end = 32.dp, top = 11.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun ComicPanel(
    modifier: Modifier = Modifier,
    background: Color = MutedPaper,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .offset(7.dp, 7.dp)
                .background(RebelRed, ReverseSlashShape),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(background, SlashShape)
                .padding(horizontal = 20.dp, vertical = 19.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        letterSpacing = 1.4.sp,
        modifier = Modifier
            .rotate(-1f)
            .background(RebelRed, SlashShape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun FieldCaption(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.4.sp,
    )
}

@Composable
private fun PlayerResult(profile: PlayerProfile) {
    ComicPanel(background = RebelRed) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZodiacImage(profile.zodiac, 86.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ДОСЬЕ ПРИНЯТО",
                    color = Color.White,
                    fontSize = 24.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                )
                Text(
                    text = profile.fullName,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = buildString {
                appendLine("ФИО: ${profile.fullName}")
                appendLine("Пол: ${profile.gender}")
                appendLine("Курс: ${profile.course}")
                appendLine("Сложность: ${profile.difficulty}/5, ${difficultyTitle(profile.difficulty)}")
                appendLine("Дата рождения: ${formatDate(profile.birthDateMillis)}")
                append("Знак зодиака: ${profile.zodiac.title} ${profile.zodiac.symbol}")
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("player_result")
                .background(Color.White)
                .border(2.dp, Ink)
                .padding(14.dp),
            color = Ink,
            fontWeight = FontWeight.Bold,
            lineHeight = 23.sp,
        )
    }
}

@Composable
private fun ZodiacImage(zodiac: ZodiacSign, size: Dp) {
    Image(
        painter = remember(zodiac) { ZodiacPainter(zodiac.symbol) },
        contentDescription = "Знак зодиака: ${zodiac.title}",
        modifier = Modifier
            .size(size)
            .rotate(3f)
            .clip(RoundedCornerShape(2.dp))
            .border(3.dp, Color.White),
    )
}

private class ZodiacPainter(private val symbol: String) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRect(Ink)
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.35f,
            center = center,
        )
        drawCircle(
            color = RebelRed,
            radius = size.minDimension * 0.41f,
            center = center,
            style = Stroke(width = size.minDimension * 0.045f),
        )

        val symbolPaint = Paint().apply {
            color = android.graphics.Color.rgb(7, 7, 10)
            textSize = size.minDimension * 0.47f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val baseline = center.y - (symbolPaint.ascent() + symbolPaint.descent()) / 2f
        drawContext.canvas.nativeCanvas.drawText(symbol, center.x, baseline, symbolPaint)
    }
}

@Composable
private fun HeistBackdrop(scrimAlpha: Float) {
    Box(Modifier.fillMaxSize()) { CrimeBackdrop(scrimAlpha.coerceIn(.35f, .82f)) }
}

internal fun zodiacFor(timeInMillis: Long): ZodiacSign {
    val date = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
    return zodiacFor(date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH))
}

internal fun zodiacFor(month: Int, day: Int): ZodiacSign = when {
    (month == 3 && day >= 21) || (month == 4 && day <= 19) -> ZodiacSign.ARIES
    (month == 4 && day >= 20) || (month == 5 && day <= 20) -> ZodiacSign.TAURUS
    (month == 5 && day >= 21) || (month == 6 && day <= 20) -> ZodiacSign.GEMINI
    (month == 6 && day >= 21) || (month == 7 && day <= 22) -> ZodiacSign.CANCER
    (month == 7 && day >= 23) || (month == 8 && day <= 22) -> ZodiacSign.LEO
    (month == 8 && day >= 23) || (month == 9 && day <= 22) -> ZodiacSign.VIRGO
    (month == 9 && day >= 23) || (month == 10 && day <= 22) -> ZodiacSign.LIBRA
    (month == 10 && day >= 23) || (month == 11 && day <= 21) -> ZodiacSign.SCORPIO
    (month == 11 && day >= 22) || (month == 12 && day <= 21) -> ZodiacSign.SAGITTARIUS
    (month == 12 && day >= 22) || (month == 1 && day <= 19) -> ZodiacSign.CAPRICORN
    (month == 1 && day >= 20) || (month == 2 && day <= 18) -> ZodiacSign.AQUARIUS
    else -> ZodiacSign.PISCES
}

private fun difficultyTitle(level: Int): String = when (level) {
    1 -> "Новичок"
    2 -> "Легко"
    3 -> "Нормально"
    4 -> "Сложно"
    else -> "Без пощады"
}

private fun formatDate(timeInMillis: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("ru-RU")).format(Date(timeInMillis))

private class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(8)
        val visibleDate = buildString {
            digits.forEachIndexed { index, digit ->
                if (index == 2 || index == 4) append('.')
                append(digit)
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                else -> offset + 2
            }.coerceAtMost(visibleDate.length)

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }.coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(visibleDate), offsetMapping)
    }
}

private fun parseDateDigits(digits: String): Long? {
    if (digits.length != 8) return null
    val formatted = "${digits.take(2)}.${digits.substring(2, 4)}.${digits.takeLast(4)}"
    return parseDateInput(formatted)
}

private fun parseDateInput(value: String): Long? {
    if (value.length != 10) return null
    val parser = SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("ru-RU")).apply {
        isLenient = false
    }
    val parsed = runCatching { parser.parse(value) }.getOrNull() ?: return null
    val calendar = Calendar.getInstance().apply {
        time = parsed
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val year = calendar.get(Calendar.YEAR)
    return calendar.timeInMillis.takeIf {
        year >= 1900 && it <= todayAtNoon()
    }
}

private fun minimumBirthDate(): Long = Calendar.getInstance().apply {
    clear()
    set(1900, Calendar.JANUARY, 1, 12, 0, 0)
}.timeInMillis

private fun todayAtNoon(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 12)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RegistrationPreview() {
    LabMobTheme(dynamicColor = false) {
        RegistrationScreen()
    }
}
