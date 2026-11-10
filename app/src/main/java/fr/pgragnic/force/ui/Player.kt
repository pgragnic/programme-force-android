package fr.pgragnic.force.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.*
import fr.pgragnic.force.gl.Body3D
import fr.pgragnic.force.gl.BodyState
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Moteur de la séance guidée : horloge murale (continue même si l'écran s'éteint). */
class Session(val c: Int, val w: Int, val s: String, val P: List<Phase>, val voice: Voice) {
    var pi by mutableIntStateOf(0)
    var pt by mutableDoubleStateOf(0.0)
    var run by mutableStateOf(true)
    var done by mutableStateOf(false)
    val sets = HashMap<String, MutableList<SetRec>>()
    val nsets = Prog.WEEKS[w].s
    /** nombre de séries prévues pour un exercice (une de plus pour les zones à travailler) */
    fun nsetsOf(id: String) = P.count { it.k == "work" && it.ex == id }.takeIf { it > 0 } ?: nsets
    val nex = P.count { it.k == "work" && it.set == 1 }
    private var last = SystemClock.elapsedRealtime()
    private var lastCue: String? = null
    var elapsed by mutableDoubleStateOf(0.0)
    val totalDur = P.sumOf { it.dur }

    var started = false; private set
    fun start() { if (started) return; started = true; last = SystemClock.elapsedRealtime(); enter(0) }

    fun enter(i: Int) {
        pi = i.coerceIn(0, P.size - 1); pt = 0.0
        val p = P[pi]
        if (p.k == "end") { done = true; voice.say("Séance terminée. Bravo"); return }
        val prev = P.getOrNull(pi - 1)
        if (run) when {
            p.k == "work" -> voice.buzz(350)
            (p.k == "rest" || p.k == "switch") && prev?.k == "work" -> voice.buzz(120, 80, 120)
        }
        when (p.k) {
            "switch" -> { voice.beep(1); voice.say((if (p.ei == 0) "Premier exercice : " else "Exercice suivant : ") + p.n + if (p.chg != null) ". Change les disques." else "") }
            "work" -> { voice.beep(2); val c = Prog.cueAt(p, 0.0); lastCue = c?.first
                // la série est annoncée à la fin de la pause ; au départ, seulement le premier repère (« Monte »), sinon « Go »
                val pre = P.getOrNull(pi - 1)?.let { (it.k == "rest" || it.k == "switch") && it.dur > 8 } == true
                voice.say(if (c != null && c.second.isNotBlank()) (if (pre) c.second else (if (p.set == 1) "Go. " else t("Série ${p.set}. ", "Set ${p.set}. ")) + c.second) else if (p.set == 1) "Go" else t("Série ${p.set}", "Set ${p.set}")) }
            "warm" -> { voice.beep(2); voice.say((if (prev?.k == "warm") "" else "Échauffement. ") + p.n) }
            "cool" -> { voice.beep(2); voice.say((if (prev?.k == "cool") "" else "Retour au calme. ") + p.n) }
            "rest" -> { voice.beep(1); voice.say("Pause") }
            else -> voice.beep(1)
        }
        // pré-remplit la série qui vient de se terminer
        if (prev?.k == "work" && prev.ex != null) slot(prev.ex!!, prev.set, tgtOf(prev))
    }

    fun tgtOf(p: Phase) = if (p.m == "time") p.dur.toInt() else p.reps
    fun slot(id: String, set: Int, tgt: Int): SetRec {
        val a = sets.getOrPut(id) { mutableListOf() }
        while (a.size < set) a += SetRec(tgt, null)
        return a[set - 1]
    }
    fun setSlot(id: String, set: Int, v: SetRec) { slot(id, set, v.r); sets[id]!![set - 1] = v }

