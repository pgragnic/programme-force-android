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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glam(26).padding(16.dp), content = content)
}

/** Sélecteur segmenté façon Apple Fitness : piste pleine, segment choisi en pastille plus claire (check gardé pour compatibilité, sans effet). */
@Composable
fun Seg(items: List<Pair<String, Boolean>>, modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") check: Boolean = true, small: Boolean = false, onClick: (Int) -> Unit) {
    Row(modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(C.s.surfaceContainerLow).border(1.dp, C.rim, RoundedCornerShape(21.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, (lab, on) ->
            // largeur selon la longueur du libellé (« Comme le téléphone » plus large que « Clair »)
            Box(Modifier.weight(maxOf(1f, lab.length / 7f)).fillMaxHeight().clip(RoundedCornerShape(17.dp)).then(if (on) Modifier.background(goldBrush) else Modifier).clickable { onClick(i) }.padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center) {
                Text(lab, color = if (on) C.onGold else C.muted, fontWeight = FontWeight.Bold, fontSize = if (small) 12.sp else 14.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Titre d'écran « Glamour » : Playfair italique, dégradé or rose (sombre) ou cuivre (clair). */
@Composable
fun H1(text: String, modifier: Modifier = Modifier, size: Int = 36) = Text(text, fontSize = size.sp, lineHeight = (size + 6).sp, fontWeight = FontWeight.SemiBold,
    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontFamily = Serif, modifier = modifier,
    style = androidx.compose.ui.text.TextStyle(brush = androidx.compose.ui.graphics.Brush.linearGradient(
        if (C.dark) listOf(Color(0xFFF7D6C8), Color(0xFFD9A28F), Color(0xFFF3C9B6)) else listOf(Color(0xFF8E4F3A), Color(0xFFC98B76), Color(0xFF9A5A44)))))

/** Anneau de progression façon Apple Fitness (piste à 25 %, arc arrondi). */
@Composable
fun Ring(frac: Float, color: Color, size: androidx.compose.ui.unit.Dp, width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) {
    val w = width.toPx(); val tl = androidx.compose.ui.geometry.Offset(w / 2, w / 2); val sz = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
    drawArc(color.copy(alpha = .25f), 0f, 360f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    if (frac > 0f) drawArc(color, -90f, 360f * frac.coerceIn(0f, 1f), false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

@Composable
fun Eyebrow(t: String) = Text(fr.pgragnic.force.tr(t).uppercase(), color = C.muted, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)

@Composable
fun PChip(label: String, on: Boolean, sub: String? = null, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) C.sel else Color.Transparent).border(1.dp, if (on) C.sel else C.line, RoundedCornerShape(8.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (on) C.onSel else C.ink, fontWeight = FontWeight.Medium)
        if (sub != null) Text(sub, color = if (on) C.onSel else C.brand, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

/** Pastille de semaine compacte : « S1 » sur une ligne, puis 4 points (séances faites) sur une ligne. */
@Composable
fun WeekChip(w: Int, n: Int, on: Boolean, modifier: Modifier, total: Int = 4, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(if (on) C.sel else Color.Transparent).border(1.dp, if (on) C.sel else C.line, RoundedCornerShape(8.dp))
        .clickable(onClick = onClick).padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(t("S${w + 1}", "W${w + 1}"), color = if (on) C.onSel else C.ink, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, softWrap = false)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(if (total > 4) 1.5.dp else 2.dp)) {
            val c = if (on) C.onSel else C.brand
            // autant de points que de séances prévues par semaine (Philippe, 5 séances choisies et 4 points affichés : « J'ai choisi 5 séances »)
            repeat(total) { i -> Box(Modifier.size(if (total > 4) 4.dp else 5.dp).clip(CircleShape).then(if (i < n) Modifier.background(c) else Modifier.border(1.dp, c, CircleShape))) }
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
    remember(log, gear, st) { Prog.computeLoads(log, st); 0 }
    val done = remember(log) { log.map { "${it.c}-${it.w}-${it.s}" }.toSet() }
    val S = Prog.sess(st.c); val W = Prog.WEEKS[st.w]
    // séance E ou F sélectionnée puis moins de séances par semaine : on revient à une séance existante
    if (st.s !in S) { LaunchedEffect(st.s, gear.perWeek) { Store.setSettings { it.s = Prog.nextSess(Store.st.value, Store.log.value, Store.days.value) } }; return }
    var weeks by remember { mutableStateOf(false) }; var sel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(log) { Store.rollWeek() }
    val order = remember(st.c, st.w, st.s, log, gear) { Prog.orderEx(st.c, st.s, st.w, st) }
    val P = remember(st.c, st.w, st.s, st.rest, log, gear) { Prog.phases(st.c, st.w, st.s, st.rest, st, log) }
    val nSets = P.count { it.k == "work" }
    // muscles pas encore récupérés sollicités par la séance (Philippe : « Et si je souhaite faire une séance par jour ? », choix « 3 » :
    // alerte avant de démarrer, avec une autre séance proposée)
    val days by Store.days.collectAsState()
    val MU = remember(log, days) { Recovery.muscles(log, days) }
    fun tired(ids: List<String>) = Prog.tired(ids, MU.R)
    val (share, sore) = remember(order, MU) { tired(order) }
    // muscles surchargés (au-delà de 0 %) travaillés en principal par cette séance : alerte franche
    val over = remember(order, MU) { order.flatMap { id -> Prog.MAP[id]?.filter { it.value >= .7 }?.keys.orEmpty() }.distinct().filter { MU.over(it) } }
    val alt = remember(st.c, st.w, MU, gear) { S.keys.filter { it != st.s }.map { it to tired(Prog.orderEx(st.c, it, st.w, st)).first }.minByOrNull { it.second }?.takeIf { it.second < share - .2 }?.first }
    var warn by remember { mutableStateOf(false) }
    if (warn) AlertDialog(onDismissRequest = { warn = false }, containerColor = C.surface,
        title = { Text(t("Muscles pas encore récupérés", "Muscles not recovered yet"), color = C.ink, fontWeight = FontWeight.Bold) },
        text = { Text((if (over.isNotEmpty()) t("Surchargés : ", "Overloaded: ") + over.joinToString(", ") { tr(it) } + t(". Les retravailler maintenant freine leur récupération ; mieux vaut une autre séance ou du repos. ", ". Working them now slows their recovery; better pick another workout or rest. ") else "") +
            (if (sore.isNotEmpty()) t("Cette séance sollicite surtout ", "This workout mostly works ") + sore.joinToString(", ") { "${tr(it)} (${MU.R[it]} %)" } + ". " else "") +
            (alt?.let { t("La séance $it (${tr(S[it]!!.t)}) les ménage davantage.", "Workout $it (${tr(S[it]!!.t)}) is easier on them.") } ?: t("Un jour de repos ou une activité douce leur ferait du bien.", "A rest day or light activity would help them.")),
            color = C.muted) },
        confirmButton = { Column(horizontalAlignment = Alignment.End) {
            alt?.let { a -> TextButton(onClick = { warn = false; Store.setSettings { it.s = a } }) { Text(t("Passer à la séance $a", "Switch to workout $a"), color = C.brand, fontWeight = FontWeight.Bold) } }
            TextButton(onClick = { warn = false; onStart() }) { Text(t("Démarrer quand même", "Start anyway"), color = C.ink) }
            TextButton(onClick = { warn = false }) { Text(t("Annuler", "Cancel"), color = C.muted) }
        } })
    // muscles les plus sollicités par la séance
    val mus = remember(order) { val m = HashMap<String, Double>(); order.forEach { id -> Prog.MAP[id]?.forEach { (k, v) -> m[k] = (m[k] ?: 0.0) + v } }; m.entries.sortedByDescending { it.value }.take(4).map { it.key } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val nWk = remember(log) { val m = Prog.mondayOf(System.currentTimeMillis()); log.count { it.d >= m } }
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) {
            val today = remember(fr.pgragnic.force.L.en) { java.text.SimpleDateFormat("EEEE d MMMM", fr.pgragnic.force.L.loc).format(java.util.Date()).uppercase(fr.pgragnic.force.L.loc) }
            Text(today, color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            H1(t("Ta prochaine séance", "Your next workout"), size = 30)
        }; HelpButton("seance") }
        // anneaux de la semaine façon Apple Fitness : séances faites cette semaine (indigo), avancée dans le cycle (vert)
        Row(Modifier.fillMaxWidth().glam().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Ring(nWk / gear.perWeek.toFloat(), C.brand, 86.dp, 12.dp)
                Ring((st.w + 1) / 8f, C.gold2, 60.dp, 11.dp)
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(t("Séances", "Workouts"), color = C.brand, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$nWk", color = C.ink, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 32.sp)
                    Text("/${gear.perWeek}", color = C.muted, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Text(t(" cette semaine", " this week"), color = C.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
                Text(t("Semaine ${st.w + 1}", "Week ${st.w + 1}"), color = C.gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                Text(t("Cycle ${st.c} · sur 8", "Cycle ${st.c} · of 8"), color = C.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        // sélecteur compact : semaine (déplie cycles et semaines) et séance
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(42.dp).glam(21).clickable { weeks = !weeks }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(t("Semaine ${st.w + 1}", "Week ${st.w + 1}"), color = C.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Icon(if (weeks) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = C.muted, modifier = Modifier.size(18.dp))
            }
            // boutons segmentés Material 3 ; le ✓ marque une séance déjà faite cette semaine
            val keys = S.keys.toList()
            Seg(keys.map { s -> (s + if ("${st.c}-${st.w}-$s" in done) (if (S.size > 4) "✓" else " ✓") else "") to (st.s == s) }, Modifier.weight(1f), check = false, small = S.size > 4) { i -> Store.setSettings { it.s = keys[i] } }
        }
        // plus (ou moins) de séances par semaine que les 4 séances A à D : on dit comment l'appli s'y prend
        if (gear.perWeek != S.size) Text(if (gear.perWeek > S.size)
            t("${gear.perWeek} séances par semaine : après A, B, C et D, l'appli te repropose la séance qui ménage le mieux tes muscles.", "${gear.perWeek} workouts a week: after A, B, C and D, the app suggests again the workout that best spares your muscles.")
            else t("${gear.perWeek} séances par semaine : l'appli propose à chaque fois celle qui ménage le mieux tes muscles.", "${gear.perWeek} workouts a week: each time, the app suggests the one that best spares your muscles."),
            color = C.muted, fontSize = 12.sp, lineHeight = 16.sp)
        if (weeks) Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                (1..2).forEach { c -> PChip("Cycle $c", st.c == c) { Store.setSettings { it.c = c } } }
                Text(if (st.c % 2 == 0) "variantes" else "exercices de base", color = C.muted, fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                (0 until 8).forEach { w ->
                    val n = log.count { it.c == st.c && it.w == w }.coerceAtMost(gear.perWeek)
                    WeekChip(w, n, st.w == w, Modifier.weight(1f), gear.perWeek) { Store.setSettings { it.w = w } }
                }
            }
            Text(tr(W.t) + " · " + t("${W.s} séries × ${W.r} reps · tempo ${W.tp.take(3).joinToString("-") { Prog.fk(it) }}", "${W.s} sets × ${W.r} reps · tempo ${W.tp.take(3).joinToString("-") { Prog.fk(it) }}"),
                color = C.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Text(W.note, color = C.muted, fontSize = 12.sp)
        }
        // carte de la séance
        Column(Modifier.fillMaxWidth().glam().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(t("Séance ${st.s}", "Workout ${st.s}"), color = C.brand, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("≈ ${((P.sumOf { it.dur } + 59) / 60).toInt()} min", color = C.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            Text(tr(S[st.s]!!.t), color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, lineHeight = 29.sp)
            if (mus.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // puces façon Fitness ; un muscle surchargé ressort en rouge
                mus.forEach { m -> val o = m in over
                    Text(tr(m), color = if (o) Color(0xFFFF6961) else C.brand, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).then(if (o) Modifier.background(Color(0xFFE3141E).copy(alpha = .22f)) else Modifier.border(1.dp, C.brand.copy(alpha = .4f), RoundedCornerShape(16.dp))).padding(horizontal = 13.dp, vertical = 6.dp)) }
            }
            // gros chiffres façon Fitness
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                listOf(Triple("${order.size}", t("exercices", "exercises"), C.ink), Triple("$nSets", t("séries", "sets"), C.ink), Triple("${W.r}", t("reps", "reps"), C.ink)).forEach { (v, l, c) ->
                    Column { Text(v, color = c, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 26.sp); Text(l, color = C.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
                }
            }
            gear.maxMin?.let { m -> if (nSets < order.sumOf { id -> W.s + if (gear.focus(id)) 1 else 0 })
                Text(t("Raccourcie pour tenir en $m min.", "Shortened to fit in $m min."), color = C.muted, fontSize = 12.sp) }
            // alerte en bandeau rouge doux avec icône (maquette Material 3 du 14 novembre)
            if (over.isNotEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFE3141E).copy(alpha = .16f).compositeOver(C.surface)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Icon(Icons.Default.Block, null, tint = Color(0xFFE3141E), modifier = Modifier.padding(end = 10.dp, top = 1.dp).size(20.dp))
                Column {
                    Text(t("Surchargés : ", "Overloaded: ") + over.take(3).joinToString(", ") { tr(it) }, color = C.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(t("Mieux vaut une autre séance", "Better pick another workout"), color = C.muted, fontSize = 14.sp)
                }
            }
            else if (share >= .4) Text("⚠ " + t("Encore en récupération : ", "Still recovering: ") + sore.take(3).joinToString(", ") { tr(it) }, color = C.warm, fontSize = 13.sp)
            // pilule en dégradé or rose (maquette Glamour)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).tourTarget("start").clip(RoundedCornerShape(28.dp)).background(goldBrush)
                .clickable { if (share >= .4 || over.isNotEmpty()) warn = true else onStart() }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = C.onGold); Spacer(Modifier.width(8.dp))
                Text(t("Démarrer", "Start"), color = C.onGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        // tous les exercices, chacun avec sa vignette du personnage (muscles travaillés en couleur) — inspiré de la capture de Philippe :
        // « Peux-tu t'inspirer de la première image pour la présentation des séances et on montre tous les exercices ? »
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Ce que tu vas faire", "What you'll do"), color = C.muted, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(t("${order.size} exercices", "${order.size} exercises"), color = C.muted, fontSize = 13.sp)
            }
            order.forEachIndexed { i, id ->
                val e = Prog.EX[id]!!; val wk = Prog.work(id, st.w)
                val line = "${P.count { it.k == "work" && it.ex == id }.takeIf { it > 0 } ?: W.s} × ${wk.label}" + Prog.load(id, st.w, st).takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                // toucher un exercice ouvre sa fiche (aide contextuelle, maquette ⑥)
                Row(Modifier.fillMaxWidth().tourTarget(if (i == 0) "info" else null).clickable { sel = id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExThumb(id, 96.dp, info = true)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(e.n, color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
                        Text(line, color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                HorizontalDivider(color = C.line, modifier = Modifier.padding(start = 110.dp))
                if (sel == id) ExSheet(id, line, order, swap = true) { sel = null }
            }
        }
    }
}

/** Vignette d'un exercice : le personnage 3D dans la pose du mouvement, muscles travaillés en couleur (rendu une fois, gardé en cache). */
@Composable
fun ExThumb(id: String, size: androidx.compose.ui.unit.Dp, info: Boolean = false) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pose = Prog.EX[id]?.pose ?: id
    val st = fr.pgragnic.force.gl.BodyState(pose = pose, ex = id, hl = id, k = .55, thumb = true).th()
    val key = "$id-${if (C.dark) "s" else "c"}-${BuildConfig.VERSION_CODE}-${Prog.version}-${remember(pose, Prog.version) { Prog.raw["POSES3"]?.jsonObject?.get(pose)?.toString()?.hashCode() ?: 0 }}"
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val bm by produceState(fr.pgragnic.force.gl.Thumbs.cached(key), key) { value = fr.pgragnic.force.gl.Thumbs.get(ctx, key, st, px) }
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(C.stage)) {
        bm?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
        if (info) Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(24.dp).clip(CircleShape).background(C.brand), contentAlignment = Alignment.Center) {
            Text("i", color = C.onBrand, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
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
        H1("Bravo !")
        Text("Chaque série telle que notée pendant les pauses : répétitions et réserve (R, touche pour changer). Corrige si besoin.", color = C.muted, modifier = Modifier.padding(vertical = 8.dp))
        SetsEditor(rows)
        OutlinedTextField(bw, { bw = it }, label = { Text("Poids du jour (facultatif, kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Button(onClick = {
            val ex = recsOf(rows); val b = bw.replace(',', '.').toDoubleOrNull()
            val e = Entry(d = System.currentTimeMillis(), c = ses.c, w = ses.w, s = ses.s, f = overall(ex), rest = st.rest, ex = ex, bw = b, kcal = Prog.sessKcal(ses.P, b ?: 80.0))
            Store.add(e)
            // prochaine séance : celle qui ménage le mieux les muscles qui viennent de travailler ; la semaine avance au calendrier (Store.rollWeek)
            val nx = Prog.nextSess(Store.st.value, Store.log.value, Store.days.value)
            Store.setSettings { it.s = nx }
            onDone()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Text("Enregistrer la séance", fontWeight = FontWeight.Medium, fontSize = 16.sp) }
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
            if (!inProgres) H1("Suivi")
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
            Row(verticalAlignment = Alignment.CenterVertically) { H1(t("Progrès", "Progress"), Modifier.weight(1f)); HelpButton("progres") }
            // ordre demandé par Philippe : « Je veux les onglets dans l'ordre activité, courbe, semaine »
            Seg(listOf(t("Activité", "Activity"), t("Courbes", "Charts"), t("Semaine", "Week")).mapIndexed { i, l -> l to (sub == i) }, Modifier.fillMaxWidth()) { sub = it }
        }
        Box(Modifier.weight(1f)) {
            when (sub) {
                0 -> ActiviteScreen(inProgres = true)
                1 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { ProgressPanel() }
                else -> SuiviScreen(inProgres = true)
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
            H1(REG.first { it.k == sub }.t())
            when (sub) {
                "prog" -> ProgrammePage()
                "rappels" -> RappelPanel()
                "compte" -> { AccountPanel(); ExportPanel() }
                "affichage" -> ThemePanel()
                "avis" -> FeedbackPanel()
                else -> {
                    Panel {
                        Text(t("Visite guidée", "Guided tour"), color = C.ink, fontWeight = FontWeight.Bold)
                        Text(t("Les bulles d'aide du premier lancement, à revoir quand tu veux.", "The first-launch help bubbles, to watch again whenever you like."), color = C.muted, fontSize = 13.sp)
                        TextButton(onClick = { sub = null; Tour.start() }) { Text(t("Revoir la visite guidée", "Watch the tour again"), color = C.brand, fontWeight = FontWeight.Bold) }
                    }
                    NewsPanel(); LegalPanel()
                    Panel {
                        Text("Comment ça marche", color = C.ink, fontWeight = FontWeight.Bold)
                        Text("8 semaines, 4 séances (A, B, C, D) à répartir dans la semaine, adaptées à ton matériel. Pendant chaque pause, note les répétitions faites et combien tu aurais encore pu en faire : la charge monte quand c'est réussi plusieurs fois, et baisse après deux échecs.", color = C.muted)
                        Text(t("Version ${BuildConfig.VERSION_NAME}. Personnage : MakeHuman (domaine public, CC0). Muscles : atlas BodyParts3D, © The Database Center for Life Science (CC BY-SA 2.1 JP).", "Version ${BuildConfig.VERSION_NAME}. Character: MakeHuman (public domain, CC0). Muscles: BodyParts3D atlas, © The Database Center for Life Science (CC BY-SA 2.1 JP)."), color = C.muted, fontSize = 12.sp)
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
        Row(verticalAlignment = Alignment.CenterVertically) { H1(t("Réglages", "Settings"), Modifier.weight(1f)); HelpButton("reglages") }
        // encarts groupés façon Apple Fitness, icônes de couleur (choix de Philippe du 14 novembre, 11 h 15)
        listOf(REG.take(3), REG.drop(3)).forEach { grp ->
            Column(Modifier.fillMaxWidth().glam()) {
                grp.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = C.line, thickness = .5.dp, modifier = Modifier.padding(start = 54.dp))
                    Row(Modifier.fillMaxWidth().clickable { sub = r.k }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(C.brand.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(r.ic, null, tint = C.brand, modifier = Modifier.size(20.dp)) }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(r.t(), color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(subOf(r.k), color = C.muted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = C.muted)
                    }
                }
            }
        }
        if (user != null && sync.isNotBlank()) Text(sync, color = C.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("UpRep ${BuildConfig.VERSION_NAME}", color = C.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

class RegItem(val k: String, val fr: String, val en: String, val ic: androidx.compose.ui.graphics.vector.ImageVector, val c: Long = 0) { fun t() = t(fr, en); val col get() = if (c == 0L) C.brand else Color(c) }
val REG = listOf(
    RegItem("prog", "Mon programme", "My program", Icons.Default.FitnessCenter),
    RegItem("rappels", "Rappels", "Reminders", Icons.Default.NotificationsNone, 0xFFFF453A),
    RegItem("compte", "Compte et sauvegarde", "Account and backup", Icons.Default.CloudDone, 0xFF0A84FF),
    RegItem("affichage", "Affichage", "Display", Icons.Default.DarkMode, 0xFF8E8E93),
    RegItem("avis", "Donner mon avis", "Send feedback", Icons.Default.ChatBubbleOutline, 0xFF30D158),
    RegItem("apropos", "À propos et confidentialité", "About and privacy", Icons.Default.Info, 0xFF8E8E93))

/** Sous-page « Mon programme » : tout ce qui change les séances. */
@Composable
fun ProgrammePage() {
    val ctx0 = androidx.compose.ui.platform.LocalContext.current; fun ctxOf() = ctx0
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
    Panel {
        Text(t("Séances par semaine", "Workouts per week"), color = C.ink, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(3, 4, 5, 6).forEach { n -> val on = g.perWeek == n
                Text("$n", color = if (on) C.bg else C.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) C.ink else C.bg).clickable { GearStore.save(ctxOf(), g.copy(perWeek = n)) }.padding(vertical = 10.dp)) }
        }
        Text(t("La semaine du programme avance chaque lundi. La prochaine séance est choisie pour laisser récupérer les muscles déjà travaillés.",
            "The program week moves on every Monday. Your next workout is picked to let recently worked muscles recover."), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
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
    ProgressionPanel(st)
}

/** Séances réussies avant une hausse de charge, par groupe (Philippe, 14 novembre 2026 : « Est-ce qu'on peut prévoir que ce soit paramétrable ? »). */
@Composable
fun ProgressionPanel(st: Settings) {
    Panel {
        Text(t("Montée des charges", "Load increases"), color = C.ink, fontWeight = FontWeight.Bold)
        Text(t("Nombre de séances réussies avant d'ajouter du poids. Plus le chiffre est grand, plus la progression est prudente.",
            "Successful sessions before adding weight. The higher the number, the more cautious the progression."), color = C.muted, fontSize = 12.sp)
        listOf(Triple(t("Jambes", "Legs"), t("squats, fentes, mollets, et le rowing un bras appuyé", "squats, lunges, calves, plus the supported one-arm row"), "leg"),
            Triple(t("Bas du dos", "Lower back"), t("soulevés de terre, rowings penchés, développé debout, swing", "deadlifts, bent-over rows, standing press, swing"), "back"),
            Triple(t("Haut du corps et le reste", "Upper body and the rest"), t("pompes et développés couchés, épaules, bras, gainage", "push-ups and floor presses, shoulders, arms, core"), "top")).forEach { (n, ex, k) ->
            val cur = when (k) { "leg" -> st.upLeg; "back" -> st.upBack; else -> st.upTop }
            Text(n, color = C.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            Text(ex, color = C.muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { v -> val on = cur == v
                    Text("$v", color = if (on) C.bg else C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) C.ink else C.bg)
                            .clickable { Store.setSettings { when (k) { "leg" -> it.upLeg = v; "back" -> it.upBack = v; else -> it.upTop = v } } }.padding(vertical = 10.dp)) }
            }
        }
        Text(t("Une séance « Facile » avec l'objectif atteint compte double. Le plafond de hausse par semaine s'applique toujours.",
            "An \"Easy\" session with the target reached counts double. The weekly increase cap still applies."), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
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
        H1(title)
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
        val langs = listOf("auto" to "Comme le téléphone", "fr" to "Français", "en" to "English")
        Seg(langs.map { (m, tx) -> tr(tx) to (fr.pgragnic.force.L.mode == m) }, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), check = false, small = true) { i -> fr.pgragnic.force.L.save(ctx, langs[i].first); Store.devChanged() }
        Text("Apparence", color = C.ink, fontWeight = FontWeight.Bold)
        val modes = listOf("auto" to "Comme le téléphone", "light" to "Clair", "dark" to "Sombre")
        Seg(modes.map { (m, tx) -> tr(tx) to (C.mode == m) }, Modifier.fillMaxWidth().padding(top = 8.dp), check = false, small = true) { i -> C.save(ctx, modes[i].first); Store.devChanged() }
        // Material You (Philippe, 14 novembre : indigo par défaut + option « Couleurs du fond d'écran »)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Couleurs du fond d'écran", "Wallpaper colors"), color = if (C.dynOk) C.ink else C.muted, fontWeight = FontWeight.Bold)
                Text(if (C.dynOk) t("L'appli prend les couleurs de ton fond d'écran au lieu de l'indigo UpRep.", "The app uses your wallpaper colors instead of UpRep indigo.") else t("Demande Android 12 ou plus récent.", "Requires Android 12 or newer."), color = C.muted, fontSize = 12.sp)
            }
            Switch(checked = C.dyn && C.dynOk, enabled = C.dynOk, onCheckedChange = { C.saveDyn(ctx, it); Store.devChanged() })
        }
    }
}

/** Nouveautés de l'appli, la plus récente en premier. */
val NEWS = listOf(
    "14 novembre 2026" to listOf(
        "Montée des charges réglable par groupe (jambes, bas du dos, haut du corps) dans Réglages → Mon programme ; le bas du dos passe à 3 séances réussies avant une hausse."),
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
            Text(d, color = C.brand, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
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

