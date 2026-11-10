package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(14.dp), content = content)
}

@Composable
fun Eyebrow(t: String) = Text(fr.pgragnic.force.tr(t).uppercase(), color = C.muted, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)

@Composable
fun PChip(label: String, on: Boolean, sub: String? = null, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) C.ink else C.surface).border(1.dp, C.line, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (on) C.bg else C.ink, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = if (on) C.bg else C.effort, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

/** Pastille de semaine compacte : « S1 » sur une ligne, puis 4 points (séances faites) sur une ligne. */
@Composable
fun WeekChip(w: Int, n: Int, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(if (on) C.ink else C.surface).border(1.dp, C.line, RoundedCornerShape(10.dp))
        .clickable(onClick = onClick).padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(t("S${w + 1}", "W${w + 1}"), color = if (on) C.bg else C.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val c = if (on) C.bg else C.effort
            repeat(4) { i -> Box(Modifier.size(5.dp).clip(CircleShape).then(if (i < n) Modifier.background(c) else Modifier.border(1.dp, c, CircleShape))) }
        }
    }
}

// ---------------- Séance ----------------
// Écran centré sur la prochaine séance (maquette « ② Séance » validée par Philippe : « Ok pour ces écrans ! »)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeanceScreen(onStart: () -> Unit) {
    val st by Store.st.collectAsState(); val log by Store.log.collectAsState()
    Store.ver.collectAsState().value
    val gear by GearStore.g.collectAsState()
    remember(log, gear) { Prog.computeLoads(log, st); 0 }
    val done = remember(log) { log.map { "${it.c}-${it.w}-${it.s}" }.toSet() }
    val S = Prog.sess(st.c); val W = Prog.WEEKS[st.w]
    var weeks by remember { mutableStateOf(false) }; var list by remember { mutableStateOf(false) }
    val order = remember(st.c, st.w, st.s, log, gear) { Prog.orderEx(st.c, st.s, st.w, st) }
    val P = remember(st.c, st.w, st.s, st.rest, log, gear) { Prog.phases(st.c, st.w, st.s, st.rest, st, log) }
    val nSets = P.count { it.k == "work" }
    // muscles pas encore récupérés sollicités par la séance (Philippe : « Et si je souhaite faire une séance par jour ? », choix « 3 » :
    // alerte avant de démarrer, avec une autre séance proposée)
    val days by Store.days.collectAsState()
    val MU = remember(log, days) { Recovery.muscles(log, days) }
    fun tired(ids: List<String>): Pair<Double, List<String>> {
        val w = HashMap<String, Double>(); ids.forEach { id -> Prog.MAP[id]?.forEach { (k, v) -> w[k] = (w[k] ?: 0.0) + v } }
        val tot = w.values.sum().takeIf { it > 0 } ?: return 0.0 to emptyList()
        val t = w.filter { (k, v) -> v >= .7 && (MU.R[k] ?: 100) < 75 }
        return t.values.sum() / tot to t.keys.sortedBy { MU.R[it] ?: 100 }
    }
    val (share, sore) = remember(order, MU) { tired(order) }
    val alt = remember(st.c, st.w, MU, gear) { S.keys.filter { it != st.s }.map { it to tired(Prog.orderEx(st.c, it, st.w, st)).first }.minByOrNull { it.second }?.takeIf { it.second < share - .2 }?.first }
    var warn by remember { mutableStateOf(false) }
    if (warn) AlertDialog(onDismissRequest = { warn = false }, containerColor = C.surface,
        title = { Text(t("Muscles pas encore récupérés", "Muscles not recovered yet"), color = C.ink, fontWeight = FontWeight.Bold) },
        text = { Text(t("Cette séance sollicite surtout ", "This workout mostly works ") + sore.joinToString(", ") { "${tr(it)} (${MU.R[it]} %)" } + ". " +
            (alt?.let { t("La séance $it (${tr(S[it]!!.t)}) les ménage davantage.", "Workout $it (${tr(S[it]!!.t)}) is easier on them.") } ?: t("Un jour de repos ou une activité douce leur ferait du bien.", "A rest day or light activity would help them.")),
            color = C.muted) },
        confirmButton = { Column(horizontalAlignment = Alignment.End) {
            alt?.let { a -> TextButton(onClick = { warn = false; Store.setSettings { it.s = a } }) { Text(t("Passer à la séance $a", "Switch to workout $a"), color = C.effort, fontWeight = FontWeight.Bold) } }
            TextButton(onClick = { warn = false; onStart() }) { Text(t("Démarrer quand même", "Start anyway"), color = C.ink) }
            TextButton(onClick = { warn = false }) { Text(t("Annuler", "Cancel"), color = C.muted) }
        } })
    // muscles les plus sollicités par la séance
    val mus = remember(order) { val m = HashMap<String, Double>(); order.forEach { id -> Prog.MAP[id]?.forEach { (k, v) -> m[k] = (m[k] ?: 0.0) + v } }; m.entries.sortedByDescending { it.value }.take(4).map { it.key } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text(t("Cycle ${st.c} · semaine ${st.w + 1} sur 8", "Cycle ${st.c} · week ${st.w + 1} of 8"), color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(t("Ta prochaine séance", "Your next workout"), fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
        }
        // sélecteur compact : semaine (déplie cycles et semaines) et séance
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(20.dp)).clickable { weeks = !weeks }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(t("Semaine ${st.w + 1}", "Week ${st.w + 1}"), color = C.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Icon(if (weeks) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = C.muted, modifier = Modifier.size(18.dp))
            }
            Row(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(20.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                S.keys.forEach { s ->
                    val on = st.s == s; val ok = "${st.c}-${st.w}-$s" in done
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(17.dp)).background(if (on) C.effort else Color.Transparent).clickable { Store.setSettings { it.s = s } }, contentAlignment = Alignment.Center) {
                        Text(s + if (ok) " ✓" else "", color = if (on) C.onColor else C.muted, fontWeight = if (on) FontWeight.Black else FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
        if (weeks) Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                (1..2).forEach { c -> PChip("Cycle $c", st.c == c) { Store.setSettings { it.c = c } } }
                Text(if (st.c % 2 == 0) "variantes" else "exercices de base", color = C.muted, fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                (0 until 8).forEach { w ->
                    val n = listOf("A", "B", "C", "D").count { "${st.c}-$w-$it" in done }
                    WeekChip(w, n, st.w == w, Modifier.weight(1f)) { Store.setSettings { it.w = w } }
                }
            }
            Text(tr(W.t) + " · " + t("${W.s} séries × ${W.r} reps · tempo ${W.tp.take(3).joinToString("-") { Prog.fk(it) }}", "${W.s} sets × ${W.r} reps · tempo ${W.tp.take(3).joinToString("-") { Prog.fk(it) }}"),
                color = C.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Text(W.note, color = C.muted, fontSize = 12.sp)
        }
        // carte de la séance
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(22.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(t("SÉANCE ${st.s}", "WORKOUT ${st.s}"), color = C.effort, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                Text("≈ ${((P.sumOf { it.dur } + 59) / 60).toInt()} min", color = C.muted, fontSize = 13.sp)
            }
            Text(tr(S[st.s]!!.t), color = C.ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp)
            if (mus.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                mus.forEach { Text(tr(it), color = C.ink, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(C.bg).padding(horizontal = 10.dp, vertical = 5.dp)) }
            }
            Text(t("${order.size} exercices · $nSets séries · ${W.r} reps", "${order.size} exercises · $nSets sets · ${W.r} reps"), color = C.muted, fontSize = 13.sp)
            gear.maxMin?.let { m -> if (nSets < order.sumOf { id -> W.s + if (gear.focus(id)) 1 else 0 })
                Text(t("Raccourcie pour tenir en $m min.", "Shortened to fit in $m min."), color = C.muted, fontSize = 12.sp) }
            if (share >= .4) Text("⚠ " + t("Encore en récupération : ", "Still recovering: ") + sore.take(3).joinToString(", ") { tr(it) }, color = C.warm, fontSize = 13.sp)
            Button(onClick = { if (share >= .4) warn = true else onStart() }, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.effort, contentColor = C.onColor)) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp))
                Text(t("Démarrer", "Start"), fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
        // exercices, repliés
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { list = !list }, verticalAlignment = Alignment.CenterVertically) {
                Text(t("Ce que tu vas faire", "What you'll do"), color = C.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(if (list) t("Réduire", "Less") else t("Voir", "Show"), color = C.muted, fontSize = 13.sp)
                Icon(if (list) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = C.muted, modifier = Modifier.size(18.dp))
            }
            HorizontalDivider(color = C.line)
            val shown = if (list) order else order.take(2)
            shown.forEach { id ->
                val e = Prog.EX[id]!!; val wk = Prog.work(id, st.w)
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(e.n, color = C.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${P.count { it.k == "work" && it.ex == id }.takeIf { it > 0 } ?: W.s} × ${wk.label}" + Prog.load(id, st.w, st).takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), color = C.muted, fontSize = 13.sp)
                    if (list) Text(e.tip, color = C.muted, fontSize = 12.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
                }
                HorizontalDivider(color = C.line)
            }
            if (!list && order.size > 2) Text(t("+ ${order.size - 2} autres exercices", "+ ${order.size - 2} more exercises"), color = C.muted, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().clickable { list = true }.padding(vertical = 10.dp))
        }
    }
}

// ---------------- Fin de séance ----------------
class EdRow(val id: String, val n: String, val target: Int, val unit: String, val kg: Double, val sets: SnapshotStateList<SetRec>, var f: MutableState<String>)

fun rowsFor(ses: Session, st: Settings): List<EdRow> {
    val ids = ses.P.filter { it.k == "work" && it.set == 1 }.mapNotNull { it.ex }
    return ids.map { id ->
        val wk = Prog.work(id, ses.w); val time = Prog.EX[id]!!.m == "time"; val tgt = if (time) wk.dur.toInt() else wk.reps
        val got = ses.sets[id] ?: mutableListOf()
        val sets = mutableStateListOf<SetRec>().apply { (0 until ses.nsetsOf(id)).forEach { add(got.getOrNull(it) ?: SetRec(tgt, null)) } }
        EdRow(id, Prog.EX[id]!!.n, tgt, if (time) "s" else "reps", if (Prog.hasDb(id)) Prog.kgOf(id, ses.w, st) else 0.0, sets, mutableStateOf(Prog.feelOf(sets, tgt)))
    }
}

@Composable
fun SetsEditor(rows: List<EdRow>) {
    rows.forEach { r ->
        Panel(Modifier.padding(vertical = 4.dp)) {
            Text(r.n, color = C.ink, fontWeight = FontWeight.Bold)
            Text((if (r.kg > 0) Prog.loadTxt(r.id, r.kg) + " · " else "") + t("objectif ${r.target} ${if (r.unit == "s") "s" else "reps"}", "goal ${r.target} ${if (r.unit == "s") "s" else "reps"}"), color = C.muted, fontSize = 12.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.sets.forEachIndexed { i, x ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, C.line, RoundedCornerShape(10.dp)).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("S${i + 1}", color = C.muted, fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("−", color = C.ink, fontSize = 20.sp, modifier = Modifier.clickable { r.sets[i] = x.copy(r = maxOf(0, x.r - 1)); r.f.value = Prog.feelOf(r.sets, r.target) }.padding(horizontal = 6.dp))
                            Text("${x.r}", color = C.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("+", color = C.ink, fontSize = 20.sp, modifier = Modifier.clickable { r.sets[i] = x.copy(r = x.r + 1); r.f.value = Prog.feelOf(r.sets, r.target) }.padding(horizontal = 6.dp))
                        }
                        Text("R " + (x.res?.let { if (it >= 3) "3+" else "$it" } ?: "–"), color = C.muted, fontSize = 12.sp,
                            modifier = Modifier.clickable { val nv = when (x.res) { null -> 0; 0 -> 1; 1 -> 2; 2 -> 3; else -> null }; r.sets[i] = x.copy(res = nv); r.f.value = Prog.feelOf(r.sets, r.target) }.padding(4.dp))
                    }
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Facile", "Juste bien", "Difficile").forEach { v -> Box(Modifier.weight(1f)) { PChip(v, r.f.value == v) { r.f.value = v } } }
            }
        }
    }
}

fun recsOf(rows: List<EdRow>) = rows.map { r -> ExRec(r.id, r.n, r.kg, r.target, r.unit, r.sets.toList(), r.sets.minOf { it.r }, r.f.value) }
fun overall(ex: List<ExRec>): String { val nF = ex.count { it.f == "Facile" }; val nD = ex.count { it.f == "Difficile" }; return if (nD > ex.size / 2.0) "Difficile" else if (nF > ex.size / 2.0) "Facile" else "Juste bien" }

@Composable
fun EndScreen(ses: Session, onDone: () -> Unit) {
    val st = Store.st.value
    val rows = remember { rowsFor(ses, st) }
    var bw by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(C.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Eyebrow(t("Séance ${ses.s} terminée", "Workout ${ses.s} complete"))
        Text("Bravo !", color = C.ink, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Chaque série telle que notée pendant les pauses : répétitions et réserve (R, touche pour changer). Corrige si besoin.", color = C.muted, modifier = Modifier.padding(vertical = 8.dp))
        SetsEditor(rows)
        OutlinedTextField(bw, { bw = it }, label = { Text("Poids du jour (facultatif, kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Button(onClick = {
            val ex = recsOf(rows); val b = bw.replace(',', '.').toDoubleOrNull()
            val e = Entry(d = System.currentTimeMillis(), c = ses.c, w = ses.w, s = ses.s, f = overall(ex), rest = st.rest, ex = ex, bw = b, kcal = Prog.sessKcal(ses.P, b ?: 80.0))
            Store.add(e)
            val order = listOf("A", "B", "C", "D"); val ni = order.indexOf(ses.s)
            Store.setSettings { if (ni < 3) it.s = order[ni + 1] else if (it.w < 7) { it.w++; it.s = "A" } else { it.c++; it.w = 0; it.s = "A" } }
            onDone()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Text("Enregistrer la séance", fontWeight = FontWeight.Black) }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Quitter sans enregistrer", color = C.muted) }
    }
}

// ---------------- Suivi ----------------
@Composable
fun SuiviScreen(inProgres: Boolean = false) {
    val log by Store.log.collectAsState()
    var open by remember { mutableStateOf<Long?>(null) }
    var edit by remember { mutableStateOf<Long?>(null) }
    var arm by remember { mutableStateOf<Long?>(null) }
    val df = remember { SimpleDateFormat("EEE d MMM", fr.pgragnic.force.L.loc) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            if (!inProgres) Text("Suivi", fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
            val wk = log.count { it.d > System.currentTimeMillis() - 7 * 86400000L }
            Text(t("${log.size} séances au total · $wk ces 7 derniers jours", "${log.size} sessions in total · $wk in the last 7 days"), color = C.muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        item { BilanPanel() }
        if (!inProgres) item { ProgressPanel() }
        item { Box(Modifier.padding(top = 8.dp)) { Eyebrow("Historique") } }
        items(log.sortedByDescending { it.d }, key = { it.d }) { e ->
            Panel {
                Row(Modifier.fillMaxWidth().clickable { open = if (open == e.d) null else e.d; edit = null }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("C${e.c} · S${e.w + 1} · Séance ${e.s} · ${e.f}", "C${e.c} · W${e.w + 1} · Workout ${e.s} · ${tr(e.f ?: "")}"), color = C.ink, fontWeight = FontWeight.Bold)
                        val ups = Prog.ups[e.d]; if (!ups.isNullOrEmpty()) Text(ups.joinToString(" · "), color = C.effort, fontSize = 12.sp)
                    }
                    Text(df.format(Date(e.d)), color = C.muted, fontSize = 13.sp)
                }
                if (open == e.d) {
                    if (edit == e.d) {
                        val rows = remember(e.d) { e.ex.map { x -> val ss = mutableStateListOf<SetRec>().apply { addAll(if (x.sets.isNotEmpty()) x.sets else List(Prog.WEEKS[e.w].s) { SetRec(x.done) }) }; EdRow(x.id, x.n, x.target, x.unit, x.kg, ss, mutableStateOf(x.f)) } }
                        SetsEditor(rows)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { val ex = recsOf(rows); Store.update(e.copy(ex = ex, f = overall(ex))); edit = null }) { Text("Enregistrer") }
                            OutlinedButton(onClick = { edit = null }) { Text("Annuler") }
                        }
                    } else {
                        e.ex.forEach { x ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(x.n + if (x.kg > 0) " · " + Prog.loadTxt(x.id, x.kg) else "", color = C.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text((if (x.sets.isNotEmpty()) x.sets.joinToString(" · ") { s -> "${s.r}" + (s.res?.let { " R" + if (it >= 3) "3+" else "$it" } ?: "") } else t("pire série ${x.done}", "worst set ${x.done}")) + " / ${x.target}", color = C.muted, fontSize = 13.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            if (e.ex.isNotEmpty()) OutlinedButton(onClick = { edit = e.d }) { Text("Modifier") }
                            val armed = arm == e.d
                            Button(onClick = { if (armed) { Store.delete(e); arm = null } else arm = e.d },
                                colors = ButtonDefaults.buttonColors(containerColor = if (armed) C.danger else C.surface, contentColor = if (armed) Color.White else C.danger)) {
                                Text(if (armed) "Confirmer la suppression" else "Supprimer")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Progrès ----------------
// Suivi et Activité réunis (maquette « ④ Progrès » validée par Philippe : « Ok pour ces écrans ! »)
@Composable
fun ProgresScreen() {
    var sub by rememberSaveable { mutableIntStateOf(Demo.sub?.toIntOrNull() ?: 0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Progrès", "Progress"), fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
            Seg3(listOf(t("Semaine", "Week"), t("Courbes", "Charts"), t("Activité", "Activity")), sub) { sub = it }
        }
        Box(Modifier.weight(1f)) {
            when (sub) {
                0 -> SuiviScreen(inProgres = true)
                1 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { ProgressPanel() }
                else -> ActiviteScreen(inProgres = true)
            }
        }
    }
}

/** Sélecteur segmenté (sous-onglets). */
@Composable
fun Seg3(opts: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(14.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(14.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        opts.forEachIndexed { i, o ->
            val on = i == sel
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(11.dp)).background(if (on) C.line else Color.Transparent).clickable { onSel(i) }, contentAlignment = Alignment.Center) {
                Text(o, color = if (on) C.ink else C.muted, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

// ---------------- Réglages ----------------
// Infos devient « Réglages » : une liste courte et des sous-pages (maquette « ① Réglages » validée par Philippe : « Ok pour ces écrans ! »)
/** sous-page ouverte de Réglages ; gardée en dehors de l'écran pour y revenir après la modification du matériel ou du profil */
val regSub = mutableStateOf<String?>(null)

@Composable
fun ReglagesScreen() {
    var sub by regSub
    if (sub != null) {
        androidx.activity.compose.BackHandler { sub = null }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable { sub = null }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ChevronLeft, null, tint = C.muted); Text(t("Réglages", "Settings"), color = C.muted, fontWeight = FontWeight.SemiBold)
            }
            Text(REG.first { it.k == sub }.t(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
            when (sub) {
                "prog" -> ProgrammePage()
                "rappels" -> RappelPanel()
                "compte" -> { AccountPanel(); ExportPanel() }
                "affichage" -> ThemePanel()
                "avis" -> FeedbackPanel()
                else -> {
                    NewsPanel(); LegalPanel()
                    Panel {
                        Text("Comment ça marche", color = C.ink, fontWeight = FontWeight.Bold)
                        Text("8 semaines, 4 séances (A, B, C, D), adaptées à ton matériel. Pendant chaque pause, note les répétitions faites et combien tu aurais encore pu en faire : la charge monte quand c'est réussi plusieurs fois, et baisse après deux échecs.", color = C.muted)
                        Text(t("Version ${BuildConfig.VERSION_NAME}. Personnage : MakeHuman (domaine public, CC0).", "Version ${BuildConfig.VERSION_NAME}. Character: MakeHuman (public domain, CC0)."), color = C.muted, fontSize = 12.sp)
                    }
                }
            }
        }
        return
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val user by Store.user.collectAsState(); val g by GearStore.g.collectAsState(); val sync by Store.sync.collectAsState()
    val rem = remember(sub) { Reminder.get(ctx) }
    fun subOf(k: String): String = when (k) {
        "prog" -> t("Matériel, charges, santé, profil, durée", "Equipment, loads, health, profile, length")
        "rappels" -> if (rem.on) t("LMMJVSD", "MTWTFSS").filterIndexed { i, _ -> (rem.days shr i) and 1 == 1 } + " · ${rem.h} h ${rem.m.toString().padStart(2, '0')}" else t("Désactivés", "Off")
        "compte" -> if (user != null) user!! else t("Non connecté · sauvegarde en ligne", "Not signed in · online backup")
        "affichage" -> (when (C.mode) { "light" -> t("Thème clair", "Light theme"); "dark" -> t("Thème sombre", "Dark theme"); else -> t("Thème du téléphone", "Phone theme") }) + " · " +
            when (fr.pgragnic.force.L.mode) { "fr" -> "Français"; "en" -> "English"; else -> t("Langue du téléphone", "Phone language") }
        "avis" -> t("Une idée, un souci ?", "An idea, a problem?")
        else -> t("Nouveautés, version, confidentialité", "What's new, version, privacy")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(t("Réglages", "Settings"), fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
        listOf(REG.take(3), REG.drop(3)).forEach { grp ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(18.dp))) {
                grp.forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth().clickable { sub = r.k }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (r.k == "prog") C.effort.copy(alpha = .18f) else C.bg), contentAlignment = Alignment.Center) {
                            Icon(r.ic, null, tint = if (r.k == "prog") C.effort else C.muted, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(r.t(), color = C.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(subOf(r.k), color = C.muted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = C.muted)
                    }
                    if (i < grp.size - 1) HorizontalDivider(color = C.line)
                }
            }
        }
        if (user != null && sync.isNotBlank()) Text(sync, color = C.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("UpRep ${BuildConfig.VERSION_NAME}", color = C.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

class RegItem(val k: String, val fr: String, val en: String, val ic: androidx.compose.ui.graphics.vector.ImageVector) { fun t() = t(fr, en) }
val REG = listOf(
    RegItem("prog", "Mon programme", "My program", Icons.Default.FitnessCenter),
    RegItem("rappels", "Rappels", "Reminders", Icons.Default.NotificationsNone),
    RegItem("compte", "Compte et sauvegarde", "Account and backup", Icons.Default.CloudDone),
    RegItem("affichage", "Affichage", "Display", Icons.Default.DarkMode),
    RegItem("avis", "Donner mon avis", "Send feedback", Icons.Default.ChatBubbleOutline),
    RegItem("apropos", "À propos et confidentialité", "About and privacy", Icons.Default.Info))

/** Sous-page « Mon programme » : tout ce qui change les séances. */
@Composable
fun ProgrammePage() {
    val g by GearStore.g.collectAsState(); val st by Store.st.collectAsState()
    val names = remember { Prog.raw["EQ"]?.jsonArray?.flatMap { s -> s.jsonArray[1].jsonArray.map { it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.content } }?.toMap() ?: emptyMap() }
    Text(t("Tout ce qui change tes séances, au même endroit. Une modification met le programme à jour.", "Everything that shapes your workouts, in one place. Any change updates your program."), color = C.muted, fontSize = 13.sp)
    val rows = listOf(
        t("Mon matériel", "My equipment") to g.items.mapNotNull { names[it]?.let { n -> tr(n) } }.ifEmpty { listOf(t("Poids du corps seulement", "Bodyweight only")) }.joinToString(" · "),
        t("Tes charges", "Your loads") to (listOfNotNull(
            if ("db" in g.items) (if (g.dbMode == "fixed") t("Haltères ", "Dumbbells ") + g.fixed.joinToString(", ") { Prog.fk(it) } + " kg" else t("Haltères jusqu'à ${Prog.fk(g.dbWeights().last())} kg", "Dumbbells up to ${Prog.fk(g.dbWeights().last())} kg")) else null,
            if ("kb" in g.items) "Kettlebell " + g.kbs.joinToString(", ") { Prog.fk(it) } + " kg" else null,
            if ("barbell" in g.items) t("Barre jusqu'à ${Prog.fk(g.bbWeights().last())} kg", "Barbell up to ${Prog.fk(g.bbWeights().last())} kg") else null).joinToString(" · ").ifBlank { t("Rien à régler", "Nothing to set") }),
        t("Ta santé", "Your health") to (if (g.limits.isEmpty()) t("Aucune zone à ménager", "No area to protect") else t("À ménager : ", "Protect: ") + Gear.LIMITS.filter { it.first in g.limits }.joinToString(", ") { tr(it.second) }),
        t("Ton profil", "Your profile") to listOfNotNull(g.height?.let { "$it cm" }, g.age()?.let { t("$it ans", "$it years old") }, when (g.sex) { "m" -> t("homme", "male"); "f" -> t("femme", "female"); else -> null }, g.bw?.let { "${Prog.fk(it)} kg" })
            .ifEmpty { listOf(t("Taille, sexe, âge, poids", "Height, sex, age, weight")) }.joinToString(" · "))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(18.dp))) {
        rows.forEachIndexed { i, (h, d) ->
            Row(Modifier.fillMaxWidth().clickable { Demo.setupStep = i; setupOpen.value = true }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(h, color = C.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(d, color = C.muted, fontSize = 12.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.ChevronRight, null, tint = C.muted)
            }
            if (i < rows.size - 1) HorizontalDivider(color = C.line)
        }
    }
    DureePanel()
    Panel {
        Text(t("Pause entre séries", "Rest between sets"), color = C.ink, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 45, 60, 90).forEach { r -> val on = st.rest == r
                Text("$r s", color = if (on) C.bg else C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) C.ink else C.bg).clickable { Store.setSettings { it.rest = r } }.padding(vertical = 10.dp)) }
        }
    }
}

/** Compte et sauvegarde en ligne. */
@Composable
fun AccountPanel() {
    val user by Store.user.collectAsState()
    var email by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }; var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }; var create by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
        Panel {
            Text("Compte et sauvegarde en ligne", color = C.ink, fontWeight = FontWeight.Bold)
            val rec by Store.recovery.collectAsState()
            if (rec) {
                // arrivée par le lien « mot de passe oublié »
                var np by remember { mutableStateOf("") }
                OutlinedTextField(np, { np = it }, label = { Text(t("Nouveau mot de passe", "New password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                Button(onClick = { scope.launch { msg = if (np.length < 6) t("6 caractères minimum.", "At least 6 characters.") else Store.newPassword(np) ?: t("Mot de passe changé", "Password changed") } },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(t("Enregistrer le mot de passe", "Save password")) }
            }
            if (user != null) {
                Text(t("Connecté : $user", "Signed in: $user"), color = C.muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Store.sync.collectAsState().value, color = C.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Store.pull() }) { Text("Actualiser", fontSize = 12.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { scope.launch { Store.signOut() } }, contentPadding = PaddingValues(0.dp)) { Text("Se déconnecter", color = C.muted) }
                    Spacer(Modifier.weight(1f))
                    var armDel by remember { mutableStateOf(false) }
                    TextButton(onClick = { if (!armDel) armDel = true else scope.launch { msg = Store.deleteAccount(); armDel = false } }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (armDel) "Confirmer : tout effacer" else "Supprimer mon compte", color = C.danger)
                    }
                }
                Text("La suppression efface définitivement ton compte et toutes tes données, en ligne et sur ce téléphone.", color = C.muted, fontSize = 11.sp)
                msg?.let { Text(it, color = C.danger, fontSize = 13.sp) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    PChip("J'ai un compte", !create) { create = false; msg = null }
                    PChip("Créer un compte", create) { create = true; msg = null }
                }
                OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                val eye = @Composable { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, fr.pgragnic.force.tr(if (show) "Masquer le mot de passe" else "Afficher le mot de passe")) } }
                OutlinedTextField(pw, { pw = it }, label = { Text("Mot de passe") }, singleLine = true, trailingIcon = eye,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (create) {
                    OutlinedTextField(pw2, { pw2 = it }, label = { Text("Confirmer le mot de passe") }, singleLine = true, trailingIcon = eye,
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = pw2.isNotEmpty() && pw2 != pw, supportingText = { if (pw2.isNotEmpty() && pw2 != pw) Text("Les deux mots de passe sont différents") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    Text("6 caractères minimum.", color = C.muted, fontSize = 12.sp)
                }
                Button(onClick = {
                    msg = when {
                        email.isBlank() || pw.isEmpty() -> "Renseigne l'e-mail et le mot de passe."
                        create && pw.length < 6 -> "Le mot de passe doit faire au moins 6 caractères."
                        create && pw != pw2 -> "Les deux mots de passe sont différents."
                        else -> { scope.launch { msg = Store.signIn(email.trim(), pw, create) ?: "Connecté" }; "…" }
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(if (create) "Créer le compte" else "Se connecter") }
                if (!create) TextButton(onClick = { scope.launch { msg = if (email.isBlank()) t("Renseigne ton e-mail.", "Enter your email.") else Store.resetPassword(email.trim()) } }, contentPadding = PaddingValues(0.dp)) {
                    Text(t("Mot de passe oublié ?", "Forgot password?"), color = C.muted, fontSize = 13.sp)
                }
                if (Store.googleReady) {
                    val act = androidx.compose.ui.platform.LocalContext.current
                    OutlinedButton(onClick = { scope.launch { msg = Store.signInGoogle(act) } }, modifier = Modifier.fillMaxWidth()) { Text(t("Continuer avec Google", "Continue with Google"), fontWeight = FontWeight.Bold) }
                }
                msg?.let { Text(it, color = C.muted, fontSize = 13.sp) }
            }
        }
}

@Composable
fun Soon(title: String, txt: String) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
        Panel(Modifier.padding(top = 12.dp)) { Text(txt, color = C.muted) }
    }
}

/** Rappel de séance : jours et heure. */
@Composable
fun RappelPanel() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var c by remember { mutableStateOf(Reminder.get(ctx)) }
    val perm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    fun upd(n: Reminder.Cfg) { c = n; Reminder.set(ctx, n); Store.devChanged() }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Rappel de séance", color = C.ink, fontWeight = FontWeight.Bold)
                Text(if (c.on) t("Notification à ${c.h} h ${c.m.toString().padStart(2, '0')} les jours cochés, sauf si tu t'es déjà entraîné ce jour-là", "Notification at ${c.h}:${c.m.toString().padStart(2, '0')} on the selected days, unless you already trained that day") else "Désactivé", color = C.muted, fontSize = 12.sp)
            }
            Switch(c.on, { on ->
                if (on && android.os.Build.VERSION.SDK_INT >= 33) runCatching { perm.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                upd(c.copy(on = on))
            })
        }
        if (c.on) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                t("LMMJVSD", "MTWTFSS").forEachIndexed { i, l ->
                    val on = (c.days shr i) and 1 == 1
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) C.ink else C.bg).clickable { upd(c.copy(days = c.days xor (1 shl i))) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(l.toString(), color = if (on) C.bg else C.muted, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Heure", color = C.muted, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { upd(c.copy(h = (c.h + 23) % 24)) }) { Text("−") }
                Text("${c.h} h ${c.m.toString().padStart(2, '0')}", color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp).clickable { upd(c.copy(m = (c.m + 15) % 60)) })
                OutlinedButton(onClick = { upd(c.copy(h = (c.h + 1) % 24)) }) { Text("+") }
            }
            Text("Touche les minutes pour avancer de 15 min.", color = C.muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun ThemePanel() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Panel {
        Text("Langue", color = C.ink, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("auto" to "Comme le téléphone", "fr" to "Français", "en" to "English").forEach { (m, tx) ->
                val on = fr.pgragnic.force.L.mode == m
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) C.ink else C.bg).clickable { fr.pgragnic.force.L.save(ctx, m); Store.devChanged() }.padding(vertical = 10.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    Text(tx, color = if (on) C.bg else C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
        Text("Apparence", color = C.ink, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("auto" to "Comme le téléphone", "light" to "Clair", "dark" to "Sombre").forEach { (m, t) ->
                val on = C.mode == m
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) C.ink else C.bg).clickable { C.save(ctx, m); Store.devChanged() }.padding(vertical = 10.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    Text(t, color = if (on) C.bg else C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Nouveautés de l'appli, la plus récente en premier. */
val NEWS = listOf(
    "8 novembre 2026" to listOf(
        "L'appli parle anglais : elle suit la langue du téléphone, ou ton choix dans Infos → Langue.",
        "Voix de séance, notification et rappel en anglais quand l'appli est en anglais."),
    "7 novembre 2026, 23 h 45" to listOf(
        "Premier lancement guidé : ton matériel, tes charges disponibles, les zones à ménager, ton objectif, ton niveau et les zones à travailler (Infos → Matériel et profil).",
        "77 exercices : haltères, kettlebell, barre, élastiques, traction, dips, banc, roue, chaise, mur, serviette, medicine ball, ballon, Bosu, poids du corps ; les séances se composent avec ton matériel.",
        "Échauffement à la corde ou au bâton, retour au calme au rouleau ou à la balle selon ton matériel.",
        "Ton avis compte : envoie une idée ou signale un problème depuis l'onglet Infos.",
        "Personnage 3D : les exercices penchés en avant (rowing, soulevé de terre, pompes…) ne sont plus dessinés cambrés en arrière."),
    "7 novembre 2026, 21 h 40" to listOf(
        "Pendant la séance, les exercices sont regroupés par poids d'haltères pour changer les disques le moins possible.",
        "Le programme (exercices, séries, consignes) se met à jour tout seul depuis ta base en ligne, sans réinstaller l'appli.",
        "Avertissement santé au premier lancement, politique de confidentialité et suppression du compte depuis l'appli."),
    "7 novembre 2026, soir" to listOf(
        "Personnage 3D réaliste pendant la séance, avec les muscles travaillés en rouge ; fais-le tourner du doigt.",
        "Onglet Corps : récupération de chaque muscle, de face et de dos, et le détail par muscle.",
        "Onglet Activité : pas importés automatiquement via Health Connect, saisie des marches lestées, randos et trails, graphique sur 14 jours et dépense estimée.",
        "Notification de séance avec le minuteur et les boutons Précédent, Pause, Suivant, aussi sur l'écran verrouillé ; la séance continue écran éteint.",
        "Rappel de séance les jours et à l'heure de ton choix.",
        "Suivi : bilan de la semaine, courbes de poids et de charges, volume par semaine.",
        "Thème clair, sombre ou comme le téléphone.",
        "Semaines S1 à S8 sur une seule ligne, avec les points de séances."),
    "7 novembre 2026, 18 h 55" to listOf(
        "Création de compte : mot de passe visible et à confirmer.",
        "Le lien de confirmation par e-mail ouvre l'appli.",
        "Synchronisation automatique."),
    "7 novembre 2026" to listOf("Première version de l'appli : séance guidée avec voix, bips et vibrations, historique synchronisé en ligne."),
)

@Composable
fun NewsPanel() {
    var all by remember { mutableStateOf(false) }
    Panel {
        Text("Nouveautés", color = C.ink, fontWeight = FontWeight.Bold)
        (if (all) NEWS else NEWS.take(1)).forEach { (d, items) ->
            Text(d, color = C.effort, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            items.forEach { Text("• $it", color = C.ink, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp, start = 4.dp)) }
        }
        if (NEWS.size > 1) TextButton(onClick = { all = !all }, contentPadding = PaddingValues(0.dp)) { Text(if (all) "Moins" else "Versions précédentes") }
        Text(t("Version ${fr.pgragnic.force.BuildConfig.VERSION_NAME} · programme ", "Version ${fr.pgragnic.force.BuildConfig.VERSION_NAME} · programme ") + if (Prog.version > 0) t("n° ${Prog.version} (en ligne)", "no. ${Prog.version} (online)") else t("d'origine", "original"), color = C.muted, fontSize = 12.sp)
    }
}

const val SITE = "https://pgragnic.github.io/programme-force-android/"

/** Avertissement santé, à accepter au premier lancement. */
@Composable
fun HealthNotice() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val p = remember { ctx.getSharedPreferences("force", android.content.Context.MODE_PRIVATE) }
    var ok by remember { mutableStateOf(p.getBoolean("avert", false)) }
    if (ok) return
    AlertDialog(onDismissRequest = {}, containerColor = C.surface,
        title = { Text("Avant de commencer", color = C.ink, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(t("UpRep propose des exercices de renforcement musculaire. Ce n'est pas un dispositif médical et elle ne remplace pas l'avis d'un médecin.", "UpRep offers strength training exercises. It is not a medical device and does not replace medical advice."), color = C.ink)
            Text("Demande l'avis d'un médecin avant de commencer si tu as un problème cardiaque, une tension élevée, une blessure, des douleurs articulaires ou dorsales, si tu es enceinte, ou si tu reprends le sport après une longue pause.", color = C.ink, modifier = Modifier.padding(top = 8.dp))
            Text("Arrête immédiatement en cas de douleur, de vertige, d'essoufflement inhabituel ou de malaise. Choisis des charges que tu maîtrises et garde une bonne technique.", color = C.ink, modifier = Modifier.padding(top = 8.dp))
            Text("Appli réservée aux personnes de 16 ans et plus.", color = C.muted, modifier = Modifier.padding(top = 8.dp))
        } },
        confirmButton = { Button(onClick = { p.edit().putBoolean("avert", true).apply(); ok = true }) { Text("J'ai compris") } })
}

@Composable
fun LegalPanel() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun open(u: String) = runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))) }
    Panel {
        Text("Santé et confidentialité", color = C.ink, fontWeight = FontWeight.Bold)
        Text(t("UpRep n'est pas un dispositif médical. En cas de doute sur ta santé, demande l'avis d'un médecin avant de t'entraîner, et arrête en cas de douleur ou de malaise.", "UpRep is not a medical device. If in doubt about your health, ask a doctor before training, and stop if you feel pain or unwell."), color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Row {
            TextButton(onClick = { open(SITE + "confidentialite.html") }, contentPadding = PaddingValues(0.dp)) { Text("Politique de confidentialité") }
            Spacer(Modifier.width(16.dp))
            TextButton(onClick = { open(SITE + "suppression.html") }, contentPadding = PaddingValues(0.dp)) { Text("Supprimer mon compte") }
        }
    }
}

/** Avis et idées d'amélioration envoyés au développeur. */
@Composable
fun FeedbackPanel() {
    val user by Store.user.collectAsState(); val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("idee") }; var txt by remember { mutableStateOf("") }
    var contact by remember(user) { mutableStateOf(user ?: "") }; var msg by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    Panel {
        Text("Ton avis compte", color = C.ink, fontWeight = FontWeight.Bold)
        Text("Une idée d'amélioration, un problème, un exercice qui manque ? Écris-le ici : chaque message est lu.", color = C.muted, fontSize = 13.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("idee" to "Idée", "probleme" to "Problème", "avis" to "Commentaire").forEach { (k, t) ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (kind == k) C.ink else C.bg).clickable { kind = k }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(t, color = if (kind == k) C.bg else C.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        OutlinedTextField(txt, { if (it.length <= 2000) txt = it }, label = { Text("Ton message") }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedTextField(contact, { contact = it }, label = { Text("E-mail pour te répondre (facultatif)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Button(onClick = { busy = true; scope.launch { msg = Store.sendFeedback(kind, txt, contact) ?: "Merci ! Ton message est bien arrivé.".also { txt = "" }; busy = false } },
            enabled = !busy && txt.trim().length >= 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (busy) "Envoi…" else "Envoyer") }
        msg?.let { Text(it, color = C.muted, fontSize = 13.sp) }
        // messages déjà envoyés avec ce compte, et la réponse quand il y en a une
        var mine by remember { mutableStateOf<List<MyRetour>>(emptyList()) }
        LaunchedEffect(user, msg) { mine = Store.myFeedback() }
        if (mine.isNotEmpty()) {
            Text(t("Tes messages", "Your messages"), color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            mine.take(5).forEach { r ->
                Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(C.bg).padding(10.dp)) {
                    Text(r.created_at.take(10).split("-").reversed().joinToString("/") + " · " + r.texte.take(140), color = C.ink, fontSize = 13.sp)
                    Text(r.reponse?.let { t("Réponse : ", "Reply: ") + it } ?: t("En attente de réponse", "Awaiting reply"),
                        color = if (r.reponse != null) C.rest else C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Durée maximale des séances (demande de Philippe : « Actuellement les séances sont à 40 minutes », choix « Plafond au choix dans Infos »). */
@Composable
fun DureePanel() {
    val g by GearStore.g.collectAsState(); val ctx = androidx.compose.ui.platform.LocalContext.current
    Panel {
        Text(t("Durée des séances", "Workout length"), color = C.ink, fontWeight = FontWeight.Bold)
        Text(t("Si une séance dépasse, l'appli retire une série au gainage puis aux petits exercices, jamais en dessous de 2 séries ; les gros exercices et tes zones à travailler passent en dernier.",
            "If a workout runs over, the app drops a set from core work, then from smaller exercises, never below 2 sets; big lifts and your focus areas come last."), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        val opts = listOf(30, 35, 40, 45, null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            opts.forEach { m ->
                val on = g.maxMin == m
                Text(m?.let { "$it min" } ?: t("Sans limite", "No limit"), color = if (on) C.bg else C.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                    modifier = Modifier.weight(if (m == null) 1.6f else 1f).clip(RoundedCornerShape(50)).background(if (on) C.ink else C.bg).clickable { GearStore.save(ctx, g.copy(maxMin = m)) }.padding(vertical = 9.dp))
            }
        }
    }
}

/** Export de toutes ses données (droit à la portabilité du RGPD). */
@Composable
fun ExportPanel() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    fun done(ok: Boolean) { msg = if (ok) t("Fichier enregistré.", "File saved.") else t("Enregistrement impossible.", "Couldn't save the file.") }
    val jsonL = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { u -> if (u != null) done(Export.write(ctx, u, Export.json())) }
    val csvL = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { u -> if (u != null) done(Export.write(ctx, u, Export.csv())) }
    val day = java.time.LocalDate.now().toString()
    Panel {
        Text(t("Mes données", "My data"), color = C.ink, fontWeight = FontWeight.Bold)
        Text(t("Télécharge toutes tes données : séances, journées, matériel et profil (JSON), ou tes séances pour un tableur (CSV).",
            "Download all your data: workouts, days, equipment and profile (JSON), or your workouts for a spreadsheet (CSV)."), color = C.muted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { jsonL.launch("uprep-$day.json") }, modifier = Modifier.weight(1f)) { Text(t("Tout (JSON)", "All (JSON)")) }
            OutlinedButton(onClick = { csvL.launch("uprep-seances-$day.csv") }, modifier = Modifier.weight(1f)) { Text(t("Séances (CSV)", "Workouts (CSV)")) }
        }
        msg?.let { Text(it, color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

