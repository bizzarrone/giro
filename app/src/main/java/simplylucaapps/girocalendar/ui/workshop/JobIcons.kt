package simplylucaapps.girocalendar.ui.workshop

import simplylucaapps.girocalendar.R

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import simplylucaapps.girocalendar.data.remote.firestore.WorkshopJob

/**
 * Icone selezionabili per un lavoro. Sono icone Material (non esistono glifi nativi per
 * catena/ammortizzatore), scelte per analogia; [keywords] servono al rilevamento automatico
 * dal testo (minuscolo, "contiene").
 */
enum class JobIcon(val id: String, val icon: ImageVector, private val labelRes: Int, val keywords: List<String>) {
    CHAIN("chain", Icons.Default.Link, R.string.ji_chain, listOf("catena")),
    CHAINRING("chainring", Icons.Default.Settings, R.string.ji_chainring,
        listOf("corona", "pignon", "cassetta", "cassette", "guarnitura", "pedivella", "ingranagg")),
    SHOCK("shock", Icons.Default.SwapVert, R.string.ji_shock,
        listOf("ammo", "forcella", "sospension", "mono ", "reggisella telescopica")),
    BRAKE("brake", Icons.Default.Album, R.string.ji_brake,
        listOf("fren", "pastigli", "disco", "dischi", "pinza", "pinze", "rotor")),
    WHEEL("wheel", Icons.Default.TireRepair, R.string.ji_wheel,
        listOf("ruota", "ruote", "copertone", "pneumatic", "camera d'aria", "tubeless", "cerchio", "raggi", "mozzo")),
    OIL("oil", Icons.Default.WaterDrop, R.string.ji_oil,
        listOf("olio", "spurgo", "lubrific", "sigillante", "grasso")),
    CABLE("cable", Icons.Default.Cable, R.string.ji_cable, listOf("cavo", "cavi", "guaina", "guaine", "filo")),
    DERAILLEUR("derailleur", Icons.Default.Tune, R.string.ji_derailleur,
        listOf("deragliatore", "registr", "indicizz", "cambio cavo", "cambio guaina")),
    SADDLE("saddle", Icons.Default.EventSeat, R.string.ji_saddle, listOf("sella", "reggisella")),
    HANDLEBAR("handlebar", Icons.Default.OpenWith, R.string.ji_handlebar,
        listOf("manubrio", "sterzo", "attacco", "manopol", "nastro")),
    PEDAL("pedal", Icons.Default.PedalBike, R.string.ji_pedal, listOf("pedal", "telaio", "bici")),
    ELECTRIC("electric", Icons.Default.Bolt, R.string.ji_electric, listOf("batteria", "luce", "luci", "motore", "display")),
    CLEAN("clean", Icons.Default.CleaningServices, R.string.ji_clean, listOf("pulizia", "lavaggio", "lavata", "sgrass")),
    BOLT("bolt", Icons.Default.Hardware, R.string.ji_bolt, listOf("vite", "viti", "bullone", "bulloni", "serraggio")),
    SERVICE("service", Icons.Default.Build, R.string.ji_service, listOf("tagliando", "revisione", "controllo", "manutenzione"));

    val labelIt: String get() = simplylucaapps.girocalendar.util.Str.t(labelRes)

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
        val EN_KEYWORDS = mapOf(
            "chain" to listOf("chain"),
            "chainring" to listOf("chainring", "cassette", "crank", "sprocket"),
            "shock" to listOf("shock", "fork", "suspension", "dropper"),
            "brake" to listOf("brake", "pads", "rotor", "caliper"),
            "wheel" to listOf("wheel", "tyre", "tire", "inner tube", "tubeless", "rim", "spoke", "hub"),
            "oil" to listOf("oil", "bleed", "lube", "sealant", "grease"),
            "cable" to listOf("cable", "housing"),
            "derailleur" to listOf("derailleur", "indexing", "shifting"),
            "saddle" to listOf("saddle", "seatpost"),
            "handlebar" to listOf("handlebar", "steering", "stem", "grip"),
            "pedal" to listOf("pedal", "frame", "bike"),
            "electric" to listOf("battery", "light", "motor", "display"),
            "clean" to listOf("clean", "wash", "degrease"),
            "bolt" to listOf("bolt", "screw", "torque"),
            "service" to listOf("service", "overhaul", "inspection", "maintenance"),
        )
        fun detect(text: String): List<JobIcon> {
            val t = text.lowercase()
            return entries.filter { i -> (i.keywords + (EN_KEYWORDS[i.id] ?: emptyList())).any { it in t } }
        }
        /** Icone da mostrare per [job]: quelle salvate, o (se mai impostate) il rilevamento automatico. */
        fun resolve(job: WorkshopJob, auto: Boolean): List<JobIcon> =
            job.icons?.mapNotNull(::of) ?: if (auto) detect(job.work) else emptyList()
    }
}

/** Simbolo testuale usato negli export dove non si possono disegnare le icone (testo, CSV, ODS). */
val JobIcon.emoji: String get() = when (this) {
    JobIcon.CHAIN -> "⛓️"
    JobIcon.CHAINRING -> "⚙️"
    JobIcon.SHOCK -> "↕️"
    JobIcon.BRAKE -> "💿"
    JobIcon.WHEEL -> "🛞"
    JobIcon.OIL -> "💧"
    JobIcon.CABLE -> "🔌"
    JobIcon.DERAILLEUR -> "🎚️"
    JobIcon.SADDLE -> "💺"
    JobIcon.HANDLEBAR -> "🕹️"
    JobIcon.PEDAL -> "🚲"
    JobIcon.ELECTRIC -> "⚡"
    JobIcon.CLEAN -> "🧹"
    JobIcon.BOLT -> "🔩"
    JobIcon.SERVICE -> "🔧"
}