    fun tick() {
        val now = SystemClock.elapsedRealtime()
        var dt = (now - last) / 1000.0; last = now
        if (!run || done) return
        if (dt > 2.5) { // rattrapage après mise en veille
            while (dt > 0 && !done) { val p = P[pi]; val rem = p.dur - pt; if (dt < rem) { pt += dt; dt = 0.0 } else { dt -= rem; elapsed += rem; quietEnter(pi + 1) } }
            return
        }
        val p = P[pi]; val prev = pt; pt += dt; elapsed += dt
        for (s in 3 downTo 1) if (prev < p.dur - s && pt >= p.dur - s) { voice.beep(0); if (p.k != "work") voice.buzz(60) }
        if (p.m == "uni") { val r = (p.dur - Prog.SWAP) / 2 + Prog.SWAP
            for (s in 3 downTo 1) if (prev < r - s && pt >= r - s) voice.beep(0)
            if (prev < r && pt >= r) { voice.beep(2); voice.buzz(350) } }
        // étirements d'un seul côté : changement de côté à mi-temps (Philippe : « Étirement quadriceps n'est que d'un côté »)
        if (p.k == "cool" && Prog.sides(p.pose) && prev < p.dur / 2 && pt >= p.dur / 2) { voice.beep(2); voice.buzz(350); voice.say(t("Change de côté", "Switch sides")) }
        if ((p.k == "rest" || p.k == "switch") && p.dur > 15 && prev < p.dur - 10 && pt >= p.dur - 10) voice.say("Dix secondes. Prépare-toi")
        P.getOrNull(pi + 1)?.let { nx -> if ((p.k == "rest" || p.k == "switch") && nx.k == "work" && p.dur > 8 && prev < p.dur - 6 && pt >= p.dur - 6)
            voice.say((if (nx.set == 1) t("Première série", "First set") else t("Série ${nx.set}", "Set ${nx.set}")) + if (nx.m == "uni") t(", côté gauche", ", left side") else "") }
        if (p.k == "work" && pt < p.dur) { val c = Prog.cueAt(p, pt); if (c != null && c.first != lastCue) { lastCue = c.first; voice.say(c.second) } }
        if (pt >= p.dur) enter(pi + 1)
    }

    private fun quietEnter(i: Int) { val v = voice.on; voice.on = false; enter(i); voice.on = v }

    fun next() { elapsed += max(0.0, P[pi].dur - pt); enter(pi + 1) }
    fun prev() { enter(if (pt > 3 || pi == 0) pi else pi - 1) }
    fun toggle() { run = !run; last = SystemClock.elapsedRealtime() }

    /** k d'animation, côté et libellé du mouvement en cours. */
    fun anim(): Triple<Double, Boolean, String> {
        val q = P[pi]
        if (q.k == "work") {
            if (q.m == "time") return Triple(0.0, false, "TIENS")
            val (h, lt, gap) = if (q.m == "uni") Prog.uniPos(q, pt) else Triple(0, pt, false)
            if (gap) return Triple(0.0, true, t("CHANGE DE CÔTÉ", "SWITCH SIDES"))
            val k = Prog.cycleK(lt, q.tp!!)
            val ph = lt % q.rt; val tp = q.tp
            val mv = if (q.m == "alt") (if (floor(pt / q.rt).toInt() % 2 == 1) "DROITE" else "GAUCHE")
            else if (ph < tp[0]) (if (q.inv) "DESCENDS" else "MONTE") else if (ph < tp[0] + tp[1]) "TIENS" else if (ph < tp[0] + tp[1] + tp[2]) (if (q.inv) "MONTE" else "DESCENDS") else ""
            val side = if (q.m == "uni") (if (h == 0) "GAUCHE · " else "DROIT · ") else ""
            // alterné (bird dog, dead bug, halo) : une répétition sur deux en miroir, du côté annoncé (Philippe : captures du bird dog,
            // « GAUCHE » puis « DROITE » avec le même bras levé)
            val alt = q.m == "alt" && floor(pt / q.rt).toInt() % 2 == 1
            return Triple(k, h == 1 || alt, side + mv)
        }
        if (q.k == "warm") {
            // échauffement : les mouvements alternés ou d'un seul côté (bird dog, fente) changent de côté à chaque répétition
            // (Philippe : « Le bird dog à l'échauffement n'est que dans un sens »)
            val tp = q.tp ?: listOf(1.0, 0.0, 1.2, 0.0)
            val other = Prog.alternates(q.pose) && floor(pt / tp.sum().coerceAtLeast(.1)).toInt() % 2 == 1
            return Triple(Prog.cycleK(pt, tp), other, "")
        }
        if (q.k == "cool" && Prog.sides(q.pose)) return Triple(0.0, pt >= q.dur / 2, if (pt >= q.dur / 2) t("CÔTÉ DROIT", "RIGHT SIDE") else t("CÔTÉ GAUCHE", "LEFT SIDE"))
        q.ghostTp?.let { return Triple(Prog.cycleK(pt, it), false, "") }
        return Triple(0.0, false, "")
    }

