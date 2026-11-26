package fr.pgragnic.force

import android.content.Context
import kotlinx.serialization.Serializable

/** Un exercice changé par un bilan de période : ancien, nouveau (null = pas de remplaçant), signal et raison en clair. */
@Serializable
data class Change(val from: String, val to: String? = null, val sig: String, val why: String, val kg: Double? = null)

/** Un bilan de période du programme sans fin (toutes les 4 semaines). */
@Serializable
data class Bilan(val d: Long, val changes: List<Change> = emptyList(), val seen: Boolean = false)

/**
 * Programme sans fin (Philippe, 26 novembre 2026 : « Réévaluation toutes les 4 semaines », « Change les exercices si besoin »,
 * « Tous ces signaux sont à prendre en compte »). Toutes les 4 semaines l'appli regarde les 4 dernières semaines et ne change
 * un exercice que si un signal se déclenche :
 *  - « rate » : exercice raté 3 séances de suite ;
 *  - « saute » : exercice sauté ou remplacé 3 fois sur 4 semaines ;
 *  - « plateau » : même charge, séries réussies, au moins 4 séances sur 4 semaines ;
 *  - « fatigue » : le muscle principal n'était pas prêt au début d'au moins 3 séances sur 4 semaines ;
 *  - « retard » : muscle sous son objectif 3 semaines de suite (équilibre des muscles).
 * La gêne signalée (écartée tout de suite par les restrictions de santé / le coach) et le matériel qui change sont traités en direct
 * par Prog.ok : un exercice impossible n'est plus proposé. Un exercice change au plus une fois par bilan, 3 changements au plus.
 */
object Reeval {
    const val MAX = 3
    private const val DAYS = 28
    private const val DAY = 86_400_000L
    private const val FAIL_RUN = 3
    private const val SKIP_N = 3
    private const val PLATEAU_N = 4
    private const val FATIGUE_N = 3

    private fun hist(log: List<Entry>, id: String, since: Long = 0L): List<ExRec> =
        log.filter { it.d >= since }.sortedByDescending { it.d }.mapNotNull { e -> e.ex.firstOrNull { it.id == id } }

    fun failed(log: List<Entry>, id: String): Boolean { val h = hist(log, id).take(FAIL_RUN); return h.size == FAIL_RUN && h.all { it.done < it.target } }

    fun plateau(log: List<Entry>, id: String, now: Long): Boolean {
        if (!Prog.hasDb(id)) return false
        val h = hist(log, id, now - DAYS * DAY)
        return h.size >= PLATEAU_N && h.all { it.done >= it.target && it.kg > 0 } && h.map { it.kg }.toSet().size == 1
    }

    /** Séances des 4 dernières semaines où l'exercice était prévu mais pas fait (séance presque complète), sauté ou remplacé. */
    fun skipped(log: List<Entry>, id: String, now: Long): Int = log.count { e ->
        e.d >= now - DAYS * DAY && Prog.sessEx(e.c, e.s).let { plan -> id in plan && e.ex.size >= plan.size - 1 && e.ex.none { it.id == id } }
    }

    /** Muscles pas prêts (80 % ou moins) au début d'une séance qui les sollicitait, au moins 3 fois sur 4 semaines. */
    fun tiredMuscles(log: List<Entry>, days: List<DayRec>, now: Long): Set<String> {
        val n = HashMap<String, Int>()
        log.filter { it.d >= now - DAYS * DAY }.forEach { e ->
            val F = Recovery.fatigueAt(e.d - 60_000L, log.filter { it.d < e.d }, days)
            val prim = e.ex.flatMap { x -> (Prog.MAP[x.id] ?: emptyMap()).filter { it.value >= .7 }.keys }.toSet()
            prim.forEach { m -> if (Recovery.recOf(F, m) <= 80) n[m] = (n[m] ?: 0) + 1 }
        }
        return n.filterValues { it >= FATIGUE_N }.keys
    }

    /** Muscles en retard (paire déséquilibrée ou presque oubliés) pendant 3 fenêtres de 2 semaines décalées d'une semaine. */
    fun lagging(log: List<Entry>, now: Long): Set<String> =
        (0..2).map { k -> Balance.compute(log, 14, now - k * 7 * DAY).let { r -> (r.pairs.flatMap { it.lag } + r.low).toSet() } }.reduce { a, b -> a intersect b }

    private fun prim(id: String) = (Prog.MAP[id] ?: emptyMap()).filter { it.value >= .7 }.keys

    private fun alt(id: String, plan: List<String>, recent: Set<String>, avoid: String? = null): String? {
        val a = Prog.alternatives(id, plan).filter { Prog.EX[it] != null && (avoid == null || (Prog.MAP[it]?.get(avoid) ?: 0.0) < .7) }
        return a.firstOrNull { it !in recent } ?: a.firstOrNull()
    }

    private fun nm(id: String) = tr(Prog.EX[id]?.n ?: id)

