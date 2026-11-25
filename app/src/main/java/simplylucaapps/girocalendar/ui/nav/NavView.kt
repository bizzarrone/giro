package simplylucaapps.girocalendar.ui.nav

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.File
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Mappa di navigazione disegnata a mano su Canvas (niente WebView): traccia, posizione, frecce grosse sulle svolte
 * e banner in alto con la prossima manovra. Il mondo è in metri con y verso SUD come lo schermo, quindi
 * basta una rotazione per la "direzione di marcia in alto".
 */
/** Testi del banner e delle frecce, passati dalla UI (così seguono la lingua dell'app). */
class NavTexts(
    val waiting: String, val summary: (String, Int) -> String, val far: String, val farDetail: (String, String) -> String,
    val off: String, val offDetail: (String) -> String, val arrival: String, val carryOn: String, val remaining: (String) -> String,
    val turn: (NavTurn) -> String, val osm: String = "© OpenStreetMap",
)

/** Dati del cruscotto, ricalcolati a ogni posizione. [eleM] e i D+ sono NaN se il GPX non ha quote. */
class NavStats(
    val doneM: Float, val totalM: Float, val eleM: Float, val gainDone: Float, val gainLeft: Float, val offRouteM: Float, val hasFix: Boolean,
) { val pct: Int get() = if (totalM > 0) (doneM * 100f / totalM).toInt().coerceIn(0, 100) else 0 }

/** Un trail da disegnare sulla mappa (occhietto): nome, colore dello stato e tracciato. */
class TrailLine(val name: String, val color: Int, val lat: DoubleArray, val lon: DoubleArray)

class NavView(ctx: Context) : View(ctx) {
    var texts: NavTexts? = null
    var onStats: ((NavStats) -> Unit)? = null
    private val d = ctx.resources.displayMetrics.density

    var route: NavRoute? = null
        set(v) { field = v; trailW = null; if (v != null) onRoute(v) }
    /** Modalità libera (scheda Registra): niente percorso, solo mappa e posizione. La rotta è un segnaposto creato al primo fix. */
    var free = false
    /** Trail dell'occhietto: vuoto = nascosti. */
    var trailLines: List<TrailLine> = emptyList()
        set(v) { field = v; trailW = null; invalidate() }
    private class TW(val name: String, val color: Int, val xs: FloatArray, val ys: FloatArray, val x0: Float, val y0: Float, val x1: Float, val y1: Float)
    private var trailW: List<TW>? = null
    private val pTr = Path()
    /** Segnalazioni da mostrare sulla mappa: (lat, lon, emoji). */
    var marks: List<Triple<Double, Double, String>> = emptyList()
        set(v) { field = v; invalidate() }
    /** Id (stesso ordine di [marks]) e callback del tocco su un segnaposto. */
    var markIds: List<String> = emptyList()
    var onMarkTap: ((String) -> Unit)? = null
    private val markHit = ArrayList<Triple<Float, Float, String>>()

    // posizione
    private var hasFix = false
    private var px = 0f
    private var py = 0f
    var heading = 0f
        private set
    private var progress = 0
    private var offRoute = 0f
    private var wasFar = false

    // scorrimento morbido tra un fix e il successivo: la vista mostra (vx,vy,vh), che raggiungono (px,py,heading) in ~1 s
    private var vx = 0f
    private var vy = 0f
    private var vh = 0f
    private var gx0 = 0f
    private var gy0 = 0f
    private var gh0 = 0f
    private var gStart = 0L
    private var gMs = 1000L
    private var lastFixAt = 0L
    private val pAll = Path()
    private val pRest = Path()
    private val pDone = Path()
    private val pTrail = Path()
    /** Traccia in registrazione (lat, lon) da disegnare colorata nella mappa libera. */
    var trail: List<Pair<Double, Double>> = emptyList()

    // vista
    private var mPerPx = 5f
    private var mapCx = 0f
    private var mapCy = 0f
    var follow = false
        private set
    var headingUp = true
        private set
    /** Sfondo scelto (null = solo traccia) e strato percorsi MTB sovrapposto. */
    var layer: NavLayer? = null
        set(v) { field = v; invalidate() }
    var mtb = false
        set(v) { field = v; invalidate() }