    /** répétition en cours et objectif (null hors répétitions : maintien, pause, changement de côté) */
    fun repNum(): Pair<Int, Int>? {
        val q = P[pi]; if (q.k != "work" || q.m == "time") return null
        if (q.m == "alt") return min(q.reps * 2, floor(pt / q.rt).toInt() + 1) to q.reps * 2
        val (_, lt, gap) = if (q.m == "uni") Prog.uniPos(q, pt) else Triple(0, pt, false)
        if (gap) return null
        return min(q.reps, floor(lt / q.rt).toInt() + 1) to q.reps
    }
    /** exercice en cours (0 pour l'échauffement) */
    fun exIdx(): Int { for (i in pi downTo 0) if (P[i].ei >= 0) return P[i].ei; return 0 }

    fun repTxt(): String {
        val q = P[pi]; if (q.k != "work" || q.m == "time") return ""
        if (q.m == "alt") { val n = floor(pt / q.rt).toInt(); return t("Rép ${min(q.reps * 2, n + 1)}/${q.reps * 2} · alterné", "Rep ${min(q.reps * 2, n + 1)}/${q.reps * 2} · alternating") }
        val (_, lt, gap) = if (q.m == "uni") Prog.uniPos(q, pt) else Triple(0, pt, false)
        if (gap) return t("Côté droit dans ${kotlin.math.ceil((q.dur - Prog.SWAP) / 2 + Prog.SWAP - pt).toInt()} s", "Right side in ${kotlin.math.ceil((q.dur - Prog.SWAP) / 2 + Prog.SWAP - pt).toInt()} s")
        return t("Rép ${min(q.reps, floor(lt / q.rt).toInt() + 1)}/${q.reps}", "Rep ${min(q.reps, floor(lt / q.rt).toInt() + 1)}/${q.reps}")
    }
}

// lecteur épuré (maquette « ③ Lecteur épuré » validée par Philippe : « Ok pour ces écrans ! ») : progression de la séance en haut,
// personnage en grand, un seul gros compteur, trois boutons
@Composable
fun PlayerScreen(ses: Session, onClose: () -> Unit, onEnd: () -> Unit) {
    LaunchedEffect(ses) { ses.start(); while (true) { withFrameNanos { }; ses.tick(); if (ses.done) { onEnd(); break } } }
    val p = ses.P[ses.pi]
    val col = C.phase(p.k)
    val (k, mir, mv) = ses.anim()
    val ex = ses.exIdx().coerceIn(0, max(0, ses.nex - 1))
    Column(Modifier.fillMaxSize().background(C.bg).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Close, fr.pgragnic.force.tr("Fermer"), tint = C.ink) }
            Row(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(ses.nex) { i ->
                    val c = when { i < ex || (i == ex && p.k == "cool") -> C.effort; i == ex && p.k != "warm" -> C.effort.copy(alpha = .4f); else -> C.line }
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c))
                }
            }
            Text(if (p.k == "warm") t("Échauff.", "Warm-up") else if (p.k == "cool") t("Calme", "Cool") else "${ex + 1} / ${ses.nex}", color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { ses.voice.on = !ses.voice.on }, modifier = Modifier.size(44.dp)) {
                Icon(if (ses.voice.on) Icons.Default.VolumeUp else Icons.Default.VolumeOff, fr.pgragnic.force.tr("Son"), tint = C.muted)
            }
        }
        Text((if (p.k == "switch") (if (p.ei == 0) "PREMIER EXERCICE" else "NOUVEL EXERCICE") else PLAB[p.k] ?: "").let { fr.pgragnic.force.tr(it) },
            color = col, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
        Text(p.n, color = C.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Text(p.det.ifBlank { p.blk }, color = C.muted, fontSize = 14.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 8.dp))
        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 10.dp).clip(RoundedCornerShape(22.dp))) {
            val ghost = p.k in listOf("prep", "rest", "switch")
            Body3D(BodyState(pose = p.pose ?: "calf", k = k, mirror = mir, ghost = ghost, nodb = p.nodb || p.pose == null, hl = if (p.k == "prep") null else (p.ex ?: p.pose), ex = p.ex).th(), Modifier.fillMaxSize())
        }
        val prevW = ses.P.getOrNull(ses.pi - 1)
        val left = ceil(max(0.0, p.dur - ses.pt)).toInt()
        if (prevW?.k == "work" && p.k in listOf("rest", "switch", "cool") && prevW.ex != null) {
            SetPanel(ses, prevW)
            Text(t("Reprise dans $left s", "Back in $left s"), color = col, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        } else {
            val rn = ses.repNum()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                if (rn != null) {
                    Text("${rn.first}", color = C.ink, fontSize = 64.sp, fontWeight = FontWeight.Black, lineHeight = 66.sp)
                    Text(" / ${rn.second}", color = C.muted, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                } else Text("$left", color = col, fontSize = 64.sp, fontWeight = FontWeight.Black, lineHeight = 66.sp)
            }
            val sub = if (mv.isNotBlank()) mv else if (rn == null && ses.repTxt().isNotBlank()) ses.repTxt() else ""
            if (sub.isNotBlank()) Text(sub, color = col, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            val tip = if (p.k == "rest") (p.chg ?: "") else p.tip
            if (tip.isNotBlank()) Text(tip, color = if (p.chg != null && p.k != "work") C.warm else C.muted, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        LinearProgressIndicator(progress = { (ses.pt / max(1.0, p.dur)).toFloat().coerceIn(0f, 1f) }, color = col, trackColor = C.line, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp).height(6.dp).clip(RoundedCornerShape(3.dp)))
        Text(t("Fin dans ${Prog.fmt(ses.totalDur - ses.elapsed)}", "Ends in ${Prog.fmt(ses.totalDur - ses.elapsed)}"), color = C.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(Icons.Default.SkipPrevious, "Précédent") { ses.prev() }
            FilledIconButton(onClick = { ses.toggle() }, modifier = Modifier.size(76.dp), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = C.effort)) {
                Icon(if (ses.run) Icons.Default.Pause else Icons.Default.PlayArrow, fr.pgragnic.force.tr(if (ses.run) "Pause" else "Reprendre"), tint = C.onColor, modifier = Modifier.size(34.dp))
            }
            RoundBtn(Icons.Default.SkipNext, "Suivant") { ses.next() }
        }
    }
}

