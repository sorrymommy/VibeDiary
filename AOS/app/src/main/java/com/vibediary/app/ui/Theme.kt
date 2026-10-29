package com.vibediary.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vibediary.app.R

/** 하루 노선도 palette: a day drawn as a transit line on paper. */
object Vibe {
    val Paper = Color(0xFFF2F4F1)   // ground
    val Ink = Color(0xFF17201B)     // text, dock, current station
    val Route = Color(0xFF138A5B)   // the line itself
    val Leaf = Color(0xFFDCEFE4)    // tag chips
    val LeafInk = Color(0xFF0D5C3D) // text on Leaf
    val Rec = Color(0xFFD3372B)     // recording only
    val Muted = Color(0xFF56615A)   // secondary text (6:1 on Paper)
    val Body = Color(0xFF2E3832)    // softer body text
    val Hairline = Color(0xFFDDE2DE)
    val Photo = Color(0xFFB9C7BE)   // photo placeholder while loading
    val Card = Color.White
}

/** Place names, times and UI signage — reads like a station sign. */
val SignFont = FontFamily(Font(R.font.dohyeon_regular))

/** What you actually wrote — reads like a book. */
val ProseFont = FontFamily(
    Font(R.font.gowunbatang_regular, FontWeight.Normal),
    Font(R.font.gowunbatang_bold, FontWeight.Bold),
)

private val colors = lightColorScheme(
    primary = Vibe.Ink,
    onPrimary = Color.White,
    primaryContainer = Vibe.Leaf,
    onPrimaryContainer = Vibe.LeafInk,
    secondary = Vibe.Route,
    onSecondary = Color.White,
    secondaryContainer = Vibe.Leaf,
    onSecondaryContainer = Vibe.LeafInk,
    tertiary = Vibe.Rec,
    background = Vibe.Paper,
    onBackground = Vibe.Ink,
    surface = Vibe.Paper,
    onSurface = Vibe.Ink,
    surfaceVariant = Color.White,
    onSurfaceVariant = Vibe.Muted,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    outline = Vibe.Muted,
    outlineVariant = Vibe.Hairline,
    error = Color(0xFFB42318),
)

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = SignFont, fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = SignFont, fontSize = 28.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = SignFont, fontSize = 22.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = SignFont, fontSize = 18.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = SignFont, fontSize = 16.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = SignFont, fontSize = 17.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = SignFont, fontSize = 15.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = SignFont, fontSize = 13.sp, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontFamily = ProseFont, fontSize = 16.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = ProseFont, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = ProseFont, fontSize = 13.sp, lineHeight = 20.sp),
)

/**
 * Full-screen dialogs get their own window, which follows the system's dark mode for its
 * bar icons. Call inside such a dialog to keep the icons dark on the paper background.
 */
@Composable
fun DarkSystemBarIcons() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
}

@Composable
fun VibeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
