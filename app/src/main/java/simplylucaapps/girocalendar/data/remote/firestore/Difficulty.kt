package simplylucaapps.girocalendar.data.remote.firestore

import simplylucaapps.girocalendar.R

import androidx.compose.ui.graphics.Color

/**
 * Scala di difficoltà STS (Scala Tecnica Sintetica) usata da Spaccasassi Eventi per le uscite
 * MTB: S0 (facile) .. S5 (estrema). Qui replicata per [RideEvent.difficolta] con gli stessi
 * colori del sito PHP originale. S5 è semplificata a nero con doppio bordo invece delle
 * strisce (troppo complesso da rendere bene in una Compose badge di poche decine di dp).
 *
 * Il dato salvato è sempre l'id "s0".."s5", indipendentemente da come viene mostrato: la
 * preferenza "Scala difficoltà" (vedi [AppSettings.difficultyScale][simplylucaapps.girocalendar.data.settings.AppSettings.difficultyScale])
 * sceglie solo QUALE delle due liste sotto ([LEVELS] o [LEVELS_TRAILFORKS]) usare per
 * etichetta/colore del badge, non cambia il valore salvato su Firestore.
 */
data class DifficultyLevel(
    val id: String,
    val label: String,
    val color: Color,
    val onColor: Color,
    val doubleBorder: Boolean = false,
)

object Difficulty {
    /** Scala STS (Spaccasassi Eventi): S0 grigio, S1 verde, S2 blu, S3 rosso, S4/S5 neri. */
    val LEVELS: List<DifficultyLevel> = listOf(
        DifficultyLevel("s0", "S0", Color(0xFFE0E0E0), Color(0xFF222222)),
        DifficultyLevel("s1", "S1", Color(0xFF27AE60), Color.White),
        DifficultyLevel("s2", "S2", Color(0xFF2980B9), Color.White),
        DifficultyLevel("s3", "S3", Color(0xFFE74C3C), Color.White),
        DifficultyLevel("s4", "S4", Color(0xFF111111), Color.White),
        DifficultyLevel("s5", "S5", Color(0xFF111111), Color.White, doubleBorder = true),
    )

    /** Scala Trailforks UK/EU (legenda del sito): White, Green, Blue, Red, Black, Proline (arancione).
     * Stessi id "s0".."s5" per restare compatibile con i dati già salvati. */
    val LEVELS_TRAILFORKS: List<DifficultyLevel> = listOf(
        DifficultyLevel("s0", "White", Color(0xFFF5F5F5), Color(0xFF222222)),
        DifficultyLevel("s1", "Green", Color(0xFF2E8B3D), Color.White),
        DifficultyLevel("s2", "Blue", Color(0xFF2980B9), Color.White),
        DifficultyLevel("s3", "Red", Color(0xFFD32F2F), Color.White),
        DifficultyLevel("s4", "Black", Color(0xFF111111), Color.White),
        DifficultyLevel("s5", "Proline", Color(0xFFE8720C), Color.White),
    )

    /** Le etichette da usare, in base alla preferenza "Scala difficoltà" ("sts" | "trailforks"). */
    fun levelsFor(scale: String): List<DifficultyLevel> = if (scale == "trailforks") LEVELS_TRAILFORKS else LEVELS

    fun of(id: String, scale: String = "sts"): DifficultyLevel? =
        levelsFor(scale).firstOrNull { it.id == id.trim().lowercase() }

    /** Etichetta descrittiva per i picker (es. "S2 · Impegnativa"), sempre in scala STS: usata
     * solo nell'editor evento, dove il riferimento numerico S0-S5 resta comunque utile. */
    fun description(id: String): String = when (id) {
        "s0" -> simplylucaapps.girocalendar.util.Str.t(R.string.df_s0)
        "s1" -> simplylucaapps.girocalendar.util.Str.t(R.string.df_s1)
        "s2" -> simplylucaapps.girocalendar.util.Str.t(R.string.df_s2)
        "s3" -> simplylucaapps.girocalendar.util.Str.t(R.string.df_s3)
        "s4" -> simplylucaapps.girocalendar.util.Str.t(R.string.df_s4)
        "s5" -> simplylucaapps.girocalendar.util.Str.t(R.string.df_s5)
        else -> id.uppercase()
    }
}
