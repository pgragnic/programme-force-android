package fr.pgragnic.force

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// Onglet Coach (Philippe, 15 novembre 2026, 10 h 26 : « il faut que chaque décision prise par l'IA soit compréhensible par l'utilisateur » ;
// 10 h 30 : « faire un onglet à part qui conserverait les échanges entre l'utilisateur et l'IA, journal « Ce que le coach a changé » et peut-être plus » ;
// réponses : 5e onglet au milieu, historique sur le téléphone + le compte, objectifs, ce que sait le coach, données envoyées + quota, ton + rappels ;
// 10 h 42 : « On fait le tout »).

/** Un message de la discussion. plan : plan proposé (JSON), gardé pour revoir l'avant / après. */
@Serializable data class CMsg(val t: Long, val coach: Boolean, val text: String, val plan: CoachPlan? = null, val mode: String = "libre")

/** Une décision du coach appliquée par l'utilisateur. */
@Serializable data class CDecision(
    val id: Long, val src: String, val title: String, val why: String, val changes: List<String>, val plan: CoachPlan,
    val day: String, val undone: Boolean = false, val until: String? = null,
)

/** Objectif donné au coach (rando, poids…). */
@Serializable data class CGoal(val id: Long, val text: String, val date: String? = null, val from: String)

/** Préférence ou fait retenu par le coach (« je n'aime pas les burpees »). */
@Serializable data class CMemo(val id: Long, val text: String, val by: String = "coach")

@Serializable data class CoachSaved(
    val msgs: List<CMsg> = emptyList(), val journal: List<CDecision> = emptyList(), val bilans: Map<String, String> = emptyMap(),
    val goals: List<CGoal> = emptyList(), val memo: List<CMemo> = emptyList(), val ton: String = "neutre",
    val remDebrief: Boolean = true, val remMorning: Boolean = false, val removed: List<Long> = emptyList(), val upd: Long = 0,
)

@Serializable private data class CoachRow(val data: JsonObject)

object CoachData {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var app: Context? = null
    var d = CoachSaved(); private set
    /** change à chaque modification (recomposition des écrans du coach) */
    var v by mutableIntStateOf(0)
    /** dernier contexte envoyé à Anthropic, en clair (« Données envoyées ») */
    var lastSent: String? = null
    /** messages restants aujourd'hui (renvoyé par la fonction), avec le jour */
    var left: Pair<String, Int>? = null

    fun load(ctx: Context) {
        app = ctx.applicationContext
        val p = ctx.getSharedPreferences("force", Context.MODE_PRIVATE)
        d = runCatching { json.decodeFromString<CoachSaved>(p.getString("coachdata", null) ?: return) }.getOrDefault(CoachSaved())
        lastSent = p.getString("coachsent", null)
        p.getString("coachleft", null)?.split("|")?.let { if (it.size == 2) left = it[0] to (it[1].toIntOrNull() ?: 0) }
        // mots du coach déjà gardés avant l'onglet : versés dans les archives
        p.getString("coachbilan", null)?.let { val k = it.substringBefore("|"); if (k !in d.bilans) d = d.copy(bilans = d.bilans + (k to it.substringAfter("|"))) }
    }

    private fun save(push: Boolean = true) {
        d = d.copy(upd = System.currentTimeMillis())
        app?.getSharedPreferences("force", Context.MODE_PRIVATE)?.edit()?.putString("coachdata", json.encodeToString(CoachSaved.serializer(), d))?.apply()
        v++
        if (push) push()
    }
    fun update(push: Boolean = true, f: (CoachSaved) -> CoachSaved) { d = f(d); save(push) }

    fun setSent(s: String) { lastSent = s; app?.getSharedPreferences("force", Context.MODE_PRIVATE)?.edit()?.putString("coachsent", s)?.apply() }
    fun setLeft(n: Int) { val k = java.time.LocalDate.now().toString(); left = k to n; app?.getSharedPreferences("force", Context.MODE_PRIVATE)?.edit()?.putString("coachleft", "$k|$n")?.apply(); v++ }
    /** messages restants aujourd'hui (40 si aucun échange connu aujourd'hui) */
    fun leftToday(): Int = left?.takeIf { it.first == java.time.LocalDate.now().toString() }?.second ?: 40

