package simplylucaapps.girocalendar.ui.theme

import simplylucaapps.girocalendar.R

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Temi dell'app, tema "verde bosco/off-road" come colore principale (coerente col dominio
 * ciclismo/MTB). Ogni tema porta uno [ColorScheme] Material3 + i colori extra [GcExtraColors].
 */
data class AppTheme(
    val id: String,
    val name: String,
    val icon: String,
    val dark: Boolean,
    val scheme: ColorScheme,
    val extra: GcExtraColors,
) {
    val previewBg: Color get() = scheme.background
    val previewAccent: Color get() = scheme.primary
    val previewAccent2: Color get() = extra.accent2
}

/** RGB (0xRRGGBB) → Color opaco. */
private fun c(rgb: Long) = Color(0xFF000000L or (rgb and 0xFFFFFFL))

private fun dark(bg: Long, surf: Long, surfVar: Long, border: Long, accent: Long, accent2: Long,
                text: Long, text2: Long, text3: Long) = AppTheme(
    id = "", name = "", icon = "", dark = true,
    scheme = darkColorScheme(
        primary = c(accent), onPrimary = Color.White,
        primaryContainer = c(accent).copy(alpha = 0.22f).compositeOverBlack(),
        onPrimaryContainer = c(accent).lighten(),
        secondary = c(accent2), onSecondary = Color(0xFF101010),
        tertiary = Color(0xFF2ECC71),
        background = c(bg), onBackground = c(text),
        surface = c(surf), onSurface = c(text),
        surfaceVariant = c(surfVar), onSurfaceVariant = c(text2),
        outline = c(border), error = Color(0xFFE63946),
    ),
    extra = GcExtraColors(
        accent2 = c(accent2),
        heroStart = c(accent), heroEnd = blend(c(accent), Color(0xFF14532D)),
        text2 = c(text2), text3 = c(text3),
    ),
)

private fun light(bg: Long, surf: Long, surfVar: Long, border: Long, accent: Long, accent2: Long,
                  text: Long, text2: Long, text3: Long) = AppTheme(
    id = "", name = "", icon = "", dark = false,
    scheme = lightColorScheme(
        primary = c(accent), onPrimary = Color.White,
        primaryContainer = c(accent).copy(alpha = 0.15f).compositeOverWhite(),
        onPrimaryContainer = c(accent).darken(),
        secondary = c(accent2), onSecondary = Color.White,
        tertiary = Color(0xFF2E7D32),
        background = c(bg), onBackground = c(text),
        surface = c(surf), onSurface = c(text),
        surfaceVariant = c(surfVar), onSurfaceVariant = c(text2),
        outline = c(border), error = Color(0xFFC62828),
    ),
    extra = GcExtraColors(
        accent2 = c(accent2),
        heroStart = c(accent), heroEnd = blend(c(accent), Color(0xFF14532D)),
        text2 = c(text2), text3 = c(text3),
    ),
)

private fun Color.lighten() = Color(
    red + (1 - red) * 0.6f, green + (1 - green) * 0.6f, blue + (1 - blue) * 0.6f, 1f,
)
private fun Color.darken() = Color(red * 0.35f, green * 0.35f, blue * 0.35f, 1f)
private fun Color.compositeOverBlack() = Color(red * alpha, green * alpha, blue * alpha, 1f)
private fun Color.compositeOverWhite() = Color(
    red * alpha + (1 - alpha), green * alpha + (1 - alpha), blue * alpha + (1 - alpha), 1f,
)
private fun blend(a: Color, b: Color) = Color(
    (a.red + b.red) / 2, (a.green + b.green) / 2, (a.blue + b.blue) / 2, 1f,
)

