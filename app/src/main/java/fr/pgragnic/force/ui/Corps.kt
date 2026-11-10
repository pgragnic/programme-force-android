package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.*
import fr.pgragnic.force.gl.Body3D
import fr.pgragnic.force.gl.BodyState
import kotlin.math.PI
import kotlin.math.roundToInt

private fun Int.col() = Color(0xFF000000 or this.toLong())

/** Onglet Corps : récupération des muscles, de face et de dos. */
@Composable
fun CorpsScreen() {
    val log by Store.log.collectAsState(); val days by Store.days.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(10 * 60_000L); tick++ } }
    val MU = remember(log, days, tick) { Recovery.muscles(log, days) }
    // muscle choisi dans la liste : détail, et seul ce muscle en couleur sur les deux vues (demande de Philippe :
    // « Détails pour chaque muscle, en cliquant dessus »)
    var sel by remember { mutableStateOf(Demo.muscle) }
    val colors = remember(MU, C.dark, sel) { Recovery.MUS.associateWith { if (sel != null && it != sel) C.rgb(C.line) else Recovery.recCol(MU.R[it]!!, C.rgb(C.danger), C.rgb(C.warm), C.rgb(C.effort)) } }
    var open by remember { mutableStateOf(Demo.muscle != null) }
    val st by Store.st.collectAsState(); val gear by GearStore.g.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Muscles", fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
            Spacer(Modifier.weight(1f)); Text("Récupération maintenant", color = C.muted, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().height(330.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Face" to 0f, "Dos" to PI.toFloat()).forEach { (t, az) ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(C.stage)) {
                    Body3D(BodyState(pose = "calf", k = 0.0, nodb = true, colors = colors, az = az, map = true).th(), Modifier.fillMaxWidth().weight(1f))
                    Text(t, color = C.muted, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(6.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Prêt" to C.effort, "En récupération" to C.warm, "Fatigué" to C.danger).forEach { (t, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Text(" $t", color = C.muted, fontSize = 12.sp) }
            }
        }
        Text("Fais glisser pour tourner, touche deux fois pour revenir.", color = C.muted, fontSize = 12.sp)
        Panel {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text("Détail par muscle · séries des 7 derniers jours", color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (open) "▲" else "▼", color = C.muted)
            }
            if (open) Text(t("Touche un muscle pour voir son détail.", "Tap a muscle to see its details."), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            if (open) Recovery.MUS.forEach { m ->
                val r = MU.R[m]!!; val c = if (r >= 90) C.effort else if (r >= 60) C.warm else C.danger; val h = MU.ready[m] ?: 120
                val stt = if (r >= 90) "Prêt" else "Prêt dans ~" + if (h >= 24) "${h / 24} j ${h % 24} h" else "$h h"
                val v = MU.V[m]!!; val sh = if (MU.tot > 0) (100 * v / MU.tot).roundToInt() else 0
                Column(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).clickable { sel = if (sel == m) null else m }) {
                    Row { Text(m, color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("$r %  " + (if (sel == m) "▲" else "▼"), color = c, fontWeight = FontWeight.Bold) }
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.line)) {
                        Box(Modifier.fillMaxWidth(r / 100f).fillMaxHeight().background(c))
                    }
                    Row {
                        Text(stt, color = C.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(if (v > 0) t("${Prog.fk(v)} séries · $sh % du volume", "${Prog.fk(v)} sets · $sh% of volume") else if (r < 100) "Fatigue due à la marche" else "Pas travaillé cette semaine", color = C.muted, fontSize = 12.sp)
                    }
                    if (sel == m) MuscleDetail(m, log, st, gear)
                }
            }
        }
    }
}

/** Couleurs de la scène 3D selon le thème. */
fun BodyState.th() = copy(bg = C.rgb(C.stage), ground = C.rgb(C.stage2), mat = C.rgb(C.stage3), db = C.rgb(C.effort), base = C.rgb(C.mrest), acc = C.rgb(C.mhot))

/** Détail d'un muscle : dernier travail, exercices des 7 derniers jours, exercices prévus cette semaine. */
@Composable
private fun MuscleDetail(m: String, log: List<Entry>, st: Settings, gear: Gear) {
    val now = System.currentTimeMillis(); val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE d", fr.pgragnic.force.L.loc)
    fun role(id: String) = Prog.MAP[id]?.get(m) ?: 0.0
    val recent = log.filter { now - it.d <= 7 * 864e5 }.sortedByDescending { it.d }.flatMap { e -> e.ex.filter { role(it.id) > 0 }.map { e to it } }
    val last = log.filter { e -> e.ex.any { role(it.id) >= .3 } }.maxByOrNull { it.d }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(C.bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(last?.let { e -> val d = ((now - e.d) / 864e5).toInt(); t("Dernier travail : ", "Last worked: ") + (if (d == 0) t("aujourd'hui", "today") else if (d == 1) t("hier", "yesterday") else t("il y a $d jours", "$d days ago")) + t(" (séance ${e.s})", " (workout ${e.s})") }
            ?: t("Pas encore travaillé.", "Not worked yet."), color = C.ink, fontSize = 13.sp)
        if (recent.isNotEmpty()) {
            Text(t("7 derniers jours", "Last 7 days"), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            recent.forEach { (e, x) ->
                val n = if (x.sets.isNotEmpty()) x.sets.size else 3
                val d = java.time.Instant.ofEpochMilli(e.d).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt)
                Text("$d · ${fr.pgragnic.force.tr(Prog.EX[x.id]?.n ?: x.n)} · $n × ${x.done}" + (if (x.kg > 0) " · ${Prog.fk(x.kg)} kg" else "") +
                    (if (role(x.id) >= .7) "" else t(" · secondaire", " · secondary")), color = C.ink, fontSize = 13.sp)
            }
        }
        val wk = Prog.week(st.c, gear)
        val todo = wk.keys.sorted().mapNotNull { s -> wk[s]!!.filter { role(it) >= .3 }.takeIf { it.isNotEmpty() }?.let { s to it } }
        if (todo.isNotEmpty()) {
            Text(t("Au programme", "In your program"), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            todo.forEach { (s, ids) ->
                Text(t("Séance $s : ", "Workout $s: ") + ids.joinToString(", ") { id -> fr.pgragnic.force.tr(Prog.EX[id]?.n ?: id) + if (role(id) >= .7) "" else t(" (secondaire)", " (secondary)") }, color = C.ink, fontSize = 13.sp)
            }
        }
    }
}
