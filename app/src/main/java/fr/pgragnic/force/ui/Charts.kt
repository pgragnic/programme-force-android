package fr.pgragnic.force.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

data class Pt(val x: Long, val y: Double)
data class Bar(val label: String, val y: Double, val n: String = "")

private fun niceTicks(mn0: Double, mx0: Double, n: Int): List<Double> {
    var mn = mn0; var mx = mx0; if (mn == mx) { mn -= 1; mx += 1 }
    val raw = (mx - mn) / n; val mag = 10.0.pow(floor(log10(raw)))
    val st = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.firstOrNull { it >= raw } ?: raw
    val a = floor(mn / st) * st; val b = ceil(mx / st) * st
    val t = ArrayList<Double>(); var v = a; while (v <= b + 1e-9) { t += (v * 100).roundToInt() / 100.0; v += st }
    return t
}
private val dfm = SimpleDateFormat("d MMM", fr.pgragnic.force.L.loc)

/** Courbe (ou escalier) avec axe, objectif facultatif et dernier point mis en valeur. */
@Composable
fun LineChart(pts: List<Pt>, empty: String, target: Double? = null, step: Boolean = false, unit: String = "kg") {
    if (pts.size < 2) { Text(empty, color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp)); return }
    val tm = rememberTextMeasurer(); val st = TextStyle(color = C.muted, fontSize = 10.sp)
    val line = C.effort; val grid = C.line; val warm = C.warm
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val L = 40.dp.toPx(); val R = 10.dp.toPx(); val T = 12.dp.toPx(); val B = 22.dp.toPx()
        var ys = pts.map { it.y }; if (target != null) ys = ys + target
        val tk = niceTicks(ys.min(), ys.max(), 3); val y0 = tk.first(); val y1 = tk.last()
        val x0 = pts.first().x; val x1 = max(pts.last().x, x0 + 1)
        fun X(x: Long) = L + (size.width - L - R) * ((x - x0).toFloat() / (x1 - x0))
        fun Y(y: Double) = T + (size.height - T - B) * (1 - ((y - y0) / ((y1 - y0).takeIf { it != 0.0 } ?: 1.0))).toFloat()
        tk.forEach { v ->
            drawLine(grid, Offset(L, Y(v)), Offset(size.width - R, Y(v)), 1f)
            val r = tm.measure(Prog.fk(v), st); drawText(r, topLeft = Offset(L - 5.dp.toPx() - r.size.width, Y(v) - r.size.height / 2))
        }
        if (target != null) {
            drawLine(warm, Offset(L, Y(target)), Offset(size.width - R, Y(target)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            drawText(tm.measure(t("objectif ${Prog.fk(target)} $unit", "goal ${Prog.fk(target)} $unit"), st.copy(color = warm)), topLeft = Offset(L + 4.dp.toPx(), Y(target) - 15.dp.toPx()))
        }
        val p = Path()
        pts.forEachIndexed { i, q -> val x = X(q.x); val y = Y(q.y); if (i == 0) p.moveTo(x, y) else if (step) { p.lineTo(x, Y(pts[i - 1].y)); p.lineTo(x, y) } else p.lineTo(x, y) }
        if (!step) { val a = Path().apply { addPath(p); lineTo(X(pts.last().x), size.height - B); lineTo(X(pts.first().x), size.height - B); close() }; drawPath(a, line.copy(alpha = .12f)) }
        drawPath(p, line, style = Stroke(2.5.dp.toPx()))
        pts.forEach { drawCircle(line, 2.4.dp.toPx(), Offset(X(it.x), Y(it.y))) }
        drawCircle(line, 5.dp.toPx(), Offset(X(pts.last().x), Y(pts.last().y)))
        val a = tm.measure(dfm.format(Date(pts.first().x)), st); drawText(a, topLeft = Offset(L, size.height - a.size.height))
        val b = tm.measure(dfm.format(Date(pts.last().x)), st); drawText(b, topLeft = Offset(size.width - R - b.size.width, size.height - b.size.height))
    }
}

/** Histogramme (dernière barre mise en valeur). */
@Composable
fun BarChart(bars: List<Bar>, empty: String) {
    if (bars.none { it.y > 0 }) { Text(empty, color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp)); return }
    val tm = rememberTextMeasurer(); val st = TextStyle(color = C.muted, fontSize = 10.sp)
    val c1 = C.rest; val c2 = C.effort; val grid = C.line
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val L = 44.dp.toPx(); val R = 8.dp.toPx(); val T = 16.dp.toPx(); val B = 22.dp.toPx()
        val tk = niceTicks(0.0, bars.maxOf { it.y }, 3); val y1 = tk.last().takeIf { it > 0 } ?: 1.0
        fun Y(y: Double) = T + (size.height - T - B) * (1 - y / y1).toFloat()
        tk.forEach { v ->
            drawLine(grid, Offset(L, Y(v)), Offset(size.width - R, Y(v)), 1f)
            val r = tm.measure(if (v >= 1000) Prog.fk((v / 100).roundToInt() / 10.0) + " t" else Prog.fk(v), st); drawText(r, topLeft = Offset(L - 5.dp.toPx() - r.size.width, Y(v) - r.size.height / 2))
        }
        val bw = (size.width - L - R) / bars.size
        bars.forEachIndexed { i, b ->
            val x = L + i * bw + bw * .18f; val w = bw * .64f; val y = Y(b.y)
            drawRoundRect(if (i == bars.size - 1) c2 else c1, Offset(x, y), Size(w, size.height - B - y), CornerRadius(3.dp.toPx()))
            if (b.n.isNotBlank()) { val r = tm.measure(b.n, st); drawText(r, topLeft = Offset(x + w / 2 - r.size.width / 2, y - r.size.height - 1)) }
            val r = tm.measure(b.label, st); drawText(r, topLeft = Offset(x + w / 2 - r.size.width / 2, size.height - r.size.height))
        }
    }
}

/** Lundi 0 h de la semaine de t. */
fun monday(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
}.timeInMillis

fun volOf(e: Entry) = e.ex.sumOf { x -> if (x.kg > 0 && x.unit != "s") (if (x.sets.isNotEmpty()) x.sets.size else 3) * x.done * x.kg else 0.0 }
