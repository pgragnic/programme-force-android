package fr.pgragnic.force

import android.content.Context
import kotlinx.serialization.json.*
import kotlin.math.*

/** Données du programme (exportées de la page web) et logique de séance. */
class Ex(val id: String, val n: String, val l: String, val tip: String, val m: String?, val inv: Boolean,
         val dr: Int, val kg0: Double?, val cues: List<String>)

class Week(val t: String, val s: Int, val r: Int, val tp: List<Double>, val pl: Int, val note: String)

class Sess(val t: String, val warm: List<String>, val ex: List<String>, val cool: List<String>)

class Work(val dur: Double, val reps: Int, val rt: Double, val tp: List<Double>, val label: String)

/** Une étape de la séance guidée. */
data class Phase(
    val k: String, var blk: String = "", var n: String = "", var det: String = "", var tip: String = "",
    var dur: Double = 0.0, var pose: String? = null, val ei: Int = -1, val set: Int = 0,
    val inv: Boolean = false, val tp: List<Double>? = null, val rt: Double = 0.0, val reps: Int = 0,
    val m: String? = null, val nodb: Boolean = false, var chg: String? = null, var next: Phase? = null,
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

    fun load(ctx: Context) {
        if (::raw.isInitialized) return
        raw = Json.parseToJsonElement(ctx.assets.open("programme.json").bufferedReader().readText()).jsonObject
        raw["EX"]!!.jsonObject.forEach { (id, v) ->
            val o = v.jsonObject
            EX[id] = Ex(id, o.s("n"), o.s("l"), o.s("tip"), o["m"]?.jsonPrimitive?.contentOrNull,
                o["inv"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.intOrNull == 1) } ?: false,
                o["dr"]?.jsonPrimitive?.intOrNull ?: 0, o["kg0"]?.jsonPrimitive?.doubleOrNull,
                o["cues"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList())
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
    }

    private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull ?: ""
    private fun JsonObject.i(k: String) = this[k]?.jsonPrimitive?.intOrNull ?: 0
    private fun JsonObject.list(k: String) = this[k]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

    fun sess(c: Int): Map<String, Sess> = if (c % 2 == 0) SESS2 else SESS1

    // ---------- charges ----------
    var loads = HashMap<String, DoubleArray>() // id -> [kg, ok, miss]
    val ups = HashMap<Long, MutableList<String>>()

    fun r1(x: Double) = (x * 10).roundToInt() / 10.0
    fun fk(x: Double): String { val v = r1(x); return (if (v == floor(v)) v.toLong().toString() else v.toString()).replace('.', ',') }
    fun maxKg(st: Settings) = r1(st.bar + 2 * (2 * floor(st.n2 / 2.0) + 0.5 * floor(st.n05 / 2.0)))
    private fun need(id: String) = if (id in HEAVY) 2 else 3
    fun hasDb(id: String) = EX[id]?.l?.contains("haltère") == true

    fun computeLoads(log: List<Entry>, st: Settings) {
        val L = HashMap<String, DoubleArray>()
        EX.values.forEach { if (hasDb(it.id)) L[it.id] = doubleArrayOf(it.kg0 ?: 4.8, 0.0, 0.0) }
        ups.clear()
        log.sortedBy { it.d }.forEach { e ->
            if (e.w == 7) return@forEach
            e.ex.forEach { x ->
                val q = L[x.id] ?: return@forEach
                if (x.kg <= 0) return@forEach
                q[0] = x.kg
                val fe = x.f
                if (x.done >= x.target && fe == "Difficile") q[2] = 0.0
                else if (x.done >= x.target) {
                    q[2] = 0.0; q[1] += if (fe == "Facile") 2.0 else 1.0
                    if (q[1] >= need(x.id)) { val nk = min(maxKg(st), r1(q[0] + 1)); if (nk > q[0]) { q[0] = nk; ups.getOrPut(e.d) { mutableListOf() }.add("↑ ${x.n} ${fk(nk)} kg") }; q[1] = 0.0 }
                } else if (x.done < x.target - 1) {
                    q[1] = 0.0; q[2]++
                    if (q[2] >= 2) { val nk = max(st.bar, r1(q[0] - 1)); if (nk < q[0]) { q[0] = nk; ups.getOrPut(e.d) { mutableListOf() }.add("↓ ${x.n} ${fk(nk)} kg") }; q[2] = 0.0 }
                } else { q[1] = 0.0; q[2] = 0.0 }
            }
        }
        loads = L
    }

    fun kgOf(id: String, w: Int, st: Settings): Double { val q = loads[id] ?: return 0.0; return if (w == 7) max(st.bar, r1(q[0] - 1)) else q[0] }

    fun platesFor(kg: Double, st: Settings): String {
        val side = r1((kg - st.bar) / 2); if (side <= 0) return "barre seule"
        var rem = side
        val a = min(st.n2 / 2, floor(rem / 2 + 1e-6).toInt()); rem = r1(rem - a * 2)
        val b = min(st.n05 / 2, (rem / 0.5).roundToInt()); rem = r1(rem - b * 0.5)
        val t = listOfNotNull(if (a > 0) (if (a > 1) "$a × " else "") + "2 kg" else null, if (b > 0) (if (b > 1) "$b × " else "") + "0,5 kg" else null).joinToString(" + ")
        return (t.ifEmpty { "barre seule" }) + " par côté" + (if (rem > 0) " (incomplet)" else "")
    }

    fun nDb(id: String) = if (EX[id]!!.l.startsWith("1")) 1 else 2
    fun load(id: String, w: Int, st: Settings): String {
        val e = EX[id]!!
        if (hasDb(id)) { val kg = fk(kgOf(id, w, st)); return if (e.l.startsWith("1")) e.l.replace("1 haltère", "1 haltère de $kg kg") else "2 × $kg kg" }
        return e.l
    }

    fun work(id: String, w: Int): Work {
        val e = EX[id]!!; val W = WEEKS[w]
        if (e.m == "time") return Work(W.pl.toDouble(), 0, 0.0, listOf(1.5, 1.5, 1.5, 1.5), "${W.pl} s")
        val reps = max(5, W.r + e.dr)
        if (e.m == "alt") return Work(reps * 2 * 3.0, reps, 3.0, listOf(1.0, 1.0, 1.0, 0.0), "$reps / côté")
        val rt = W.tp.sum(); val n = if (e.m == "uni") reps * 2 else reps
        return Work(n * rt, reps, rt, W.tp, "$reps" + if (e.m == "uni") " / côté" else " reps")
    }

    private class Sim(val ch: Int, val steps: List<IntArray?>, val kgs: List<Double>)
    private fun simDb(order: List<String>, w: Int, st: Settings): Sim {
        var cur: DoubleArray? = null; var ch = 0; val steps = ArrayList<IntArray?>(); val kgs = ArrayList<Double>()
        order.forEach { id ->
            val kg = kgOf(id, w, st); kgs += kg
            if (kg <= 0) { steps += null; return@forEach }
            val n = nDb(id)
            if (cur == null) { cur = doubleArrayOf(kg, kg); steps += null; return@forEach }
            var c = 0
            val s = cur!!
            if (n == 2) { c = (if (s[0] != kg) 1 else 0) + (if (s[1] != kg) 1 else 0); s[0] = kg; s[1] = kg }
            else if (s[0] != kg && s[1] != kg) { c = 1; s[1] = kg }
            ch += c; steps += if (c > 0) intArrayOf(c, n) else null
        }
        return Sim(ch, steps, kgs)
    }

    private fun prim(id: String) = (MAP[id] ?: emptyMap()).filter { it.value >= .7 }.keys

    fun orderEx(c: Int, s: String, w: Int, st: Settings): List<String> {
        val ex = sess(c)[s]!!.ex
        val core = ex.filter { it in CORE }; val rest = ex.filter { it !in CORE }
        var best: List<String>? = null; var bc = 1e9
        fun perm(a: List<String>, pre: List<String>) {
            if (a.isEmpty()) {
                val o = pre + core
                var cost = simDb(o, w, st).ch * 10.0
                for (i in 1 until o.size) { val p = prim(o[i - 1]); if (prim(o[i]).any { it in p }) cost += 8 }
                for (i in pre.indices) for (j in i + 1 until pre.size) if (pre[i] !in BIG && pre[j] in BIG) cost += 3
                pre.forEachIndexed { i, x -> cost += abs(ex.indexOf(x) - i) * .01 }
                if (cost < bc) { bc = cost; best = o }
                return
            }
            a.forEachIndexed { i, x -> perm(a.take(i) + a.drop(i + 1), pre + x) }
        }
        perm(rest, emptyList())
        return best ?: ex
    }

    fun phases(c: Int, w: Int, s: String, restS: Int, st: Settings, log: List<Entry>): List<Phase> {
        val S = sess(c)[s]!!; val W = WEEKS[w]; val P = ArrayList<Phase>()
        computeLoads(log, st)
        val exo = orderEx(c, s, w, st); val sim = simDb(exo, w, st)
        val first = exo.firstOrNull { kgOf(it, w, st) > 0 }
        P += Phase("prep", blk = "Préparation", n = "Prépare ton matériel",
            tip = if (first != null) "Règle les 2 haltères à ${fk(kgOf(first, w, st))} kg : ${platesFor(kgOf(first, w, st), st)}" else "Un tapis au sol",
            dur = if (first != null) 20.0 else 10.0, pose = "circle")
        S.warm.forEachIndexed { i, k ->
            val x = WU[k]!!
            P += Phase("warm", blk = "Échauffement", n = x[1].jsonPrimitive.content, det = "${i + 1}/3 · 1 min", tip = x[2].jsonPrimitive.content,
                dur = 60.0, pose = x[0].jsonPrimitive.content, nodb = x.size > 3, tp = listOf(1.0, 0.0, 1.2, 0.0))
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
            for (i in 1..W.s) {
                P += Phase("work", ei = ei, set = i, inv = invOn, blk = "Exercice ${ei + 1}/${exo.size} · Série $i/${W.s}", n = e.n,
                    det = "Série $i/${W.s} · ${wk.label} · ${load(id, w, st)}", tip = e.tip, dur = wk.dur, pose = id,
                    tp = if (invOn) listOf(wk.tp[2], wk.tp[1], wk.tp[0], wk.tp[3]) else wk.tp, rt = wk.rt, reps = wk.reps, m = e.m)
                val last = ei == exo.size - 1 && i == W.s
                if (last) continue
                val extra = if (id in HEAVY) 30 else 0
                if (i < W.s) P += Phase("rest", ei = ei, set = i, blk = "Exercice ${ei + 1}/${exo.size}", n = "Pause",
                    det = "Série $i/${W.s} terminée · encore ${W.s - i} série" + (if (W.s - i > 1) "s" else "") + " de ${e.n}", dur = (restS + extra).toDouble())
                else P += Phase("switch", ei = ei + 1, set = 0, blk = "Exercice ${ei + 1}/${exo.size} terminé", dur = max(45, restS + extra).toDouble())
            }
        }
        S.cool.forEachIndexed { i, cId -> P += Phase("cool", blk = "Retour au calme", n = COOLN[cId] ?: cId, det = "${i + 1}/3 · 40 s", tip = "Respire, relâche sans forcer", dur = 40.0, pose = cId) }
        P += Phase("end")
        P.forEachIndexed { i, p ->
            if (p.k == "rest" || p.k == "prep" || p.k == "switch") {
                val nx = P.drop(i + 1).firstOrNull { it.k != "rest" && it.k != "switch" }
                if (nx != null && nx.k != "end") {
                    p.next = nx; p.pose = nx.pose; p.ghostTp = listOf(1.2, .4, 1.6, .6)
                    if (p.k == "switch") { p.n = nx.n; p.det = "Exercice ${nx.ei + 1}/${exo.size} · ${W.s} séries · " + nx.det.split(" · ").drop(1).joinToString(" · "); p.tip = p.chg ?: nx.tip }
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
        val half = if (q.m == "uni") q.dur / 2 else q.dur
        val h = if (q.m == "uni" && t >= half) 1 else 0
        val lt = t - h * half; val rep = floor(lt / q.rt).toInt(); if (rep >= q.reps) return null
        val c = lt - rep * q.rt
        val ph = if (c < tp[0]) 0 else if (c < tp[0] + tp[1]) 1 else if (c < tp[0] + tp[1] + tp[2]) 2 else 3
        var txt = (if (q.inv) listOf("Descends", "Tiens", "Monte", "") else listOf("Monte", "Tiens", "Descends", ""))[ph]
        if (ph == 0 && rep == q.reps - 1) txt = "Dernière. $txt"
        if (ph == 0 && rep == 0 && h == 1) txt = "Côté droit. $txt"
        if (ph == 0 && rep == 0 && h == 0 && q.m == "uni") txt = "Côté gauche. $txt"
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
