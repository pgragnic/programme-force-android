package fr.pgragnic.force

import android.content.Context
import kotlinx.serialization.json.*
import kotlin.math.*

/** Données du programme (exportées de la page web) et logique de séance. */
class Ex(val id: String, val n: String, val l: String, val tip: String, val m: String?, val inv: Boolean,
         val dr: Int, val kg0: Double?, val cues: List<String>,
         /** matériel requis (« a|b » = l'un ou l'autre), type de charge (kg, band, bw), outil affiché, articulations sollicitées */
         val eq: List<String> = emptyList(), val lt: String = "bw", val tool: String = "", val stress: List<String> = emptyList(),
         val pose: String = id, val band: List<List<String>> = emptyList(), val f0: Double? = null,
         /** rythme propre (mouvements explosifs : swing, slam), à la place du tempo de la semaine */
         val tp: List<Double>? = null,
         /** erreurs à éviter, affichées dans la fiche de l'exercice (tools/avoid.py) */
         val avoid: List<String> = emptyList())

class Week(val t: String, val s: Int, val r: Int, val tp: List<Double>, val pl: Int, val note: String)

class Sess(val t: String, val warm: List<String>, val ex: List<String>, val cool: List<String>)

class Work(val dur: Double, val reps: Int, val rt: Double, val tp: List<Double>, val label: String)

/** Une étape de la séance guidée. */
data class Phase(
    val k: String, var blk: String = "", var n: String = "", var det: String = "", var tip: String = "",
    var dur: Double = 0.0, var pose: String? = null, val ei: Int = -1, val set: Int = 0, var ex: String? = null,
    val inv: Boolean = false, val tp: List<Double>? = null, val rt: Double = 0.0, val reps: Int = 0,
    val m: String? = null, var nodb: Boolean = false, var chg: String? = null, var next: Phase? = null,
    var ghostTp: List<Double>? = null
)

class Settings(var c: Int = 1, var w: Int = 0, var s: String = "A", var rest: Int = 30,
               var bar: Double = 1.8, var n2: Int = 2, var n05: Int = 6)

/** Un exercice enregistré dans une séance. */
@kotlinx.serialization.Serializable
data class SetRec(val r: Int, val res: Int? = null)

@kotlinx.serialization.Serializable
data class ExRec(val id: String, val n: String, val kg: Double = 0.0, val target: Int, val unit: String = "reps",
                 val sets: List<SetRec> = emptyList(), val done: Int, val f: String = "Juste bien")

@kotlinx.serialization.Serializable
data class Entry(val d: Long, val c: Int = 1, val w: Int, val s: String, val f: String = "Juste bien",
                 val rest: Int? = null, val ex: List<ExRec> = emptyList(), val bw: Double? = null,
                 val kcal: Double? = null, val _id: String? = null)

object Prog {
    lateinit var raw: JsonObject
    val EX = LinkedHashMap<String, Ex>()
    val WEEKS = ArrayList<Week>()
    val SESS1 = LinkedHashMap<String, Sess>()
    val SESS2 = LinkedHashMap<String, Sess>()
    val WU = HashMap<String, List<JsonElement>>()
    val COOLN = HashMap<String, String>()
    val MAP = HashMap<String, Map<String, Double>>()
    lateinit var HEAVY: Set<String>; lateinit var BIG: Set<String>; lateinit var CORE: Set<String>
    val METP = HashMap<String, Double>()
    val FEF = HashMap<String, Double>()

    /** Version du programme en cours : 0 = celle livrée avec l'appli, sinon numéro de la version en base. */
    var version = 0L

    /** Part de la séance (pondérée par muscle principal) qui sollicite des muscles pas encore récupérés (< 75 %), et ces muscles. */
    fun tired(ids: List<String>, R: Map<String, Int>): Pair<Double, List<String>> {
        val w = HashMap<String, Double>(); ids.forEach { id -> MAP[id]?.forEach { (k, v) -> w[k] = (w[k] ?: 0.0) + v } }
        val tot = w.values.sum().takeIf { it > 0 } ?: return 0.0 to emptyList()
        val t = w.filter { (k, v) -> v >= .7 && (R[k] ?: 100) < 75 }
        return t.values.sum() / tot to t.keys.sortedBy { R[it] ?: 100 }
    }

    /** Prochaine séance : celle qui ménage le mieux les muscles en récupération ; à égalité, celle faite il y a le plus longtemps. */
    fun nextSess(st: Settings, log: List<Entry>, days: List<DayRec>): String {
        val R = Recovery.muscles(log, days).R
        val last = sess(st.c).keys.associateWith { s -> log.filter { it.s == s }.maxOfOrNull { it.d } ?: 0L }
        // d'abord une séance pas encore faite cette semaine du programme (avec E et F, chacune a son tour), puis celle qui ménage le mieux les muscles
        val doneW = log.filter { it.c == st.c && it.w == st.w }.map { it.s }.toSet()
        return sess(st.c).keys.minWith(compareBy<String>({ if (it in doneW) 1 else 0 }, { (tired(orderEx(st.c, it, st.w, st), R).first * 10).roundToInt() }, { last[it] }))
    }

    /** pose d'un exercice alterné ou d'un seul côté (bird dog, dead bug, fentes…) : à faire des deux côtés */
    fun alternates(pose: String?) = pose != null && EX.values.any { it.pose == pose && (it.m == "alt" || it.m == "uni") }

