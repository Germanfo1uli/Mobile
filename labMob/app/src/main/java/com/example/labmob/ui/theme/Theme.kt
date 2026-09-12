package com.example.labmob.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ComicColorScheme = darkColorScheme(
    primary = Color(0xFFE60012),
    onPrimary = Color.White,
    secondary = Color.White,
    onSecondary = Color(0xFF07070A),
    background = Color(0xFF07070A),
    onBackground = Color.White,
    surface = Color(0xFF17171D),
    onSurface = Color.White,
    error = Color(0xFFFF4051),
)

@Composable
fun LabMobTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ComicColorScheme,
        typography = Typography,
        content = content,
    )
}
