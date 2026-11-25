package simplylucaapps.girocalendar.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import simplylucaapps.girocalendar.ui.workshop.JobIcon
import simplylucaapps.girocalendar.ui.workshop.emoji
import androidx.core.content.FileProvider
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.JobColor
import simplylucaapps.girocalendar.data.remote.firestore.WorkshopJob
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Esporta il log lavori di una bici in CSV, testo formattato, ODS, PDF, JPEG. */
object WorkshopExport {
    enum class Format(val ext: String, val mime: String) {
        CSV("csv", "text/csv"),
        TEXT("txt", "text/plain"),
        ODS("ods", "application/vnd.oasis.opendocument.spreadsheet"),
        PDF("pdf", "application/pdf"),
        JPEG("jpg", "image/jpeg"),
    }

    /** Testi localizzati, riempiti all'inizio di [share] dalle risorse (it/en). */
    private var HEADERS = listOf("DATA", "COSTO", "LUOGO", "LAVORO", "KM")
    private var TOTAL = "TOTALE"
    private var UPDATED = "aggiornamento"
    private var TITLE = "Manutenzione bici %s"
    private var ICONS_HEADER = "ICONE"

    /** Icone per lavoro (id -> icone) e loro colore, impostati da [share]. */
    private var ICONS: Map<String, List<JobIcon>> = emptyMap()
    private var COLOR: (String) -> Int? = { null }
    private fun icons(j: WorkshopJob) = ICONS[j.id].orEmpty()
    private fun emojis(j: WorkshopJob) = icons(j).joinToString("") { it.emoji }

    private fun cost(c: Double?) = when {
        c == null -> ""
        c % 1.0 == 0.0 -> c.toLong().toString()
        else -> String.format(Locale.US, "%.2f", c).trimEnd('0').trimEnd('.')
    }
    private fun row(j: WorkshopJob) = listOf(j.date, cost(j.cost), j.place, j.work, j.km?.toString() ?: "")
    /** Riga per output "da leggere" (testo, PDF, JPEG): data gg.mm.aa e costo con virgola. */
    private fun shownRow(j: WorkshopJob) = row(j).toMutableList().also {
        it[0] = runCatching { LocalDate.parse(j.date).format(DateTimeFormatter.ofPattern("dd.MM.yy")) }.getOrDefault(j.date)
        it[1] = it[1].replace('.', ',')
    }
    private fun total(jobs: List<WorkshopJob>) = cost(jobs.sumOf { it.cost ?: 0.0 })
    private fun title(bike: String) = TITLE.format(bike)
    private fun today() = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yy"))

