package fr.pgragnic.force

import kotlinx.serialization.Serializable
import java.util.Calendar
import kotlin.math.*

/** Une journée d'activité (pas, marche lestée, randonnée, trail). */
@Serializable
data class DayRec(val date: String, val steps: Int? = null, val act: String? = null, val pack: Double? = null,
                  val dplus: Int? = null, val dur: Int? = null, val kcal: Double? = null, val src: String? = null,
                  /** pas faits avec le sac dans la journée (Philippe, 12 novembre : « je veux juste pouvoir rentrer le nombre de pas faits
                   *  en marche lestée sur la journée ») : le sac ne compte que pour ces pas-là */
                  val rsteps: Int? = null)

/** Récupération musculaire : même calcul que la page web. */
object Recovery {
    val MUS = listOf("Pectoraux", "Dos", "Épaules", "Biceps", "Triceps", "Abdos", "Lombaires", "Fessiers", "Quadriceps", "Ischios", "Mollets")
    private val RT = mapOf("Pectoraux" to 60.0, "Dos" to 72.0, "Épaules" to 48.0, "Biceps" to 48.0, "Triceps" to 48.0, "Abdos" to 36.0,
        "Lombaires" to 72.0, "Fessiers" to 72.0, "Quadriceps" to 72.0, "Ischios" to 72.0, "Mollets" to 36.0)
    private val FEF = mapOf("Facile" to .75, "Juste bien" to 1.0, "Difficile" to 1.3)
    private val DMAP = mapOf("Quadriceps" to 1.0, "Fessiers" to .8, "Mollets" to .9, "Ischios" to .4)
    private const val CAP = 10.0

    private fun nSets(x: ExRec) = if (x.sets.isNotEmpty()) x.sets.size else 3

    fun dayTs(d: DayRec): Long {
        val p = d.date.split("-").map { it.toInt() }
        fun at(h: Int) = Calendar.getInstance().apply { set(p[0], p[1] - 1, p[2], h, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        return min(at(18), max(at(8), System.currentTimeMillis()))
    }

    fun dayLoad(d: DayRec): Double {
        val steps = (d.steps ?: 0).toDouble(); val pack = d.pack ?: 0.0; val dp = (d.dplus ?: 0).toDouble()
        var L = max(0.0, steps - 8000) / 1000 * .3
        if (d.rsteps != null && d.act == "Marche lestée") {
            // marche lestée en pas : tous les pas de la journée comptent normalement, le sac s'ajoute seulement sur les pas faits avec
            val all = max(steps, d.rsteps.toDouble()); val rs = min(d.rsteps.toDouble(), all)
            L = max(0.0, all - 8000) / 1000 * .3 + rs / 1000 * .3 * pack / 20
            L += dp / 100 * .8 * (1 + pack / 30)
            return (L * 100).roundToInt() / 100.0
        }
        if (steps == 0.0 && d.act != null && d.dur != null) L = d.dur / 60.0 * 1.5
        L *= 1 + pack / 20; L += dp / 100 * (if (d.act == "Trail") 1.0 else .8) * (1 + pack / 30)
        return (L * 100).roundToInt() / 100.0
    }

    fun fatigueAt(t: Long, log: List<Entry>, days: List<DayRec>): Map<String, Double> {
        val F = MUS.associateWith { 0.0 }.toMutableMap()
        log.forEach { e ->
            val h = (t - e.d) / 3.6e6; if (h < 0) return@forEach
            e.ex.forEach { x ->
                val mp = Prog.MAP[x.id] ?: return@forEach
                val f = FEF[x.f] ?: FEF[e.f] ?: 1.0
                val c = if (x.target > 0) min(1.2, max(.3, x.done.toDouble() / x.target)) else 1.0
                mp.forEach { (m, v) -> val r = max(0.0, 1 - h / RT[m]!!); if (r > 0) F[m] = F[m]!! + nSets(x) * v * f * c * r }
            }
        }
        days.forEach { d ->
            val L = dayLoad(d); if (L == 0.0) return@forEach
            val h = (t - dayTs(d)) / 3.6e6; if (h < 0) return@forEach
            DMAP.forEach { (m, v) -> val r = max(0.0, 1 - h / RT[m]!!); if (r > 0) F[m] = F[m]!! + L * v * r }
        }
        return F
    }

    fun recOf(F: Map<String, Double>, m: String) = max(0, (100 * (1 - F[m]!! / CAP)).roundToInt())

    /** ex : fatigue au-delà de 0 %, en séries (Philippe, 13 novembre : « Est-ce qu'on pourrait descendre en dessous de zéro et est-ce que
     *  ça aurait du sens ? », puis « Je veux cette version ») ; un muscle est « surchargé » à partir d'une demi-série au-delà. */
    class State(val R: Map<String, Int>, val V: Map<String, Double>, val tot: Double, val ready: Map<String, Int>, val ex: Map<String, Double> = emptyMap()) {
        fun over(m: String) = (ex[m] ?: 0.0) >= .5
    }
    /** couleur d'un muscle surchargé : rouge foncé */
    /** rouge vif et saturé, distinct du rouge corail de « fatigué » (Philippe, 13 novembre 20 h 40 : « Rouge vif sans la pulsation ») */
    fun overCol(@Suppress("UNUSED_PARAMETER") danger: Int = 0): Int = 0xE3141E

    fun muscles(log: List<Entry>, days: List<DayRec>): State {
        val now = System.currentTimeMillis(); val F = fatigueAt(now, log, days)
        val R = MUS.associateWith { recOf(F, it) }
        val V = MUS.associateWith { 0.0 }.toMutableMap(); var tot = 0.0
        log.forEach { e -> if (now - e.d > 7 * 864e5) return@forEach
            e.ex.forEach { x -> Prog.MAP[x.id]?.forEach { (m, v) -> V[m] = V[m]!! + nSets(x) * v; tot += nSets(x) * v } } }
        val ready = HashMap<String, Int>()
        MUS.forEach { m ->
            if (R[m]!! >= 90) { ready[m] = 0; return@forEach }
            for (h in 1..120) if (recOf(fatigueAt(now + h * 3_600_000L, log, days), m) >= 90) { ready[m] = h; break }
        }
        return State(R, V, tot, ready, MUS.associateWith { max(0.0, F[it]!! - CAP) })
    }

    /** Séance la plus adaptée : celle dont les muscles principaux sont les plus reposés. */
    fun sessScore(c: Int, s: String, R: Map<String, Int>): Int {
        var mn = 100
        Prog.sessEx(c, s).forEach { id -> Prog.MAP[id]?.forEach { (m, v) -> if (v >= .7) mn = min(mn, R[m] ?: 100) } }
        return mn
    }

    private fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(c: Int, sh: Int) = (c shr sh) and 255
        return listOf(16, 8, 0).fold(0) { acc, sh -> acc or (((ch(a, sh) * (1 - t) + ch(b, sh) * t).roundToInt()) shl sh) }
    }
    /** Couleur de récupération : rouge (fatigué) → orange → vert (prêt). */
    fun recCol(r: Int, danger: Int = 0xF06A6A, warm: Int = 0xE3A33E, ok: Int = 0x2EC089): Int =
        if (r >= 90) ok else if (r >= 60) mix(warm, ok, (r - 60) / 30.0) else mix(danger, warm, r / 60.0)
}