    fun addMsg(m: CMsg) = update { it.copy(msgs = (it.msgs + m).takeLast(400)) }

    /** Enregistre une décision appliquée et renvoie son id. */
    fun decide(src: String, title: String, why: String, changes: List<String>, plan: CoachPlan): Long {
        val id = System.currentTimeMillis()
        val until = listOfNotNull(CoachState.genes[plan.gene?.zone ?: ""], plan.sans_materiel_jours?.let { CoachState.noGear }).maxOrNull()
        update { it.copy(journal = (listOf(CDecision(id, src, title, why, changes, plan, java.time.LocalDate.now().toString(), until = until)) + it.journal).take(200)) }
        return id
    }

    /** Annule une décision : zone ménagée, sans matériel, crans de charge, adaptation du jour (si c'est aujourd'hui). */
    fun undo(ctx: Context, id: Long) {
        val e = d.journal.firstOrNull { it.id == id } ?: return
        CoachState.undo(ctx, e.plan)
        if (e.day == java.time.LocalDate.now().toString() && (e.plan.seance != null || e.plan.duree_max != null || (e.plan.series_moins ?: 0) > 0 || e.plan.remplacements.isNotEmpty())) Adapt.clear(ctx)
        update { it.copy(journal = it.journal.map { j -> if (j.id == id) j.copy(undone = true) else j }) }
    }

    /** décision encore active ? (pas annulée, et zone / sans matériel / charge / adaptation du jour toujours en place) */
    fun active(e: CDecision): Boolean {
        if (e.undone) return false
        val p = e.plan; val today = java.time.LocalDate.now().toString()
        if (p.gene != null && CoachState.genes.containsKey(p.gene.zone)) return true
        if (p.sans_materiel_jours != null && CoachState.noGear != null) return true
        if (p.charges.any { c -> CoachState.bias[c.id]?.let { it.t >= e.id - 60_000 } == true }) return true
        return e.day == today && Adapt.on && (p.seance != null || p.duree_max != null || (p.series_moins ?: 0) > 0 || p.remplacements.isNotEmpty())
    }

    // ---------- sauvegarde avec le compte (table coach_data, supabase/coach-data.sql) ----------
    private fun push() {
        val c = Store.sb ?: return; if (Store.user.value == null) return
        val snap = d
        scope.launch { runCatching { c.from("coach_data").upsert(CoachRow(json.encodeToJsonElement(CoachSaved.serializer(), snap).jsonObject)) } }
    }

    /** Récupère la copie du compte et la fusionne (messages, décisions, bilans réunis ; réglages : le plus récent gagne). */
    fun pull() {
        val c = Store.sb ?: return
        scope.launch {
            runCatching {
                if (c.auth.currentUserOrNull() == null) return@launch
                val r = c.from("coach_data").select().decodeList<CoachRow>().firstOrNull() ?: run { push(); return@launch }
                val o = json.decodeFromJsonElement(CoachSaved.serializer(), r.data)
                val gone = (d.removed + o.removed).toSet()
                val newer = if (o.upd > d.upd) o else d
                val m = CoachSaved(
                    msgs = (d.msgs + o.msgs).distinctBy { it.t to it.coach }.sortedBy { it.t }.takeLast(400),
                    journal = (d.journal + o.journal).groupBy { it.id }.map { (_, l) -> l.firstOrNull { it.undone } ?: l.first() }.sortedByDescending { it.id }.take(200),
                    bilans = o.bilans + d.bilans,
                    goals = (d.goals + o.goals).distinctBy { it.id }.filter { it.id !in gone },
                    memo = (d.memo + o.memo).distinctBy { it.id }.filter { it.id !in gone },
                    ton = newer.ton, remDebrief = newer.remDebrief, remMorning = newer.remMorning, removed = gone.toList().takeLast(300), upd = maxOf(d.upd, o.upd))
                kotlinx.coroutines.withContext(Dispatchers.Main) { d = m; save(true); app?.let { CoachRem.schedule(it) } }
            }
        }
    }
}