    private val loaders = HashMap<NavLayer, NavTiles>()
    private fun loader(l: NavLayer) = loaders.getOrPut(l) { NavTiles(File(context.cacheDir, "tiles/${l.id}"), OfflineMaps.dir(context, l), l) { postInvalidate() } }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (follow) { mapCx = centerX(); mapCy = centerY(); follow = false; onFollowChanged?.invoke(false) }
            val r = Math.toRadians(rot().toDouble())
            val wx = (dx * cos(r) - dy * sin(r)).toFloat() * mPerPx
            val wy = (dx * sin(r) + dy * cos(r)).toFloat() * mPerPx
            mapCx += wx; mapCy += wy
            invalidate()
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val hit = markHit.minByOrNull { hypot(it.first - e.x, it.second - e.y) } ?: return false
            if (hit.third.isEmpty() || hypot(hit.first - e.x, hit.second - e.y) > 32 * d) return false
            onMarkTap?.invoke(hit.third); return true
        }
    })
    private val scaler = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(det: ScaleGestureDetector): Boolean { zoom(1f / det.scaleFactor); return true }
    })

    var onFollowChanged: ((Boolean) -> Unit)? = null
    private var topInset = 0f
    fun setInsets(top: Int) { topInset = top.toFloat(); invalidate() }

    /** Riquadro del percorso ancora da inquadrare: si applica appena la vista ha una dimensione (anche dopo lo schermo intero). */
    private var fitBox: FloatArray? = null
    private fun tryFit() {
        val b = fitBox ?: return
        if (width <= 0 || height <= 0) return
        mPerPx = max(1f, max((b[1] - b[0]) / width, (b[3] - b[2]) / height) * 1.25f)
        fitBox = null; invalidate()
    }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); tryFit() }

    private fun onRoute(r: NavRoute) {
        progress = 0
        if (free) { mapCx = 0f; mapCy = 0f; mPerPx = 3f; follow = false; hasFix = false; invalidate(); return }
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (i in 0 until r.n) { x0 = min(x0, r.x[i]); x1 = max(x1, r.x[i]); y0 = min(y0, r.y[i]); y1 = max(y1, r.y[i]) }
        mapCx = (x0 + x1) / 2; mapCy = (y0 + y1) / 2
        fitBox = floatArrayOf(x0, x1, y0, y1); tryFit()
        follow = false; hasFix = false
        invalidate()
    }

    // ---------- comandi ----------
    fun zoom(f: Float) { mPerPx = (mPerPx * f).coerceIn(0.15f, 80f); invalidate() }

    fun setFollow(v: Boolean) {
        follow = v && hasFix
        if (follow && mPerPx > 12f) mPerPx = 3f
        onFollowChanged?.invoke(follow)
        invalidate()
    }

    fun toggleHeadingUp(): Boolean { headingUp = !headingUp; invalidate(); return headingUp }

    /** Nuova posizione (GPS o simulata). [brg] = direzione di marcia se nota. */
    fun fix(lat: Double, lon: Double, brg: Float?, alt: Double? = null) {
        val r = route ?: if (free) NavRoute(doubleArrayOf(lat, lat + 0.00009), doubleArrayOf(lon, lon), null).also { route = it } else return
        val nx = r.xOf(lon)
        val ny = r.yOf(lat)
        val first = !hasFix
        // progresso: cerca vicino all'ultimo punto, poi su tutto il percorso se ci si è allontanati
        var best = -1
        var bd = Float.MAX_VALUE
        fun scan(lo: Int, hi: Int) {
            for (i in lo..hi) {
                val dx = r.x[i] - nx; val dy = r.y[i] - ny
                val dd = dx * dx + dy * dy
                if (dd < bd) { bd = dd; best = i }
            }
        }
        if (!first) scan(max(0, progress - 20), min(r.n - 1, progress + 150))
        if (best < 0 || sqrt(bd) > 60f) { bd = Float.MAX_VALUE; scan(0, r.n - 1) }
        progress = best
        offRoute = if (free) 0f else sqrt(bd)
        heading = when {
            brg != null -> brg
            offRoute < 40f && progress + 3 < r.n -> r.bearing(progress, progress + 3)
            else -> heading
        }
        val newH = heading
        val now = SystemClock.uptimeMillis()
        if (first) { vx = nx; vy = ny; vh = newH; gStart = 0L }
        else { gx0 = vx; gy0 = vy; gh0 = vh; gStart = now; gMs = (now - lastFixAt).coerceIn(250L, 1500L) }
        lastFixAt = now
        px = nx; py = ny; hasFix = true
        // se il GPS è lontano dal percorso (es. ancora a casa) resta la vista dell'intero percorso
        // al primo fix, o quando si torna vicino al percorso dopo essere stati lontani (es. GPS a casa, poi simulazione), la vista segue
        if ((first || wasFar) && offRoute < 1500f) setFollow(true)
        wasFar = offRoute >= 1500f
        val e = r.ele; val g = r.gain
        val onRt = offRoute < 60f
        val eleM = when { e != null && onRt -> e[progress]; alt != null -> alt.toFloat(); else -> Float.NaN }
        // lontano dal percorso (>1,5 km) il «punto più vicino» non dice a che punto sei: cruscotto vuoto
        val far = !free && offRoute >= 1500f
        onStats?.invoke(NavStats(if (far) 0f else r.cum[progress], r.length, eleM, if (far) Float.NaN else g?.get(progress) ?: Float.NaN,
            if (g == null) Float.NaN else if (far) g.last() else g.last() - g[progress], offRoute, !far))
        invalidate()
    }

    fun emitInitial() { route?.let { onStats?.invoke(NavStats(0f, it.length, Float.NaN, Float.NaN, it.gain?.last() ?: Float.NaN, 0f, false)) } }

    // ---------- geometria vista ----------
    private fun centerX() = if (follow && hasFix) vx else mapCx
    private fun centerY() = if (follow && hasFix) vy else mapCy
    private fun rot() = if (headingUp && follow && hasFix) vh else 0f

    /** Aggiorna la posizione mostrata; true se l'animazione non è ancora arrivata. */
    private fun glide(): Boolean {
        if (!hasFix || gStart == 0L) return false
        val t = ((SystemClock.uptimeMillis() - gStart).toFloat() / gMs).coerceIn(0f, 1f)
        vx = gx0 + (px - gx0) * t; vy = gy0 + (py - gy0) * t
        var dh = heading - gh0
        if (dh > 180f) dh -= 360f
        if (dh < -180f) dh += 360f
        vh = (gh0 + dh * t + 360f) % 360f
        return t < 1f
    }

    /** Traccia in coordinate schermo: solo i punti visibili e diradati (≥ ~3 px tra l'uno e l'altro), così il costo non dipende dalla lunghezza del percorso. */
    private fun buildPath(p: Path, r: NavRoute, from: Int, to: Int, cx: Float, cy: Float, rot: Float, s: Float, ox: Float, oy: Float, w: Float, h: Float) {
        p.rewind()
        val a = Math.toRadians((-rot).toDouble())
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        val stride = max(1, (3f * d / (s * NavRoute.STEP)).toInt())
        val m = 24f * d
        var pen = false
        var psx = 0f; var psy = 0f; var pvis = false; var have = false
        var i = from
        while (true) {
            val dx = r.x[i] - cx; val dy = r.y[i] - cy
            val sx = ox + (dx * ca - dy * sa) * s
            val sy = oy + (dx * sa + dy * ca) * s
            val vis = sx > -m && sx < w + m && sy > -m && sy < h + m
            if (vis || pvis) {
                if (!pen) { if (have) p.moveTo(psx, psy) else p.moveTo(sx, sy); pen = true }
                p.lineTo(sx, sy)
            } else pen = false
            psx = sx; psy = sy; pvis = vis; have = true
            if (i >= to) break
            i = min(i + stride, to)
        }
    }

    // ---------- disegno ----------
    override fun onDraw(c: Canvas) {
        c.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        val r = route ?: return
        val moving = glide()
        val w = width.toFloat(); val h = height.toFloat()
        val s = 1f / mPerPx
        val ox = w / 2f; val oy = h * 0.62f
        val cx = centerX(); val cy = centerY(); val rot = rot()
        c.drawColor(0xFFF1EFE7.toInt())

        c.save()
        c.translate(ox, oy); c.rotate(-rot); c.scale(s, s); c.translate(-cx, -cy)
        layer?.let { drawTiles(c, r, cx, cy, rot, s, w, h, ox, oy, it) }
        if (mtb) drawTiles(c, r, cx, cy, rot, s, w, h, ox, oy, NavLayer.MTB)
        c.restore()

        if (trailLines.isNotEmpty()) drawTrailLines(c, r, cx, cy, rot, s, w, h, ox, oy)

        if (!free) {
        val done = if (hasFix) progress else 0
        buildPath(pAll, r, 0, r.n - 1, cx, cy, rot, s, ox, oy, w, h)
        line.color = 0xFFFFFFFF.toInt(); line.strokeWidth = 11 * d
        c.drawPath(pAll, line)
        if (done > 1) {
            buildPath(pDone, r, 0, done, cx, cy, rot, s, ox, oy, w, h)
            buildPath(pRest, r, done, r.n - 1, cx, cy, rot, s, ox, oy, w, h)
            line.color = 0xFF757575.toInt(); line.strokeWidth = 7 * d
            c.drawPath(pDone, line)
            line.color = 0xFFC2185B.toInt(); line.strokeWidth = 7 * d
            c.drawPath(pRest, line)
        } else {
            line.color = 0xFFC2185B.toInt(); line.strokeWidth = 7 * d
            c.drawPath(pAll, line)
        }
        }

        // linea percorsa durante la registrazione (mappa libera)
        if (free && trail.size >= 2) {
            pTrail.rewind()
            trail.forEachIndexed { i, (la, lo) ->
                val sp = toScreen(r.xOf(lo), r.yOf(la), cx, cy, rot, s, ox, oy)
                if (i == 0) pTrail.moveTo(sp[0], sp[1]) else pTrail.lineTo(sp[0], sp[1])
            }
            line.color = 0xFFFFFFFF.toInt(); line.strokeWidth = 11 * d; c.drawPath(pTrail, line)
            line.color = 0xFFFF6D00.toInt(); line.strokeWidth = 7 * d; c.drawPath(pTrail, line)
        }

        // la prossima manovra si vede solo nel banner in alto (niente frecce sulla mappa)
        val next = nextTurn()

        // segnalazioni (albero, fango, pericolo…): solo quelle che cadono sullo schermo
        markHit.clear()
        if (marks.isNotEmpty()) {
            txt.textAlign = Paint.Align.CENTER
            txt.textSize = 18 * d
            for ((mi, m) in marks.withIndex()) {
                val sp = toScreen(r.xOf(m.second), r.yOf(m.first), cx, cy, rot, s, ox, oy)
                if (sp[0] < -30 * d || sp[0] > w + 30 * d || sp[1] < -30 * d || sp[1] > h + 30 * d) continue
                markHit.add(Triple(sp[0], sp[1], markIds.getOrNull(mi) ?: ""))
                fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(sp[0], sp[1], 16 * d, fill)
                fill.color = 0xFFEF6C00.toInt(); c.drawCircle(sp[0], sp[1], 14 * d, fill)
                txt.color = 0xFF000000.toInt()
                c.drawText(m.third, sp[0], sp[1] + 6.5f * d, txt)
            }
            txt.textAlign = Paint.Align.LEFT
        }

        // posizione dell'utente
        if (hasFix) {
            val sp = toScreen(vx, vy, cx, cy, rot, s, ox, oy)
            c.save(); c.translate(sp[0], sp[1]); c.rotate(vh - rot)
            fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(0f, 0f, 14 * d, fill)
            fill.color = 0xFF1E88E5.toInt(); c.drawCircle(0f, 0f, 10 * d, fill)
            val tri = Path().apply { moveTo(0f, -26 * d); lineTo(-10 * d, -9 * d); lineTo(10 * d, -9 * d); close() }
            fill.color = 0xFF0D47A1.toInt(); c.drawPath(tri, fill)
            c.restore()
        }

        if (!free) drawBanner(c, r, next, w)
        if (moving) postInvalidateDelayed(33)

        val att = (layer?.attribution ?: "") + if (mtb) (if (layer != null) " · " else "") + NavLayer.MTB.attribution else ""
        if (att.isNotEmpty()) {
            txt.textSize = 11 * d
            val tw = txt.measureText(att)
            fill.color = 0xCCFFFFFF.toInt()
            c.drawRoundRect(4 * d, h - 175 * d - 14 * d, 4 * d + tw + 8 * d, h - 175 * d + 5 * d, 4 * d, 4 * d, fill)
            txt.color = 0xFF333333.toInt()
            c.drawText(att, 8 * d, h - 175 * d, txt)
        }
    }

    private fun drawTrailLines(c: Canvas, r: NavRoute, cx: Float, cy: Float, rot: Float, s: Float, w: Float, h: Float, ox: Float, oy: Float) {
        val list = trailW ?: trailLines.map { t ->
            val xs = FloatArray(t.lat.size) { r.xOf(t.lon[it]) }; val ys = FloatArray(t.lat.size) { r.yOf(t.lat[it]) }
            TW(t.name, t.color, xs, ys, xs.minOrNull() ?: 0f, ys.minOrNull() ?: 0f, xs.maxOrNull() ?: 0f, ys.maxOrNull() ?: 0f)
        }.also { trailW = it }
        val a = Math.toRadians(rot.toDouble())
        var bx0 = Float.MAX_VALUE; var by0 = Float.MAX_VALUE; var bx1 = -Float.MAX_VALUE; var by1 = -Float.MAX_VALUE
        for (px in floatArrayOf(0f, w)) for (py in floatArrayOf(0f, h)) {
            val dx = (px - ox) / s; val dy = (py - oy) / s
            val wx = cx + (dx * cos(a) - dy * sin(a)).toFloat(); val wy = cy + (dx * sin(a) + dy * cos(a)).toFloat()
            bx0 = min(bx0, wx); bx1 = max(bx1, wx); by0 = min(by0, wy); by1 = max(by1, wy)
        }
        var labels = 0
        for (t in list) {
            if (t.x1 < bx0 || t.x0 > bx1 || t.y1 < by0 || t.y0 > by1 || t.xs.size < 2) continue
            pTr.rewind()
            for (i in t.xs.indices) {
                val sp = toScreen(t.xs[i], t.ys[i], cx, cy, rot, s, ox, oy)
                if (i == 0) pTr.moveTo(sp[0], sp[1]) else pTr.lineTo(sp[0], sp[1])
            }
            line.color = 0xCCFFFFFF.toInt(); line.strokeWidth = 8 * d; c.drawPath(pTr, line)
            line.color = t.color; line.strokeWidth = 5 * d; c.drawPath(pTr, line)
            if (mPerPx < 12f && labels < 20 && t.name.isNotBlank()) {
                val m = t.xs.size / 2
                val sp = toScreen(t.xs[m], t.ys[m], cx, cy, rot, s, ox, oy)
                if (sp[0] in 0f..w && sp[1] in 0f..h) {
                    txt.textSize = 11 * d; txt.textAlign = Paint.Align.CENTER
                    val tw = txt.measureText(t.name)
                    fill.color = 0xDDFFFFFF.toInt(); c.drawRoundRect(sp[0] - tw / 2 - 4 * d, sp[1] - 16 * d, sp[0] + tw / 2 + 4 * d, sp[1] + 2 * d, 4 * d, 4 * d, fill)
                    txt.color = 0xFF222222.toInt(); c.drawText(t.name, sp[0], sp[1] - 3 * d, txt)
                    txt.textAlign = Paint.Align.LEFT; labels++
                }
            }
        }
    }

    private fun nextTurn(): NavTurn? {
        val r = route ?: return null
        if (!hasFix) return null
        return r.turns.firstOrNull { it.idx > progress + 1 }
    }

    private fun toScreen(x: Float, y: Float, cx: Float, cy: Float, rot: Float, s: Float, ox: Float, oy: Float): FloatArray {
        val a = Math.toRadians((-rot).toDouble())
        val dx = x - cx; val dy = y - cy
        return floatArrayOf(ox + (dx * cos(a) - dy * sin(a)).toFloat() * s, oy + (dx * sin(a) + dy * cos(a)).toFloat() * s)
    }

    private fun fmt(m: Float): String = when {
        m >= 1000f -> "%.1f km".format(m / 1000f)
        m >= 100f -> "${(round(m / 10f) * 10).toInt()} m"
        else -> "${(round(m / 5f) * 5).toInt()} m"
    }

    private fun drawBanner(c: Canvas, r: NavRoute, next: NavTurn?, w: Float) {
        val tx = texts ?: return
        val bh = 112 * d
        val far = hasFix && offRoute > 1500f
        val remaining = if (hasFix) r.length - r.cum[progress] else r.length
        val off = hasFix && offRoute > 40f
        fill.color = if (off) 0xF0B71C1C.toInt() else 0xF0212121.toInt()
        c.drawRect(0f, 0f, w, bh + topInset, fill)
        c.save(); c.translate(0f, topInset)
        txt.color = 0xFFFFFFFF.toInt()
        when {
            !hasFix -> {
                txt.textSize = 22 * d; c.drawText(tx.waiting, 16 * d, 46 * d, txt)
                txt.textSize = 15 * d; c.drawText(tx.summary(fmt(r.length), r.turns.size), 16 * d, 80 * d, txt)
            }
            far -> {
                txt.textSize = 24 * d; c.drawText(tx.far, 16 * d, 46 * d, txt)
                txt.textSize = 18 * d; c.drawText(tx.farDetail(fmt(offRoute), fmt(r.length)), 16 * d, 84 * d, txt)
            }
            off -> {
                txt.textSize = 26 * d; c.drawText(tx.off, 16 * d, 50 * d, txt)
                txt.textSize = 20 * d; c.drawText(tx.offDetail(fmt(offRoute)), 16 * d, 86 * d, txt)
            }
            remaining < 30f -> {
                txt.textSize = 32 * d; c.drawText(tx.arrival, 16 * d, 66 * d, txt)
            }
            next == null -> {
                txt.textSize = 22 * d; c.drawText(tx.carryOn, 16 * d, 46 * d, txt)
                txt.textSize = 18 * d; c.drawText(tx.remaining(fmt(remaining)), 16 * d, 84 * d, txt)
            }
            else -> {
                arrow(c, 16 * d + 40 * d, bh / 2, 76 * d, 0f, next.angle, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), true)
                txt.textSize = 44 * d; c.drawText(fmt(next.dist - r.cum[progress]), 108 * d, 58 * d, txt)
                txt.textSize = 17 * d; c.drawText(tx.turn(next), 108 * d, 88 * d, txt)
                txt.textSize = 13 * d; txt.color = 0xFFBDBDBD.toInt()
                c.drawText(tx.remaining(fmt(remaining)), 108 * d, 106 * d, txt)
            }
        }
        c.restore()
    }

    /**
     * Freccia "piegata": arriva dal basso nella direzione [inDeg] (0 = su, senso orario), gira di [turn] gradi
     * (+ destra) e finisce con la punta. [size] = ingombro totale.
     */
    private fun arrow(c: Canvas, x: Float, y: Float, size: Float, inDeg: Float, turn: Float, color: Int, outline: Int, withOutline: Boolean) {
        val a1 = Math.toRadians(inDeg.toDouble()); val a2 = Math.toRadians((inDeg + turn).toDouble())
        val half = size * 0.5f
        val tx = x - (sin(a1) * half).toFloat(); val ty = y + (cos(a1) * half).toFloat()
        val ex = x + (sin(a2) * half * 0.62f).toFloat(); val ey = y - (cos(a2) * half * 0.62f).toFloat()
        val body = Path().apply { moveTo(tx, ty); lineTo(x, y); lineTo(ex, ey) }
        val head = size * 0.34f
        val dx = sin(a2).toFloat(); val dy = -cos(a2).toFloat()
        val tip = Path().apply {
            moveTo(ex + dx * head, ey + dy * head)
            lineTo(ex - dy * head * 0.85f, ey + dx * head * 0.85f)
            lineTo(ex + dy * head * 0.85f, ey - dx * head * 0.85f)
            close()
        }
        line.strokeCap = Paint.Cap.BUTT
        if (withOutline) {
            line.color = outline; line.strokeWidth = size * 0.30f
            c.drawPath(body, line)
        }
        line.color = color; line.strokeWidth = size * 0.20f
        c.drawPath(body, line)
        fill.color = color; c.drawPath(tip, fill)
        line.strokeCap = Paint.Cap.ROUND
    }

    // ---------- tessere ----------
    private fun drawTiles(c: Canvas, r: NavRoute, cx: Float, cy: Float, rot: Float, s: Float, w: Float, h: Float, ox: Float, oy: Float, lay: NavLayer) {
        val tiles = loader(lay)
        val res = 156543.03f * cos(Math.toRadians(r.latOf(cy))).toFloat()
        var z = (ln((res / mPerPx).toDouble()) / ln(2.0)).let { round(it).toInt() }.coerceIn(8, lay.maxZoom)
        val a = Math.toRadians(rot.toDouble())
        var minLon = 1e9; var maxLon = -1e9; var minLat = 1e9; var maxLat = -1e9
        for (cxs in floatArrayOf(0f, w)) for (cys in floatArrayOf(0f, h)) {
            val dx = (cxs - ox) / s; val dy = (cys - oy) / s
            val wx = cx + (dx * cos(a) - dy * sin(a)).toFloat()
            val wy = cy + (dx * sin(a) + dy * cos(a)).toFloat()
            val lo = r.lonOf(wx); val la = r.latOf(wy)
            minLon = min(minLon, lo); maxLon = max(maxLon, lo); minLat = min(minLat, la); maxLat = max(maxLat, la)
        }
        var n = 2.0.pow(z)
        fun tx(lon: Double) = floor((lon + 180) / 360 * n).toInt()
        fun ty(lat: Double): Int {
            val rr = Math.toRadians(lat)
            return floor((1 - ln(tan(rr) + 1 / cos(rr)) / PI) / 2 * n).toInt()
        }
        var x0 = 0; var x1 = 0; var y0 = 0; var y1 = 0
        while (true) {
            n = 2.0.pow(z)
            x0 = tx(minLon).coerceAtLeast(0); x1 = tx(maxLon).coerceAtMost(n.toInt() - 1)
            y0 = ty(maxLat).coerceAtLeast(0); y1 = ty(minLat).coerceAtMost(n.toInt() - 1)
            // lo schermo ruotato può richiedere molte tessere: si scende di zoom finché sono poche
            if ((x1 - x0 + 1) * (y1 - y0 + 1) <= 36 || z <= 8) break
            z--
        }
        if ((x1 - x0 + 1) * (y1 - y0 + 1) > 60) return
        fun lonOf(t: Int) = t / n * 360 - 180
        fun latOf(t: Int) = Math.toDegrees(atan(sinh(PI * (1 - 2 * t / n))))
        for (xx in x0..x1) for (yy in y0..y1) {
            val bmp = tiles.get(z, xx, yy) ?: continue
            val rect = RectF(r.xOf(lonOf(xx)), r.yOf(latOf(yy)), r.xOf(lonOf(xx + 1)), r.yOf(latOf(yy + 1)))
            c.drawBitmap(bmp, null, rect, tilePaint)
        }
    }

    // ---------- tocco ----------
    /** Nella scheda (non a schermo intero) il tocco va al pager delle schede: la mappa si usa solo a tutto schermo. */
    var touchEnabled = true
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!touchEnabled) return false
        // trascinando la mappa non devono scorrere le schede del pager
        if (e.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        scaler.onTouchEvent(e)
        if (!scaler.isInProgress) gestures.onTouchEvent(e)
        return true
    }

    fun release() { loaders.values.forEach { it.shutdown() }; loaders.clear() }

    /** Distanza di prova: punto del percorso a [meters] dall'inizio. */
    fun pointAt(meters: Float): Triple<Double, Double, Float>? {
        val r = route ?: return null
        val i = min(r.n - 2, max(0, (meters / NavRoute.STEP).toInt()))
        return Triple(r.latOf(r.y[i]), r.lonOf(r.x[i]), r.bearing(i, i + 1))
    }

    val routeLength: Float get() = route?.length ?: 0f
}
