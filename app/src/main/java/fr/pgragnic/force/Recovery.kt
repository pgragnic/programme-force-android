package fr.pgragnic.force

import kotlinx.serialization.Serializable
import java.util.Calendar
import kotlin.math.*

/** Une sortie dans la journée : marche lestée, randonnée ou trail, avec son sac et sa durée (Philippe, 12 novembre : « Concernant la marche
 *  lestée c'est une activité qui peut être intermittente sur une journée, comment peut-on gérer ça ? », puis « 1,2 et 3 »). */
@Serializable
data class Out(val h: String? = null, val act: String = "Marche lestée", val pack: Double? = null, val dur: Int = 0, val dplus: Int? = null)

/** Une journée d'activité (pas, marche lestée, randonnée, trail). Les journées d'avant les sorties gardent act / pack / dur / dplus ;
 *  avec des sorties, ces champs résument la journée (type de la première sortie, sac le plus lourd, durées et D+ additionnés). */
@Serializable
data class DayRec(val date: String, val steps: Int? = null, val act: String? = null, val pack: Double? = null,
                  val dplus: Int? = null, val dur: Int? = null, val kcal: Double? = null, val src: String? = null,
                  val outs: List<Out> = emptyList()) {
    /** sorties de la journée ; une ancienne journée avec activité compte pour une seule sortie */
    fun sorties(): List<Out> = outs.ifEmpty { if (act != null) listOf(Out(null, act, pack, dur ?: 0, dplus)) else emptyList() }
    /** même journée avec ces sorties, résumé recalculé */
    fun withOuts(o: List<Out>): DayRec { val s = o.sortedBy { it.h ?: "" }
        return copy(outs = s, act = s.firstOrNull()?.act, pack = s.mapNotNull { it.pack }.maxOrNull(), dur = s.sumOf { it.dur }.takeIf { it > 0 },
            dplus = s.sumOf { it.dplus ?: 0 }.takeIf { it > 0 }) }
}

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
        if (d.outs.isEmpty()) {   // journée saisie avant les sorties : calcul d'origine (sac appliqué à tous les pas)
            if (steps == 0.0 && d.act != null && d.dur != null) L = d.dur / 60.0 * 1.5
            L *= 1 + pack / 20; L += dp / 100 * (if (d.act == "Trail") 1.0 else .8) * (1 + pack / 30)
        } else d.outs.forEach { o ->
            // le sac ne pèse que pendant la sortie ; si les pas sont connus, la marche elle-même y est déjà comptée
            val p = o.pack ?: 0.0; val hr = o.dur / 60.0
            L += if (steps > 0) hr * 1.5 * p / 20 else hr * 1.5 * (1 + p / 20)
            L += (o.dplus ?: 0) / 100.0 * (if (o.act == "Trail") 1.0 else .8) * (1 + p / 30)
        }
        return (L * 100).roundToInt() / 100.0
    }

    /** Charge du sac sur le bas et le haut du dos (Philippe, « 1,2 et 3 », point 3) : proportionnelle au poids et au temps porté. */
    fun backLoad(d: DayRec): Double = (d.sorties().sumOf { (it.pack ?: 0.0) / 10 * it.dur / 60.0 * .8 } * 100).roundToInt() / 100.0
    private val BMAP = mapOf("Lombaires" to .6, "Dos" to .4)

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
        days.forEach { d ->
            val B = backLoad(d); if (B == 0.0) return@forEach
            val h = (t - dayTs(d)) / 3.6e6; if (h < 0) return@forEach
            BMAP.forEach { (m, v) -> val r = max(0.0, 1 - h / RT[m]!!); if (r > 0) F[m] = F[m]!! + B * v * r }
        }
        return F
    }

    fun recOf(F: Map<String, Double>, m: String) = max(0, (100 * (1 - F[m]!! / CAP)).roundToInt())

    class State(val R: Map<String, Int>, val V: Map<String, Double>, val tot: Double, val ready: Map<String, Int>)

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
        return State(R, V, tot, ready)
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