@Composable
private fun RoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(54.dp).border(1.dp, C.line, CircleShape)) { Icon(icon, label, tint = C.ink) }
}

/** Panneau « série par série » affiché pendant la pause qui suit une série. */
@Composable
private fun SetPanel(ses: Session, pw: Phase) {
    val id = pw.ex!!; val tgt = ses.tgtOf(pw); val time = pw.m == "time"
    var cur by remember(id, pw.set) { mutableStateOf(ses.slot(id, pw.set, tgt)) }
    fun upd(v: SetRec) { cur = v; ses.setSlot(id, pw.set, v) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(t("${Prog.EX[id]?.n} · série ${pw.set}/${ses.nsetsOf(id)}  ·  objectif $tgt", "${tr(Prog.EX[id]?.n ?: "")} · set ${pw.set}/${ses.nsetsOf(id)}  ·  goal $tgt") + if (time) " s" else " reps", color = C.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(if (time) "Secondes tenues" else if (Prog.EX[id]?.m == "uni") "Répétitions par côté" else "Répétitions faites", color = C.muted, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { upd(cur.copy(r = max(0, cur.r - if (time) 5 else 1))) }) { Text("−", fontSize = 20.sp) }
            Text("${cur.r}", color = C.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
            OutlinedButton(onClick = { upd(cur.copy(r = min(300, cur.r + if (time) 5 else 1))) }) { Text("+", fontSize = 20.sp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Column(Modifier.weight(1f)) { Text("Encore possibles", color = C.muted); Text("répétitions en réserve", color = C.muted, fontSize = 11.sp) }
            listOf(0, 1, 2, 3).forEach { v ->
                val on = cur.res != null && min(3, cur.res!!) == v
                Box(Modifier.padding(2.dp).size(44.dp).clip(RoundedCornerShape(8.dp)).background(if (on) C.ink else C.bg).border(1.dp, C.line, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center) {
                    TextButton(onClick = { upd(cur.copy(res = if (on) null else v)) }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (v == 3) "3+" else "$v", color = if (on) C.bg else C.ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
