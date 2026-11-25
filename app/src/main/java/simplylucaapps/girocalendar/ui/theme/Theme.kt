package simplylucaapps.girocalendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Colori extra non coperti da ColorScheme (accento2/arancio, gradiente hero, text2/3).
data class GcExtraColors(
    val accent2: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val text2: Color,
    val text3: Color,
)

val LocalGcColors = staticCompositionLocalOf {
    GcExtraColors(
        accent2 = Color(0xFFF4A261),
        heroStart = Color(0xFF27AE60), heroEnd = Color(0xFF14532D),
        text2 = Color(0xFF9997CC), text3 = Color(0xFF6664A0),
    )
}

/** "sts" (default) o "trailforks": vedi [simplylucaapps.girocalendar.data.settings.AppSettings.difficultyScale].
 * Fornito qui invece che passato esplicitamente a ogni DifficultyBadge, dato che riguarda
 * decine di punti di utilizzo sparsi (card evento, dettaglio, archivio, proposte, trail…). */
val LocalDifficultyScale = staticCompositionLocalOf { "sts" }

@Composable
fun GiroCalendarTheme(
    themeId: String = DEFAULT_THEME_ID,
    fontStyleId: String = AppFontStyle.DEFAULT.id,
    difficultyScale: String = "sts",
    content: @Composable () -> Unit,
) {
    val theme = themeById(themeId)
    CompositionLocalProvider(LocalGcColors provides theme.extra, LocalDifficultyScale provides difficultyScale) {
        MaterialTheme(
            colorScheme = theme.scheme,
            typography = typographyFor(AppFontStyle.of(fontStyleId)),
            content = content,
        )
    }
}
