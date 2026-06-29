package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CalmLightColorScheme = lightColorScheme(
    primary = AccentClay,
    secondary = AccentOlive,
    tertiary = AccentGold,
    background = BgCreamLight,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = TextDeepCoffee,
    onBackground = TextDeepCoffee,
    onSurface = TextDeepCoffee
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Use calm warm light beige theme as requested
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CalmLightColorScheme,
        typography = Typography,
        content = content
    )
}