    fun evaluate(log: List<Entry>, days: List<DayRec>, st: Settings, now: Long): List<Change> {
        if (log.size < 6) return emptyList()
        val plan = Prog.sess(st.c).keys.flatMap { Prog.sessEx(st.c, it) }.distinct()
        val recent = log.filter { it.d >= now - DAYS * DAY }.flatMap { e -> e.ex.map { it.id } }.toSet()
        val out = ArrayList<Change>(); val touched = HashSet<String>()
        fun add(id: String, to: String?, sig: String, why: String) {
            if (out.size >= MAX || id in touched || to == null) return
            touched += id; touched += to
            val kgOld = Prog.loads[id]?.get(0) ?: 0.0
            out += Change(id, to, sig, why, Prog.startFrom(id, to, kgOld))
        }
        plan.forEach { id ->
            if (failed(log, id)) add(id, alt(id, plan, recent), "rate", t("raté 3 séances de suite", "missed 3 workouts in a row"))
        }
        plan.forEach { id ->
            val n = skipped(log, id, now)
            if (n >= SKIP_N) add(id, alt(id, plan, recent), "saute", t("sauté ou remplacé $n fois en 4 semaines", "skipped or replaced $n times in 4 weeks"))
        }
        plan.forEach { id ->
            if (plateau(log, id, now)) add(id, alt(id, plan, recent), "plateau", t("même charge depuis 4 semaines, séries réussies", "same load for 4 weeks, sets completed"))
        }
        tiredMuscles(log, days, now).forEach { m ->
            val victim = plan.filter { m in prim(it) && it !in touched }.maxByOrNull { Prog.MAP[it]?.get(m) ?: 0.0 } ?: return@forEach
            add(victim, alt(victim, plan, recent, avoid = m), "fatigue", t("${m.lowercase()} pas prêt au début de 3 séances ou plus", "${m.lowercase()} not ready at the start of 3 or more workouts"))
        }
        val lag = lagging(log, now)
        if (lag.isNotEmpty() && out.size < MAX) {
            val vol = Balance.compute(log, 28, now).sets
            lag.forEach { m ->
                val victim = plan.filter { it !in touched && prim(it).none { x -> x in lag } }.maxByOrNull { id -> prim(id).maxOfOrNull { vol[it] ?: 0.0 } ?: 0.0 } ?: return@forEach
                val better = Prog.MAP.entries.filter { (id, r) -> (r[m] ?: 0.0) >= .7 && id in Prog.EX && id !in plan && Prog.ok(id) && id !in touched }
                    .sortedWith(compareBy({ -(it.value[m] ?: 0.0) }, { it.value.size }, { it.key })).firstOrNull()?.key
                add(victim, better, "retard", t("${m.lowercase()} en retard depuis 3 semaines", "${m.lowercase()} lagging for 3 weeks"))
            }
        }
        return out
    }

    /** Décharge (Philippe, 26 novembre 20 h 48 : « Ok pour ta proposition de décharge ») : 2 semaines de suite avec au moins 3 muscles
     *  surchargés (fatigue au-delà de 100 %), vérifié au changement de semaine ; jamais deux semaines de décharge de suite. */
    fun deloadDue(log: List<Entry>, days: List<DayRec>, now: Long, prevWeek: Int): Boolean {
        if (Prog.isDl(prevWeek)) return false
        fun heavy(k: Int): Boolean = (0 until 7).any { d ->
            val t = now - ((k * 7 + d) * DAY) - 4 * 3_600_000L
            val F = Recovery.fatigueAt(t, log.filter { it.d <= t }, days)
            Recovery.MUS.count { (F[it] ?: 0.0) - 10.0 >= .5 } >= 3
        }
        return heavy(1) && heavy(2)
    }

    /** Applique les changements aux remplacements du matériel (Gear.swaps) avec leur charge de départ, et garde le bilan. */
    fun apply(g: Gear, ch: List<Change>, now: Long): Gear {
        var s = g.swaps; var seeds = g.seeds
        ch.forEach { c ->
            val to = c.to ?: return@forEach
            val base = Prog.origin(c.from, g) ?: c.from
            s = (s - base).let { if (to != base) it + (base to to) else it }
            c.kg?.let { seeds = seeds + (to to it) }
        }
        return g.copy(swaps = s, seeds = seeds, bilans = (g.bilans + Bilan(now, ch)).takeLast(8))
    }

    fun run(ctx: Context) {
        val log = Store.log.value; val st = Store.st.value; val g = GearStore.g.value; val now = System.currentTimeMillis()
        Prog.computeLoads(log, st)
        val ch = runCatching { evaluate(log, Store.days.value, st, now) }.getOrDefault(emptyList())
        GearStore.save(ctx, apply(g, ch, now))
    }

    /** Texte d'un changement, pour le bilan affiché. */
    fun line(c: Change): String = t("${nm(c.from)} remplacé par ${c.to?.let { nm(it) } ?: "—"} (${c.why})", "${nm(c.from)} replaced by ${c.to?.let { nm(it) } ?: "—"} (${c.why})") +
        (c.kg?.let { " · " + Prog.loadTxt(c.to!!, it) } ?: "")

    fun markSeen(ctx: Context) {
        val g = GearStore.g.value; if (g.bilans.lastOrNull()?.seen != false) return
        GearStore.save(ctx, g.copy(bilans = g.bilans.dropLast(1) + g.bilans.last().copy(seen = true)))
    }
}
