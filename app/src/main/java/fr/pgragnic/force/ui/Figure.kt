package fr.pgragnic.force.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import fr.pgragnic.force.Prog
import kotlinx.serialization.json.*

/** Positions 2D (repère 200×200) d'une pose à l'instant k ∈ [0,1], avec images clés intermédiaires. */
object Poses {
    private val cache = HashMap<String, List<Pair<Double, Map<String, DoubleArray>>>>()
    private val flip: Set<String> get() = Prog.raw["FLIPX"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
    fun clear() = cache.clear()

    private fun pts(o: JsonObject?): Map<String, DoubleArray> =
        o?.mapValues { (_, v) -> v.jsonArray.let { doubleArrayOf(it[0].jsonPrimitive.double, it[1].jsonPrimitive.double) } } ?: emptyMap()

    private fun keys(id: String): List<Pair<Double, Map<String, DoubleArray>>>? = cache.getOrPut(id) {
        val P = Prog.raw["POSES"]!!.jsonObject[id]?.jsonObject ?: return null
        val a = pts(P["a"]?.jsonObject); val b = a + pts(P["b"]?.jsonObject)
        fun lerp(t: Double) = a.mapValues { (n, v) -> val w = b[n] ?: v; doubleArrayOf(v[0] + (w[0] - v[0]) * t, v[1] + (w[1] - v[1]) * t) }
        val ks = ArrayList<Pair<Double, Map<String, DoubleArray>>>()
        ks += 0.0 to a
        P["kf"]?.jsonArray?.forEach { m -> val t = m.jsonObject["t"]!!.jsonPrimitive.double; ks += t to (lerp(t) + pts(m.jsonObject["p"]?.jsonObject)) }
        ks += 1.0 to b
        ks
    }

    private fun cr(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val t2 = t * t; val t3 = t2 * t
        return .5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
    }

    fun at(id: String, k: Double): Map<String, DoubleArray>? {
        val out = raw(id, k) ?: return null
        return if (id in flip) out.mapValues { (_, v) -> doubleArrayOf(200 - v[0], v[1]) } else out
    }

    /** Pose sans le retournement gauche-droite (le personnage 3D l'applique lui-même). */
    fun raw(id: String, k: Double): Map<String, DoubleArray>? {
        val ks = keys(id) ?: return null
        var i = 0; while (i < ks.size - 2 && k > ks[i + 1].first) i++
        val a = ks[i]; val b = ks[i + 1]; val z = ks[maxOf(0, i - 1)]; val c = ks[minOf(ks.size - 1, i + 2)]
        val u = ((k - a.first) / ((b.first - a.first).takeIf { it != 0.0 } ?: 1.0)).coerceIn(0.0, 1.0)
        if (ks.size == 2) return a.second.mapValues { (n, v) -> val w = b.second[n] ?: v; doubleArrayOf(v[0] + (w[0] - v[0]) * k, v[1] + (w[1] - v[1]) * k) }
        val out = a.second.mapValues { (n, v) ->
            fun g(q: Pair<Double, Map<String, DoubleArray>>, j: Int) = (q.second[n] ?: v)[j]
            doubleArrayOf(cr(g(z, 0), g(a, 0), g(b, 0), g(c, 0), u), cr(g(z, 1), g(a, 1), g(b, 1), g(c, 1), u))
        }
        return out
    }

    fun dbs(id: String): List<String> = Prog.raw["POSES"]!!.jsonObject[id]?.jsonObject?.get("db")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
}

/** Personnage simplifié (étape 1, avant le personnage 3D). */
@Composable
fun Figure(pose: String?, k: Double, modifier: Modifier, ghost: Boolean = false, mirror: Boolean = false, nodb: Boolean = false, accent: Color = C.effort) {
    Canvas(modifier) {
        drawRect(C.stage)
        val p = pose?.let { Poses.at(it, k) } ?: return@Canvas
        val s = minOf(size.width, size.height) / 210f
        val ox = (size.width - 200 * s) / 2; val oy = (size.height - 200 * s) / 2 - 4 * s
        fun o(n: String): Offset? = p[n]?.let { v -> val x = if (mirror) 200 - v[0] else v[0]; Offset(ox + x.toFloat() * s, oy + v[1].toFloat() * s) }
        val base = if (ghost) C.fig.copy(alpha = .45f) else C.fig
        val far = base.copy(alpha = base.alpha * .55f)
        // sol
        drawLine(C.mat, Offset(ox, oy + 182 * s), Offset(ox + 200 * s, oy + 182 * s), 6 * s, StrokeCap.Round)
        fun seg(a: String, b: String, c: Color, w: Float) { val pa = o(a); val pb = o(b); if (pa != null && pb != null) drawLine(c, pa, pb, w * s, StrokeCap.Round) }
        // membres côté éloigné
        seg("P", "K2", far, 9f); seg("K2", "A2", far, 8f); seg("N", "E2", far, 7f); seg("E2", "W2", far, 6f)
        // tronc et tête
        seg("N", "P", base, 15f)
        o("H")?.let { drawCircle(base, 10 * s, it) }
        seg("H", "N", base, 6f)
        // membres côté proche
        seg("P", "K1", base, 10f); seg("K1", "A1", base, 8.5f); seg("N", "E1", base, 7.5f); seg("E1", "W1", base, 6.5f)
        if (!nodb && !ghost) Poses.dbs(pose).forEach { w -> o(w)?.let { dumbbell(it, s) } }
        if (ghost) Poses.dbs(pose).forEach { w -> o(w)?.let { dumbbell(it, s, .45f) } }
    }
}

private fun DrawScope.dumbbell(c: Offset, s: Float, a: Float = 1f) {
    drawLine(C.db.copy(alpha = a), Offset(c.x - 9 * s, c.y), Offset(c.x + 9 * s, c.y), 3 * s)
    drawCircle(C.db.copy(alpha = a), 5.5f * s, Offset(c.x - 9 * s, c.y)); drawCircle(C.db.copy(alpha = a), 5.5f * s, Offset(c.x + 9 * s, c.y))
}
