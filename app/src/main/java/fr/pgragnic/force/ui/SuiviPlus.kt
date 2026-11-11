package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.*
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.roundToInt

/** Bilan automatique de la semaine, calculé sur le téléphone. */
object Bilan {
    class B(val title: String, val lines: List<String>)

    fun auto(log: List<Entry>, days: List<DayRec>, st: Settings): B {
        val now = System.currentTimeMillis(); val m0 = monday(now); val wk = 7 * 864e5.toLong()
        val L = log.filter { it.ex.isNotEmpty() }.sortedBy { it.d }
        val cur = L.filter { it.d >= m0 }; val prev = L.filter { it.d >= m0 - wk && it.d < m0 }
        val out = ArrayList<String>()
        val names = cur.map { it.s }.distinct().sorted().joinToString(", ")
        out += t("Séances : ${cur.size} sur ${GearStore.g.value.perWeek} cette semaine", "Sessions: ${cur.size} of ${GearStore.g.value.perWeek} this week") + if (names.isNotBlank()) " ($names)." else "."
        val v = cur.sumOf { volOf(it) }; val vp = prev.sumOf { volOf(it) }
        if (v > 0) out += t("Volume soulevé : ${Prog.fk((v).roundToInt().toDouble())} kg", "Volume lifted: ${Prog.fk((v).roundToInt().toDouble())} kg") + if (vp > 0) t(" (${if (v >= vp) "+" else "−"}${(kotlin.math.abs(v - vp) / vp * 100).roundToInt()} % par rapport à la semaine dernière).", " (${if (v >= vp) "+" else "−"}${(kotlin.math.abs(v - vp) / vp * 100).roundToInt()}% vs last week).") else "."
        // charges en hausse
        val before = HashMap<String, Double>(); L.filter { it.d < m0 }.forEach { e -> e.ex.forEach { x -> if (x.kg > 0) before[x.id] = x.kg } }
        val ups = LinkedHashMap<String, Pair<Double, Double>>()
        cur.forEach { e -> e.ex.forEach { x -> val b = before[x.id]; if (b != null && x.kg > b) ups[x.id] = b to x.kg } }
        if (ups.isNotEmpty()) out += t("Charges en hausse : ", "Loads up: ") + ups.entries.joinToString(", ") { (id, p) -> "${tr(Prog.EX[id]?.n ?: id)} ${Prog.fk(p.first)} → ${Prog.fk(p.second)} kg" } + "."
        val hard = cur.flatMap { it.ex }.filter { it.f == "Difficile" }.map { Prog.EX[it.id]?.n ?: it.id }.distinct()
        if (hard.isNotEmpty()) out += t("Ressenti difficile sur : ${hard.joinToString(", ")}. La charge restera la même la prochaine fois.", "Felt hard on: ${hard.joinToString(", ") { tr(it) }}. The load will stay the same next time.")
        val ld = LocalDate.now(); val steps = (0..6).mapNotNull { i -> days.firstOrNull { it.date == ld.minusDays(i.toLong()).toString() }?.steps?.takeIf { it > 0 } }
        if (steps.isNotEmpty()) out += t("Pas : ${String.format(fr.pgragnic.force.L.loc, "%,d", steps.average().roundToInt())} par jour en moyenne sur 7 jours.", "Steps: ${String.format(fr.pgragnic.force.L.loc, "%,d", steps.average().roundToInt())} per day on average over 7 days.")
        val acts = days.filter { it.act != null && Recovery.dayTs(it) >= m0 }
        if (acts.isNotEmpty()) out += t("Activités : ", "Activities: ") + acts.joinToString(", ") { tr(it.act ?: "") + (it.dur?.let { d -> " $d min" } ?: "") + (it.dplus?.let { d -> " D+ $d m" } ?: "") } + "."
        val t0 = now - wk
        val k = L.filter { it.d >= t0 }.sumOf { Kcal.entry(it, log) } + days.filter { Recovery.dayTs(it) >= t0 }.sumOf { Kcal.day(it, log) }
        if (k > 0) out += t("Dépense estimée sur 7 jours : ${Kcal.kr(k)}.", "Estimated burn over 7 days: ${Kcal.kr(k)}.")
        val MU = Recovery.muscles(log, days)
        val tired = Recovery.MUS.filter { MU.R[it]!! < 60 }; val mid = Recovery.MUS.filter { MU.R[it]!! in 60..89 }
        out += if (tired.isEmpty() && mid.isEmpty()) "Tous les muscles sont prêts." else
            (if (tired.isNotEmpty()) t("Encore fatigués : ${tired.joinToString(", ")}. ", "Still tired: ${tired.joinToString(", ") { tr(it) }}. ") else "") + (if (mid.isNotEmpty()) t("En récupération : ${mid.joinToString(", ")}.", "Recovering: ${mid.joinToString(", ") { tr(it) }}.") else "")
        val doneS = cur.map { it.s }.toSet()
        val left = Prog.sess(st.c).keys.filter { it !in doneS }
        if (left.isNotEmpty() && L.isNotEmpty()) {
            val best = left.maxBy { Recovery.sessScore(st.c, it, MU.R) }
            out += t("Prochaine séance conseillée : ${best} · ${Prog.sess(st.c)[best]?.t ?: ""} (muscles les plus reposés).", "Suggested next workout: ${best} · ${tr(Prog.sess(st.c)[best]?.t ?: "")} (most rested muscles).")
        }
        val title = when { cur.size >= 4 -> "Semaine complète, bravo"; cur.size >= 2 -> "Bonne semaine en cours"; cur.isEmpty() -> "Nouvelle semaine"; else -> "C'est parti" }
        return B(title, out)
    }
}

