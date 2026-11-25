package simplylucaapps.girocalendar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import simplylucaapps.girocalendar.R

// Bebas Neue per i titoli, DM Sans per il corpo — stile "Classico", stesso pattern grafico
// di VolleyCalendar. Bebas è già condensed di suo: niente letterSpacing extra sopra,
// altrimenti il risultato è innaturalmente spazioso in orizzontale.
val Bebas = FontFamily(Font(R.font.bebas_neue))
val DmSans = FontFamily(Font(R.font.dm_sans_regular))

// Font alternativi selezionabili da Preferenze: ciascuno usato sia per titoli che per corpo
// (con pesi diversi per dare gerarchia), a differenza del Classico che separa titolo/corpo
// su due famiglie diverse.
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_bold, FontWeight.Bold),
)
// Lexend: font moderno disegnato per la leggibilità (spaziatura ampia, lettere ben distinte).
val Lexend = FontFamily(
    Font(R.font.lexend_variable, FontWeight.Normal),
    Font(R.font.lexend_variable, FontWeight.Bold),
)
val Inter = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_variable, FontWeight.Bold),
)

/** Font style selezionabile da Preferenze. [id] è persistito in AppSettings. */
enum class AppFontStyle(val id: String, private val labelOverride: String?) {
    CLASSIC("classic", null),
    POPPINS("poppins", "Poppins"),
    LEXEND("lexend", "Lexend"),
    INTER("inter", "Inter");

    val label: String get() = labelOverride ?: simplylucaapps.girocalendar.util.Str.t(R.string.ft_classic)

    companion object {
        val DEFAULT = CLASSIC
        fun of(id: String): AppFontStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

private val base = Typography()

fun typographyFor(style: AppFontStyle): Typography = when (style) {
    AppFontStyle.CLASSIC -> base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = Bebas),
        displayMedium = base.displayMedium.copy(fontFamily = Bebas),
        displaySmall = base.displaySmall.copy(fontFamily = Bebas),
        headlineLarge = base.headlineLarge.copy(fontFamily = Bebas),
        headlineMedium = base.headlineMedium.copy(fontFamily = Bebas),
        headlineSmall = base.headlineSmall.copy(fontFamily = Bebas),
        titleLarge = base.titleLarge.copy(fontFamily = Bebas),
        titleMedium = base.titleMedium.copy(fontFamily = DmSans, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = DmSans, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = DmSans),
        bodyMedium = base.bodyMedium.copy(fontFamily = DmSans),
        bodySmall = base.bodySmall.copy(fontFamily = DmSans),
        labelLarge = base.labelLarge.copy(fontFamily = DmSans, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = DmSans, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = DmSans, fontWeight = FontWeight.Medium),
    )
    else -> {
        // Poppins/Lexend/Inter: stessa famiglia per titoli e corpo, gerarchia data dal peso
        // (Bold per titoli/label, Normal per il corpo) — più uniforme e "leggibile" del
        // contrasto forte titolo-condensed/corpo-neutro dello stile Classico.
        val family = when (style) {
            AppFontStyle.POPPINS -> Poppins
            AppFontStyle.LEXEND -> Lexend
            AppFontStyle.INTER -> Inter
            AppFontStyle.CLASSIC -> Bebas // irraggiungibile, gestito sopra
        }
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
            displayMedium = base.displayMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
            displaySmall = base.displaySmall.copy(fontFamily = family, fontWeight = FontWeight.Bold),
            headlineLarge = base.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
            headlineMedium = base.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
            headlineSmall = base.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontFamily = family, fontWeight = FontWeight.Normal),
            bodyMedium = base.bodyMedium.copy(fontFamily = family, fontWeight = FontWeight.Normal),
            bodySmall = base.bodySmall.copy(fontFamily = family, fontWeight = FontWeight.Normal),
            labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
            labelMedium = base.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
            labelSmall = base.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        )
    }
}