    fun share(context: Context, format: Format, bike: String, jobs: List<WorkshopJob>,
              autoIcons: Boolean, colorOf: (String) -> Int?) {
        ICONS = jobs.associate { it.id to JobIcon.resolve(it, autoIcons) }
        COLOR = colorOf
        HEADERS = listOf(R.string.workshop_col_date, R.string.workshop_col_cost, R.string.workshop_col_place,
            R.string.workshop_col_job, R.string.workshop_col_km).map(context::getString)
        TOTAL = context.getString(R.string.workshop_total)
        ICONS_HEADER = context.getString(R.string.workshop_col_icons)
        UPDATED = context.getString(R.string.workshop_updated)
        TITLE = context.getString(R.string.workshop_title_bike, "%s").replace("%%s", "%s")
        val safe = bike.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "bici" }
        val file = File(File(context.cacheDir, "export").apply { mkdirs() }, "officina_$safe.${format.ext}")
        when (format) {
            Format.CSV -> file.writeText(csv(jobs))
            Format.TEXT -> file.writeText(text(bike, jobs))
            Format.ODS -> ods(file, bike, jobs)
            Format.PDF -> pdf(file, bike, jobs)
            Format.JPEG -> jpeg(file, bike, jobs)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, title(bike)))
    }

    // ── CSV ────────────────────────────────────────────────────
    // Separatore ";" e decimali con la virgola: è ciò che Excel/LibreOffice in italiano si aspettano.
    private fun q(s: String) = if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    private fun csv(jobs: List<WorkshopJob>): String {
        val withIcons = jobs.any { icons(it).isNotEmpty() }
        val head = if (withIcons) HEADERS + ICONS_HEADER else HEADERS
        val body = jobs.map { j ->
            row(j).toMutableList().also { it[1] = it[1].replace('.', ',') }.let { if (withIcons) it + emojis(j) else it }
        }
        return (listOf(head) + body).joinToString("\r\n") { r -> r.joinToString(";", transform = ::q) } + "\r\n"
    }

    // ── Testo formattato (colonne allineate, font monospazio) ─
    private fun text(bike: String, jobs: List<WorkshopJob>): String {
        val rows = jobs.map { j ->
            shownRow(j).also { r -> emojis(j).takeIf { it.isNotEmpty() }?.let { e -> r[3] = "$e ${r[3]}" } }
        }
        val w = HEADERS.indices.map { c -> (rows.map { it[c].length } + HEADERS[c].length).max() }
        fun line(r: List<String>) = r.indices.joinToString("  ") { c ->
            if (c == 1 || c == 4) r[c].padStart(w[c]) else r[c].padEnd(w[c])
        }.trimEnd()
        return buildString {
            append(title(bike)).append("   ($UPDATED: ").append(today()).append(")\n\n")
            append(line(HEADERS)).append('\n')
            append("-".repeat(w.sum() + 2 * (w.size - 1))).append('\n')
            rows.forEach { append(line(it)).append('\n') }
            append("-".repeat(w.sum() + 2 * (w.size - 1))).append('\n')
            append("$TOTAL: ").append(total(jobs).replace('.', ',')).append(" €").append('\n')
        }
    }

    // ── ODS (LibreOffice / Excel) ──────────────────────────────
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun ods(file: File, bike: String, jobs: List<WorkshopJob>) {
        val styles = JobColor.entries.filter { it.color != null }.joinToString("") {
            val hex = "#%06X".format(0xFFFFFF and it.color!!.toArgb())
            """<style:style style:name="c_${it.id}" style:family="table-cell"><style:table-cell-properties fo:background-color="$hex"/></style:style>"""
        }
        fun cell(v: String, num: Boolean, style: String = "") = when {
            v.isEmpty() -> "<table:table-cell$style/>"
            num -> "<table:table-cell$style office:value-type=\"float\" office:value=\"${esc(v)}\"><text:p>${esc(v)}</text:p></table:table-cell>"
            else -> "<table:table-cell$style office:value-type=\"string\"><text:p>${esc(v)}</text:p></table:table-cell>"
        }
        val withIcons = jobs.any { icons(it).isNotEmpty() }
        val body = buildString {
            append("<table:table-row>").append((if (withIcons) HEADERS + ICONS_HEADER else HEADERS).joinToString("") { cell(it, false, " table:style-name=\"hdr\"") }).append("</table:table-row>")
            jobs.forEach { j ->
                val r = row(j)
                val st = if (j.color.color != null) " table:style-name=\"c_${j.color.id}\"" else ""
                append("<table:table-row>")
                append(cell(r[0], false)).append(cell(r[1], true)).append(cell(r[2], false))
                append(cell(r[3], false, st)).append(cell(r[4], true))
                if (withIcons) append(cell(emojis(j), false))
                append("</table:table-row>")
            }
            append("<table:table-row>").append(cell("", false)).append(cell(total(jobs), true))
                .append(cell(TOTAL, false)).append("</table:table-row>")
        }
        val content = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" office:version="1.2"><office:automatic-styles><style:style style:name="hdr" style:family="table-cell"><style:table-cell-properties fo:background-color="#999999"/><style:text-properties fo:color="#ffffff" fo:font-weight="bold"/></style:style>$styles</office:automatic-styles><office:body><office:spreadsheet><table:table table:name="${esc(title(bike).take(31).replace(Regex("[\\[\\]*?:/\\\\]"), "-"))}">$body</table:table></office:spreadsheet></office:body></office:document-content>"""
        val manifest = """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2"><manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.spreadsheet"/><manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/></manifest:manifest>"""
        ZipOutputStream(file.outputStream()).use { z ->
            // "mimetype" deve essere la prima voce e non compressa.
            val mime = "application/vnd.oasis.opendocument.spreadsheet".toByteArray()
            val e = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size
                crc = java.util.zip.CRC32().also { it.update(mime) }.value
            }
            z.putNextEntry(e); z.write(mime); z.closeEntry()
            z.putNextEntry(ZipEntry("content.xml")); z.write(content.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("META-INF/manifest.xml")); z.write(manifest.toByteArray()); z.closeEntry()
        }
    }

    // ── Disegno tabella (condiviso da PDF e JPEG) ──────────────
    private const val PAGE_W = 842  // A4 orizzontale, punti PDF
    private const val PAGE_H = 595
    private const val MARGIN = 28f
    private val COL_W = floatArrayOf(0.13f, 0.09f, 0.14f, 0.54f, 0.10f)

    private class Layouts(val cells: List<StaticLayout>, val height: Float, val indent: Float = 0f)

    private fun paint(size: Float, bold: Boolean, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL); this.color = color
    }

    private fun layoutRow(r: List<String>, tableW: Float, size: Float, bold: Boolean, fg: Int, indent: Float = 0f): Layouts {
        val p = paint(size, bold, fg)
        val cells = r.indices.map { c ->
            val w = (tableW * COL_W[c]).toInt() - 8 - (if (c == 3) indent.toInt() else 0)
            val align = if (c == 1 || c == 4) Layout.Alignment.ALIGN_OPPOSITE else if (c == 0 || c == 2) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
            StaticLayout.Builder.obtain(r[c], 0, r[c].length, p, w).setAlignment(align).build()
        }
        return Layouts(cells, (cells.maxOf { it.height } + 6f), indent)
    }

    /** Disegna l'icona Material [icon] (vettoriale) su [cv] a ([x],[y]) con lato [size] e colore [argb]. */
    private fun drawIcon(cv: Canvas, icon: JobIcon, x: Float, y: Float, size: Float, argb: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb; style = Paint.Style.FILL }
        cv.save(); cv.translate(x, y); cv.scale(size / 24f, size / 24f)
        fun walk(n: VectorNode) {
            when (n) {
                is VectorPath -> {
                    val path = PathParser().addPathNodes(n.pathData).toPath().asAndroidPath()
                    path.fillType = if (n.pathFillType == PathFillType.EvenOdd) android.graphics.Path.FillType.EVEN_ODD
                    else android.graphics.Path.FillType.WINDING
                    cv.drawPath(path, paint)
                }
                is VectorGroup -> n.forEach(::walk)
            }
        }
        walk(icon.icon.root)
        cv.restore()
    }

    private fun drawRow(cv: Canvas, y: Float, l: Layouts, tableW: Float, bg: Int?, workBg: Int? = null,
                        rowIcons: List<JobIcon> = emptyList(), iconSize: Float = 0f) {
        var x = MARGIN
        val line = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 0.6f }
        for (c in l.cells.indices) {
            val w = tableW * COL_W[c]
            val fill = if (c == 3 && workBg != null) workBg else bg
            if (fill != null) cv.drawRect(x, y, x + w, y + l.height, Paint().apply { color = fill })
            cv.drawRect(x, y, x + w, y + l.height, line)
            cv.save(); cv.translate(x + 4 + (if (c == 3) l.indent else 0f), y + 3); l.cells[c].draw(cv); cv.restore()
            if (c == 3) rowIcons.forEachIndexed { i, ic ->
                drawIcon(cv, ic, x + 4 + i * (iconSize + 2f), y + 3 + (l.cells[c].getLineBottom(0) - iconSize) / 2f, iconSize, COLOR(ic.id) ?: Color.BLACK)
            }
            x += w
        }
    }

    /** Disegna intestazione, righe e totale; [pageH] = altezza utile per pagina (null = un'unica pagina). */
    private fun render(bike: String, jobs: List<WorkshopJob>, width: Int, size: Float,
                       newPage: (Int) -> Canvas, pageH: Float?, finish: (Float) -> Unit) {
        val tableW = width - 2 * MARGIN
        val head = layoutRow(HEADERS.toMutableList().also { it[1] = it[1] + " €" }, tableW, size, true, Color.WHITE)
        val iconSize = size * 1.15f
        val rows = jobs.map { it to layoutRow(shownRow(it), tableW, size, false, Color.BLACK, icons(it).size * (iconSize + 2f)) }
        val titleP = paint(size * 1.7f, true, Color.BLACK)
        val subP = paint(size, false, Color.BLACK)
        var page = 1
        var cv = newPage(page)
        fun header(): Float {
            var y = MARGIN
            if (page == 1) {
                cv.drawText(title(bike), MARGIN, y + titleP.textSize, titleP)
                cv.drawText("$UPDATED: ${today()}", width - MARGIN - subP.measureText("$UPDATED: 00/00/00"), y + titleP.textSize, subP)
                y += titleP.textSize * 1.8f
            }
            drawRow(cv, y, head, tableW, 0xFF999999.toInt()); return y + head.height
        }
        var y = header()
        for ((j, l) in rows) {
            if (pageH != null && y + l.height > pageH - MARGIN) { finish(y); page++; cv = newPage(page); y = header() }
            drawRow(cv, y, l, tableW, null, j.color.color?.toArgb(), icons(j), iconSize)
            y += l.height
        }
        val bold = paint(size, true, Color.BLACK)
        val tot = total(jobs).replace('.', ',') + " €"
        cv.drawText(tot, MARGIN + tableW * (COL_W[0] + COL_W[1] / 2) - bold.measureText(tot) / 2, y + 4 + size + 2, bold)
        cv.drawText(TOTAL, MARGIN + tableW * (COL_W[0] + COL_W[1] + COL_W[2] / 2) - bold.measureText(TOTAL) / 2, y + 4 + size + 2, bold)
        finish(y + size * 3)
    }

    private fun pdf(file: File, bike: String, jobs: List<WorkshopJob>) {
        val doc = PdfDocument()
        var cur: PdfDocument.Page? = null
        render(bike, jobs, PAGE_W, 9f, { n ->
            cur?.let { doc.finishPage(it) }
            cur = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, n).create()); cur!!.canvas
        }, PAGE_H.toFloat(), { })
        cur?.let { doc.finishPage(it) }
        file.outputStream().use { doc.writeTo(it) }; doc.close()
    }

    private fun jpeg(file: File, bike: String, jobs: List<WorkshopJob>) {
        val scale = 2f
        val w = (PAGE_W * scale).toInt()
        // Prima passata su un canvas fittizio per misurare l'altezza, seconda per disegnare.
        var h = 0f
        render(bike, jobs, w, 9f * scale, { Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)) }, null) { h = it }
        val bmp = Bitmap.createBitmap(w, (h + MARGIN).toInt().coerceAtLeast(200), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp).apply { drawColor(Color.WHITE) }
        render(bike, jobs, w, 9f * scale, { cv }, null) { }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
    }
}