val APP_THEMES: List<AppTheme> = listOf(
    // ── scuri ──
    dark(0x0D1A0F, 0x14261A, 0x1A301F, 0x25422B, 0x27AE60, 0xF4A261, 0xEFF7F0, 0x9DBBA3, 0x60816A)
        .copy(id = "dark", name = "Bosco Notte", icon = "🌲"),
    dark(0x111111, 0x1E1E1E, 0x2A2A2A, 0x333333, 0xFF7A00, 0x2980B9, 0xE8E8E8, 0x999999, 0x555555)
        .copy(id = "carbon", name = "Carbonio", icon = "⛰️"),
    // Notte Viola: stessa palette della versione web (girocalendar.pages.dev), per coerenza
    // visiva tra app e sito — bg/surf viola scurissimo, accent arancio.
    dark(0x0D0D1A, 0x1C1C38, 0x242450, 0x2A2A50, 0xF5A623, 0xE8720C, 0xF0EFFF, 0x9997CC, 0x6664A0)
        .copy(id = "sentiero", name = "Notte Viola", icon = "🌌"),
    // Oceano: blu notte con accento azzurro e ambra.
    dark(0x0A1622, 0x112436, 0x182F47, 0x244563, 0x29B6F6, 0xFFB74D, 0xE8F4FC, 0x9DB8CC, 0x5F7C92)
        .copy(id = "oceano", name = "Oceano", icon = "🌊"),
    // Bordeaux: vinaccia scuro con accento rosso corallo e oro.
    dark(0x1A0D10, 0x2A1519, 0x361C21, 0x4A2A30, 0xEF5350, 0xFFCA28, 0xF8ECEC, 0xC9A0A3, 0x8A6468)
        .copy(id = "bordeaux", name = "Bordeaux", icon = "🍷"),
    // Miniera: bruno scuro con accento ambra e verde acqua.
    dark(0x16120A, 0x241D10, 0x2F2616, 0x453A22, 0xFFC107, 0x26A69A, 0xF7F1E1, 0xC4B58F, 0x8A7D5C)
        .copy(id = "miniera", name = "Miniera", icon = "⛏️"),
    // ── chiari ──
    light(0xF4FAF4, 0xFFFFFF, 0xDDF0DD, 0xC3DEC3, 0x2D7A2D, 0xE67E22, 0x0F2E0F, 0x3A6B3A, 0x7AAB7A)
        .copy(id = "light", name = "Giorno", icon = "☀️"),
    light(0xFDF8F0, 0xFFFFFF, 0xF2E9D8, 0xE0D0B0, 0xB06000, 0x2980B9, 0x2A1A00, 0x7A5A20, 0xBB9960)
        .copy(id = "sterrato", name = "Sterrato", icon = "🏜️"),
    // Alba Viola: "gemello" chiaro di Notte Viola, stessa famiglia cromatica viola/fucsia ma
    // come colore principale (non solo sfondo) — a differenza di Notte Viola, dove l'accento
    // resta l'arancio del brand.
    light(0xFBF6FC, 0xFFFFFF, 0xF0E0F5, 0xDFC0EA, 0x9B2FAE, 0xE91E8C, 0x2E0A33, 0x7A4A85, 0xB090BB)
        .copy(id = "alba", name = "Alba Viola", icon = "🌅"),
    // Cielo: stessa palette azzurra del sito web (girocalendar.pages.dev).
    light(0xEEF5FC, 0xFFFFFF, 0xDCE9F7, 0xB9D0EA, 0x1976D2, 0x0D5CAB, 0x0F2236, 0x46627F, 0x8AA2BB)
        .copy(id = "cielo", name = "Cielo", icon = "🌤️"),
    // Menta: bianco-verde freddo con accento verde petrolio e arancio.
    light(0xF0FAF7, 0xFFFFFF, 0xD7F0E9, 0xB2DDD2, 0x00897B, 0xFF7043, 0x07302B, 0x3F6F67, 0x86B1A9)
        .copy(id = "menta", name = "Menta", icon = "🍃"),
    // Corallo: crema rosata con accento rosso mattone e indaco.
    light(0xFFF5F3, 0xFFFFFF, 0xFBE2DD, 0xF0C2BA, 0xD84315, 0x3949AB, 0x3A140C, 0x8A4A3D, 0xC49A92)
        .copy(id = "corallo", name = "Corallo", icon = "🪸"),
)

/** Nome localizzato del tema (il campo [AppTheme.name] resta quello italiano originale). */
fun themeNameRes(id: String): Int = when (id) {
    "dark" -> R.string.th_dark; "carbon" -> R.string.th_carbon; "sentiero" -> R.string.th_sentiero
    "light" -> R.string.th_light; "sterrato" -> R.string.th_sterrato; "cielo" -> R.string.th_cielo
    "oceano" -> R.string.th_oceano; "bordeaux" -> R.string.th_bordeaux; "miniera" -> R.string.th_miniera
    "menta" -> R.string.th_menta; "corallo" -> R.string.th_corallo; else -> R.string.th_alba
}

val DEFAULT_THEME_ID = "sentiero"

fun themeById(id: String?): AppTheme = APP_THEMES.firstOrNull { it.id == id } ?: APP_THEMES.first()

/** Tema "gemello" di luminosità opposta, per il toggle sole/luna. */
fun oppositeBrightness(id: String?): String {
    val pairs = mapOf(
        "dark" to "light", "light" to "dark",
        "carbon" to "sterrato", "sterrato" to "carbon",
        "sentiero" to "alba", "alba" to "sentiero",
        "cielo" to "oceano", "oceano" to "cielo",
        "bordeaux" to "corallo", "corallo" to "bordeaux",
        "miniera" to "menta", "menta" to "miniera",
    )
    pairs[id]?.let { return it }
    val cur = themeById(id)
    return APP_THEMES.first { it.dark != cur.dark }.id
}
