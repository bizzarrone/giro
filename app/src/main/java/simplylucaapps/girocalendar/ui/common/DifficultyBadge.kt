package simplylucaapps.girocalendar.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import simplylucaapps.girocalendar.data.remote.firestore.Difficulty
import simplylucaapps.girocalendar.ui.theme.LocalDifficultyScale

/**
 * Badge colorato per la difficoltà. Etichetta/colori dipendono dalla preferenza "Scala
 * difficoltà" ([LocalDifficultyScale]): "sts" (S0..S5, colori Spaccasassi Eventi) o
 * "trailforks" (White/Green/Blue/Red/Black/Proline). Il valore [difficolta] salvato
 * resta sempre l'id "s0".."s5" indipendentemente da come viene mostrato.
 */
@Composable
fun DifficultyBadge(difficolta: String, modifier: Modifier = Modifier) {
    val scale = LocalDifficultyScale.current
    val level = Difficulty.of(difficolta, scale) ?: return
    val shape = RoundedCornerShape(6.dp)
    var mod = modifier
        .background(level.color, shape)
        .then(
            if (level.doubleBorder)
                Modifier
                    .border(1.dp, level.onColor.copy(alpha = 0.9f), shape)
                    .padding(1.5.dp)
                    .border(1.dp, level.onColor.copy(alpha = 0.9f), shape)
            else if (level.id == "s0")
                Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
            else Modifier
        )
    Text(
        level.label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = level.onColor,
        modifier = mod.padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