@Composable
fun BilanPanel() {
    val log by Store.log.collectAsState(); val days by Store.days.collectAsState(); val st by Store.st.collectAsState(); val remote by Store.bilan.collectAsState()
    val auto = remember(log, days, st) { Bilan.auto(log, days, st) }
    Panel {
        Text("Bilan de la semaine", color = C.ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(t("Semaine du ", "Week of ") + LocalDate.now().with(java.time.DayOfWeek.MONDAY).format(DateTimeFormatter.ofPattern("d MMMM", fr.pgragnic.force.L.loc)), color = C.muted, fontSize = 12.sp)
        val r = remote
        val rDate = r?.get("date")?.jsonPrimitive?.contentOrNull
        val fresh = rDate != null && runCatching { LocalDate.parse(rDate) >= LocalDate.now().minusDays(8) }.getOrDefault(false)
        if (r != null && fresh) {
            Text("Bilan de Claude · " + LocalDate.parse(rDate).format(DateTimeFormatter.ofPattern("EEEE d MMMM", fr.pgragnic.force.L.loc)), color = C.cool, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            r["title"]?.jsonPrimitive?.contentOrNull?.let { Text(it, color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
            (r["text"]?.jsonPrimitive?.contentOrNull ?: "").split(Regex("\n+")).filter { it.isNotBlank() }.forEach { t ->
                val b = Regex("^[-•]\\s*").containsMatchIn(t)
                Text((if (b) "• " else "") + t.replace(Regex("^[-•]\\s*"), ""), color = C.ink, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, start = if (b) 6.dp else 0.dp))
            }
            HorizontalDivider(color = C.line, modifier = Modifier.padding(vertical = 10.dp))
            Text("En chiffres", color = C.muted, fontSize = 12.sp)
        } else Text(auto.title, color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        auto.lines.forEach { Text("• $it", color = C.ink, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, start = 6.dp)) }
    }
}

@Composable
fun ProgressPanel() {
    val log by Store.log.collectAsState(); val st by Store.st.collectAsState()
    val L = remember(log) { log.filter { it.ex.isNotEmpty() }.sortedBy { it.d } }
    Panel {
        Text("Progression", color = C.ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("Poids du jour (kg)", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        LineChart(L.filter { it.bw != null }.map { Pt(it.d, it.bw!!) }, "Note ton poids à la fin d'au moins deux séances pour voir la courbe.")

        val ids = remember(L) { val a = ArrayList<String>(); L.forEach { e -> e.ex.forEach { x -> if (x.kg > 0 && x.id !in a) a += x.id } }; a }
        var cur by remember(ids) { mutableStateOf(if ("squat" in ids) "squat" else ids.firstOrNull()) }
        Text("Charge par haltère (kg)", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
        if (ids.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ids.forEach { id ->
                Text(Prog.EX[id]?.n ?: id, color = if (id == cur) C.bg else C.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (id == cur) C.ink else C.bg).clickable { cur = id }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        val c = cur
        val lp = if (c == null) emptyList() else L.filter { e -> e.ex.any { it.id == c && it.kg > 0 } }.map { e -> Pt(e.d, e.ex.first { it.id == c }.kg) }.let { p ->
            Prog.computeLoads(log, st); val now = Prog.loads[c]?.get(0)
            if (now != null && p.isNotEmpty()) p + Pt(System.currentTimeMillis(), now) else p
        }
        LineChart(lp, "La courbe apparaîtra après deux séances avec cet exercice.", step = true)

        Text("Volume par semaine (kg soulevés · nombre de séances)", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
        val m0 = monday(System.currentTimeMillis()); val wk = 7 * 864e5.toLong()
        val bars = (7 downTo 0).map { i ->
            val m = m0 - i * wk; val es = L.filter { it.d >= m && it.d < m + wk }
            val cal = Calendar.getInstance().apply { timeInMillis = m }
            Bar("${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}", es.sumOf { volOf(it) }.roundToInt().toDouble(), if (es.isNotEmpty()) "${es.size} s." else "")
        }
        BarChart(bars, "Le volume apparaîtra après ta première séance.")
    }
}
