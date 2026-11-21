package fr.pgragnic.force

import android.content.Context

/**
 * Séance adaptée à la récupération des muscles (Philippe, 21 novembre 19 h 00 : « Mais globalement toutes les séances devraient s'adapter
 * par rapport aux muscles déjà travaillés ou à travailler, comment peut-on faire ça ? » ; 19 h 02 : « Ok pour combiner 1 et 2 »).
 * Niveau 1 : la séance qui ménage le mieux les muscles est proposée (Prog.nextSess / postponeAlt).
 * Niveau 2 (ici) : avant de démarrer, chaque exercice est ajusté selon ses muscles principaux (poids ≥ 0,7 dans Prog.MAP).
 * Règle de Philippe (21 novembre 19 h 04 : « Tout muscle <= 80% doit être remplacé sur un muscle prêt ») :
 *   - tous au-dessus de 80 % : comme prévu ;
 *   - un muscle principal à 80 % ou moins (ou surchargé) : remplacé par un exercice du cycle dont tous les muscles principaux sont
 *     au-dessus de 80 % ; s'il n'y en a plus, retiré (s'il ne restait plus rien, le dernier est gardé avec une série de moins).
 * « Séance d'origine » (pour la journée) remet la séance prévue.
 */
object Recup {
    /** au-dessus : muscle prêt */
    const val READY = 80
    class Change(val id: String, val kind: String, val to: String? = null, val muscle: String, val pct: Int)

    /** calcul sans adaptation (choix de la séance, comparaisons) */
    @Volatile var disabled = false
    fun <T> without(block: () -> T): T { val d = disabled; disabled = true; try { return block() } finally { disabled = d } }

    private var app: Context? = null
    fun load(ctx: Context) { app = ctx.applicationContext }
    private fun today() = java.time.LocalDate.now().toString()
    /** « Séance d'origine » choisie aujourd'hui */
    val off get() = app?.getSharedPreferences("force", Context.MODE_PRIVATE)?.getString("recup_off", null) == today()
    fun setOff(v: Boolean) { app?.getSharedPreferences("force", Context.MODE_PRIVATE)?.edit()?.putString("recup_off", if (v) today() else null)?.apply(); Store.ver.value++ }

    // récupération mise en cache (le calcul relit tout le journal)
    private var key = ""; private var state: Recovery.State? = null
    private fun mu(): Recovery.State {
        val log = Store.log.value; val days = Store.days.value
        val k = "${log.size}/${log.maxOfOrNull { it.d }}/${days.hashCode()}/${System.currentTimeMillis() / 900_000}"
        if (k != key || state == null) { state = Recovery.muscles(log, days); key = k }
        return state!!
    }

    private fun prim(id: String) = (Prog.MAP[id] ?: emptyMap()).filter { it.value >= .7 }.keys
    /** muscle principal le moins récupéré et son pourcentage (surchargé compté 0) */
    private fun worst(id: String, m: Recovery.State): Pair<String, Int>? =
        prim(id).map { it to if (m.over(it)) 0 else (m.R[it] ?: 100) }.minByOrNull { it.second }

    /** Ajustements de la séance [ex] (ordre déjà calculé), dans l'ordre. */
    fun plan(c: Int, ex: List<String>): List<Change> {
        if (disabled || off) return emptyList()
        val m = mu(); val out = ArrayList<Change>()
        val used = ex.toMutableSet(); var kept = ex.size
        // candidats de remplacement : exercices du cycle (toutes séances) dont tous les muscles principaux sont prêts
        val pool by lazy {
            Prog.sess(c).keys.flatMap { Prog.sessEx(c, it) }.distinct().filter { Prog.ok(it) && Prog.EX[it]?.m != "time" }
                .mapNotNull { id -> worst(id, m)?.let { id to it.second } }.filter { it.second > READY }.sortedByDescending { it.second }.map { it.first }
        }
        for (id in ex) {
            val (mus, pct) = worst(id, m) ?: continue
            if (pct > READY) continue
            val to = pool.firstOrNull { it !in used }
            when {
                to != null -> { used += to; out += Change(id, "swap", to, mus, pct) }
                kept > 1 -> { kept--; out += Change(id, "drop", muscle = mus, pct = pct) }
                else -> out += Change(id, "less", muscle = mus, pct = pct)
            }
        }
        return out
    }

    /** ordre de la séance après remplacements et retraits */
    fun apply(c: Int, ex: List<String>): List<String> {
        val p = plan(c, ex); if (p.isEmpty()) return ex
        return ex.mapNotNull { id -> val ch = p.firstOrNull { it.id == id }; when (ch?.kind) { "swap" -> ch.to; "drop" -> null; else -> id } }.distinct()
    }
    /** séries en moins pour l'exercice (dans la séance [ex] d'origine) */
    fun less(c: Int, ex: List<String>, id: String) = if (plan(c, ex).any { it.id == id && it.kind == "less" }) 1 else 0

    /** raison courte (muscle et pourcentage) */
    fun why(ch: Change) = if (ch.pct <= 0) t("${mus(ch.muscle).lowercase()} surchargés", "${mus(ch.muscle).lowercase()} overloaded") else "${mus(ch.muscle).lowercase()} ${ch.pct} %"
    /** phrase courte pour la carte « Prochaine séance » */
    fun text(ch: Change): String {
        val n = { id: String -> tr(Prog.EX[id]?.n ?: id) }
        val why = if (ch.pct <= 0) t("${mus(ch.muscle).lowercase()} surchargés", "${mus(ch.muscle).lowercase()} overloaded") else "${mus(ch.muscle).lowercase()} ${ch.pct} %"
        return when (ch.kind) {
            "swap" -> t("${n(ch.id)} → ${n(ch.to!!)} ($why)", "${n(ch.id)} → ${n(ch.to!!)} ($why)")
            "drop" -> t("${n(ch.id)} retiré ($why)", "${n(ch.id)} removed ($why)")
            else -> t("${n(ch.id)} −1 série ($why)", "${n(ch.id)} −1 set ($why)")
        }
    }
}
