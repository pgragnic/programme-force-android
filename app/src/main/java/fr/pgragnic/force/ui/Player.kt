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
            "work" -> { voice.beep(2); val c = Prog.cueAt(p, 0.0); lastCue = c?.first; voice.say((if (p.set == 1) "Go" else "Série ${p.set}") + if (c != null && c.second.isNotBlank()) ". " + c.second else "") }
            "warm" -> { voice.beep(2); voice.say((if (prev?.k == "warm") "" else "Échauffement. ") + p.n) }
            "cool" -> { voice.beep(2); voice.say((if (prev?.k == "cool") "" else "Retour au calme. ") + p.n) }
            "rest" -> { voice.beep(1); voice.say("Pause") }
            else -> voice.beep(1)
        }
        // pré-remplit la série qui vient de se terminer
        if (prev?.k == "work" && prev.pose != null) slot(prev.pose!!, prev.set, tgtOf(prev))
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
        if (p.m == "uni" && prev < p.dur / 2 && pt >= p.dur / 2) voice.beep(2)
        if ((p.k == "rest" || p.k == "switch") && p.dur > 15 && prev < p.dur - 10 && pt >= p.dur - 10) voice.say("Dix secondes. Prépare-toi")
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
            val lt = if (q.m == "uni") pt % (q.dur / 2) else pt
            val k = Prog.cycleK(lt, q.tp!!)
            val ph = lt % q.rt; val tp = q.tp
            val mv = if (q.m == "alt") (if (floor(pt / q.rt).toInt() % 2 == 1) "DROITE" else "GAUCHE")
            else if (ph < tp[0]) (if (q.inv) "DESCENDS" else "MONTE") else if (ph < tp[0] + tp[1]) "TIENS" else if (ph < tp[0] + tp[1] + tp[2]) (if (q.inv) "MONTE" else "DESCENDS") else ""
            val side = if (q.m == "uni") (if (pt < q.dur / 2) "GAUCHE · " else "DROIT · ") else ""
            return Triple(k, q.m == "uni" && pt >= q.dur / 2, side + mv)
        }
        if (q.k == "warm") return Triple(Prog.cycleK(pt, q.tp ?: listOf(1.0, 0.0, 1.2, 0.0)), false, "")
        q.ghostTp?.let { return Triple(Prog.cycleK(pt, it), false, "") }
        return Triple(0.0, false, "")
    }

    fun repTxt(): String {
        val q = P[pi]; if (q.k != "work" || q.m == "time") return ""
        if (q.m == "alt") { val n = floor(pt / q.rt).toInt(); return "Rép ${min(q.reps * 2, n + 1)}/${q.reps * 2} · alterné" }
        val lt = if (q.m == "uni") pt % (q.dur / 2) else pt
        return "Rép ${min(q.reps, floor(lt / q.rt).toInt() + 1)}/${q.reps}"
    }
}

@Composable
fun PlayerScreen(ses: Session, onClose: () -> Unit, onEnd: () -> Unit) {
    LaunchedEffect(ses) { ses.start(); while (true) { withFrameNanos { }; ses.tick(); if (ses.done) { onEnd(); break } } }
    val p = ses.P[ses.pi]
    val col = C.phase(p.k)
    val (k, mir, mv) = ses.anim()
    Column(Modifier.fillMaxSize().background(C.bg).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(PLAB[p.k] ?: "", color = Color(0xFF06261A), fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(col).padding(horizontal = 14.dp, vertical = 6.dp))
            Spacer(Modifier.weight(1f))
            Text(p.blk, color = C.muted, fontSize = 12.sp, letterSpacing = 1.sp, textAlign = TextAlign.End, modifier = Modifier.padding(end = 8.dp).weight(2f))
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp).border(1.dp, C.line, CircleShape)) { Icon(Icons.Default.Close, "Fermer", tint = C.ink) }
        }
        if (p.k == "switch") Text(if (p.ei == 0) "PREMIER EXERCICE" else "NOUVEL EXERCICE", color = col, letterSpacing = 2.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
        Text(p.n, color = C.ink, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        if (p.det.isNotBlank()) Text(p.det, color = C.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp).clip(RoundedCornerShape(18.dp))) {
            val ghost = p.k in listOf("prep", "rest", "switch")
            Body3D(BodyState(pose = p.pose ?: "calf", k = k, mirror = mir, ghost = ghost, nodb = p.nodb || p.pose == null, hl = if (p.k == "prep") null else p.pose), Modifier.fillMaxSize())
            if (mv.isNotBlank()) Text(mv, color = col, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
        }
        val prevW = ses.P.getOrNull(ses.pi - 1)
        if (prevW?.k == "work" && p.k in listOf("rest", "switch", "cool") && prevW.pose != null) SetPanel(ses, prevW)
        else {
            val tip = if (p.k == "rest") (p.chg ?: "") else p.tip
            if (ses.repTxt().isNotBlank()) Text(ses.repTxt(), color = C.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (tip.isNotBlank()) Text(tip, color = if (p.chg != null && p.k != "work") C.warm else C.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        Text("${ceil(max(0.0, p.dur - ses.pt)).toInt()}", color = col, fontSize = 84.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        LinearProgressIndicator(progress = { (ses.pt / max(1.0, p.dur)).toFloat().coerceIn(0f, 1f) }, color = col, trackColor = C.line, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("Écoulé ${Prog.fmt(ses.elapsed)}", color = C.muted, fontSize = 13.sp); Spacer(Modifier.weight(1f))
            Text("Restant ${Prog.fmt(ses.totalDur - ses.elapsed)}", color = C.muted, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(Icons.Default.SkipPrevious, "Précédent") { ses.prev() }
            FilledIconButton(onClick = { ses.toggle() }, modifier = Modifier.size(width = 92.dp, height = 64.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = C.ink)) {
                Icon(if (ses.run) Icons.Default.Pause else Icons.Default.PlayArrow, if (ses.run) "Pause" else "Reprendre", tint = C.bg, modifier = Modifier.size(34.dp))
            }
            RoundBtn(Icons.Default.SkipNext, "Suivant") { ses.next() }
            RoundBtn(if (ses.voice.on) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "Son") { ses.voice.on = !ses.voice.on }
        }
    }
}

@Composable
private fun RoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(60.dp).border(1.dp, C.line, CircleShape)) { Icon(icon, label, tint = C.ink) }
}

/** Panneau « série par série » affiché pendant la pause qui suit une série. */
@Composable
private fun SetPanel(ses: Session, pw: Phase) {
    val id = pw.pose!!; val tgt = ses.tgtOf(pw); val time = pw.m == "time"
    var cur by remember(id, pw.set) { mutableStateOf(ses.slot(id, pw.set, tgt)) }
    fun upd(v: SetRec) { cur = v; ses.setSlot(id, pw.set, v) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text("${Prog.EX[id]?.n} · série ${pw.set}/${ses.nsets}  ·  objectif $tgt" + if (time) " s" else " reps", color = C.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
