package com.example.labmob

import android.graphics.Paint
import android.os.Bundle
import android.widget.CalendarView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
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
    THIEVES_MESSAGE,
    POLICE_MESSAGE,
    REGISTRATION,
}

@Composable
private fun LabMobApp() {
    var stage by rememberSaveable { mutableStateOf(AppStage.THIEVES_MESSAGE) }

    when (stage) {
        AppStage.THIEVES_MESSAGE -> IntroMessageScreen(
            cardColor = RebelRed,
            eyebrow = "ЗАПИСКА БЕЗ ПОДПИСИ",
            title = "ЕЩЁ НЕ КОНЕЦ.",
            message = "Ну и вляпался ты.\n\nСкоро полиция предложит сделку. Соглашайся и делай всё, что скажут. Только не показывай, что знаешь о нас.\n\nМы рядом. Не геройствуй, не привлекай внимание и жди сигнала.\n\nВытащим тебя.",
            signature = "ТЕ, КТО НА ТВОЕЙ СТОРОНЕ",
            onNext = { stage = AppStage.POLICE_MESSAGE },
        )

        AppStage.POLICE_MESSAGE -> IntroMessageScreen(
            cardColor = Color(0xFF1737C7),
            eyebrow = "СЛУЖЕБНЫЙ ДОПРОС",
            title = "ВЫБОРА НЕТ.",
            message = "С этого момента я за тобой слежу.\n\nСначала заполни досье. Имя, курс, дата рождения. Ничего не пропускай.\n\nСделаешь всё сам, закончим быстро. Начнёшь упрямиться, разговор будет другим.\n\nЯсно?",
            signature = "СЛЕДОВАТЕЛЬ ПОЛИЦИИ",
            avatarRes = R.drawable.police_curator,
            onNext = { stage = AppStage.REGISTRATION },
        )

        AppStage.REGISTRATION -> RegistrationScreen()
    }
}

@Composable
private fun IntroMessageScreen(
    cardColor: Color,
    eyebrow: String,
    title: String,
    message: String,
    signature: String,
    avatarRes: Int? = null,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .clickable(onClickLabel = "Следующая карточка", onClick = onNext)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("intro_message"),
    ) {
        HeistBackdrop(scrimAlpha = 0.32f)

        Text(
            text = eyebrow,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .background(Ink.copy(alpha = 0.88f))
                .border(2.dp, Color.White)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .rotate(-2.5f),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(10.dp, 12.dp)
                    .background(Color.Black)
                    .border(3.dp, Color.White),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardColor)
                    .border(4.dp, Color.White)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                if (avatarRes == null) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 32.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = (-0.5).sp,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(128.dp),
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 27.sp,
                            lineHeight = 27.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = (-0.5).sp,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(0.56f),
                        )
                        Image(
                            painter = painterResource(avatarRes),
                            contentDescription = "Следователь полиции",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(128.dp)
                                .height(145.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = message,
                        color = Ink,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = signature,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Text(
            text = "КОСНИСЬ ЭКРАНА  →",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .background(cardColor)
                .border(2.dp, Color.White)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen() {
    var fullName by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("Мужской") }
    var course by rememberSaveable { mutableStateOf(1) }
    var difficulty by rememberSaveable { mutableStateOf(3) }
    var birthDateMillis by rememberSaveable { mutableStateOf(todayAtNoon()) }
    var quickDateDigits by rememberSaveable { mutableStateOf("") }
    var quickDateError by rememberSaveable { mutableStateOf(false) }
    var courseMenuExpanded by remember { mutableStateOf(false) }
    var showNameError by rememberSaveable { mutableStateOf(false) }
    var submittedProfile by remember { mutableStateOf<PlayerProfile?>(null) }

    val zodiac = remember(birthDateMillis) { zodiacFor(birthDateMillis) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        HeistBackdrop(scrimAlpha = 0.12f)

        Column(
            modifier = Modifier
                .fillMaxSize()
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
                        submittedProfile = PlayerProfile(
                            fullName = cleanName,
                            gender = gender,
                            course = course,
                            difficulty = difficulty,
                            birthDateMillis = birthDateMillis,
                            zodiac = zodiac,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .rotate(-1f)
                    .testTag("submit_player"),
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RebelRed,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
            ) {
                Text(
                    text = "ПЕРЕДАТЬ ДОСЬЕ  →",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                )
            }

            submittedProfile?.let { profile ->
                PlayerResult(profile)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RegistrationHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
            Text(
                text = "ТВОЁ\nДОСЬЕ",
                color = RebelRed,
                fontSize = 46.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-1).sp,
                modifier = Modifier.offset(5.dp, 5.dp),
            )
            Text(
                text = "ТВОЁ\nДОСЬЕ",
                color = Color.White,
                fontSize = 46.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-1).sp,
            )
        }
        Text(
            text = "ЗАПОЛНИ АНКЕТУ. ОТВЕЧАЙ ЧЁТКО И НИЧЕГО НЕ ПРОПУСКАЙ.",
            color = Color.White,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp,
            modifier = Modifier
                .rotate(1f)
                .background(RebelRed)
                .padding(horizontal = 8.dp, vertical = 3.dp),
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
                .background(RebelRed),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .border(3.dp, Color.White)
                .padding(16.dp),
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
            .background(RebelRed)
            .padding(horizontal = 10.dp, vertical = 5.dp),
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
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.heist_brush_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink.copy(alpha = scrimAlpha)),
        )
    }
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
