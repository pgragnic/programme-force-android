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
    val colors = remember(MU, C.dark, sel) { Recovery.MUS.filter { sel == null || it == sel }.associateWith { if (MU.over(it)) Recovery.overCol(C.rgb(C.danger)) else Recovery.recCol(MU.R[it]!!, C.rgb(C.danger), C.rgb(C.warm), C.rgb(C.effort)) } }   // muscle choisi seul en couleur, les autres gris clair
    val st by Store.st.collectAsState(); val gear by GearStore.g.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Corps", "Body"), fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
            Spacer(Modifier.weight(1f)); Text(t("Récupération", "Recovery"), color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
            HelpButton("corps")
        }
        // un seul personnage, qu'on fait tourner au doigt ou avec Face / Dos (Philippe, capture du 11 novembre : « Un seul corps suffit
        // puisqu'on peut le tourner »)
        var back by remember { mutableStateOf(false) }
        // bouton allumé selon le côté réellement visible, y compris après une rotation au doigt (capture de Philippe, 13 novembre 20 h 37)
        var shownBack by remember { mutableStateOf(false) }; var viewKey by remember { mutableIntStateOf(0) }
        val onView = remember { { b: Boolean -> shownBack = b } }
        // muscle touché sur le personnage : bulle avec % et temps avant d'être prêt (Philippe, 12 novembre : « Est-ce qu'on pourrait avoir
        // plus de détails que les 3 niveaux actuels des récupération ? », maquette A, « J'aime bien l'option A »)
        var pick by remember { mutableStateOf<Triple<String, Float, Float>?>(null) }
        LaunchedEffect(pick) { if (pick != null) { kotlinx.coroutines.delay(4000); pick = null } }
        val onPick = remember { { n: String?, x: Float, y: Float -> pick = n?.let { Triple(it, x, y) } } }
        val dens = androidx.compose.ui.platform.LocalDensity.current
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.stage), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(330.dp)) {
                Body3D(BodyState(pose = "calf", k = 0.0, nodb = true, colors = colors, az = if (back) PI.toFloat() else 0f, map = true, onPick = onPick, onView = onView, viewKey = viewKey).th(), Modifier.fillMaxSize())
                pick?.let { (m, x, y) ->
                    val r = MU.R[m] ?: 100; val h = MU.ready[m] ?: 0
                    val wPx = with(dens) { 150.dp.toPx() }; val maxX = with(dens) { maxWidth.toPx() } - wPx
                    val ox = (x + with(dens) { 14.dp.toPx() }).let { if (it > maxX) x - wPx - with(dens) { 14.dp.toPx() } else it }.coerceAtLeast(0f)
                    val oy = (y - with(dens) { 24.dp.toPx() }).coerceAtLeast(0f)
                    Column(Modifier.offset { androidx.compose.ui.unit.IntOffset(ox.toInt(), oy.toInt()) }.width(150.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(fr.pgragnic.force.tr(m) + " · $r %", color = Color(0xFF111111), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (MU.over(m)) Text(t("surchargé, +${Prog.fk(Prog.r1(MU.ex[m]!!))} séries", "overloaded, +${Prog.fk(Prog.r1(MU.ex[m]!!))} sets"), color = Color(0xFFE3141E), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(if (r >= 90) t("prêt à travailler", "ready to train") else t("prêt dans ~", "ready in ~") + if (h >= 24) t("${h / 24} j ${h % 24} h", "${h / 24} d ${h % 24} h") else "$h h", color = Color(0xFF555555), fontSize = 12.sp)
                    }
                }
            }
            Row(Modifier.padding(8.dp).clip(RoundedCornerShape(18.dp)).background(C.surface).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(t("Face", "Front") to false, t("Dos", "Back") to true).forEach { (lab, v) ->
                    Text(lab, color = if (shownBack == v) C.onColor else C.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(if (shownBack == v) C.effort else Color.Transparent).clickable { back = v; viewKey++ }.padding(horizontal = 18.dp, vertical = 7.dp))
                }
            }
        }
        Text(t("Fais tourner le personnage avec le doigt, touche un muscle pour voir sa récupération.", "Rotate the character with your finger, tap a muscle to see its recovery."), color = C.muted, fontSize = 12.sp)
        // échelle continue (option A) : même dégradé que le personnage, de fatigué (0 %) à prêt (90 % et plus)
        Column {
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient(
                0f to C.danger, .6f to C.warm, .9f to C.effort, 1f to C.effort)))
            Box(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                Text(t("0 % fatigué", "0% tired"), color = C.muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterStart))
                Text("60 %", color = C.muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(.6f).wrapContentWidth(Alignment.End).align(Alignment.CenterStart))
                Text(t("prêt 90 %", "ready 90%"), color = C.muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(.93f).wrapContentWidth(Alignment.End).align(Alignment.CenterStart))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Recovery.overCol(C.rgb(C.danger)).col()))
                Text(t(" Surchargé : fatigue au-delà de 0 %", " Overloaded: fatigue beyond 0%"), color = C.muted, fontSize = 11.sp)
            }
        }
        // liste complète (Philippe : « Montre tous les exercices y compris ceux à 100% ») : muscles à laisser reposer d'abord,
        // puis les muscles prêts avec la même ligne détaillée ; toucher un muscle ouvre son détail
        val tired = Recovery.MUS.filter { MU.R[it]!! < 90 }.sortedBy { MU.R[it]!! }; val ready = Recovery.MUS.filter { MU.R[it]!! >= 90 }.sortedBy { MU.R[it]!! }
        @Composable fun row(m: String) {
            val r = MU.R[m]!!; val ov = MU.over(m); val c = (if (ov) Recovery.overCol(C.rgb(C.danger)) else Recovery.recCol(r, C.rgb(C.danger), C.rgb(C.warm), C.rgb(C.effort))).col(); val h = MU.ready[m] ?: 0
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.surface).clickable { sel = if (sel == m) null else m }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 6.dp, height = 32.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(fr.pgragnic.force.tr(m), color = C.ink, fontWeight = FontWeight.Bold)
                        // jauge du muscle, dans la couleur exacte de son niveau
                        Box(Modifier.padding(vertical = 3.dp).fillMaxWidth(.8f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(C.bg)) {
                            Box(Modifier.fillMaxWidth(r / 100f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c))
                        }
                        Text((if (ov) t("Surchargé (+${Prog.fk(Prog.r1(MU.ex[m]!!))} séries) · ", "Overloaded (+${Prog.fk(Prog.r1(MU.ex[m]!!))} sets) · ") else "") +
                            (if (r >= 90) t("Prêt à travailler", "Ready to train") else t("Prêt dans ~", "Ready in ~") + if (h >= 24) t("${h / 24} j ${h % 24} h", "${h / 24} d ${h % 24} h") else "$h h"), color = C.muted, fontSize = 12.sp)
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
