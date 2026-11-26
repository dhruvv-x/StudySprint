package com.pookie.studysprint.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Material ke standard slots se bahar ke colors (overrun, skipped, subject colors). */
@Immutable
data class ExtraColors(
    val overrun: Color,
    val overrunContainer: Color,
    val skipped: Color,
    val skippedContainer: Color,
    val textTertiary: Color,
    val subjectColors: List<Color>
)

private val LightExtra = ExtraColors(
    overrun = AmberLight,
    overrunContainer = AmberContainerLight,
    skipped = RedLight,
    skippedContainer = RedContainerLight,
    textTertiary = InkTertiaryLight,
    subjectColors = SubjectColorsLight
)

private val DarkExtra = ExtraColors(
    overrun = AmberDark,
    overrunContainer = AmberContainerDark,
    skipped = RedDark,
    skippedContainer = RedContainerDark,
    textTertiary = InkTertiaryDark,
    subjectColors = SubjectColorsDark
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

/** Use: MaterialTheme.extraColors.overrun */
val MaterialTheme.extraColors: ExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtraColors.current

private val LightScheme = lightColorScheme(
    primary = GreenLight,
    onPrimary = Color.White,
    primaryContainer = GreenContainerLight,
    onPrimaryContainer = OnGreenContainerLight,
    secondary = InkSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = NeutralChipLight,
    onSecondaryContainer = InkPrimaryLight,
    tertiary = AmberLight,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = InkPrimaryLight,
    background = Paper,
    onBackground = InkPrimaryLight,
    surface = SurfaceLight,
    onSurface = InkPrimaryLight,
    surfaceVariant = NeutralChipLight,
    onSurfaceVariant = InkSecondaryLight,
    outline = InkTertiaryLight,
    outlineVariant = BorderLight,
    error = RedLight,
    onError = Color.White,
    errorContainer = RedContainerLight,
    onErrorContainer = RedLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = Paper,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = NeutralChipLight,
    surfaceContainerHighest = NeutralChipLight
)

private val DarkScheme = darkColorScheme(
    primary = GreenDark,
    onPrimary = OnGreenDark,
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = OnGreenContainerDark,
    secondary = InkSecondaryDark,
    onSecondary = PaperDark,
    secondaryContainer = NeutralChipDark,
    onSecondaryContainer = InkPrimaryDark,
    tertiary = AmberDark,
    onTertiary = PaperDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = InkPrimaryDark,
    background = PaperDark,
    onBackground = InkPrimaryDark,
    surface = SurfaceDark,
    onSurface = InkPrimaryDark,
    surfaceVariant = NeutralChipDark,
    onSurfaceVariant = InkSecondaryDark,
    outline = InkTertiaryDark,
    outlineVariant = BorderDark,
    error = RedDark,
    onError = PaperDark,
    errorContainer = RedContainerDark,
    onErrorContainer = RedDark,
    surfaceContainerLowest = PaperDark,
    surfaceContainerLow = PaperDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = NeutralChipDark,
    surfaceContainerHighest = NeutralChipDark
)

@Composable
fun StudySprintTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val extra = if (darkTheme) DarkExtra else LightExtra

    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}