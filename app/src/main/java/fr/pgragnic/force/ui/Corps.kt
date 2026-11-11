package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CorpsScreen() {
    val log by Store.log.collectAsState(); val days by Store.days.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(10 * 60_000L); tick++ } }
    val MU = remember(log, days, tick) { Recovery.muscles(log, days) }
    // muscle choisi dans la liste : détail, et seul ce muscle en couleur sur les deux vues (demande de Philippe :
    // « Détails pour chaque muscle, en cliquant dessus »)
    var sel by remember { mutableStateOf(Demo.muscle) }
    val colors = remember(MU, C.dark, sel) { Recovery.MUS.filter { sel == null || it == sel }.associateWith { Recovery.recCol(MU.R[it]!!, C.rgb(C.danger), C.rgb(C.warm), C.rgb(C.effort)) } }   // muscle choisi seul en couleur, les autres gris clair
    val st by Store.st.collectAsState(); val gear by GearStore.g.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Corps", "Body"), fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
            Spacer(Modifier.weight(1f)); Text(t("Récupération", "Recovery"), color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
            HelpButton("corps")
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
        // liste complète (Philippe : « Montre tous les exercices y compris ceux à 100% ») : muscles à laisser reposer d'abord,
        // puis les muscles prêts avec la même ligne détaillée ; toucher un muscle ouvre son détail
        val tired = Recovery.MUS.filter { MU.R[it]!! < 90 }.sortedBy { MU.R[it]!! }; val ready = Recovery.MUS.filter { MU.R[it]!! >= 90 }.sortedBy { MU.R[it]!! }
        @Composable fun row(m: String) {
            val r = MU.R[m]!!; val c = if (r >= 90) C.effort else if (r >= 60) C.warm else C.danger; val h = MU.ready[m] ?: 0
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.surface).clickable { sel = if (sel == m) null else m }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 6.dp, height = 32.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(fr.pgragnic.force.tr(m), color = C.ink, fontWeight = FontWeight.Bold)
                        Text(if (r >= 90) t("Prêt à travailler", "Ready to train") else t("Prêt dans ~", "Ready in ~") + if (h >= 24) t("${h / 24} j ${h % 24} h", "${h / 24} d ${h % 24} h") else "$h h", color = C.muted, fontSize = 12.sp)
                    }
                    Text("$r %", color = c, fontWeight = FontWeight.Bold)
                    Icon(if (sel == m) androidx.compose.material.icons.Icons.Default.ExpandLess else androidx.compose.material.icons.Icons.Default.ExpandMore, null, tint = C.muted, modifier = Modifier.padding(start = 6.dp))
                }
                if (sel == m) MuscleDetail(m, log, st, gear, MU.V[m]!!, MU.tot)
            }
        }
        if (tired.isNotEmpty()) {
            Text(t("À laisser reposer", "Let these recover"), color = C.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            tired.forEach { row(it) }
        } else Text(t("Tous tes muscles sont prêts.", "All your muscles are ready."), color = C.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (ready.isNotEmpty()) {
            Text(t("Prêts", "Ready"), color = C.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
            ready.forEach { row(it) }
        }
    }
}

/** Couleurs de la scène 3D selon le thème. */
fun BodyState.th() = copy(bg = C.rgb(C.stage), ground = C.rgb(C.stage2), mat = C.rgb(C.stage3), db = C.rgb(C.effort), base = C.rgb(C.mrest), acc = C.rgb(C.mhot))

/** Détail d'un muscle : dernier travail, exercices des 7 derniers jours, exercices prévus cette semaine. */
@Composable
private fun MuscleDetail(m: String, log: List<Entry>, st: Settings, gear: Gear, v: Double, tot: Double) {
    val now = System.currentTimeMillis(); val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE d", fr.pgragnic.force.L.loc)
    fun role(id: String) = Prog.MAP[id]?.get(m) ?: 0.0
    val recent = log.filter { now - it.d <= 7 * 864e5 }.sortedByDescending { it.d }.flatMap { e -> e.ex.filter { role(it.id) > 0 }.map { e to it } }
    val last = log.filter { e -> e.ex.any { role(it.id) >= .3 } }.maxByOrNull { it.d }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(C.bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (v > 0) Text(t("${Prog.fk(v)} séries ces 7 derniers jours · ${(100 * v / tot).roundToInt()} % du volume", "${Prog.fk(v)} sets in the last 7 days · ${(100 * v / tot).roundToInt()}% of volume"), color = C.muted, fontSize = 12.sp)
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