    /** Lundi 0 h (heure locale) de la semaine de « t ». */
    fun mondayOf(t: Long): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = t; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
        while (c.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY) c.add(java.util.Calendar.DAY_OF_YEAR, -1)
        return c.timeInMillis
    }
    /** étirement d'un seul côté (drapeau « sides » de la pose 3D) : changement de côté à mi-temps */
    fun sides(id: String?) = id != null && raw["POSES3"]?.jsonObject?.get(id)?.jsonObject?.get("sides")?.jsonPrimitive?.booleanOrNull == true

    /** Format de programme que cette version de l'appli sait lire : un programme en base marqué « FMT » plus grand est ignoré. */
    const val FORMAT = 1

    fun load(ctx: Context) {
        if (::raw.isInitialized) return
        val p = ctx.getSharedPreferences("force", Context.MODE_PRIVATE)
        val bundled = Json.parseToJsonElement(ctx.assets.open("programme.json").bufferedReader().readText()).jsonObject
        val bv = bundled["V"]?.jsonPrimitive?.longOrNull ?: 0L
        val cached = p.getString("prog", null)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val cv = p.getLong("progv", 0)
        // le programme en base gardé sur le téléphone, s'il est valide et pas plus ancien que celui livré avec l'appli
        if (cached != null && cv >= bv && check(cached) == null && safeApply(cached)) { version = cv; return }
        apply(bundled); version = bv
    }

    /** Contrôle un programme avant de l'appliquer : renvoie la raison du refus, ou null s'il est utilisable. */
    fun check(o: JsonObject): String? { return runCatching {
        val fmt = o["FMT"]?.jsonPrimitive?.intOrNull ?: 1
        if (fmt > FORMAT) return "format $fmt trop récent pour cette version de l'appli"
        listOf("EX", "WEEKS", "SESS1", "SESS2", "WU", "COOLN", "MAP", "METP", "FEF").forEach { if (o[it] == null) return "clé $it manquante" }
        val ex = o["EX"]!!.jsonObject; val wu = o["WU"]!!.jsonObject
        if (ex.isEmpty()) return "aucun exercice"
        val weeks = o["WEEKS"]!!.jsonArray
        if (weeks.size < 8) return "${weeks.size} semaines au lieu de 8"
        weeks.forEachIndexed { i, w -> if ((w.jsonObject["tp"]?.jsonArray?.size ?: 0) != 4) return "semaine ${i + 1} : tempo invalide" }
        for (k in listOf("SESS1", "SESS2")) o[k]!!.jsonObject.forEach { (s, v) ->
            val so = v.jsonObject
            (so["ex"]?.jsonArray ?: return "$k $s sans exercices").forEach { if (it.jsonPrimitive.content !in ex) return "$k $s : exercice ${it.jsonPrimitive.content} inconnu" }
            so["warm"]?.jsonArray?.forEach { if (it.jsonPrimitive.content !in wu) return "$k $s : échauffement ${it.jsonPrimitive.content} inconnu" }
        }
        for (k in listOf("UP", "ALT")) o[k]?.jsonObject?.forEach { (_, v) -> v.jsonArray.forEach { if (it.jsonPrimitive.content !in ex) return "$k : exercice ${it.jsonPrimitive.content} inconnu" } }
        o["SIG"]?.jsonObject?.values?.forEach { c -> c.jsonObject.values.forEach { s -> s.jsonObject.values.forEach { l -> l.jsonArray.forEach { if (it.jsonPrimitive.content !in ex) return "SIG : exercice ${it.jsonPrimitive.content} inconnu" } } } }
        null
    }.getOrElse { "programme illisible (${it.message})" } }

    /** Applique un programme ; en cas d'erreur, remet le précédent et renvoie false. */
    @Synchronized fun safeApply(o: JsonObject): Boolean {
        val old = if (::raw.isInitialized) raw else null
        return try { apply(o); true } catch (t: Throwable) { if (old != null) runCatching { apply(old) }; false }
    }

    /** Remplace le programme (exercices, semaines, séances…) par celui reçu. */
    @Synchronized fun apply(o: JsonObject) {
        listOf("EX", "WEEKS", "SESS1", "SESS2").forEach { require(o[it] != null) { "programme incomplet" } }
        EX.clear(); WEEKS.clear(); SESS1.clear(); SESS2.clear(); WU.clear(); COOLN.clear(); MAP.clear(); METP.clear(); FEF.clear()
        raw = o
        fr.pgragnic.force.ui.Poses.clear()
        raw["EX"]!!.jsonObject.forEach { (id, v) ->
            val o = v.jsonObject
            EX[id] = Ex(id, o.s("n"), o.s("l"), o.s("tip"), o["m"]?.jsonPrimitive?.contentOrNull,
                o["inv"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.intOrNull == 1) } ?: false,
                o["dr"]?.jsonPrimitive?.intOrNull ?: 0, o["kg0"]?.jsonPrimitive?.doubleOrNull,
                o["cues"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                o.list("eq"), o["lt"]?.jsonPrimitive?.contentOrNull ?: (if (o.s("l").contains("haltère")) "kg" else "bw"),
                o["tool"]?.jsonPrimitive?.contentOrNull ?: (if (o.s("l").contains("haltère")) "db" else ""), o.list("stress"),
                o["pose"]?.jsonPrimitive?.contentOrNull ?: id,
                o["band"]?.jsonArray?.map { b -> b.jsonArray.map { it.jsonPrimitive.content } } ?: emptyList(),
                o["f0"]?.jsonPrimitive?.doubleOrNull,
                o["tp"]?.jsonArray?.map { it.jsonPrimitive.double }, o.list("avoid"))
        }
        raw["WEEKS"]!!.jsonArray.forEach { v ->
            val o = v.jsonObject
            WEEKS += Week(o.s("t"), o.i("s"), o.i("r"), o["tp"]!!.jsonArray.map { it.jsonPrimitive.double }, o.i("pl"), o.s("note"))
        }
        for ((key, dst) in listOf("SESS1" to SESS1, "SESS2" to SESS2))
            raw[key]!!.jsonObject.forEach { (s, v) ->
                val o = v.jsonObject
                dst[s] = Sess(o.s("t"), o.list("warm"), o.list("ex"), o.list("cool"))
            }
        raw["WU"]!!.jsonObject.forEach { (k, v) -> WU[k] = v.jsonArray }
        raw["COOLN"]!!.jsonObject.forEach { (k, v) -> COOLN[k] = v.jsonPrimitive.content }
        raw["MAP"]!!.jsonObject.forEach { (k, v) -> MAP[k] = v.jsonObject.mapValues { it.value.jsonPrimitive.double } }
        HEAVY = raw.list("HEAVY").toSet(); BIG = raw.list("BIG").toSet(); CORE = raw.list("CORE").toSet()
        raw["METP"]!!.jsonObject.forEach { (k, v) -> METP[k] = v.jsonPrimitive.double }
        raw["FEF"]!!.jsonObject.forEach { (k, v) -> FEF[k] = v.jsonPrimitive.double }
        L.dict.clear(); raw["EN"]?.jsonObject?.forEach { (k, v) -> L.dict[k] = v.jsonPrimitive.content }
        UP.clear(); ALT.clear(); WARM_UP.clear(); COOL_UP.clear()
        raw["WARMUP_UP"]?.jsonObject?.forEach { (k, v) -> WARM_UP[k] = v.jsonArray.map { it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.content } }
        raw["COOL_UP"]?.jsonObject?.forEach { (k, v) -> COOL_UP[k] = v.jsonArray.map { it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.content } }
        raw["UP"]?.jsonObject?.forEach { (k, v) -> UP[k] = v.jsonArray.map { it.jsonPrimitive.content } }
        raw["ALT"]?.jsonObject?.forEach { (k, v) -> ALT[k] = v.jsonArray.map { it.jsonPrimitive.content } }
        SIG.clear(); raw["SIG"]?.jsonObject?.forEach { (c, ss) -> SIG[c] = ss.jsonObject.mapValues { (_, sl) -> sl.jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } } } }
        weekCache = null
    }

    val UP = HashMap<String, List<String>>()
    /** exercices propres à chaque matériel : cycle (SESS1/SESS2) -> séance -> emplacement -> exercices */
    val SIG = HashMap<String, Map<String, Map<String, List<String>>>>()
    /** échauffement / retour au calme remplacés quand on a le matériel (corde à sauter, rouleau) */
    val WARM_UP = HashMap<String, List<Pair<String, String>>>()
    val COOL_UP = HashMap<String, List<Pair<String, String>>>()
    /** Échauffement / retour au calme : s'il y a plusieurs matériels possibles, chaque séance en prend un différent. */
    private fun upg(m: Map<String, List<Pair<String, String>>>, id: String, s: String = "A"): String {
        val own = m[id]?.filter { gear.has(it.second) } ?: return id
        return if (own.isEmpty()) id else own[((s.firstOrNull() ?: 'A') - 'A').coerceAtLeast(0) % own.size].first
    }
    val ALT = HashMap<String, List<String>>()
    private val gear get() = GearStore.g.value

    /** L'exercice est-il faisable avec le matériel et compatible avec les restrictions de santé ? */
    fun ok(id: String, g: Gear = gear): Boolean {
        val e = EX[id] ?: return false
        if (e.stress.any { it in g.limits }) return false
        if (!e.eq.all { q -> q.split("|").any { g.has(it) } }) return false
        if (e.lt == "kg" && g.steps(e.tool).isEmpty()) return false
        return true
    }

    /** Exercice réellement proposé pour un emplacement de séance : meilleure option, sinon le premier remplaçant possible. */
    fun pick(id: String, g: Gear = gear): String? =
        ((UP[id] ?: emptyList()) + id + (ALT[id] ?: emptyList())).firstOrNull { ok(it, g) }

    /** Exercices de la séance après adaptation au matériel (sans doublon). */
    fun sessEx(c: Int, s: String, g: Gear = gear): List<String> = week(c, g)[s] ?: emptyList()

    /** Matériel qui caractérise un exercice (vide pour le poids du corps et les haltères seuls). */
    fun toolKey(id: String): String = EX[id]?.eq?.map { it.split("|").first() }?.lastOrNull { it != "db" } ?: ""

    private var weekCache: Triple<Int, Gear, Map<String, List<String>>>? = null
    /**
     * Exercices de chaque séance de la semaine. Chaque matériel coché reçoit au moins un emplacement s'il en a un possible
     * (demande de Philippe : « je veux que ça devienne vrai pour tous les matériels ») ; les matériels qui ont le moins
     * d'emplacements possibles passent en premier ; sans exercice propre possible, on garde le choix habituel (pick).
     */
    fun week(c: Int, g: Gear = gear): Map<String, List<String>> {
        weekCache?.let { if (it.first == c && it.second == g) return it.third }
        val S = sess(c, g); val sig = SIG[if (c % 2 == 0) "SESS2" else "SESS1"] ?: emptyMap()
        val cands = S.keys.sorted().associateWith { s -> S[s]!!.ex.map { slot -> (sig[s]?.get(slot) ?: emptyList()).filter { ok(it, g) && toolKey(it).isNotEmpty() } } }
        val count = HashMap<String, Int>(); cands.values.flatten().forEach { l -> l.map { toolKey(it) }.toSet().forEach { count[it] = (count[it] ?: 0) + 1 } }
        val used = HashSet<String>(); val chosen = HashMap<Pair<String, Int>, String>()
        // d'abord chaque matériel sur un emplacement où il est le choix principal, puis les matériels restants sur un emplacement libre
        for (prim in listOf(true, false)) count.keys.sortedBy { count[it] }.filter { it !in used }.forEach { tool ->
            run {
                val hit = cands.entries.firstNotNullOfOrNull { (s, sl) -> sl.indices.firstNotNullOfOrNull { i ->
                    if ((s to i) in chosen) null else sl[i].firstOrNull { toolKey(it) == tool }?.takeIf { !prim || it == sig[s]?.get(S[s]!!.ex[i])?.firstOrNull() }?.let { (s to i) to it } } }
                if (hit != null) { chosen[hit.first] = hit.second; used += tool }
            }
        }
        // 2e passage : un emplacement restant prend son exercice propre principal (premier de la liste) si le matériel est là, sinon le choix habituel
        val out = S.keys.sorted().associateWith { s ->
            S[s]!!.ex.mapIndexedNotNull { i, slot -> chosen[s to i] ?: cands[s]!![i].firstOrNull()?.takeIf { it == sig[s]?.get(slot)?.firstOrNull() } ?: pick(slot, g) }
                // remplacements choisis à la main dans la fiche de l'exercice (« Remplacer »)
                .map { x -> g.swaps[x]?.takeIf { ok(it, g) } ?: x }.distinct()
        }
        weekCache = Triple(c, g, out); return out
    }

    /** Exercice d'origine quand celui-ci est un remplacement choisi à la main. */
    fun origin(id: String, g: Gear = gear): String? = g.swaps.entries.firstOrNull { it.value == id }?.key
    /** Remplaçants possibles : même famille (variantes et remplaçants du programme), faisables avec le matériel, pas déjà dans la séance. */
    fun alternatives(id: String, inSess: List<String>, g: Gear = gear): List<String> {
        val base = origin(id, g) ?: id; val fam = LinkedHashSet<String>()
        if (base != id) fam += base
        EX.keys.forEach { b -> val grp = (UP[b] ?: emptyList()) + b + (ALT[b] ?: emptyList()); if (base in grp || id in grp) fam += grp }
        return fam.filter { it != id && ok(it, g) && (it == base || it !in inSess) }
    }
    /** Remplace un exercice par un autre dans toutes les séances (revenir à l'exercice d'origine annule le remplacement). */
    fun swap(ctx: android.content.Context, id: String, to: String) {
        val g = gear; val base = origin(id, g) ?: id
        GearStore.save(ctx, g.copy(swaps = (g.swaps - base).let { if (to != base) it + (base to to) else it }))
    }

    private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull ?: ""
    private fun JsonObject.i(k: String) = this[k]?.jsonPrimitive?.intOrNull ?: 0
    private fun JsonObject.list(k: String) = this[k]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

    /** Séances du cycle : A à D, plus E à partir de 5 séances par semaine et F à 6 (Philippe : « une E et une F avec leurs propres exercices »). */
    fun sess(c: Int, g: Gear = gear): Map<String, Sess> = (if (c % 2 == 0) SESS2 else SESS1).filterKeys { it <= "D" || (it == "E" && g.perWeek >= 5) || (it == "F" && g.perWeek >= 6) }

    // ---------- charges ----------
    var loads = HashMap<String, DoubleArray>() // id -> [kg, ok, miss]
    val ups = HashMap<Long, MutableList<String>>()

    fun r1(x: Double) = (x * 10).roundToInt() / 10.0
    fun fk(x: Double): String { val v = r1(x); return (if (v == floor(v)) v.toLong().toString() else v.toString()).let { if (L.en) it else it.replace('.', ',') } }
    fun maxKg(st: Settings) = gear.dbWeights().lastOrNull() ?: st.bar
    private fun need(id: String) = if (id in HEAVY) 2 else 3
    /** L'exercice a une charge réglable (haltère, kettlebell ou élastique). */
    fun hasDb(id: String) = EX[id]?.lt.let { it == "kg" || it == "band" }
    fun toolOf(id: String) = EX[id]?.tool ?: ""

    private fun nextStep(id: String, cur: Double, dir: Int): Double {
        val st = gear.steps(toolOf(id)); if (st.isEmpty()) return cur
        return if (dir > 0) st.firstOrNull { it > cur + 1e-6 } ?: cur else st.lastOrNull { it < cur - 1e-6 } ?: cur
    }

    /** Charge de départ : réglée à la main dans le programme, sinon estimée d'après le poids et le niveau. */
    fun startLoad(id: String): Double {
        val e = EX[id]!!; val g = gear; val st = g.steps(e.tool)
        if (st.isEmpty()) return 0.0
        if (e.lt == "band") return st[min(st.size - 1, g.level ?: 0)]
        if (g.bw == null) { val k = e.kg0 ?: 4.8; return st.lastOrNull { it <= k + 1e-6 } ?: st.first() }
        val one = e.l.startsWith("1")
        val f = e.f0 ?: if (e.tool == "bb") (if (e.id == "bbcurl") .2 else if (e.id == "bbpress") .25 else .35) else if (e.tool == "kb") (if (e.l.contains("2 mains")) .14 else .09) else if (one) .08 else .06
        // femmes : charges de départ plus prudentes (surtout le haut du corps) ; plus de 60 ans : un peu plus prudentes aussi
        val sx = if (g.sex == "f") .75 else 1.0; val ag = if ((g.age() ?: 0) >= 60) .85 else 1.0
        val target = g.bw * f * sx * ag * listOf(1.0, 1.5, 2.0)[(g.level ?: 0).coerceIn(0, 2)]
        return st.lastOrNull { it <= target + 1e-6 } ?: st.first()
    }

    fun unitOf(id: String) = if (EX[id]?.lt == "band") "" else " kg"
    fun loadTxt(id: String, v: Double) = if (EX[id]?.lt == "band") t("niveau ${v.toInt()}/${gear.bands}", "level ${v.toInt()}/${gear.bands}") else "${fk(v)} kg"

    // ---------- plafond de progression hebdomadaire ----------
    // Demande de Philippe (10 novembre 2026) : « Oui pour un plafond de progression hebdomadaire, plus prudent pour les débutants et après 50 ans ».
    // + 10 % par semaine au plus ; + 5 % pour un débutant ou à partir de 50 ans. Si le plus petit cran disponible dépasse
    // le plafond (petites charges), ce cran est permis au plus une fois toutes les deux semaines.
    const val DAY = 86_400_000L

    /** Profil prudent : débutant ou 50 ans et plus. */
    fun prudent(g: Gear = gear) = g.level == 0 || (g.age() ?: 0) >= 50

    /** Hausse de charge maximale sur 7 jours glissants (0,05 ou 0,10). */
    fun weeklyCap(g: Gear = gear) = if (prudent(g)) .05 else .10

    /** Hausse permise ? hist = charges successives de l'exercice (date, charge), last = date de la dernière hausse ou du premier essai. */
    fun capOk(id: String, nk: Double, d: Long, hist: List<Pair<Long, Double>>, last: Long, g: Gear = gear): Boolean {
        val wait = d - last >= 14 * DAY
        if (EX[id]?.lt == "band") return d - last >= (if (prudent(g)) 14 else 7) * DAY   // niveaux d'élastique : pas de pourcentage
        val ref = hist.lastOrNull { it.first <= d - 7 * DAY }?.second ?: hist.firstOrNull()?.second ?: return true
        return nk <= ref * (1 + weeklyCap(g)) + 1e-6 || wait
    }

    fun computeLoads(log: List<Entry>, st: Settings) {
        val L = HashMap<String, DoubleArray>()
        EX.values.forEach { if (hasDb(it.id)) L[it.id] = doubleArrayOf(startLoad(it.id), 0.0, 0.0) }
        ups.clear()
        val hist = HashMap<String, MutableList<Pair<Long, Double>>>(); val last = HashMap<String, Long>()
        log.sortedBy { it.d }.forEach { e ->
            if (e.w == 7) return@forEach
            e.ex.forEach { x ->
                val q = L[x.id] ?: return@forEach
                if (x.kg <= 0) return@forEach
                q[0] = x.kg
                val h = hist.getOrPut(x.id) { mutableListOf() }; if (h.lastOrNull()?.second != x.kg) h += e.d to x.kg
                last.putIfAbsent(x.id, e.d)
                val fe = x.f
                if (x.done >= x.target && fe == "Difficile") q[2] = 0.0
                else if (x.done >= x.target) {
                    q[2] = 0.0; q[1] += if (fe == "Facile") 2.0 else 1.0
                    if (q[1] >= need(x.id)) {
                        val nk = nextStep(x.id, q[0], 1)
                        if (nk > q[0] && !capOk(x.id, nk, e.d, h, last[x.id]!!)) { q[1] = need(x.id).toDouble(); return@forEach } // plafond atteint : la hausse attend
                        if (nk > q[0]) { q[0] = nk; h += e.d to nk; last[x.id] = e.d; ups.getOrPut(e.d) { mutableListOf() }.add("↑ ${x.n} ${loadTxt(x.id, nk)}") }
                        q[1] = 0.0
                    }
                } else if (x.done < x.target - 1) {
                    q[1] = 0.0; q[2]++
                    if (q[2] >= 2) { val nk = nextStep(x.id, q[0], -1); if (nk < q[0]) { q[0] = nk; ups.getOrPut(e.d) { mutableListOf() }.add("↓ ${x.n} ${loadTxt(x.id, nk)}") }; q[2] = 0.0 }
                } else { q[1] = 0.0; q[2] = 0.0 }
            }
        }
        loads = L
    }

    fun kgOf(id: String, w: Int, st: Settings): Double { val q = loads[id] ?: return 0.0; return if (w == 7) nextStep(id, q[0], -1) else q[0] }

    /** Disques à mettre de chaque côté d'un haltère réglable pour obtenir kg. */
    fun platesFor(kg: Double, st: Settings): String {
        val g = gear
        if (g.dbMode == "fixed") return t("haltères de ${fk(kg)} kg", "${fk(kg)} kg dumbbells")
        var rem = r1((kg - g.bar) / 2); if (rem <= 0) return "barre seule"
        val parts = ArrayList<String>()
        g.plates.sortedByDescending { it.kg }.forEach { p ->
            val n = min(p.n / 2, floor(rem / p.kg + 1e-6).toInt())
            if (n > 0) { parts += (if (n > 1) "$n × " else "") + fk(p.kg) + " kg"; rem = r1(rem - n * p.kg) }
        }
        return (parts.joinToString(" + ").ifEmpty { "barre seule" }) + " par côté" + (if (rem > 0) " (incomplet)" else "")
    }

    /** Disques de chaque côté de la barre longue. */
    fun bbPlates(kg: Double): String {
        val g = gear; var rem = r1((kg - g.bbBar) / 2); if (rem <= 0) return "barre seule"
        val parts = ArrayList<String>()
        g.bbPlates.sortedByDescending { it.kg }.forEach { p -> val n = min(p.n / 2, floor(rem / p.kg + 1e-6).toInt()); if (n > 0) { parts += (if (n > 1) "$n × " else "") + fk(p.kg) + " kg"; rem = r1(rem - n * p.kg) } }
        return parts.joinToString(" + ") + " par côté"
    }

    fun nDb(id: String) = if (EX[id]!!.l.startsWith("1")) 1 else 2
    fun load(id: String, w: Int, st: Settings): String {
        val e = EX[id]!!
        if (!hasDb(id)) return e.l
        val v = kgOf(id, w, st); val kg = fk(v)
        return when (e.tool) {
            "kb" -> if (L.en) tr(e.l) + " · $kg kg" else e.l.replace("1 kettlebell", "1 kettlebell de $kg kg")
            "bb" -> t("barre chargée à $kg kg", "barbell loaded to $kg kg")
            "band", "mini" -> e.l + " · résistance " + loadTxt(id, v)
            else -> if (e.l.startsWith("1")) (if (L.en) tr(e.l) + " · $kg kg" else e.l.replace("1 haltère", "1 haltère de $kg kg")) else "2 × $kg kg"
        }
    }

    fun work(id: String, w: Int): Work {
        val e = EX[id]!!; val W = WEEKS[w]
        if (e.m == "time") return Work(W.pl.toDouble(), 0, 0.0, listOf(1.5, 1.5, 1.5, 1.5), "${W.pl} s")
        val reps = max(5, W.r + e.dr + if (e.lt == "bw" || e.lt == "band") Gear.repsFor(gear.obj) else Gear.repsFor(gear.obj) / 2)
        if (e.m == "alt") return Work(reps * 2 * 3.0, reps, 3.0, listOf(1.0, 1.0, 1.0, 0.0), t("$reps / côté", "$reps / side"))
        val tp = e.tp ?: W.tp; val rt = tp.sum(); val n = if (e.m == "uni") reps * 2 else reps
        return Work(n * rt + (if (e.m == "uni") SWAP else 0.0), reps, rt, tp, "$reps" + if (e.m == "uni") t(" / côté", " / side") else " reps")
    }

    private class Sim(val ch: Int, val stops: Int, val steps: List<IntArray?>, val kgs: List<Double>)
    private fun simDb(order: List<String>, w: Int, st: Settings): Sim {
        var cur: DoubleArray? = null; var ch = 0; var stops = 0; val steps = ArrayList<IntArray?>(); val kgs = ArrayList<Double>()
        order.forEach { id ->
            val kg = kgOf(id, w, st); kgs += kg
            if (kg <= 0 || toolOf(id) != "db" || gear.dbMode == "fixed") { steps += null; return@forEach }
            val n = nDb(id)
            if (cur == null) { cur = doubleArrayOf(kg, kg); steps += null; return@forEach }
            var c = 0
            val s = cur!!
            if (n == 2) { c = (if (s[0] != kg) 1 else 0) + (if (s[1] != kg) 1 else 0); s[0] = kg; s[1] = kg }
            else if (s[0] != kg && s[1] != kg) { c = 1; s[1] = kg }
            ch += c; if (c > 0) stops++; steps += if (c > 0) intArrayOf(c, n) else null
        }
        return Sim(ch, stops, steps, kgs)
    }

    private fun prim(id: String) = (MAP[id] ?: emptyMap()).filter { it.value >= .7 }.keys

    fun orderEx(c: Int, s: String, w: Int, st: Settings): List<String> {
        val ex = sessEx(c, s)
        val core = ex.filter { it in CORE }; val rest = ex.filter { it !in CORE }
        var best: List<String>? = null; var bc = 1e9
        fun perm(a: List<String>, pre: List<String>) {
            if (a.isEmpty()) {
                val o = pre + core
                val sd = simDb(o, w, st)
                // regroupement par poids prioritaire (demande de Philippe) : d'abord le moins d'arrêts pour changer les disques, puis le moins d'haltères à recharger
                var cost = sd.stops * 200.0 + sd.ch * 100.0
                for (i in 1 until o.size) { val p = prim(o[i - 1]); if (prim(o[i]).any { it in p }) cost += 8 }
                for (i in pre.indices) for (j in i + 1 until pre.size) if (pre[i] !in BIG && pre[j] in BIG) cost += 3
                pre.forEachIndexed { i, x -> cost += abs(ex.indexOf(x) - i) * .01; if (gear.focus(x)) cost += i * 4 }
                if (cost < bc) { bc = cost; best = o }
                return
            }
            a.forEachIndexed { i, x -> perm(a.take(i) + a.drop(i + 1), pre + x) }
        }
        perm(rest, emptyList())
        return best ?: ex
    }

    /**
     * Séance complète. Si elle dépasse la durée maximale choisie (Infos → Durée des séances), on retire des séries une à une,
     * d'abord au gainage, puis aux petits exercices (bras, épaules…), en dernier aux gros exercices ; jamais en dessous de 2 séries,
     * et les zones à travailler passent en dernier.
     */
    fun phases(c: Int, w: Int, s: String, restS: Int, st: Settings, log: List<Entry>): List<Phase> {
        val cap = gear.maxMin?.let { it * 60.0 }
        val ns = HashMap<String, Int>()
        var P = build(c, w, s, restS, st, log, ns)
        if (cap == null) return P
        val exo = P.filter { it.k == "work" }.mapNotNull { it.ex }.distinct()
        exo.forEach { ns[it] = P.count { p -> p.k == "work" && p.ex == it } }
        fun rank(id: String) = (if (id in CORE) 0 else if (id in BIG || id in HEAVY) 2 else 1) + (if (gear.focus(id)) 3 else 0)
        while (P.sumOf { it.dur } > cap) {
            val id = exo.filter { ns[it]!! > 2 }.sortedWith(compareBy<String>({ rank(it) }, { -ns[it]!! }, { -exo.indexOf(it) })).firstOrNull() ?: break
            ns[id] = ns[id]!! - 1
            P = build(c, w, s, restS, st, log, ns)
        }
        return P
    }

    private fun build(c: Int, w: Int, s: String, restS: Int, st: Settings, log: List<Entry>, nsOver: Map<String, Int>): ArrayList<Phase> {
        val S = sess(c)[s] ?: sess(c).values.first(); val W = WEEKS[w]; val P = ArrayList<Phase>()
        computeLoads(log, st)
        val exo = orderEx(c, s, w, st); val sim = simDb(exo, w, st)
        val first = exo.firstOrNull { kgOf(it, w, st) > 0 && toolOf(it) == "db" }
        val adj = gear.dbMode != "fixed"
        val need = exo.mapNotNull { EX[it]?.eq }.flatten().flatMap { it.split("|").take(1) }.toSet()
        val tools = listOfNotNull(if ("kb" in need) "ta kettlebell" else null, if ("band" in need) "ta bande à poignées" else null, if ("mini" in need) "ta mini-bande" else null,
            if (exo.any { EX[it]?.eq?.any { q -> "chair" in q } == true }) "une chaise stable" else null, if ("bench" in need || "incbench" in need) "ton banc" else null,
            exo.firstOrNull { toolOf(it) == "bb" }?.let { t("la barre à ${fk(kgOf(it, w, st))} kg (${bbPlates(kgOf(it, w, st))})", "the barbell at ${fk(kgOf(it, w, st))} kg (${tr(bbPlates(kgOf(it, w, st)))})") },
            if ("abwheel" in need) "ta roue abdominale" else null)
        P += Phase("prep", blk = "Préparation", n = "Prépare ton matériel",
            tip = (if (first != null) (if (adj) t("Règle les 2 haltères à ${fk(kgOf(first, w, st))} kg : ${platesFor(kgOf(first, w, st), st)}", "Set both dumbbells to ${fk(kgOf(first, w, st))} kg: ${tr(platesFor(kgOf(first, w, st), st))}") else t("Prends les haltères de ${fk(kgOf(first, w, st))} kg", "Take the ${fk(kgOf(first, w, st))} kg dumbbells")) else "Un tapis au sol") +
                (if (tools.isNotEmpty()) " · Prévois aussi " + tools.joinToString(", ") else ""),
            dur = if (first != null) 20.0 else 10.0, pose = "circle")
        S.warm.map { upg(WARM_UP, it, s) }.forEachIndexed { i, k ->
            val x = WU[k]!!
            P += Phase("warm", blk = "Échauffement", n = x[1].jsonPrimitive.content, det = "${i + 1}/3 · 1 min", tip = x[2].jsonPrimitive.content,
                dur = 60.0, pose = x[0].jsonPrimitive.content, nodb = x.size > 3,
                // rythme propre au mouvement si le programme en donne un (WTP), sinon 2,2 s par aller-retour
                tp = raw["WTP"]?.jsonObject?.get(k)?.jsonArray?.map { it.jsonPrimitive.double } ?: listOf(1.0, 0.0, 1.2, 0.0))
        }
        P += Phase("switch", ei = 0, set = 0, blk = "Échauffement terminé", dur = 20.0)
        exo.forEachIndexed { ei, id ->
            val wk = work(id, w); val e = EX[id]!!
            sim.steps[ei]?.let { sc ->
                val pr = P.last()
                if (pr.k == "rest" || pr.k == "switch") {
                    val kg = sim.kgs[ei]
                    pr.chg = "Change les disques : " + (if (sc[0] == 1 && sc[1] == 2) "le 2ᵉ haltère" else if (sc[0] == 1) "un haltère" else "les 2 haltères") + " à ${fk(kg)} kg : ${platesFor(kg, st)}"
                    pr.dur = max(pr.dur, 60.0)
                }
            }
            val invOn = e.inv && e.m != "time" && e.m != "alt"
            val ns = nsOver[id] ?: (W.s + if (gear.focus(id)) 1 else 0)
            for (i in 1..ns) {
                P += Phase("work", ei = ei, set = i, inv = invOn, blk = t("Exercice ${ei + 1}/${exo.size} · Série $i/${ns}", "Exercise ${ei + 1}/${exo.size} · Set $i/${ns}"), n = e.n,
                    det = t("Série $i/${ns} · ${wk.label} · ${load(id, w, st)}", "Set $i/${ns} · ${wk.label} · ${tr(load(id, w, st))}"), tip = e.tip, dur = wk.dur, pose = e.pose, ex = id, nodb = e.tool != "db" && e.tool != "kb",
                    tp = if (invOn) listOf(wk.tp[2], wk.tp[1], wk.tp[0], wk.tp[3]) else wk.tp, rt = wk.rt, reps = wk.reps, m = e.m)
                val last = ei == exo.size - 1 && i == ns
                if (last) continue
                val extra = if (id in HEAVY) 30 else 0
                if (i < ns) P += Phase("rest", ei = ei, set = i, blk = t("Exercice ${ei + 1}/${exo.size}", "Exercise ${ei + 1}/${exo.size}"), n = "Pause",
                    det = t("Série $i/${ns} terminée · encore ${ns - i} série", "Set $i/${ns} done · ${ns - i} more set") + (if (ns - i > 1) "s" else "") + t(" de ", " of ") + tr(e.n), dur = (restS + extra).toDouble())
                else P += Phase("switch", ei = ei + 1, set = 0, blk = t("Exercice ${ei + 1}/${exo.size} terminé", "Exercise ${ei + 1}/${exo.size} done"), dur = max(45, restS + extra).toDouble())
            }
        }
        S.cool.map { upg(COOL_UP, it, s) }.forEachIndexed { i, cId -> P += Phase("cool", blk = "Retour au calme", n = COOLN[cId] ?: cId, det = "${i + 1}/3 · " + if (sides(cId)) t("20 s par côté", "20 s per side") else "40 s", tip = "Respire, relâche sans forcer", dur = 40.0, pose = cId) }
        P += Phase("end")
        P.forEachIndexed { i, p ->
            if (p.k == "rest" || p.k == "prep" || p.k == "switch") {
                val nx = P.drop(i + 1).firstOrNull { it.k != "rest" && it.k != "switch" }
                if (nx != null && nx.k != "end") {
                    p.next = nx; p.pose = nx.pose; p.ex = nx.ex; p.nodb = nx.nodb; p.ghostTp = listOf(1.2, .4, 1.6, .6)
                    if (p.k == "switch") { p.n = nx.n; p.det = t("Exercice ${nx.ei + 1}/${exo.size} · ${P.count { it.k == "work" && it.ei == nx.ei }} séries · ", "Exercise ${nx.ei + 1}/${exo.size} · ${P.count { it.k == "work" && it.ei == nx.ei }} sets · ") + nx.det.split(" · ").drop(1).joinToString(" · "); p.tip = p.chg ?: nx.tip }
                }
            }
        }
        return P
    }

    fun ease(x: Double) = if (x < .5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
    fun cycleK(t: Double, tp: List<Double>): Double {
        val T = tp.sum(); if (T <= 0) return 0.0
        val c = t % T
        if (c < tp[0]) return ease(c / tp[0])
        if (c < tp[0] + tp[1]) return 1.0
        if (c < tp[0] + tp[1] + tp[2]) return 1 - ease((c - tp[0] - tp[1]) / tp[2])
        return 0.0
    }

    /** Pause pour changer de côté au milieu d'un exercice unilatéral (retour de Philippe : « Sur le rowing il n'y a pas de pause (5 secondes par exemple) pour changer de mains »). */
    const val SWAP = 5.0
    /** Exercice unilatéral : côté (0 gauche, 1 droit), temps écoulé dans le côté, et vrai pendant la pause de changement de côté. */
    /** Exercices d'un seul côté dont la pose de base travaille le côté droit (le personnage a sa gauche en x+) : rowing un bras (bras qui
     *  tire), montée sur banc (jambe qui monte), soulevé de terre sur une jambe (jambe d'appui). Les autres commencent par la gauche.
     *  (Philippe, capture du rowing unilatéral, 13 novembre : « C'est indiqué gauche pour le bras droit et vice versa ») */
    private val FIRST_RIGHT = setOf("row1", "stepup", "sldl")
    /** côté travaillé : h = 0 première moitié de la série, 1 seconde moitié (en miroir) */
    fun uniRight(pose: String?, h: Int) = (pose in FIRST_RIGHT) == (h == 0)
    fun uniPos(q: Phase, pt: Double): Triple<Int, Double, Boolean> {
        val side = (q.dur - SWAP) / 2
        return when { pt < side -> Triple(0, pt, false); pt < side + SWAP -> Triple(1, 0.0, true); else -> Triple(1, pt - side - SWAP, false) }
    }

    /** Repère vocal à l'instant t d'une phase d'effort. */
    fun cueAt(q: Phase, t0: Double): Pair<String, String>? {
        val t = max(0.0, t0)
        if (q.m == "time") {
            val cps = listOf(0.0 to "Tiens la position", q.dur / 2 to "Moitié", q.dur - 10 to "Dix secondes").filter { it.first >= 0 && (it.first == 0.0 || q.dur > 20) }
            var i = -1; cps.forEachIndexed { j, cp -> if (t >= cp.first) i = j }
            return if (i < 0) null else ("t$i" to if (i == 0) "" else cps[i].second)
        }
        if (q.m == "alt") {
            val n = floor(t / q.rt).toInt(); val tot = q.reps * 2; if (n >= tot) return null
            return ("a$n" to (if (n == tot - 1) "Dernière. " else "") + if (n % 2 == 1) "Droite" else "Gauche")
        }
        val tp = q.tp ?: return null
        val (h, lt, gap) = if (q.m == "uni") uniPos(q, t) else Triple(0, t, false)
        if (gap) return ("swap" to t("Change de côté", "Switch sides"))
        val rep = floor(lt / q.rt).toInt(); if (rep >= q.reps) return null
        val c = lt - rep * q.rt
        val ph = if (c < tp[0]) 0 else if (c < tp[0] + tp[1]) 1 else if (c < tp[0] + tp[1] + tp[2]) 2 else 3
        val txt = (if (q.inv) listOf("Descends", "Tiens", "Monte", "") else listOf("Monte", "Tiens", "Descends", ""))[ph]
        // Annonces placées dans la phase la plus longue (la descente) de la répétition d'avant, pour ne pas couvrir « Monte » (retour de Philippe :
        // « Et dernière et monte se chevauchent »)
        val L = tp.indices.maxBy { tp[it] }; val st = tp.take(L).sum()
        if (ph == L && c >= st + tp[L] * .4) {
            if (rep == q.reps - 2) return ("$h-$rep-n" to t("Dernière", "Last one"))
        }
        return ("$h-$rep-$ph" to txt)
    }

    fun feelOf(sets: List<SetRec>, tgt: Int): String {
        val rs = sets.mapNotNull { it.res }
        if (sets.any { it.r < tgt } || rs.any { it == 0 }) return "Difficile"
        if (rs.isNotEmpty() && rs.size == sets.size && rs.all { it >= 3 }) return "Facile"
        return "Juste bien"
    }

    fun fmt(s0: Double): String { val s = max(0, ceil(s0).toInt()); return "${s / 60}:" + (s % 60).toString().padStart(2, '0') }

    /** Kcal approximatives d'une séance (MET par type de phase). */
    fun sessKcal(P: List<Phase>, bw: Double) = r1(P.sumOf { (METP[it.k] ?: 0.0) * bw * it.dur / 3600 })
}
